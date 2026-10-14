package com.frikinjay.dungeonconfig.client.ui;

import com.frikinjay.dungeonconfig.client.ui.DungeonsFonts.Role;

import static com.frikinjay.dungeonconfig.client.ui.DungeonsTheme.*;

public final class DungeonsDraw {

    private DungeonsDraw() {}

    private static final String[] MOUSE = {
            ".######.",
            "#lll#rr#",
            "#lll#rr#",
            "#lll#rr#",
            "#lll#rr#",
            "########",
            "#wwwwwws",
            "#wwwwwws",
            "#wwwwwws",
            "#wwwwwws",
            ".#wwwws.",
            "..####..",
    };

    private static final String[] MAGNIFIER = {
            "..####....",
            ".#....#...",
            "#..##..#..",
            "#.#....#..",
            "#......#..",
            ".#....#...",
            "..####.#..",
            ".......##.",
            "........##",
            ".........#",
    };

    private static final String[] CLOSE = {
            "##.....##",
            "###...###",
            ".###.###.",
            "..#####..",
            "...###...",
            "..#####..",
            ".###.###.",
            "###...###",
            "##.....##",
    };

    private static final String[] CORNER = {
            "#####..",
            "#...###",
            "#.#.#..",
            "#.###..",
            "#......",
            "##.....",
            ".#.....",
    };

    private static final String[] SPEAKER_ON = {
            "...#.......",
            "..##....#..",
            "####..#..#.",
            "####...#.#.",
            "####...#.#.",
            "####..#..#.",
            "..##....#..",
            "...#.......",
    };

    private static final String[] SPEAKER_OFF = {
            "...#.......",
            "..##.......",
            "####.#...#.",
            "####..#.#..",
            "####...#...",
            "####..#.#..",
            "..##.#...#.",
            "...#.......",
    };

    private static final String[] DIAMOND = {
            "..#..",
            ".#h#.",
            "#hhh#",
            ".#h#.",
            "..#..",
    };

    private static final String[] ARROW_DOWN = {
            "#######",
            ".#####.",
            "..###..",
            "...#...",
    };

    private static final String[] ARROW_RIGHT = {
            "#...",
            "##..",
            "###.",
            "####",
            "###.",
            "##..",
            "#...",
    };

    private static final String[] ARROW_LEFT = mirror(ARROW_RIGHT);

    private static String[] mirror(String[] rows) {
        String[] out = new String[rows.length];
        for (int i = 0; i < rows.length; i++) out[i] = new StringBuilder(rows[i]).reverse().toString();
        return out;
    }

    private static String[] flip(String[] rows) {
        String[] out = new String[rows.length];
        for (int i = 0; i < rows.length; i++) out[i] = rows[rows.length - 1 - i];
        return out;
    }

    private static final String[] CORNER_TR = mirror(CORNER);
    private static final String[] CORNER_BL = flip(CORNER);
    private static final String[] CORNER_BR = flip(CORNER_TR);

    // Key for single colour sprites, where every '#' is drawn
    private static final char[] INK = {'#'};

    public static int spriteWidth(String[] rows, int p) {
        return rows[0].length() * p;
    }

    // Draws a single colour sprite vertically centred on cy
    private static void mono(DungeonsCanvas c, String[] rows, int x, int cy, int p, int color) {
        c.sprite(rows, x, cy - rows.length * p / 2, p, INK, new int[]{color});
    }

    public static void backdrop(DungeonsCanvas c, int w, int h) {
        int mid = h * 45 / 100;
        c.gradientV(0, 0, w, mid, BG_TOP, BG_MID);
        c.gradientV(0, mid, w, h, BG_MID, BG_BOTTOM);
    }

