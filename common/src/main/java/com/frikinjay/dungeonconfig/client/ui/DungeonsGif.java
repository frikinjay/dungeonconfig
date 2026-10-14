package com.frikinjay.dungeonconfig.client.ui;

import org.jetbrains.annotations.Nullable;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class DungeonsGif {

    private static final int MAX_SIDE = 4096;
    private static final int MAX_FRAMES = 1000;
    private static final long BUDGET_BYTES = 96L * 1024 * 1024;

    public record Animation(int width, int height, int[][] frames, int[] delays) {}

    private DungeonsGif() {}

    public static boolean isGif(byte[] data) {
        return data.length > 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8';
    }

    @Nullable
    public static Animation decode(byte[] data, int maxWidth, int blurRadius) throws Exception {
        if (!isGif(data)) return null;
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) return null;
        ImageReader reader = readers.next();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(in, false, false);
            int count;
            try {
                count = reader.getNumImages(true);
            } catch (Exception e) {
                count = -1;
            }
            if (count == 1) return null;

            int screenW = 0, screenH = 0;
            IIOMetadata streamMeta = reader.getStreamMetadata();
            if (streamMeta != null) {
                Node lsd = child(streamMeta.getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
                screenW = intAttr(lsd, "logicalScreenWidth", 0);
                screenH = intAttr(lsd, "logicalScreenHeight", 0);
            }
            if (screenW <= 0 || screenH <= 0) {
                BufferedImage first = reader.read(0);
                screenW = first.getWidth();
                screenH = first.getHeight();
            }
            if (screenW > MAX_SIDE || screenH > MAX_SIDE) throw new IllegalArgumentException("GIF is too large");

            int outW = Math.max(1, Math.min(maxWidth, screenW));
            int outH = Math.max(1, Math.round(screenH * (outW / (float) screenW)));
            int frameLimit = count > 0 ? Math.min(count, MAX_FRAMES) : MAX_FRAMES;
            long perFrame = (long) outW * outH * 4;
            if (perFrame * frameLimit > BUDGET_BYTES) {
                float shrink = (float) Math.sqrt(BUDGET_BYTES / (double) (perFrame * frameLimit));
                int minW = Math.min(outW, 160);
                outW = Math.max(minW, Math.round(outW * shrink));
                outH = Math.max(1, Math.round(screenH * (outW / (float) screenW)));
                perFrame = (long) outW * outH * 4;
            }
            int step = (int) Math.max(1, (perFrame * frameLimit + BUDGET_BYTES - 1) / BUDGET_BYTES);

            BufferedImage canvas = new BufferedImage(screenW, screenH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            g.setComposite(AlphaComposite.SrcOver);
            List<int[]> frames = new ArrayList<>();
            List<Integer> delays = new ArrayList<>();
            int[] full = new int[screenW * screenH];
            int pendingDelay = 0;
            try {
                for (int i = 0; i < frameLimit; i++) {
                    BufferedImage frame;
                    IIOMetadata meta;
                    try {
                        frame = reader.read(i);
                        meta = reader.getImageMetadata(i);
                    } catch (IndexOutOfBoundsException end) {
                        break;
                    } catch (Exception broken) {
                        if (frames.isEmpty()) throw broken;
                        break;
                    }
                    Node root = meta.getAsTree("javax_imageio_gif_image_1.0");
                    Node desc = child(root, "ImageDescriptor");
                    Node gce = child(root, "GraphicControlExtension");
                    int left = intAttr(desc, "imageLeftPosition", 0), top = intAttr(desc, "imageTopPosition", 0);
                    int fw = frame.getWidth(), fh = frame.getHeight();
                    String disposal = gce == null ? "none" : strAttr(gce, "disposalMethod", "none");
                    int delay = gce == null ? 0 : intAttr(gce, "delayTime", 0) * 10;
                    if (delay < 20) delay = 100;

                    BufferedImage saved = null;
                    if ("restoreToPrevious".equals(disposal)) {
                        saved = new BufferedImage(screenW, screenH, BufferedImage.TYPE_INT_ARGB);
                        saved.setData(canvas.getRaster());
                    }
                    g.drawImage(frame, left, top, null);

                    pendingDelay += delay;
                    if (i % step == 0) {
                        canvas.getRGB(0, 0, screenW, screenH, full, 0, screenW);
                        frames.add(prepare(full, screenW, screenH, outW, outH, blurRadius));
                        delays.add(pendingDelay);
                        pendingDelay = 0;
                    } else if (!delays.isEmpty()) {
                        delays.set(delays.size() - 1, delays.get(delays.size() - 1) + delay);
                        pendingDelay = 0;
                    }

                    if ("restoreToBackgroundColor".equals(disposal)) {
                        g.setComposite(AlphaComposite.Clear);
                        g.fillRect(left, top, fw, fh);
                        g.setComposite(AlphaComposite.SrcOver);
                    } else if (saved != null) {
                        canvas.setData(saved.getRaster());
                    }
                }
            } finally {
                g.dispose();
            }
            if (frames.size() < 2) return null;
            int[] delayArray = new int[delays.size()];
            for (int i = 0; i < delayArray.length; i++) delayArray[i] = Math.max(20, delays.get(i));
            return new Animation(outW, outH, frames.toArray(new int[0][]), delayArray);
        } finally {
            reader.dispose();
        }
    }

    private static int[] prepare(int[] argb, int w, int h, int outW, int outH, int blurRadius) {
        int[] pixels = outW == w && outH == h ? argb.clone() : DungeonsBackground.scale(argb, w, h, outW, outH);
        if (blurRadius > 0) pixels = DungeonsBackground.blurPixels(pixels, outW, outH, blurRadius);
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            if ((p >>> 24) < 255) p = blendOverBackdrop(p);
            pixels[i] = DungeonsCanvas.streamPixel(p);
        }
        return pixels;
    }

    private static int blendOverBackdrop(int p) {
        int a = p >>> 24, bg = DungeonsTheme.BG_BOTTOM;
        int r = (((p >> 16) & 0xFF) * a + ((bg >> 16) & 0xFF) * (255 - a)) / 255;
        int gr = (((p >> 8) & 0xFF) * a + ((bg >> 8) & 0xFF) * (255 - a)) / 255;
        int b = ((p & 0xFF) * a + (bg & 0xFF) * (255 - a)) / 255;
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

    @Nullable
    private static Node child(Node parent, String name) {
        if (parent == null) return null;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (name.equals(n.getNodeName())) return n;
        }
        return null;
    }

    private static int intAttr(@Nullable Node node, String name, int fallback) {
        String v = strAttr(node, name, null);
        if (v == null) return fallback;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Nullable
    private static String strAttr(@Nullable Node node, String name, @Nullable String fallback) {
        if (node == null) return fallback;
        NamedNodeMap attrs = node.getAttributes();
        Node a = attrs == null ? null : attrs.getNamedItem(name);
        return a == null ? fallback : a.getNodeValue();
    }
}
