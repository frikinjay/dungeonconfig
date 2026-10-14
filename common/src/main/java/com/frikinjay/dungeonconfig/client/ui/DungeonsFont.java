package com.frikinjay.dungeonconfig.client.ui;

import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DungeonsFont {

    public final String name;
    private final java.awt.Font base;
    private final int unitsPerEm;
    public final int grid;
    private final boolean antialias;
    private final float capRatio;
    private final Map<Integer, Strike> strikes = new HashMap<>();
    private final Map<Integer, Strike> smoothStrikes = new HashMap<>();
    DungeonsFont fallback;
    private static int nextId = 0;
    final int id = nextId++;
    private static int nextVersion = 0;

    private DungeonsFont(String name, java.awt.Font base, int unitsPerEm, int grid, boolean antialias, float capRatio) {
        this.name = name;
        this.base = base;
        this.unitsPerEm = unitsPerEm;
        this.grid = grid;
        this.antialias = antialias;
        this.capRatio = capRatio;
    }

    public static DungeonsFont load(String name, byte[] data, int pixelSize) throws IOException {
        try {
            return create(name, data, pixelSize);
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException("Font system failed on " + name + ": " + t, t);
        }
    }

    private static DungeonsFont create(String name, byte[] data, int pixelSize) throws IOException {
        java.awt.Font font;
        try {
            font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, new ByteArrayInputStream(data));
        } catch (FontFormatException e) {
            throw new IOException("Not a usable TrueType font: " + name, e);
        }
        int upm = unitsPerEm(data);
        java.awt.Font em = font.deriveFont((float) upm);
        FontRenderContext frc = new FontRenderContext(null, false, true);
        List<float[]> points = new ArrayList<>();
        boolean curves = false;
        float capUnits = 0;
        for (char ch : "HAMNaxgoesy0".toCharArray()) {
            if (!font.canDisplay(ch)) continue;
            Shape outline = em.createGlyphVector(frc, new char[]{ch}).getGlyphOutline(0);
            if (ch == 'H') capUnits = (float) outline.getBounds2D().getHeight();
            float[] seg = new float[6];
            for (PathIterator it = outline.getPathIterator(null); !it.isDone(); it.next()) {
                int type = it.currentSegment(seg);
                if (type == PathIterator.SEG_QUADTO || type == PathIterator.SEG_CUBICTO) curves = true;
                if (type != PathIterator.SEG_CLOSE) points.add(new float[]{seg[0], seg[1]});
            }
        }
        int grid = pixelSize > 0 ? Math.min(pixelSize, MAX_PX) : 0;
        if (pixelSize == 0 && !curves) grid = detectGrid(points, upm);
        boolean antialias = pixelSize < 0 || (grid == 0 && curves);
        float capRatio = capUnits > 0 ? capUnits / upm : 0.7F;
        return new DungeonsFont(name, font, upm, grid, antialias, capRatio);
    }

    private static int detectGrid(List<float[]> points, int upm) {
        if (points.isEmpty()) return 0;
        for (int n = 5; n <= 40; n++) {
            int ok = 0;
            for (float[] p : points) {
                if (near(p[0] * n / upm) && near(p[1] * n / upm)) ok++;
            }
            if (ok >= points.size() * 0.97F) return n;
        }
        return 0;
    }

    private static boolean near(float v) {
        return Math.abs(v - Math.round(v)) < 0.08F;
    }

    private static int unitsPerEm(byte[] data) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(data);
            int tables = buf.getShort(4) & 0xFFFF;
            for (int i = 0; i < tables; i++) {
                int rec = 12 + i * 16;
                if (buf.getInt(rec) == 0x68656164) { // "head"
                    int upm = buf.getShort(buf.getInt(rec + 8) + 18) & 0xFFFF;
                    if (upm >= 16) return upm;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return 1000;
    }

    static final int MAX_PX = 256;
    private static final int MAX_ATLAS = 4096;

    private static final List<DungeonsCanvas.Texture> retired = new ArrayList<>();
    private static final List<Long> retiredAt = new ArrayList<>();
    private static int frame = 0;
    private static int sweepAt = -1;

    static void retire(DungeonsCanvas.Texture texture) {
        retired.add(texture);
        retiredAt.add(System.currentTimeMillis());
    }

    public static void collect() {
        frame++;
        if (sweepAt >= 0 && frame >= sweepAt) {
            sweepAt = -1;
            DungeonsFonts.dropUnusedStrikes(frame - 2);
        }
        long now = System.currentTimeMillis();
        while (!retired.isEmpty() && now - retiredAt.get(0) > 1000) {
            DungeonsCanvas.release(retired.remove(0));
            retiredAt.remove(0);
        }
    }

    public static void collectAll() {
        for (DungeonsCanvas.Texture t : retired) DungeonsCanvas.release(t);
        retired.clear();
        retiredAt.clear();
    }

    static void scheduleSweep() {
        sweepAt = frame + 3;
    }

    void dropStrikesBefore(int usedFrame) {
        for (Map<Integer, Strike> map : List.of(strikes, smoothStrikes)) {
            map.values().removeIf(s -> {
                if (s.lastUsed >= usedFrame) return false;
                s.release();
                return true;
            });
        }
    }

    public boolean canDisplay(int codePoint) {
        return base.canDisplay(codePoint);
    }

    public Strike forCap(float capPixels) {
        int px;
        if (grid > 0) {
            int cap = strike(grid).cap;
            int m = Math.max(1, Math.round(capPixels / Math.max(1, cap)));
            px = grid * Math.min(m, Math.max(1, MAX_PX / grid));
        } else {
            px = Math.max(6, Math.round(capPixels / capRatio));
        }
        return strike(px);
    }

    public Strike strike(int px) {
        Strike s = strikes.computeIfAbsent(Math.max(1, Math.min(MAX_PX, px)), p -> new Strike(this, p, false));
        s.lastUsed = frame;
        return s;
    }

    public int nativeCap() {
        return grid > 0 ? strike(grid).cap : 0;
    }

    public Strike smoothForCap(float capPixels) {
        int px = Math.max(4, Math.min(MAX_PX, Math.round(capPixels / capRatio)));
        Strike s = smoothStrikes.computeIfAbsent(px, p -> new Strike(this, p, true));
        s.lastUsed = frame;
        return s;
    }

    public void release() {
        for (Strike s : strikes.values()) s.release();
        for (Strike s : smoothStrikes.values()) s.release();
        strikes.clear();
        smoothStrikes.clear();
    }

    public static final class Glyph {
        public final int x, y, w, h, u, v;
        public final float advance;
        public final int inkLeft, inkRight;

        Glyph(int x, int y, int w, int h, int u, int v, float advance) {
            this(x, y, w, h, u, v, advance, x, x + w);
        }

        Glyph(int x, int y, int w, int h, int u, int v, float advance, int inkLeft, int inkRight) {
            this.inkLeft = inkLeft;
            this.inkRight = inkRight;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.u = u;
            this.v = v;
            this.advance = advance;
        }
    }

    public static final class Strike {
        public final DungeonsFont font;
        public final int px;
        public final boolean smooth;
        public final int cap, ascent, descent;
        public final int unit;
        private final java.awt.Font sized;
        private final FontRenderContext frc;
        private final Map<Integer, Glyph> glyphs = new HashMap<>();
        private int atlasW = 256, atlasH = 256;
        private int[] atlas = new int[atlasW * atlasH];
        private int penX = 1, penY = 1, rowH = 0;
        private boolean dirty = true;
        private DungeonsCanvas.Texture texture;
        int lastUsed;
        private final Map<String, Float> widths = new HashMap<>();
        private final Map<String, String> trims = new HashMap<>();

        Strike(DungeonsFont font, int px, boolean smooth) {
            this.font = font;
            this.px = px;
            this.smooth = smooth;
            this.sized = font.base.deriveFont((float) px);
            this.frc = new FontRenderContext(null, font.antialias || smooth, font.grid == 0 || smooth);
            Rectangle2D h = font.base.canDisplay('H')
                    ? sized.createGlyphVector(frc, "H").getGlyphOutline(0).getBounds2D()
                    : new Rectangle2D.Float(0, -px * 0.7F, 1, px * 0.7F);
            this.cap = Math.max(1, (int) Math.round(h.getHeight()));
            LineMetrics lm = sized.getLineMetrics("Hgjy", frc);
            this.ascent = Math.round(lm.getAscent());
            this.descent = Math.round(lm.getDescent());
            this.unit = font.grid > 0 && !smooth ? Math.max(1, px / font.grid) : Math.max(1, Math.round(cap / 7.0F));
            for (int c = 32; c < 127; c++) glyph(c);
        }

        public Glyph glyph(int codePoint) {
            Glyph g = glyphs.get(codePoint);
            if (g != null) return g;
            try {
                g = rasterise(codePoint);
            } catch (Throwable t) {
                // Fallback to question mark glyph if font glyph rasterization fails
                g = codePoint != '?' ? glyph('?') : new Glyph(0, 0, 0, 0, 0, 0, Math.max(1, px / 2));
            }
            glyphs.put(codePoint, g);
            return g;
        }

        private Glyph rasterise(int codePoint) {
            java.awt.Font face = sized;
            FontRenderContext context = frc;
            int dy = 0;
            if (!font.base.canDisplay(codePoint)) {
                DungeonsFont fb = font.fallback;
                if (fb != null && fb.base.canDisplay(codePoint)) {
                    Strike other = fb.forCap(cap);
                    face = other.sized;
                    context = other.frc;
                } else if (codePoint != '?') {
                    Glyph q = glyph('?');
                    return q;
                }
            }
            GlyphVector gv = face.createGlyphVector(context, Character.toChars(codePoint));
            float advance = gv.getGlyphMetrics(0).getAdvanceX();
            if (font.grid > 0 && !smooth) advance = Math.round(advance);
            Shape outline = gv.getGlyphOutline(0);
            Rectangle2D b = outline.getBounds2D();
            if (b.isEmpty()) return new Glyph(0, 0, 0, 0, 0, 0, advance);
            int x0 = (int) Math.floor(b.getMinX()), y0 = (int) Math.floor(b.getMinY()) + dy;
            int w = (int) Math.ceil(b.getMaxX()) - x0 + 1, h = (int) Math.ceil(b.getMaxY()) - y0 + 1;
            if (w > 512 || h > 512) return new Glyph(0, 0, 0, 0, 0, 0, advance);

            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    context.isAntiAliased() ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(java.awt.Color.WHITE);
            g.translate(-x0, -y0);
            g.fill(outline);
            g.dispose();

            int inkL = w, inkR = -1;
            for (int yy = 0; yy < h; yy++) {
                for (int xx = 0; xx < w; xx++) {
                    if ((img.getRGB(xx, yy) >>> 24) != 0) {
                        if (xx < inkL) inkL = xx;
                        if (xx > inkR) inkR = xx;
                    }
                }
            }
            if (inkR < 0) {
                inkL = 0;
                inkR = -1;
            }

            if (penX + w + 1 > atlasW) {
                penX = 1;
                penY += rowH + 1;
                rowH = 0;
            }
            while (w + 2 > atlasW || penY + h + 1 > atlasH) {
                boolean wide = w + 2 > atlasW, tall = penY + h + 1 > atlasH;
                if ((wide && atlasW >= MAX_ATLAS) || (tall && atlasH >= MAX_ATLAS)) return new Glyph(0, 0, 0, 0, 0, 0, advance);
                grow(wide, tall);
            }
            int u = penX, v = penY;
            for (int yy = 0; yy < h; yy++) {
                for (int xx = 0; xx < w; xx++) {
                    int a = img.getRGB(xx, yy) >>> 24;
                    atlas[(v + yy) * atlasW + u + xx] = (a << 24) | 0xFFFFFF;
                }
            }
            penX += w + 1;
            rowH = Math.max(rowH, h);
            dirty = true;
            return new Glyph(x0, y0, w, h, u, v, advance, x0 + inkL, x0 + inkR + 1);
        }

        private void grow(boolean wide, boolean tall) {
            int nw = wide ? atlasW * 2 : atlasW, nh = tall ? atlasH * 2 : atlasH;
            int[] next = new int[nw * nh];
            for (int y = 0; y < atlasH; y++) System.arraycopy(atlas, y * atlasW, next, y * nw, atlasW);
            atlas = next;
            atlasW = nw;
            atlasH = nh;
            dirty = true;
        }

        public DungeonsCanvas.Texture texture() {
            if (dirty || texture == null) {
                // Delay atlas disposal to avoid invalidating in-flight draw quads
                if (texture != null) retire(texture);
                texture = DungeonsCanvas.upload(DungeonsFonts.namespace, "dungeonsui/font/" + font.id + "_" + px + (smooth ? "s" : "") + "_" + (nextVersion++), atlas, atlasW, atlasH, false);
                dirty = false;
            }
            return texture;
        }

        void release() {
            if (texture != null) retire(texture);
            texture = null;
        }

        public float width(String text) {
            Float cached = widths.get(text);
            if (cached != null) return cached;
            float w = 0;
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                w += glyph(cp).advance;
                i += Character.charCount(cp);
            }
            if (widths.size() > 2048) widths.clear();
            widths.put(text, w);
            return w;
        }

        public float[] ink(String text) {
            float pen = 0, left = Float.NaN, right = 0;
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                i += Character.charCount(cp);
                Glyph g = glyph(cp);
                if (g.inkRight > g.inkLeft) {
                    if (Float.isNaN(left)) left = Math.round(pen) + g.inkLeft;
                    right = Math.round(pen) + g.inkRight;
                }
                pen += g.advance;
            }
            return Float.isNaN(left) ? new float[]{0, pen} : new float[]{left, right};
        }

        public String trim(String text, float maxWidth) {
            String key = maxWidth + "\u0000" + text;
            String cached = trims.get(key);
            if (cached != null) return cached;
            String result;
            if (width(text) <= maxWidth) {
                result = text;
            } else {
                float room = maxWidth - width("...");
                float pen = 0, kept = 0;
                int keepEnd = -1;
                for (int i = 0; i < text.length(); ) {
                    int cp = text.codePointAt(i);
                    i += Character.charCount(cp);
                    pen += glyph(cp).advance;
                    if (!Character.isWhitespace(cp)) kept = pen;
                    if (i >= text.length() || kept > room) break;
                    keepEnd = i;
                }
                result = keepEnd < 0 ? "" : text.substring(0, keepEnd).stripTrailing() + "...";
            }
            if (trims.size() > 512) trims.clear();
            trims.put(key, result);
            return result;
        }
    }
}
