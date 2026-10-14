package com.frikinjay.dungeonconfig.client.ui;

import com.frikinjay.dungeonconfig.client.ui.DungeonsFonts.Role;

import java.util.ArrayList;
import java.util.List;

public abstract class DungeonsCanvasBase {

    public final float scale;
    public final int device;
    public float alpha = 1.0F;

    private float tx, ty;
    private final float[] stack = new float[64];
    private int depth = 0;
    private static int lastDevice = 0;

    protected DungeonsCanvasBase(float scale, int device) {
        this.scale = scale;
        this.device = Math.max(1, device);
        if (lastDevice != 0 && lastDevice != this.device) DungeonsFont.scheduleSweep();
        lastDevice = this.device;
    }

    protected abstract void posePush();
    protected abstract void posePop();
    protected abstract void poseTranslate(float x, float y);
    protected abstract void poseScale(float s);
    protected abstract void rawFill(int x0, int y0, int x1, int y1, int color);
    protected abstract void rawGradientV(int x0, int y0, int x1, int y1, int top, int bottom);
    protected abstract void rawScissor(int x0, int y0, int x1, int y1);
    public abstract void endScissor();
    protected abstract void rawImage(DungeonsCanvas.Texture texture, int x, int y, int w, int h, float u, float v, int uw, int vh, int color);
    protected abstract void rawGlyphs(DungeonsCanvas.Texture texture, int[] quads, int count, int color);
    protected abstract void vanillaText(String text, float x, float y, float size, int color, boolean shadow);
    protected abstract int vanillaWidth(String text);
    public abstract void beginOverlay();
    public abstract void endOverlay();

    public void push() {
        stack[depth * 2] = tx;
        stack[depth * 2 + 1] = ty;
        depth++;
        posePush();
    }

    public void pop() {
        depth--;
        tx = stack[depth * 2];
        ty = stack[depth * 2 + 1];
        posePop();
    }

    protected float offsetX() {
        return tx;
    }

    protected float offsetY() {
        return ty;
    }

    public void translate(float x, float y) {
        tx += x;
        ty += y;
        poseTranslate(x, y);
    }

    // Round canvas coords to nearest real pixel for crisp edges
    public float snap(float v, boolean vertical) {
        float offset = vertical ? ty : tx;
        return Math.round((v + offset) * device) / (float) device - offset;
    }

    public int a(int color) {
        if (alpha >= 1.0F) return color;
        int a = Math.round((color >>> 24) * Math.max(0, alpha));
        return (a << 24) | (color & 0xFFFFFF);
    }

    public void fill(int x0, int y0, int x1, int y1, int color) {
        if (x1 > x0 && y1 > y0 && (color >>> 24) != 0) {
            int c = a(color);
            if ((c >>> 24) != 0) rawFill(x0, y0, x1, y1, c);
        }
    }

    public void rect(int x, int y, int w, int h, int color) {
        fill(x, y, x + w, y + h, color);
    }

    public void gradientV(int x0, int y0, int x1, int y1, int top, int bottom) {
        if (x1 > x0 && y1 > y0) rawGradientV(x0, y0, x1, y1, a(top), a(bottom));
    }

    public void gradientH(int x0, int y0, int x1, int y1, int left, int right) {
        int w = x1 - x0;
        if (w <= 0 || y1 <= y0) return;
        int steps = Math.min(w, 48);
        for (int i = 0; i < steps; i++) {
            int sx = x0 + w * i / steps, ex = x0 + w * (i + 1) / steps;
            fill(sx, y0, ex, y1, DungeonsTheme.lerp(left, right, (i + 0.5F) / steps));
        }
    }

    public void fillAt(float x, float y, int w, int h, int color) {
        int c = a(color);
        if ((c >>> 24) == 0 || w <= 0 || h <= 0) return;
        float sx = snap(x, false), sy = snap(y, true);
        posePush();
        poseTranslate(sx, sy);
        rawFill(0, 0, w, h, c);
        posePop();
    }

    public void gradientAt(float x, float y, int w, int h, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        float sx = snap(x, false), sy = snap(y, true);
        posePush();
        poseTranslate(sx, sy);
        rawGradientV(0, 0, w, h, a(top), a(bottom));
        posePop();
    }

    public void outline(int x, int y, int w, int h, int t, int color) {
        fill(x, y, x + w, y + t, color);
        fill(x, y + h - t, x + w, y + h, color);
        fill(x, y + t, x + t, y + h - t, color);
        fill(x + w - t, y + t, x + w, y + h - t, color);
    }

