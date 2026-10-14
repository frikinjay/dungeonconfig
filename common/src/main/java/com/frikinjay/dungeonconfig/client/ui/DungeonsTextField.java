package com.frikinjay.dungeonconfig.client.ui;

import com.frikinjay.dungeonconfig.client.ui.DungeonsFonts.Role;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class DungeonsTextField {

    private String value = "";
    private int caret = 0;
    private boolean allSelected = false;
    private float scroll = 0;
    private long blinkStart = DungeonsAnim.millis();
    public final int maxLength;
    public boolean focused = false;

    private int[] offsets = {0};
    private float[] widths = {0};
    private String measuredValue;
    private Object measuredKey;

    public DungeonsTextField(int maxLength) {
        this.maxLength = maxLength;
    }

    public String value() {
        return value;
    }

    public int caret() {
        return caret;
    }

    public void setValue(String text) {
        value = text;
        caret = value.length();
        allSelected = false;
        scroll = 0;
    }

    public void focus(boolean selectAll) {
        focused = true;
        allSelected = selectAll && !value.isEmpty();
        caret = value.length();
        blinkStart = DungeonsAnim.millis();
    }

    public void blur() {
        focused = false;
        allSelected = false;
    }

    private void insert(String text) {
        StringBuilder clean = new StringBuilder();
        text.codePoints().filter(cp -> cp >= 32 && cp != 127 && !Character.isISOControl(cp)).forEach(clean::appendCodePoint);
        if (allSelected) {
            value = "";
            caret = 0;
            allSelected = false;
        }
        String add = clean.toString();
        int room = maxLength - value.length();
        if (room <= 0) return;
        if (add.length() > room) {
            // Avoid splitting Unicode surrogate pairs
            if (Character.isHighSurrogate(add.charAt(room - 1))) room--;
            add = add.substring(0, room);
        }
        value = value.substring(0, caret) + add + value.substring(caret);
        caret += add.length();
        blinkStart = DungeonsAnim.millis();
    }

    public boolean charTyped(int codePoint) {
        if (!focused) return false;
        insert(new String(Character.toChars(codePoint)));
        return true;
    }

    private int prev(int i) {
        return i <= 0 ? 0 : value.offsetByCodePoints(i, -1);
    }

    private int next(int i) {
        return i >= value.length() ? value.length() : value.offsetByCodePoints(i, 1);
    }

    public boolean keyPressed(int key, boolean control) {
        if (!focused) return false;
        blinkStart = DungeonsAnim.millis();
        var keyboard = Minecraft.getInstance().keyboardHandler;
        if (control) {
            switch (key) {
                case GLFW.GLFW_KEY_A -> {
                    allSelected = !value.isEmpty();
                    return true;
                }
                case GLFW.GLFW_KEY_C -> {
                    if (allSelected) keyboard.setClipboard(value);
                    return true;
                }
                case GLFW.GLFW_KEY_X -> {
                    if (allSelected) {
                        keyboard.setClipboard(value);
                        setValue("");
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    insert(keyboard.getClipboard());
                    return true;
                }
                default -> {
                }
            }
        }
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (allSelected) {
                    setValue("");
                } else if (caret > 0) {
                    int from = control ? wordStart(caret) : prev(caret);
                    value = value.substring(0, from) + value.substring(caret);
                    caret = from;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (allSelected) {
                    setValue("");
                } else if (caret < value.length()) {
                    value = value.substring(0, caret) + value.substring(next(caret));
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                caret = allSelected ? 0 : control ? wordStart(caret) : prev(caret);
                allSelected = false;
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                caret = allSelected ? value.length() : control ? wordEnd(caret) : next(caret);
                allSelected = false;
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                caret = 0;
                allSelected = false;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                caret = value.length();
                allSelected = false;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private int wordStart(int from) {
        int i = from;
        while (i > 0 && value.charAt(i - 1) == ' ') i--;
        while (i > 0 && value.charAt(i - 1) != ' ') i--;
        return i;
    }

    private int wordEnd(int from) {
        int i = from;
        while (i < value.length() && value.charAt(i) == ' ') i++;
        while (i < value.length() && value.charAt(i) != ' ') i++;
        return i;
    }

    private void measure(DungeonsCanvas c, Role role, float cap) {
        Object key = c.measureKey(role, cap);
        if (value.equals(measuredValue) && key.equals(measuredKey)) return;
        int n = value.codePointCount(0, value.length());
        offsets = new int[n + 1];
        widths = new float[n + 1];
        float w = 0;
        int i = 0;
        for (int k = 0; k < n; k++) {
            int cp = value.codePointAt(i);
            offsets[k] = i;
            widths[k] = w;
            w += c.advance(cp, role, cap);
            i += Character.charCount(cp);
        }
        offsets[n] = value.length();
        widths[n] = w;
        measuredValue = value;
        measuredKey = key;
    }

    private int boundary(int charIndex) {
        int lo = 0, hi = offsets.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (offsets[mid] <= charIndex) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    public void click(DungeonsCanvas c, Role role, float cap, float localX) {
        measure(c, role, cap);
        allSelected = false;
        float x = localX + scroll;
        int best = 0;
        float bestDist = Float.MAX_VALUE;
        for (int k = 0; k < widths.length; k++) {
            float d = Math.abs(widths[k] - x);
            if (d < bestDist) {
                bestDist = d;
                best = k;
            }
        }
        caret = offsets[best];
        blinkStart = DungeonsAnim.millis();
    }

    public void draw(DungeonsCanvas c, int x, int cy, int w, Role role, float cap, int color, String placeholder, int placeholderColor) {
        float capH = c.capHeight(role, cap);
        if (value.isEmpty() && !focused) {
            c.textMid(c.trim(placeholder, role, cap, w), role, cap, x, cy, placeholderColor);
            return;
        }
        measure(c, role, cap);
        int caretIndex = boundary(Math.min(caret, value.length()));
        caret = offsets[caretIndex];
        float caretX = widths[caretIndex];
        float total = widths[widths.length - 1];
        if (caretX - scroll > w - 2) scroll = caretX - w + 2;
        if (caretX - scroll < 0) scroll = caretX;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - w + 2)));

        int top = Math.round(cy - capH / 2 - 2);
        int bottom = Math.round(cy + capH / 2 + c.descent(role, cap) + 1);
        c.scissor(x - 1, top - 1, x + w + 1, bottom + 1);
        if (allSelected && focused) {
            int sw = Math.round(total);
            c.fill(Math.round(x - scroll), top, Math.round(x - scroll + sw), bottom, 0x806BB8D6);
        }
        int first = Math.max(0, countBelow(scroll) - 1);
        int last = Math.min(widths.length - 1, countBelow(scroll + w) + 1);
        if (last > first) {
            c.textMid(value.substring(offsets[first], offsets[last]), role, cap, x - scroll + widths[first], cy, color);
        }
        if (focused && !allSelected && ((DungeonsAnim.millis() - blinkStart) / 530) % 2 == 0) {
            int cx = Math.round(x + caretX - scroll);
            c.fill(cx, Math.round(cy - capH / 2 - 1), cx + 1, Math.round(cy + capH / 2 + 1), DungeonsTheme.CARET);
        }
        c.endScissor();
    }

    private int countBelow(float x) {
        int lo = 0, hi = widths.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (widths[mid] <= x) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }
}
