package com.frikinjay.dungeonconfig.client.ui;

import com.frikinjay.dungeonconfig.client.ui.DungeonsFonts.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.frikinjay.dungeonconfig.client.ui.DungeonsTheme.*;

public final class DungeonsTooltip {

    private final String title;
    private String body = "";
    private final List<String[]> rows = new ArrayList<>();
    private final List<Integer> rowColors = new ArrayList<>();
    private String footer = "";
    private List<String> lines = List.of();
    private Object linesKey;

    public DungeonsTooltip(String title) {
        this.title = title;
    }

    public DungeonsTooltip body(String text) {
        this.body = text;
        return this;
    }

    public DungeonsTooltip row(String label, String value, int color) {
        rows.add(new String[]{label, value});
        rowColors.add(color);
        return this;
    }

    public DungeonsTooltip footer(String text) {
        this.footer = text;
        return this;
    }

    public void draw(DungeonsCanvas c, int mx, int my, int screenW, int screenH, float cap, int p, float fade) {
        int pad = 14 * p;
        int maxText = Math.min(240 * p, screenW / 2);
        float line = c.capHeight(Role.BODY, cap) * 2.1F;
        Object key = c.measureKey(Role.BODY, cap) + "|" + maxText;
        if (!key.equals(linesKey)) {
            lines = c.wrap(body, Role.BODY, cap, maxText);
            linesKey = key;
        }
        String head = title.toUpperCase(Locale.ROOT);

        float width = c.width(head, Role.HEADING, cap);
        for (String l : lines) width = Math.max(width, c.width(l, Role.BODY, cap));
        float labelW = 0, valueW = 0;
        for (String[] r : rows) {
            labelW = Math.max(labelW, c.width(r[0] + ":", Role.BODY, cap));
            valueW = Math.max(valueW, c.width(r[1].toUpperCase(Locale.ROOT), Role.VALUE, cap));
        }
        width = Math.max(width, labelW + 8 * p + valueW);
        if (!footer.isEmpty()) width = Math.max(width, c.width(footer, Role.BODY, cap));
        width = Math.min(width, maxText);

        int w = Math.round(width) + pad * 2;
        int h = pad + Math.round(line) + Math.round(lines.size() * line);
        if (!rows.isEmpty()) h += Math.round(line * 0.6F) + Math.round(rows.size() * line);
        if (!footer.isEmpty()) h += Math.round(line * 1.1F);
        h += pad - Math.round(line - c.capHeight(Role.BODY, cap));

        int x = mx + 12 * p, y = my + 10 * p;
        if (x + w > screenW - 4) x = mx - w - 8 * p;
        if (y + h > screenH - 4) y = screenH - 4 - h;
        x = Math.max(4, x);
        y = Math.max(4, y);

        float old = c.alpha;
        c.alpha = old * fade;
        c.push();
        c.translate(0, Math.round((1 - fade) * 4 * p));
        c.rect(x + 2 * p, y + 2 * p, w, h, 0x50000000);
        c.rect(x, y, w, h, 0xF0061820);
        DungeonsDraw.panel(c, x, y, w, h, p);
        int tx = x + pad;
        float ty = y + pad;
        c.text(c.trim(head, Role.HEADING, cap, w - pad * 2), Role.HEADING, cap, tx, ty, PANEL_TITLE);
        ty += line;
        for (String l : lines) {
            c.text(l, Role.BODY, cap, tx, ty, PANEL_TEXT);
            ty += line;
        }
        if (!rows.isEmpty()) {
            DungeonsDraw.divider(c, tx, x + w - pad, Math.round(ty - line * 0.25F), p);
            ty += line * 0.6F;
            for (int i = 0; i < rows.size(); i++) {
                String[] r = rows.get(i);
                c.text(r[0] + ":", Role.BODY, cap, tx, ty, LABEL_DIM);
                String v = c.trim(r[1].toUpperCase(Locale.ROOT), Role.VALUE, cap, w - pad * 2 - labelW - 8 * p);
                c.text(v, Role.VALUE, cap, tx + labelW + 8 * p, ty + c.baselineShift(Role.BODY, Role.VALUE, cap), rowColors.get(i));
                ty += line;
            }
        }
        if (!footer.isEmpty()) {
            ty += line * 0.1F;
            c.text(c.trim(footer, Role.BODY, cap, w - pad * 2), Role.BODY, cap, tx, ty, MUTED);
        }
        c.pop();
        c.alpha = old;
    }
}