    public void box(int x, int y, int w, int h, int cut, int color) {
        if (cut <= 0 || w <= cut * 2 || h <= cut * 2) {
            rect(x, y, w, h, color);
            return;
        }
        fill(x + cut, y, x + w - cut, y + cut, color);
        fill(x, y + cut, x + w, y + h - cut, color);
        fill(x + cut, y + h - cut, x + w - cut, y + h, color);
    }

    public void boxOutline(int x, int y, int w, int h, int t, int color) {
        fill(x + t, y, x + w - t, y + t, color);
        fill(x + t, y + h - t, x + w - t, y + h, color);
        fill(x, y + t, x + t, y + h - t, color);
        fill(x + w - t, y + t, x + w, y + h - t, color);
    }

    public void scissor(int x0, int y0, int x1, int y1) {
        rawScissor(x0, y0, Math.max(x0, x1), Math.max(y0, y1));
    }

    public void image(DungeonsCanvas.Texture texture, int x, int y, int w, int h) {
        if (texture == null) return;
        image(texture, x, y, w, h, 0, 0, texture.width, texture.height);
    }

    public void image(DungeonsCanvas.Texture texture, int x, int y, int w, int h, float u, float v, int uw, int vh) {
        rawImage(texture, x, y, w, h, u, v, uw, vh, a(0xFFFFFFFF));
    }

