package com.frikinjay.dungeonconfig.client.ui;

import com.frikinjay.dungeonconfig.util.Json5;
import com.frikinjay.dungeonconfig.util.SafeDownloader;
import com.frikinjay.dungeonconfig.util.Threads;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;

public final class DungeonsTheme {

    private static final Logger LOGGER = LoggerFactory.getLogger("DungeonsTheme");

    private static final long MAX_COLORS_BYTES = 1L * 1024 * 1024;

    private static final Set<String> WARNED_COLORS = new HashSet<>();

    private DungeonsTheme() {}

    public static int BG_TOP = 0xFF0B3647;
    public static int BG_MID = 0xFF0A2633;
    public static int BG_BOTTOM = 0xFF060D14;
    public static int BG_TINT = 0xFF06293A;
    public static int BG_BLOCK = 0x0EFFFFFF;
    public static int BG_STREAK = 0x2E7FE3FF;

    public static int BAR_TOP = 0xFF1B1816;
    public static int BAR_BOTTOM = 0xFF0F0D0B;
    public static int BAR_EDGE = 0xFF2C2824;
    public static int TAB_TEXT = 0xFF8F999E;
    public static int TAB_HOVER = 0xFFC9D1D4;
    public static int TAB_SELECTED = 0xFFF4F4F2;
    public static int ACCENT = 0xFFFFA300;
    public static int ACCENT_LIGHT = 0xFFFFC85A;
    public static int ACCENT_DARK = 0xFFB86A00;

    public static int HEADER = 0xFFC3CDD2;
    public static int ROW = 0x2E000A12;
    public static int ROW_HOVER = 0x14FFFFFF;
    public static int ROW_SELECTED = 0x24C8E4F0;
    public static int ROW_BORDER = 0xE09FB4BF;
    public static int LABEL = 0xFFD6DFE2;
    public static int LABEL_DIM = 0xFF9DAAB0;
    public static int VALUE = 0xFFF2F4F2;
    public static int VALUE_BOX = 0xC2041722;
    public static int VALUE_BOX_HOVER = 0xD8072433;
    public static int VALUE_BOX_EDGE = 0x40A9C6D2;
    public static int MUTED = 0xFF71828A;
    public static int CARET = 0xFFFFC85A;

    public static int DROPDOWN_BG = 0xF0081720;
    public static int DROPDOWN_BORDER = 0xE09FB4BF;
    public static int DROPDOWN_HOVER = 0x24FFFFFF;
    public static int DROPDOWN_SELECTED = 0x24C8E4F0;

    public static int TOGGLE_ON = 0xFF22D467;
    public static int TOGGLE_ON_DARK = 0xFF139A47;
    public static int TOGGLE_MARK = 0xFF0B3F20;
    public static int TOGGLE_EDGE = 0xFF07131A;
    public static int TOGGLE_OFF = 0xD0091A23;
    public static int TOGGLE_CIRCLE = 0xFF6C7C86;
    public static int KNOB = 0xFFDCDDD6;
    public static int KNOB_LIGHT = 0xFFF4F4EE;
    public static int KNOB_SHADE = 0xFFA3A49E;
    public static int KNOB_DARK = 0xFF6B6C68;

    public static int SLIDER_TRACK = 0xFF2B4A58;
    public static int SLIDER_FILL = 0xFF8FD3EE;
    public static int SLIDER_FILL_DARK = 0xFF3D8FB2;

    public static int CHIP = 0xFFDCDCCD;
    public static int CHIP_SHADE = 0xFFA6A799;
    public static int CHIP_EDGE = 0xFF0C1216;
    public static int CHIP_TEXT = 0xFF1C2A1A;
    public static int FOOTER_TEXT = 0xFFD9E1E4;

