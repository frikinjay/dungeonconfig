package com.frikinjay.dungeonconfig.client.ui;

import com.frikinjay.dungeonconfig.mixin.client.NativeImageAccessor;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public final class DungeonsCanvas extends DungeonsCanvasBase {

    public final GuiGraphicsExtractor graphics;
    public final Font font;

    public DungeonsCanvas(GuiGraphicsExtractor graphics, Font font, float scale) {
        super(scale, devicePixels(scale));
        this.graphics = graphics;
        this.font = font;
    }

    private static int devicePixels(float scale) {
        var window = Minecraft.getInstance().getWindow();
        double gui = (double) window.getWidth() / Math.max(1, window.getGuiScaledWidth());
        return Math.max(1, (int) Math.round(scale * gui));
    }

    public void begin() {
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
    }

    public void end() {
        graphics.pose().popMatrix();
    }

    @Override protected void posePush() { graphics.pose().pushMatrix(); }
    @Override protected void posePop() { graphics.pose().popMatrix(); }
    @Override protected void poseTranslate(float x, float y) { graphics.pose().translate(x, y); }
    @Override protected void poseScale(float s) { graphics.pose().scale(s, s); }

    @Override
    protected void rawFill(int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1, y1, color);
    }

    @Override
    protected void rawGradientV(int x0, int y0, int x1, int y1, int top, int bottom) {
        graphics.fillGradient(x0, y0, x1, y1, top, bottom);
    }

    @Override
    protected void rawScissor(int x0, int y0, int x1, int y1) {
        graphics.enableScissor(x0, y0, x1, y1);
    }

    @Override
    public void endScissor() {
        graphics.disableScissor();
    }

    @Override
    public void beginOverlay() {
        graphics.nextStratum();
    }

    @Override
    public void endOverlay() {
    }

    @Override
    protected void rawImage(Texture t, int x, int y, int w, int h, float u, float v, int uw, int vh, int color) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, t.id, x, y, u, v, w, h, uw, vh, t.width, t.height, color);
    }

    @Override
    protected void rawGlyphs(Texture t, int[] q, int count, int color) {
        for (int i = 0; i < count; i++) {
            int o = i * 6;
            graphics.blit(RenderPipelines.GUI_TEXTURED, t.id, q[o], q[o + 1], q[o + 4], q[o + 5], q[o + 2], q[o + 3],
                    q[o + 2], q[o + 3], t.width, t.height, color);
        }
    }

    @Override
    protected void vanillaText(String text, float x, float y, float size, int color, boolean shadow) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(size, size);
        graphics.text(font, text, 0, 0, color, shadow);
        graphics.pose().popMatrix();
    }

    @Override
    protected int vanillaWidth(String text) {
        return font.width(text);
    }

    public static final class Texture {
        public final Identifier id;
        public final int width;
        public final int height;
        private DynamicTexture stream;

        private Texture(Identifier id, int width, int height) {
            this.id = id;
            this.width = width;
            this.height = height;
        }
    }

    @SuppressWarnings("resource")
    public static Texture upload(String namespace, String path, int[] argb, int width, int height, boolean smooth) {
        NativeImage image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixel(x, y, argb[y * width + x]);
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
        Minecraft.getInstance().getTextureManager().register(id, new UiTexture(path, image, smooth));
        return new Texture(id, width, height);
    }

    public static final String STREAM_PIXEL_FORMAT = "bgra";

    public static int streamPixel(int argb) {
        return argb;
    }

    @SuppressWarnings("resource")
    public static Texture createStream(String namespace, String path, int width, int height, boolean smooth) {
        NativeImage image = new NativeImage(width, height, true);
        Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
        UiTexture tex = new UiTexture(path, image, smooth);
        Minecraft.getInstance().getTextureManager().register(id, tex);
        Texture texture = new Texture(id, width, height);
        texture.stream = tex;
        return texture;
    }

    public static void writeStream(Texture texture, java.nio.IntBuffer pixels) {
        NativeImage image = texture.stream == null ? null : texture.stream.getPixels();
        if (image == null) return;
        int w = Math.min(texture.width, image.getWidth()), h = Math.min(texture.height, image.getHeight());
        for (int y = 0; y < h; y++) {
            int row = y * texture.width;
            for (int x = 0; x < w; x++) image.setPixel(x, y, pixels.get(row + x));
        }
    }

    public static long streamAddress(Texture texture) {
        try {
            NativeImage image = texture.stream == null ? null : texture.stream.getPixels();
            long address = streamPointer(texture);
            if (image == null || address == 0) return 0;
            image.setPixel(0, 0, 0x80402010);
            boolean ok = org.lwjgl.system.MemoryUtil.memGetInt(address) == 0x80102040;
            image.setPixel(0, 0, 0);
            return ok ? address : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static long streamPointer(Texture texture) {
        try {
            NativeImage image = texture.stream == null ? null : texture.stream.getPixels();
            if (image == null) return 0;
            return ((NativeImageAccessor) (Object) image).dungeonconfig$pixels();
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void uploadStream(Texture texture) {
        if (texture.stream != null) texture.stream.upload();
    }

    public static void release(Texture texture) {
        Minecraft.getInstance().getTextureManager().release(texture.id);
    }

    public static InputStream openResource(String namespace, String path) throws IOException {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager()
                .getResource(Identifier.fromNamespaceAndPath(namespace, path));
        return resource.isPresent() ? resource.get().open() : null;
    }

    public static boolean hasResource(String namespace, String path) {
        try {
            return Minecraft.getInstance().getResourceManager().getResource(Identifier.fromNamespaceAndPath(namespace, path)).isPresent();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static java.util.List<String> listResources(String namespace, String folder) {
        java.util.List<String> paths = new java.util.ArrayList<>();
        try {
            for (Identifier id : Minecraft.getInstance().getResourceManager().listResources(folder, id -> id.getNamespace().equals(namespace)).keySet()) {
                paths.add(id.getPath());
            }
        } catch (RuntimeException ignored) {
        }
        paths.sort(null);
        return paths;
    }

    private static final class UiTexture extends DynamicTexture {
        UiTexture(String label, NativeImage image, boolean smooth) {
            super(() -> label, image);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(smooth ? FilterMode.LINEAR : FilterMode.NEAREST);
        }
    }
}
