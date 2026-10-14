package com.frikinjay.dungeonconfig.client.ui;

import com.frikinjay.dungeonconfig.platform.DcPlatform;
import com.frikinjay.dungeonconfig.util.ImageDecoder;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ModIcons {

    private static final Map<String, Optional<DungeonsCanvas.Texture>> CACHE = new ConcurrentHashMap<>();
    @Nullable private static DungeonsCanvas.Texture genericIcon;

    private ModIcons() {}

    @Nullable
    public static DungeonsCanvas.Texture get(String modId) {
        String key = modId.toLowerCase(Locale.ROOT);
        return CACHE.computeIfAbsent(key, ModIcons::load).orElse(null);
    }

    public static DungeonsCanvas.Texture getOrDefault(String modId) {
        DungeonsCanvas.Texture tex = get(modId);
        return tex != null ? tex : getGeneric();
    }

    public static boolean has(String modId) {
        return get(modId) != null;
    }

    private static Optional<DungeonsCanvas.Texture> load(String modId) {
        try {
            InputStream stream = DcPlatform.openModIcon(modId);
            if (stream == null) {
                stream = tryOpenResource(modId, "icon.png");
            }
            if (stream == null) {
                stream = tryOpenResource(modId, modId + ".png");
            }
            if (stream == null) {
                stream = tryOpenResource(modId, "textures/gui/icon.png");
            }

            if (stream != null) {
                try (InputStream in = stream) {
                    byte[] data = in.readAllBytes();
                    ImageDecoder.Image image = ImageDecoder.decode(data);
                    if (image != null && image.width() > 0 && image.height() > 0) {
                        return Optional.of(DungeonsCanvas.upload("dungeonconfig", "mod_icon/" + modId,
                                image.argb(), image.width(), image.height(), true));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    private static InputStream tryOpenResource(String namespace, String path) {
        try {
            return DungeonsCanvas.openResource(namespace, path);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static DungeonsCanvas.Texture getGeneric() {
        if (genericIcon == null) {
            genericIcon = createGeneric();
        }
        return genericIcon;
    }

    private static DungeonsCanvas.Texture createGeneric() {
        int w = 64, h = 64;
        int[] pixels = new int[w * h];

        // Square border (no rounded corners) and colors matching default theme colors (PANEL_FRAME and PANEL / BG_MID)
        int borderColor = 0xFF2F5A69;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (x <= 1 || x >= w - 2 || y <= 1 || y >= h - 2) {
                    pixels[y * w + x] = borderColor;
                } else {
                    float t = (float) y / (h - 1);
                    int r = Math.round(0x0A * (1.0F - t) + 0x06 * t);
                    int g = Math.round(0x26 * (1.0F - t) + 0x1B * t);
                    int b = Math.round(0x33 * (1.0F - t) + 0x25 * t);
                    pixels[y * w + x] = (0xF0 << 24) | (r << 16) | (g << 8) | b;
                }
            }
        }

        return DungeonsCanvas.upload("dungeonconfig", "mod_icon/generic", pixels, w, h, true);
    }

    public static String getInitial(@Nullable String name) {
        if (name == null || name.isBlank()) return "?";
        name = name.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
        if (name.isEmpty()) return "?";
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                return String.valueOf(Character.toUpperCase(ch));
            }
        }
        return name.substring(0, 1).toUpperCase(Locale.ROOT);
    }
}