    private static float hash(int x, int y) {
        int n = x * 374761393 + y * 668265263;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) & 0xFFFF) / 65535.0F;
    }

    public static void blocks(DungeonsCanvas c, int w, int h, int cell, float seconds) {
        for (int gy = 0; gy * cell < h; gy++) {
            for (int gx = 0; gx * cell < w; gx++) {
                float n = hash(gx, gy);
                if (n < 0.55F) continue;
                float pulse = 0.5F + 0.5F * (float) Math.sin(seconds * (0.4F + n) + n * 40);
                float strength = (n - 0.55F) / 0.45F * (0.55F + 0.45F * pulse);
                strength *= 1.0F - 0.6F * (gy * cell / (float) h);
                c.rect(gx * cell, gy * cell, cell, cell, withAlpha(BG_BLOCK, strength));
            }
        }
    }

    public static void streaks(DungeonsCanvas c, int w, int h, float seconds, int count) {
        for (int i = 0; i < count; i++) {
            float speed = 6 + hash(i, 7) * 14;
            int len = Math.round(h * (0.06F + hash(i, 3) * 0.16F));
            float travel = h * 0.55F + len;
            float pos = (seconds * speed + hash(i, 11) * travel) % travel;
            float x = Math.round(hash(i, 5) * w);
            float y0 = h + len * 0.3F - pos - len;
            int sw = hash(i, 9) > 0.7F ? 2 : 1;
            float fade = (float) Math.sin(Math.PI * (pos / travel));
            int color = withAlpha(BG_STREAK, fade * (0.35F + hash(i, 13) * 0.65F));
            c.gradientAt(x, y0, sw, len, color & 0x00FFFFFF, color);
        }
    }

    public static void motes(DungeonsCanvas c, int w, int h, float seconds, int count) {
        for (int i = 0; i < count; i++) {
            float speed = 5 + hash(i, 21) * 9;
            float travel = h + 24;
            float pos = (seconds * speed + hash(i, 23) * travel) % travel;
            float drift = (float) Math.sin(seconds * (0.25F + hash(i, 25) * 0.35F) + i * 2.1F) * (6 + hash(i, 31) * 8);
            float x = hash(i, 27) * w + drift;
            float y = h + 12 - pos;
            int s = hash(i, 29) > 0.75F ? 2 : 1;
            float twinkle = 0.55F + 0.45F * (float) Math.sin(seconds * (1.2F + hash(i, 33) * 1.6F) + i * 1.7F);
            float edge = Math.min(1, Math.min(pos / (travel * 0.12F), (travel - pos) / (travel * 0.12F)));
            float alpha = twinkle * Math.max(0, edge);
            c.fillAt(x - 1, y - 1, s + 2, s + 2, withAlpha(0xFF7FE8FF, 0.10F * alpha));
            c.fillAt(x, y, s, s, withAlpha(0xFFD8FBFF, 0.55F * alpha));
        }
    }

    public static void topBar(DungeonsCanvas c, int w, int h) {
        c.gradientV(0, 0, w, h, BAR_TOP, BAR_BOTTOM);
        c.rect(0, h - 1, w, 1, BAR_EDGE);
        c.gradientV(0, h, w, h + 6, 0x60000000, 0x00000000);
    }

    public static void tabUnderline(DungeonsCanvas c, int x, int w, int y, int p) {
        c.rect(x, y - 3 * p, w, 3 * p, ACCENT);
        c.rect(x, y - 3 * p, w, p, ACCENT_LIGHT);
        c.gradientV(x, y - 9 * p, x + w, y - 3 * p, 0x00FFA300, 0x30FFA300);
    }

    public static int chipHeight(DungeonsCanvas c, float cap) {
        return Math.round(c.capHeight(Role.VALUE, cap)) + 6;
    }

    public static int chipWidth(DungeonsCanvas c, String key, float cap) {
        int h = chipHeight(c, cap);
        return Math.max(h, Math.round(c.width(key, Role.VALUE, cap)) + 8);
    }

    public static int chip(DungeonsCanvas c, String key, int x, int cy, float cap, float press) {
        int h = chipHeight(c, cap);
        int w = chipWidth(c, key, cap);
        int y = cy - h / 2 + Math.round(press);
        c.box(x - 1, y - 1, w + 2, h + 2, 1, CHIP_EDGE);
        c.box(x, y, w, h, 1, press > 0 ? CHIP_SHADE : CHIP);
        c.rect(x + 1, y + h - 1, w - 2, 1, CHIP_SHADE);
        float tw = c.width(key, Role.VALUE, cap);
        c.textMid(key, Role.VALUE, cap, x + (w - tw) / 2.0F, y + (h - 1) / 2.0F, CHIP_TEXT);
        return w;
    }

    public static int mouse(DungeonsCanvas c, int x, int cy, int p, boolean right) {
        int h = MOUSE.length * p;
        c.sprite(MOUSE, x, cy - h / 2, p, new char[]{'#', 'l', 'r', 'w', 's'},
                new int[]{CHIP_EDGE, CHIP, right ? ACCENT : CHIP, CHIP, CHIP_SHADE});
        return spriteWidth(MOUSE, p);
    }

    public static int mouseWidth(int p) {
        return spriteWidth(MOUSE, p);
    }

    public static int prompt(DungeonsCanvas c, String label, String key, int x, int cy, float cap, int p, boolean hover, float press) {
        float tw = c.textMid(label, Role.BODY, cap, x, cy, hover ? 0xFFFFFFFF : FOOTER_TEXT);
        int kx = x + Math.round(tw) + 6 * p;
        int kw = key == null ? mouse(c, kx, cy, p, true) : chip(c, key, kx, cy, cap, press);
        return kx + kw - x;
    }

    public static int speaker(DungeonsCanvas c, int x, int cy, int p, boolean muted, int color) {
        String[] sprite = muted ? SPEAKER_OFF : SPEAKER_ON;
        mono(c, sprite, x, cy, p, muted ? withAlpha(color, 0.6F) : color);
        return spriteWidth(sprite, p);
    }

    public static int speakerWidth(int p) {
        return spriteWidth(SPEAKER_ON, p);
    }

    public static int promptWidth(DungeonsCanvas c, String label, String key, float cap, int p) {
        int kw = key == null ? mouseWidth(p) : chipWidth(c, key, cap);
        return Math.round(c.width(label, Role.BODY, cap)) + 6 * p + kw;
    }

    public static void close(DungeonsCanvas c, int x, int y, int p, boolean hover) {
        c.sprite(CLOSE, x, y, p, INK, new int[]{hover ? 0xFFFFFFFF : TAB_TEXT});
    }

    public static int closeSize(int p) {
        return CLOSE.length * p;
    }

    public static void row(DungeonsCanvas c, int x, int y, int w, int h, boolean hover) {
        c.rect(x, y, w, h, ROW);
        if (hover) c.rect(x, y, w, h, ROW_HOVER);
    }

    public static void selection(DungeonsCanvas c, int x, int y, int w, int h, int p, float alpha) {
        if (alpha <= 0) return;
        float old = c.alpha;
        c.alpha = old * alpha;
        c.box(x, y, w, h, p, ROW_SELECTED);
        c.boxOutline(x, y, w, h, p, ROW_BORDER);
        c.alpha = old;
    }

    public static void header(DungeonsCanvas c, String text, int x, int y, float cap) {
        c.text(text, Role.HEADING, cap, x, y, HEADER);
    }

    public static void modified(DungeonsCanvas c, int x, int cy, int p) {
        c.sprite(DIAMOND, x, cy - DIAMOND.length * p / 2, p, new char[]{'#', 'h'}, new int[]{ACCENT_DARK, ACCENT});
    }

    public static int modifiedWidth(int p) {
        return spriteWidth(DIAMOND, p);
    }

    public static int toggleWidth(int p) {
        return 26 * p;
    }

    public static void toggle(DungeonsCanvas c, int x, int cy, int p, float t, boolean hover) {
        int w = toggleWidth(p), th = 11 * p, kw = 9 * p, kh = 15 * p;
        int y = cy - th / 2;
        c.box(x - p, y - p, w + 2 * p, th + 2 * p, p, TOGGLE_EDGE);
        c.rect(x, y, w, th, TOGGLE_OFF);
        int kx = x + Math.round((w - kw) * t);
        if (kx > x) {
            c.rect(x, y, kx - x, th, withAlpha(TOGGLE_ON, Math.min(1, t * 1.6F)));
            c.rect(x, y + th - p, kx - x, p, withAlpha(TOGGLE_ON_DARK, Math.min(1, t * 1.6F)));
            int mx = x + (w - kw) / 2 - p / 2;
            c.rect(mx, y + 3 * p, p * 2 - p / 2, th - 6 * p, withAlpha(TOGGLE_MARK, DungeonsAnim.clamp01(t * 2 - 1)));
        }
        float off = DungeonsAnim.clamp01(1 - t * 2);
        if (off > 0) {
            int rx = x + kw + (w - kw - 5 * p) / 2, ry = cy - 5 * p / 2;
            int ring = withAlpha(TOGGLE_CIRCLE, off);
            c.rect(rx + p, ry, 3 * p, p, ring);
            c.rect(rx + p, ry + 4 * p, 3 * p, p, ring);
            c.rect(rx, ry + p, p, 3 * p, ring);
            c.rect(rx + 4 * p, ry + p, p, 3 * p, ring);
        }
        int ky = cy - kh / 2;
        c.box(kx - p, ky - p, kw + 2 * p, kh + 2 * p, p, TOGGLE_EDGE);
        c.rect(kx, ky, kw, kh, hover ? KNOB_LIGHT : KNOB);
        c.rect(kx, ky, p, kh - p, KNOB_LIGHT);
        c.rect(kx + kw - 2 * p, ky + p, p, kh - 2 * p, KNOB_SHADE);
        c.rect(kx + kw - p, ky, p, kh, KNOB_DARK);
        c.rect(kx, ky + kh - p, kw, p, KNOB_DARK);
    }

    public static void valueBox(DungeonsCanvas c, int x, int y, int w, int h, int p, boolean hover, boolean active) {
        c.box(x, y, w, h, p, hover || active ? VALUE_BOX_HOVER : VALUE_BOX);
        if (active) c.boxOutline(x, y, w, h, p, withAlpha(ACCENT_LIGHT, 0.63F));
        else if (hover) c.boxOutline(x, y, w, h, p, VALUE_BOX_EDGE);
    }

    public static void arrowDown(DungeonsCanvas c, int x, int cy, int p, int color) {
        mono(c, ARROW_DOWN, x, cy, p, color);
    }

    public static int arrowDownWidth(int p) {
        return spriteWidth(ARROW_DOWN, p);
    }

    public static int arrowWidth(int p) {
        return spriteWidth(ARROW_RIGHT, p);
    }

    public static void arrow(DungeonsCanvas c, int x, int cy, int p, boolean right, int color) {
        mono(c, right ? ARROW_RIGHT : ARROW_LEFT, x, cy, p, color);
    }

    public static void slider(DungeonsCanvas c, int x, int cy, int w, int p, float t, boolean hover, boolean dragging) {
        int fill = Math.round(w * DungeonsAnim.clamp01(t));
        for (int sx = x + fill; sx < x + w; sx += 5 * p) {
            c.rect(sx, cy - p / 2, Math.min(3 * p, x + w - sx), Math.max(1, p), SLIDER_TRACK);
        }
        if (fill > 0) {
            c.rect(x, cy - p, fill, 2 * p, SLIDER_FILL_DARK);
            c.rect(x, cy - p, fill, p, SLIDER_FILL);
        }
        int kw = 3 * p, kh = 13 * p;
        int kx = x + fill - kw / 2, ky = cy - kh / 2;
        c.rect(kx - p, ky - p, kw + 2 * p, kh + 2 * p, TOGGLE_EDGE);
        c.rect(kx, ky, kw, kh, hover || dragging ? KNOB_LIGHT : KNOB);
        c.rect(kx + kw - p, ky, p, kh, KNOB_SHADE);
    }

    public static void magnifier(DungeonsCanvas c, int x, int cy, int p, int color) {
        mono(c, MAGNIFIER, x, cy, p, color);
    }

    public static int magnifierWidth(int p) {
        return spriteWidth(MAGNIFIER, p);
    }

    public static void scrollbar(DungeonsCanvas c, int x, int y, int h, int thumbY, int thumbH, int p, boolean hover) {
        c.rect(x, y, 2 * p, h, SCROLL_TRACK);
        c.rect(x, thumbY, 2 * p, thumbH, hover ? SCROLL_THUMB_HOVER : SCROLL_THUMB);
    }

    public static int panel(DungeonsCanvas c, int x, int y, int w, int h, int p) {
        c.rect(x, y, w, h, PANEL);
        int in = 6 * p;
        int fx = x + in, fy = y + in, fw = w - in * 2, fh = h - in * 2;
        int corner = CORNER.length * p;
        c.rect(fx + corner - p, fy, fw - corner * 2 + 2 * p, p, PANEL_FRAME);
        c.rect(fx + corner - p, fy + fh - p, fw - corner * 2 + 2 * p, p, PANEL_FRAME);
        c.rect(fx, fy + corner - p, p, fh - corner * 2 + 2 * p, PANEL_FRAME);
        c.rect(fx + fw - p, fy + corner - p, p, fh - corner * 2 + 2 * p, PANEL_FRAME);
        int[] col = {PANEL_FRAME_LIGHT};
        c.sprite(CORNER, fx - p, fy - p, p, INK, col);
        c.sprite(CORNER_TR, fx + fw - corner + p, fy - p, p, INK, col);
        c.sprite(CORNER_BL, fx - p, fy + fh - corner + p, p, INK, col);
        c.sprite(CORNER_BR, fx + fw - corner + p, fy + fh - corner + p, p, INK, col);
        return in;
    }

    public static void divider(DungeonsCanvas c, int x0, int x1, int y, int p) {
        int w = x1 - x0;
        int edge = Math.min(w / 4, 24 * p);
        c.gradientH(x0, y, x0 + edge, y + p, PANEL_DIVIDER & 0x00FFFFFF, PANEL_DIVIDER);
        c.rect(x0 + edge, y, w - edge * 2, p, PANEL_DIVIDER);
        c.gradientH(x1 - edge, y, x1, y + p, PANEL_DIVIDER, PANEL_DIVIDER & 0x00FFFFFF);
    }
}
