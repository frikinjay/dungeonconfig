package com.frikinjay.dungeonconfig.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;
import java.util.Set;

public final class DungeonsUi {

    private static final Set<String> SMALL_WORDS = Set.of("a", "an", "and", "as", "at", "but", "by", "for", "from",
            "in", "into", "of", "on", "or", "per", "the", "to", "via", "vs", "with");
    private static final Set<String> ACRONYMS = Set.of("hud", "tp", "tpa", "xp", "ui", "id", "fov", "nbt", "url", "gui", "npc", "pvp", "rgb");

    private DungeonsUi() {}

    public static float scaleFor(int guiWidth, int guiHeight) {
        var window = Minecraft.getInstance().getWindow();
        double gui = Math.max(1, Math.round((double) window.getWidth() / Math.max(1, guiWidth)));
        int pixels = Math.max(1, (int) ((gui + 1) / 2));
        pixels = Math.max(pixels, window.getHeight() / 1080);
        return (float) (pixels / gui);
    }

    public static float fit(int width, int height, float min) {
        return Math.max(min, Math.min(1.0F, Math.min(width / 960.0F, height / 540.0F)));
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public static String titleCase(String text) {
        String[] words = text.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isEmpty()) continue;
            String lower = word.toLowerCase(Locale.ROOT);
            if (!out.isEmpty()) out.append(' ');
            if (ACRONYMS.contains(lower)) {
                out.append(lower.toUpperCase(Locale.ROOT));
            } else if (i > 0 && i < words.length - 1 && SMALL_WORDS.contains(lower)) {
                out.append(lower);
            } else if (!word.equals(lower)) {
                out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            } else {
                out.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
            }
        }
        return out.toString();
    }

    public static String sentence(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return trimmed;
        int first = 0;
        while (first < trimmed.length() && !Character.isLetterOrDigit(trimmed.charAt(first))) first++;
        if (!trimmed.startsWith("/") && first < trimmed.length()) {
            trimmed = trimmed.substring(0, first) + Character.toUpperCase(trimmed.charAt(first)) + trimmed.substring(first + 1);
        }
        char last = trimmed.charAt(trimmed.length() - 1);
        if (".!?".indexOf(last) < 0 && !trimmed.endsWith("...")) trimmed += ".";
        return trimmed;
    }
}
