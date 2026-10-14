package com.frikinjay.dungeonconfig.util;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Decodes PNG, JPEG, GIF, BMP, ICO, WebP and other images into ARGB pixels.
 * Shared by Dungeon Config and mods built on it.
 */
public final class ImageDecoder {

    public static final int MAX_DIMENSION = 4096;

    public record Image(int[] argb, int width, int height) {

        /**
         * Returns this image centred on a transparent square canvas, or itself if already square.
         */
        public Image square() {
            if (width == height) return this;
            int side = Math.max(width, height);
            int[] out = new int[side * side];
            int ox = (side - width) / 2, oy = (side - height) / 2;
            for (int y = 0; y < height; y++) {
                System.arraycopy(argb, y * width, out, (y + oy) * side + ox, width);
            }
            return new Image(out, side, side);
        }
    }

    @FunctionalInterface
    private interface Decoder {
        @Nullable
        Image decode() throws Exception;
    }

    private ImageDecoder() {}

    public static boolean isSvg(byte[] data) {
        int n = Math.min(data.length, 1024);
        String head = new String(data, 0, n, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return head.contains("<svg") || (head.trim().startsWith("<?xml") && head.contains("svg"));
    }

    public static boolean isWebp(byte[] data) {
        return data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
    }

    /**
     * Decodes an image from a trusted source (the jar, resource packs or the config folder).
     */
    public static Image decode(byte[] data) throws Exception {
        return orFail(() -> {
            if (isIco(data)) {
                Image ico = decodeIco(data, false);
                if (ico != null) return ico;
            }
            Image stb = decodeStb(data);
            return stb != null ? stb : decodeImageIo(data);
        }, "Unsupported image format");
    }

    /**
     * Decodes a downloaded image. Uses ImageIO and keeps native stb away from untrusted input,
     * except for WebP which ImageIO can't read.
     */
    public static Image decodeUntrusted(byte[] data) throws Exception {
        return orFail(() -> {
            if (isIco(data)) {
                Image ico = decodeIco(data, true);
                if (ico != null) return ico;
            }
            Image awt = decodeImageIo(data);
            if (awt != null) return awt;
            return isWebp(data) ? decodeStb(data) : null;
        }, "Unsupported image format for a download (use PNG, JPEG, GIF, BMP, ICO, WebP or SVG)");
    }

    private static Image orFail(Decoder decoder, String unsupported) throws Exception {
        Image image;
        try {
            image = decoder.decode();
        } catch (Exception e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalArgumentException("Image could not be decoded", t);
        }
        if (image == null) throw new IllegalArgumentException(unsupported);
        return image;
    }

    @Nullable
    private static Image decodeStb(byte[] data) throws IllegalArgumentException {
        if (data.length == 0) return null;
        ByteBuffer input = MemoryUtil.memAlloc(data.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            input.put(data).flip();
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), comp = stack.mallocInt(1);
            if (!STBImage.stbi_info_from_memory(input, w, h, comp)) return null;
            if (w.get(0) <= 0 || h.get(0) <= 0) return null;
            if (w.get(0) > MAX_DIMENSION || h.get(0) > MAX_DIMENSION) throw new IllegalArgumentException("Image is too big");
            ByteBuffer pixels = STBImage.stbi_load_from_memory(input, w, h, comp, 4);
            if (pixels == null) return null;
            try {
                int width = w.get(0), height = h.get(0);
                if (!fits(width, height) || (long) width * height * 4 > pixels.capacity()) return null;
                int[] argb = new int[width * height];
                for (int i = 0; i < argb.length; i++) {
                    int r = pixels.get(i * 4) & 0xFF, g = pixels.get(i * 4 + 1) & 0xFF;
                    int b = pixels.get(i * 4 + 2) & 0xFF, a = pixels.get(i * 4 + 3) & 0xFF;
                    argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
                }
                return new Image(argb, width, height);
            } finally {
                STBImage.stbi_image_free(pixels);
            }
        } finally {
            MemoryUtil.memFree(input);
        }
    }

    @Nullable
    private static Image decodeImageIo(byte[] data) {
        try (javax.imageio.stream.ImageInputStream stream = javax.imageio.ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            var readers = javax.imageio.ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) return null;
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                if (!fits(reader.getWidth(0), reader.getHeight(0))) return null;
                java.awt.image.BufferedImage image = reader.read(0);
                int width = image.getWidth(), height = image.getHeight();
                if (!fits(width, height)) return null;
                return new Image(image.getRGB(0, 0, width, height, null, 0, width), width, height);
            } finally {
                reader.dispose();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean fits(int width, int height) {
        return width > 0 && height > 0 && width <= MAX_DIMENSION && height <= MAX_DIMENSION;
    }

    private static boolean isIco(byte[] data) {
        return data.length > 6 && data[0] == 0 && data[1] == 0 && data[2] == 1 && data[3] == 0;
    }

    @Nullable
    private static Image decodeIco(byte[] data, boolean untrusted) throws Exception {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int count = buf.getShort(4) & 0xFFFF;
        int best = -1, bestSize = -1;
        for (int i = 0; i < count; i++) {
            int entry = 6 + i * 16;
            if (entry + 16 > data.length) break;
            int w = data[entry] & 0xFF, h = data[entry + 1] & 0xFF;
            int size = Math.max(w == 0 ? 256 : w, h == 0 ? 256 : h);
            if (size > bestSize) {
                bestSize = size;
                best = entry;
            }
        }
        if (best < 0) return null;
        long length = buf.getInt(best + 8) & 0xFFFFFFFFL, offset = buf.getInt(best + 12) & 0xFFFFFFFFL;
        if (length <= 0 || offset + length > data.length) return null;
        byte[] image = new byte[(int) length];
        System.arraycopy(data, (int) offset, image, 0, (int) length);

        if (length > 8 && (image[0] & 0xFF) == 0x89 && image[1] == 'P') {
            return untrusted ? decodeImageIo(image) : decodeStb(image);
        }

        if (length < 16) return null;
        ByteBuffer dib = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN);
        int headerSize = dib.getInt(0);
        int width = dib.getInt(4), rawHeight = dib.getInt(8);
        if (rawHeight == Integer.MIN_VALUE) return null;
        int height = Math.abs(rawHeight) / 2;
        int bpp = dib.getShort(14);
        if (bpp != 32 || !fits(width, height)) return null;
        if (headerSize < 12 || (long) headerSize + (long) width * height * 4 > image.length) return null;
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            int row = headerSize + (height - 1 - y) * width * 4;
            for (int x = 0; x < width; x++) {
                int p = row + x * 4;
                if (p + 3 >= image.length) return null;
                int b = image[p] & 0xFF, g = image[p + 1] & 0xFF, r = image[p + 2] & 0xFF, a = image[p + 3] & 0xFF;
                argb[y * width + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return new Image(argb, width, height);
    }
}