    public void sprite(String[] rows, int x, int y, int px, char[] keys, int[] colors) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            int c = 0;
            while (c < row.length()) {
                char ch = row.charAt(c);
                int run = 1;
                while (c + run < row.length() && row.charAt(c + run) == ch) run++;
                if (ch != '.' && ch != ' ') {
                    for (int k = 0; k < keys.length; k++) {
                        if (keys[k] == ch) {
                            fill(x + c * px, y + r * px, x + (c + run) * px, y + (r + 1) * px, colors[k]);
                            break;
                        }
                    }
                }
                c += run;
            }
        }
    }

    private DungeonsFont.Strike strike(Role role, float cap) {
        DungeonsFont font = DungeonsFonts.get(role);
        if (font == null) return null;
        try {
            float target = cap * device;
            DungeonsFont.Strike s = font.forCap(target);
            if (font.grid > 0 && s.cap > target * 1.2F) {
                float want = target;
                DungeonsFont body = DungeonsFonts.get(Role.BODY);
                if (body != null && body != font) want = Math.max(target, body.grid > 0 ? body.forCap(target).cap : target);
                if (s.cap > want * 1.1F) s = font.smoothForCap(want);
            }
            return s;
        } catch (Throwable t) {
            DungeonsFonts.fail(t);
            return null;
        }
    }

    public float capHeight(Role role, float cap) {
        DungeonsFont.Strike s = strike(role, cap);
        return s == null ? Math.round(cap) : s.cap / (float) device;
    }

    public float descent(Role role, float cap) {
        DungeonsFont.Strike s = strike(role, cap);
        return s == null ? cap * 2 / 7 : Math.max(s.descent, s.cap * 2 / 7) / (float) device;
    }

    public float baselineShift(Role from, Role to, float cap) {
        return capHeight(from, cap) - capHeight(to, cap);
    }

    public float fontPixel(Role role, float cap) {
        DungeonsFont.Strike s = strike(role, cap);
        return s == null ? Math.max(1, cap / 7) : s.unit / (float) device;
    }

    public float width(String text, Role role, float cap) {
        DungeonsFont.Strike s = strike(role, cap);
        if (s == null) return vanillaWidth(text) * vanillaSize(cap);
        return s.width(text) / device;
    }

    public float[] ink(String text, Role role, float cap) {
        DungeonsFont.Strike s = strike(role, cap);
        if (s == null) {
            float size = vanillaSize(cap);
            return new float[]{0, Math.max(0, vanillaWidth(text) - 1) * size};
        }
        float[] px = s.ink(text);
        return new float[]{px[0] / device, px[1] / device};
    }

    private static float vanillaSize(float cap) {
        return Math.max(0.5F, Math.round(cap / 7.0F * 2) / 2.0F);
    }

    public float text(String text, Role role, float cap, float x, float y, int color) {
        return text(text, role, cap, x, y, color, 0);
    }

    public float text(String text, Role role, float cap, float x, float y, int color, int shadow) {
        if (text.isEmpty()) return 0;
        DungeonsFont.Strike s = strike(role, cap);
        if (s == null) {
            float size = vanillaSize(cap);
            vanillaText(text, x, y, size, a(color), shadow != 0);
            return vanillaWidth(text) * size;
        }
        int ox = Math.round((x + tx) * device);
        int baseline = Math.round((y + ty) * device) + s.cap;
        if (shadow != 0) glyphRun(s, text, ox + s.unit, baseline + s.unit, a(shadow));
        glyphRun(s, text, ox, baseline, a(color));
        return s.width(text) / device;
    }

    private int[] quads = new int[6 * 64];

    private void glyphRun(DungeonsFont.Strike s, String text, int ox, int baseline, int color) {
        if ((color >>> 24) == 0) return;
        int n = 0;
        float pen = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            DungeonsFont.Glyph g = s.glyph(cp);
            if (g.w > 0) {
                if ((n + 1) * 6 > quads.length) quads = java.util.Arrays.copyOf(quads, quads.length * 2);
                int o = n * 6;
                quads[o] = ox + Math.round(pen) + g.x;
                quads[o + 1] = baseline + g.y;
                quads[o + 2] = g.w;
                quads[o + 3] = g.h;
                quads[o + 4] = g.u;
                quads[o + 5] = g.v;
                n++;
            }
            pen += g.advance;
        }
        if (n == 0) return;
        DungeonsCanvas.Texture texture = s.texture();
        posePush();
        poseTranslate(-tx, -ty);
        poseScale(1.0F / device);
        rawGlyphs(texture, quads, n, color);
        posePop();
    }

    public float textMid(String text, Role role, float cap, float x, float cy, int color) {
        return text(text, role, cap, x, cy - capHeight(role, cap) / 2.0F, color, 0);
    }

    public float textMid(String text, Role role, float cap, float x, float cy, int color, int shadow) {
        return text(text, role, cap, x, cy - capHeight(role, cap) / 2.0F, color, shadow);
    }

    public float textRight(String text, Role role, float cap, float right, float cy, int color) {
        return textMid(text, role, cap, right - width(text, role, cap), cy, color);
    }

    public float textCenter(String text, Role role, float cap, float cx, float cy, int color, int shadow) {
        return textMid(text, role, cap, cx - width(text, role, cap) / 2.0F, cy, color, shadow);
    }

    public List<String> wrap(String text, Role role, float cap, float maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            String[] words = paragraph.split(" ");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                while (width(word, role, cap) > maxWidth && word.length() > 1) {
                    if (!line.isEmpty()) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    int cut = fitting(word, role, cap, maxWidth);
                    lines.add(word.substring(0, cut));
                    word = word.substring(cut);
                }
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (line.isEmpty() || width(candidate, role, cap) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private int fitting(String word, Role role, float cap, float maxWidth) {
        int fit = 1;
        for (int i = 1; i <= word.length(); i++) {
            if (i < word.length() && Character.isLowSurrogate(word.charAt(i))) continue;
            if (width(word.substring(0, i), role, cap) > maxWidth) break;
            fit = i;
        }
        for (int i = fit; i > fit / 2; i--) {
            char ch = word.charAt(i - 1);
            if (ch == '/' || ch == '-' || ch == '?' || ch == '&' || ch == '.' || ch == '_' || ch == '=') return i;
        }
        return fit;
    }

    public String trim(String text, Role role, float cap, float maxWidth) {
        DungeonsFont.Strike s = strike(role, cap);
        if (s != null) return s.trim(text, maxWidth * device);
        if (width(text, role, cap) <= maxWidth) return text;
        int count = text.codePointCount(0, text.length());
        int lo = 1, hi = count - 1, best = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            String candidate = text.substring(0, text.offsetByCodePoints(0, mid)).stripTrailing() + "...";
            if (width(candidate, role, cap) <= maxWidth) {
                best = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return best < 0 ? "" : text.substring(0, text.offsetByCodePoints(0, best)).stripTrailing() + "...";
    }

    public float advance(int codePoint, Role role, float cap) {
        DungeonsFont.Strike s = strike(role, cap);
        if (s == null) return vanillaWidth(new String(Character.toChars(codePoint))) * vanillaSize(cap);
        return s.glyph(codePoint).advance / device;
    }

    public Object measureKey(Role role, float cap) {
        DungeonsFont.Strike s = strike(role, cap);
        return s != null ? s.font.id + ":" + s.px : "vanilla:" + vanillaSize(cap) + ":" + device;
    }
}