    public static int PANEL = 0xE0061B25;
    public static int PANEL_FRAME = 0xFF2F5A69;
    public static int PANEL_FRAME_LIGHT = 0xFF4D7F90;
    public static int PANEL_TITLE = 0xFFDDE6EA;
    public static int PANEL_TEXT = 0xFFC7D2D7;
    public static int PANEL_DIVIDER = 0xFF2A3F4A;
    public static int MODIFIED = 0xFFFFA300;

    public static int SHADOW = 0xA0000000;
    public static int SCROLL_TRACK = 0x50000000;
    public static int SCROLL_THUMB = 0xFF5E7A87;
    public static int SCROLL_THUMB_HOVER = 0xFF8FA9B5;

    public static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    public static int withAlpha(int color, float alpha) {
        int a = Math.round((color >>> 24) * Math.max(0, Math.min(1, alpha)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    private record Slot(String name, int defaultColor, IntConsumer setter, String description) {}

    private static Slot slot(String name, int defaultColor, IntConsumer setter, String description) {
        return new Slot(name, defaultColor, setter, description);
    }

    // Built after the fields above, so each slot captures its field's default
    private static final List<Slot> SLOTS = List.of(
            slot("BG_TOP", BG_TOP, v -> BG_TOP = v, "Top of the background gradient"),
            slot("BG_MID", BG_MID, v -> BG_MID = v, "Middle of the background gradient"),
            slot("BG_BOTTOM", BG_BOTTOM, v -> BG_BOTTOM = v, "Bottom of the background gradient"),
            slot("BG_TINT", BG_TINT, v -> BG_TINT = v, "Tint applied over the background picture"),
            slot("BG_BLOCK", BG_BLOCK, v -> BG_BLOCK = v, "Faint decorative blocks in the background"),
            slot("BG_STREAK", BG_STREAK, v -> BG_STREAK = v, "Vertical light streaks in the background"),
            slot("BAR_TOP", BAR_TOP, v -> BAR_TOP = v, "Top of the header bar gradient"),
            slot("BAR_BOTTOM", BAR_BOTTOM, v -> BAR_BOTTOM = v, "Bottom of the header bar gradient"),
            slot("BAR_EDGE", BAR_EDGE, v -> BAR_EDGE = v, "Header bar bottom edge line"),
            slot("TAB_TEXT", TAB_TEXT, v -> TAB_TEXT = v, "Inactive tab text"),
            slot("TAB_HOVER", TAB_HOVER, v -> TAB_HOVER = v, "Hovered tab text"),
            slot("TAB_SELECTED", TAB_SELECTED, v -> TAB_SELECTED = v, "Selected tab text"),
            slot("ACCENT", ACCENT, v -> ACCENT = v, "Primary accent (orange) - used for highlights and the active tab underline"),
            slot("ACCENT_LIGHT", ACCENT_LIGHT, v -> ACCENT_LIGHT = v, "Lighter accent for gradients and glows"),
            slot("ACCENT_DARK", ACCENT_DARK, v -> ACCENT_DARK = v, "Darker accent for shadows"),
            slot("HEADER", HEADER, v -> HEADER = v, "Section header text in the list"),
            slot("ROW", ROW, v -> ROW = v, "List row background"),
            slot("ROW_HOVER", ROW_HOVER, v -> ROW_HOVER = v, "Hovered list row highlight"),
            slot("ROW_SELECTED", ROW_SELECTED, v -> ROW_SELECTED = v, "Selected list row highlight"),
            slot("ROW_BORDER", ROW_BORDER, v -> ROW_BORDER = v, "Row border when a dropdown is open"),
            slot("LABEL", LABEL, v -> LABEL = v, "Setting label text"),
            slot("LABEL_DIM", LABEL_DIM, v -> LABEL_DIM = v, "Dimmed label text (e.g. for sub-labels)"),
            slot("VALUE", VALUE, v -> VALUE = v, "Setting value text"),
            slot("VALUE_BOX", VALUE_BOX, v -> VALUE_BOX = v, "Value input box background"),
            slot("VALUE_BOX_HOVER", VALUE_BOX_HOVER, v -> VALUE_BOX_HOVER = v, "Value input box background when hovered/focused"),
            slot("VALUE_BOX_EDGE", VALUE_BOX_EDGE, v -> VALUE_BOX_EDGE = v, "Value input box border"),
            slot("MUTED", MUTED, v -> MUTED = v, "Muted secondary text"),
            slot("CARET", CARET, v -> CARET = v, "Text input caret"),
            slot("TOGGLE_ON", TOGGLE_ON, v -> TOGGLE_ON = v, "Toggle switch ON fill"),
            slot("TOGGLE_ON_DARK", TOGGLE_ON_DARK, v -> TOGGLE_ON_DARK = v, "Toggle switch ON shadow"),
            slot("TOGGLE_MARK", TOGGLE_MARK, v -> TOGGLE_MARK = v, "Toggle switch ON checkmark"),
            slot("TOGGLE_EDGE", TOGGLE_EDGE, v -> TOGGLE_EDGE = v, "Toggle switch border"),
            slot("TOGGLE_OFF", TOGGLE_OFF, v -> TOGGLE_OFF = v, "Toggle switch OFF fill"),
            slot("TOGGLE_CIRCLE", TOGGLE_CIRCLE, v -> TOGGLE_CIRCLE = v, "Toggle switch OFF circle"),
            slot("KNOB", KNOB, v -> KNOB = v, "Slider knob main color"),
            slot("KNOB_LIGHT", KNOB_LIGHT, v -> KNOB_LIGHT = v, "Slider knob top highlight"),
            slot("KNOB_SHADE", KNOB_SHADE, v -> KNOB_SHADE = v, "Slider knob bottom shade"),
            slot("KNOB_DARK", KNOB_DARK, v -> KNOB_DARK = v, "Slider knob outline"),
            slot("SLIDER_TRACK", SLIDER_TRACK, v -> SLIDER_TRACK = v, "Slider track background"),
            slot("SLIDER_FILL", SLIDER_FILL, v -> SLIDER_FILL = v, "Slider filled portion"),
            slot("SLIDER_FILL_DARK", SLIDER_FILL_DARK, v -> SLIDER_FILL_DARK = v, "Slider filled portion bottom edge"),
            slot("CHIP", CHIP, v -> CHIP = v, "Keybind chip background"),
            slot("CHIP_SHADE", CHIP_SHADE, v -> CHIP_SHADE = v, "Keybind chip bottom shade"),
            slot("CHIP_EDGE", CHIP_EDGE, v -> CHIP_EDGE = v, "Keybind chip border"),
            slot("CHIP_TEXT", CHIP_TEXT, v -> CHIP_TEXT = v, "Keybind chip text"),
            slot("FOOTER_TEXT", FOOTER_TEXT, v -> FOOTER_TEXT = v, "Footer (bottom bar) text"),
            slot("PANEL", PANEL, v -> PANEL = v, "Description panel background"),
            slot("PANEL_FRAME", PANEL_FRAME, v -> PANEL_FRAME = v, "Description panel frame"),
            slot("PANEL_FRAME_LIGHT", PANEL_FRAME_LIGHT, v -> PANEL_FRAME_LIGHT = v, "Description panel frame highlight"),
            slot("PANEL_TITLE", PANEL_TITLE, v -> PANEL_TITLE = v, "Description panel title text"),
            slot("PANEL_TEXT", PANEL_TEXT, v -> PANEL_TEXT = v, "Description panel body text"),
            slot("PANEL_DIVIDER", PANEL_DIVIDER, v -> PANEL_DIVIDER = v, "Description panel divider line"),
            slot("MODIFIED", MODIFIED, v -> MODIFIED = v, "\"Modified\" indicator text"),
            slot("SHADOW", SHADOW, v -> SHADOW = v, "Drop shadow under panels and bars"),
            slot("SCROLL_TRACK", SCROLL_TRACK, v -> SCROLL_TRACK = v, "Scrollbar track"),
            slot("SCROLL_THUMB", SCROLL_THUMB, v -> SCROLL_THUMB = v, "Scrollbar thumb"),
            slot("SCROLL_THUMB_HOVER", SCROLL_THUMB_HOVER, v -> SCROLL_THUMB_HOVER = v, "Scrollbar thumb when hovered"),
            slot("DROPDOWN_BG", DROPDOWN_BG, v -> DROPDOWN_BG = v, "Dropdown popup background"),
            slot("DROPDOWN_BORDER", DROPDOWN_BORDER, v -> DROPDOWN_BORDER = v, "Dropdown popup border"),
            slot("DROPDOWN_HOVER", DROPDOWN_HOVER, v -> DROPDOWN_HOVER = v, "Dropdown item hover highlight"),
            slot("DROPDOWN_SELECTED", DROPDOWN_SELECTED, v -> DROPDOWN_SELECTED = v, "Dropdown active item highlight")
    );

    private static final String TEMPLATE_HEADER = "// Colours for this mod's Dungeon Config screen, in ARGB hex";

    /**
     * Applies a mod's {@code colors.json5}, creating it from the mod's bundled file, its default colours
     * link or the built in template. Never waits for a download: when the colours still have to be
     * downloaded the built in colours are used, and {@code onDownloaded} runs once the file is ready.
     *
     * @param onDownloaded runs on a background thread after a download replaced the file, or {@code null}
     */
    public static void loadColors(String modId, Path configDir, String defaultColors, @Nullable Runnable onDownloaded) {
        resetToDefaults();
        Path file = configDir.resolve("colors.json5");
        boolean fromLink = defaultColors != null && !defaultColors.isBlank() && SafeDownloader.isUrl(defaultColors);
        try {
            Files.createDirectories(configDir);
            if (!DungeonsMediaCache.isConfigured()) {
                Path shared = configDir.getParent() != null ? configDir.getParent().resolve("dungeonconfig") : configDir;
                DungeonsMediaCache.configure(shared.resolve("cache").resolve("media"));
            }
            if (!Files.exists(file) && modId != null && DungeonsCanvas.hasResource(modId, "dungeonconfig/colors.json5")) {
                try (InputStream in = DungeonsCanvas.openResource(modId, "dungeonconfig/colors.json5")) {
                    if (in != null) Files.copy(in, file);
                }
            }
            if (fromLink && replaceable(file)) {
                Path ready = downloadedColors(defaultColors);
                if (ready != null) Files.copy(ready, file, StandardCopyOption.REPLACE_EXISTING);
                else if (onDownloaded != null) downloadColors(defaultColors, file, onDownloaded);
            }
            if (!Files.exists(file) && !fromLink) Files.writeString(file, template());
        } catch (IOException e) {
            LOGGER.warn("Could not prepare colors.json5 in {}: {}", configDir, e.getMessage());
        }
        applyFile(file);
    }

    // A missing file or the untouched template may be replaced by downloaded colours
    private static boolean replaceable(Path file) {
        if (!Files.exists(file)) return true;
        try {
            return Files.readString(file).startsWith(TEMPLATE_HEADER);
        } catch (IOException e) {
            return false;
        }
    }

    // Colours that were already downloaded earlier, without touching the network
    @Nullable
    private static Path downloadedColors(String link) throws IOException {
        Path cached = DungeonsMediaCache.cached(link);
        if (cached == null) return null;
        byte[] data = Files.readAllBytes(cached);
        if (!DungeonsMediaFiles.isTextUrlList(data)) return cached;
        String picked = DungeonsMediaFiles.pickRandomUrl(data);
        return picked != null ? DungeonsMediaCache.cached(picked) : null;
    }

    private static void downloadColors(String link, Path file, Runnable onDownloaded) {
        DungeonsMediaCache.fetch(link, MAX_COLORS_BYTES).thenCompose(downloaded -> {
            try {
                byte[] data = Files.readAllBytes(downloaded);
                if (!DungeonsMediaFiles.isTextUrlList(data)) return CompletableFuture.completedFuture(downloaded);
                String picked = DungeonsMediaFiles.pickRandomUrl(data);
                if (picked == null) return CompletableFuture.<Path>failedFuture(new IOException("the colour list has no links"));
                return DungeonsMediaCache.fetch(picked, MAX_COLORS_BYTES);
            } catch (IOException e) {
                return CompletableFuture.<Path>failedFuture(e);
            }
        }).thenAccept(colors -> {
            try {
                if (!replaceable(file)) return;
                Files.copy(colors, file, StandardCopyOption.REPLACE_EXISTING);
                onDownloaded.run();
            } catch (IOException e) {
                LOGGER.warn("Couldn't save downloaded colours to {}: {}", file, e.getMessage());
            }
        }).exceptionally(e -> {
            LOGGER.warn("Couldn't fetch default colours from {}: {}", link, Threads.rootMessage(e));
            return null;
        });
    }

    private static String template() {
        StringBuilder sb = new StringBuilder();
        sb.append(TEMPLATE_HEADER).append(" (alpha, red, green, blue).\n");
        sb.append("// Lines starting with // are comments. Edit a value and reopen the screen to see it.\n");
        sb.append("{\n");
        for (int i = 0; i < SLOTS.size(); i++) {
            Slot slot = SLOTS.get(i);
            sb.append("  // ").append(slot.description()).append('\n');
            sb.append("  \"").append(slot.name()).append("\": \"").append(hex(slot.defaultColor())).append("\"");
            if (i < SLOTS.size() - 1) sb.append(',');
            sb.append('\n');
        }
        sb.append("}\n");
        return sb.toString();
    }

    // Applies whatever colours the file holds; a broken file or value is logged and skipped
    private static void applyFile(Path file) {
        if (!Files.exists(file)) return;
        JsonObject json;
        try {
            JsonElement parsed = JsonParser.parseString(Json5.stripTrailingCommas(Files.readString(file)));
            if (!parsed.isJsonObject()) throw new JsonParseException("it isn't a JSON object");
            json = parsed.getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            if (WARNED_COLORS.add(file.toString())) LOGGER.warn("Ignoring {}, it can't be read: {}", file, e.getMessage());
            return;
        }
        for (Slot slot : SLOTS) {
            String name = slot.name();
            JsonElement element = json.get(name);
            if (element == null) continue;
            try {
                slot.setter().accept(Long.decode(element.getAsString().trim()).intValue());
            } catch (RuntimeException e) {
                if (WARNED_COLORS.add(name)) {
                    LOGGER.warn("Ignoring bad colour {} = {}: {}", name, element, e.getMessage());
                }
            }
        }
        if (!json.has("DROPDOWN_BG")) {
            DROPDOWN_BG = 0xF0000000 | (VALUE_BOX_HOVER & 0xFFFFFF);
        }
        if (!json.has("DROPDOWN_BORDER")) {
            DROPDOWN_BORDER = ROW_BORDER != 0 ? ROW_BORDER : VALUE_BOX_EDGE;
        }
        if (!json.has("DROPDOWN_HOVER")) {
            DROPDOWN_HOVER = ROW_HOVER != 0 ? ROW_HOVER : 0x24FFFFFF;
        }
        if (!json.has("DROPDOWN_SELECTED")) {
            DROPDOWN_SELECTED = ROW_SELECTED != 0 ? ROW_SELECTED : withAlpha(ACCENT, 0.35F);
        }
    }

    // Restore theme colors to defaults when returning to root mod list
    public static void resetToDefaults() {
        for (Slot slot : SLOTS) slot.setter().accept(slot.defaultColor());
    }

    private static String hex(int color) {
        return String.format(Locale.ROOT, "0x%08X", color);
    }
}
