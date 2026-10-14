package com.frikinjay.dungeonconfig.client.screen;

import com.frikinjay.dungeonconfig.DungeonConfigMod;
import com.frikinjay.dungeonconfig.api.Appearance;
import com.frikinjay.dungeonconfig.api.ConfigEntry;
import com.frikinjay.dungeonconfig.api.DungeonConfig;
import com.frikinjay.dungeonconfig.api.DungeonConfigs;
import com.frikinjay.dungeonconfig.api.RestartType;
import com.frikinjay.dungeonconfig.client.ui.DungeonsAnim;
import com.frikinjay.dungeonconfig.client.ui.DungeonsBackground;
import com.frikinjay.dungeonconfig.client.ui.DungeonsCanvas;
import com.frikinjay.dungeonconfig.client.ui.DungeonsDraw;
import com.frikinjay.dungeonconfig.client.ui.DungeonsFfmpeg;
import com.frikinjay.dungeonconfig.client.ui.DungeonsFfmpegInstaller;
import com.frikinjay.dungeonconfig.client.ui.DungeonsFont;
import com.frikinjay.dungeonconfig.client.ui.DungeonsFonts.Role;
import com.frikinjay.dungeonconfig.client.ui.DungeonsMusic;
import com.frikinjay.dungeonconfig.client.ui.DungeonsTextField;
import com.frikinjay.dungeonconfig.client.ui.DungeonsTheme;
import com.frikinjay.dungeonconfig.client.ui.DungeonsTooltip;
import com.frikinjay.dungeonconfig.client.ui.DungeonsUi;
import com.frikinjay.dungeonconfig.client.ui.DungeonsUiState;
import com.frikinjay.dungeonconfig.client.ui.ModIcons;
import com.frikinjay.dungeonconfig.network.ConfigSync;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

import static com.frikinjay.dungeonconfig.client.ui.DungeonsTheme.*;

/**
 * Interactive Minecraft Dungeons-styled configuration screen.
 * <p>
 * Displays options for a specific {@link DungeonConfig}, or acts as the global directory
 * when instantiated via {@link #list(Screen)}.
 */
public class DungeonConfigScreen extends Screen {

    private static final long TOOLTIP_DELAY_MS = 350;
    private static final long OPEN_MS = 380;
    private static final long TAB_MS = 240;
    private static final long DETAIL_MS = 200;
    private static boolean handoff = false;

    @Nullable private final Screen parent;
    @Nullable private final DungeonConfig config;
    private final Map<ConfigEntry<?>, DungeonConfig> modRows = new LinkedHashMap<>();
    private final List<String> categories;
    private ScreenMedia.Background background;
    private ScreenMedia.Music music;
    private final DungeonsAnim.Timer backgroundTimer = new DungeonsAnim.Timer();
    private String selectedCategory;
    private final Map<ConfigEntry<?>, Object> savedValues = new HashMap<>();
    private final DungeonsAnim.Timer savedTimer = new DungeonsAnim.Timer();
    private boolean savedOnce = false;
    private boolean saveFailed = false;
    private boolean confirming = false;
    private final DungeonsAnim.Timer confirmTimer = new DungeonsAnim.Timer();

    private final DungeonsTextField searchField = new DungeonsTextField(64);
    private final DungeonsTextField allEntriesSearchField = new DungeonsTextField(64);
    private final DungeonsTextField editField = new DungeonsTextField(32767);
    @Nullable private ConfigEntry<?> editingEntry;

    @Nullable private ConfigEntry<?> registryOpen = null;
    private final DungeonsAnim.Timer registryTimer = new DungeonsAnim.Timer();
    private int registryHover = -1;
    private int registryScroll = 0;
    private List<String> filteredRegistryIds = List.of();
    private String lastRegistryQuery = "";
    @Nullable private ConfigEntry<?> targetEntryOnOpen = null;

    private final List<Item> items = new ArrayList<>();
    private int scroll = 0;
    private int contentHeight = 0;
    @Nullable private ConfigEntry<?> selectedEntry;
    @Nullable private ConfigEntry<?> hoveredEntry;
    @Nullable private ConfigEntry<?> lastHovered;
    private long hoverStart;
    @Nullable private ConfigEntry<?> draggingSlider;
    private boolean draggingScrollbar = false;
    private boolean keyboardFocus = false;
    private int tabScroll = 0;

    private final DungeonsAnim.Timer openTimer = new DungeonsAnim.Timer();
    private final DungeonsAnim.Timer tabTimer = new DungeonsAnim.Timer();
    private final DungeonsAnim.Timer detailTimer = new DungeonsAnim.Timer();
    private int tabDirection = 1;
    private final DungeonsAnim.Smooth underlineX = new DungeonsAnim.Smooth(16);
    private final DungeonsAnim.Smooth underlineW = new DungeonsAnim.Smooth(16);
    private final DungeonsAnim.Smooth scrollAnim = new DungeonsAnim.Smooth(18);
    private final DungeonsAnim.Smooth selectY = new DungeonsAnim.Smooth(24);
    private final DungeonsAnim.Smooth selectAlpha = new DungeonsAnim.Smooth(14);
    private final DungeonsAnim.Smooth tooltipAlpha = new DungeonsAnim.Smooth(14);
    private final Map<ConfigEntry<?>, DungeonsAnim.Smooth> knobs = new HashMap<>();
    private final Map<ConfigEntry<?>, DungeonsAnim.Smooth> sliders = new HashMap<>();
    private final Map<ConfigEntry<?>, DungeonsAnim.Timer> flashes = new HashMap<>();
    private final Map<String, DungeonsAnim.Timer> presses = new HashMap<>();
    @Nullable private ConfigEntry<?> detailShown;
    private long lastFrame = 0;
    private float dt = 0;

    private float scale = 1.0F, k = 1.0F;
    private int vw, vh;
    private boolean showDetail;
    private final int p = 1;
    private final float cap = 8.0F;
    private static final long BACKGROUND_FADE_MS = OPEN_MS;
    private long backgroundDueAt = 0;
    @Nullable private ConfigEntry<?> choiceOpen = null;
    private int choiceHover = -1;
    private final DungeonsAnim.Timer choiceTimer = new DungeonsAnim.Timer();
    private final float titleCap = 8.0F;
    private int marginX, tabH, footH;
    private int listX, listW, detailX, detailW, bodyTop, bodyBottom;
    private int searchY, entrySearchY, searchH, headerY, contentY, contentH, rowW, rowH, rowGap, scrollW, rowPad;
    private int boxX, boxW, sliderX, sliderW, valueW;

    private int[] tabWidths = new int[0];
    private int titleWidth;
    @Nullable private Object tabKey;
    @Nullable private ConfirmLayout confirmCache;
    @Nullable private Object confirmCacheKey;
    @Nullable private ConfigEntry<?> tooltipEntry;
    @Nullable private DungeonsTooltip tooltipCache;
    private final Map<String, List<String>> wrapCache = new HashMap<>();

    private record Item(@Nullable String section, @Nullable ConfigEntry<?> entry, int y, int h) {}

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return DungeonsUi.inside(mx, my, x, y, w, h);
        }
    }

    public DungeonConfigScreen(@Nullable Screen parent, DungeonConfig config) {
        this(parent, config, true);
    }

    public DungeonConfigScreen(@Nullable Screen parent, @Nullable DungeonConfig config, @Nullable ConfigEntry<?> targetEntry) {
        this(parent, config, false);
        this.targetEntryOnOpen = targetEntry;
        if (targetEntry != null) {
            this.selectedCategory = targetEntry.getCategory();
            this.selectedEntry = targetEntry;
        }
    }

    public static DungeonConfigScreen list(@Nullable Screen parent) {
        return new DungeonConfigScreen(parent, null, false);
    }

    private DungeonConfigScreen(@Nullable Screen parent, @Nullable DungeonConfig config, boolean ignored) {
        super(Component.literal(titleFor(config)));
        this.parent = parent;
        this.config = config;
        if (config != null) {
            config.reload();
            this.categories = config.categories();
        } else {
            this.categories = List.of();
            for (DungeonConfig mod : DungeonConfigs.forList()) {
                modRows.put(new ConfigEntry<>(mod.modId(), mod.description(), "", "", String.class), mod);
            }
        }
        this.selectedCategory = categories.isEmpty() ? "" : categories.get(0);
        this.background = ScreenMedia.background(media());
        this.music = ScreenMedia.music(media());
        snapshot();
    }

    private static String titleFor(@Nullable DungeonConfig config) {
        if (config == null) return t("dungeonconfig.screen.list_title", "Dungeon Configs");
        String key = config.modId() + ".config.title";
        return I18n.exists(key) ? I18n.get(key) : t("dungeonconfig.screen.mod_title", "%s Settings", config.displayName());
    }

    private DungeonConfig media() {
        return config != null ? config : ScreenMedia.own();
    }

    private boolean listMode() {
        return config == null;
    }

    private List<ConfigEntry<?>> entries() {
        return config != null ? new ArrayList<>(config.entries()) : new ArrayList<>(modRows.keySet());
    }

    @Override
    public void added() {
        super.added();
        lastFrame = 0;
        if (!handoff) backgroundTimer.start();
        handoff = false;
    }

    // Rebuild layout on resize without tearing down textures mid-frame
    @Override
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        this.init();
    }

    @Override
    protected void init() {
        ScreenMedia.setupShared();
        ScreenMedia.configureFonts(media());
        stopEditing(true);
        choiceOpen = null;
        closeRegistry();
        tabKey = null;
        confirmCacheKey = null;
        tooltipCache = null;
        wrapCache.clear();
        if (config != null) loadColors();
        else DungeonsTheme.resetToDefaults();
        layout();
        rebuildItems();
        if (targetEntryOnOpen != null) {
            ConfigEntry<?> target = targetEntryOnOpen;
            targetEntryOnOpen = null;
            for (Item item : items) {
                if (item.entry() == target) {
                    selectedEntry = target;
                    scroll = Math.max(0, item.y() - contentH / 3);
                    clampScroll();
                    scrollAnim.snap(scroll);
                    selectY.snap(item.y() - scroll);
                    break;
                }
            }
        } else {
            scrollAnim.snap(scroll);
            selectY.reset();
        }
        underlineX.reset();
        underlineW.reset();
        ScreenMedia.tidyDownloads();
        requestBackground();
        startMusic();
        maybeOfferFfmpeg();
        ScreenMedia.prepareDefaults(media()).thenRun(() -> this.minecraft.execute(() -> {
            if (this.minecraft.screen == this) mediaChanged();
        }));
    }

    private void loadColors() {
        DungeonsTheme.loadColors(config.modId(), config.directory(), config.defaultColors(), () -> this.minecraft.execute(() -> {
            if (this.minecraft.screen != this) return;
            DungeonsTheme.loadColors(config.modId(), config.directory(), config.defaultColors(), null);
            tooltipCache = null;
        }));
    }

    private void mediaChanged() {
        requestBackground();
        music = ScreenMedia.music(media());
    }

    private void requestBackground() {
        backgroundDueAt = 0;
        background = ScreenMedia.background(media());
        DungeonsBackground.request(DungeonConfigMod.MOD_ID, background.folders(), background.spec(), background.blur(), background.fps());
    }

    private void startMusic() {
        music = ScreenMedia.music(media());
        DungeonsMusic.open(music.folders(), stateFile(), music.spec(), music.volume());
    }

    private static Path stateFile() {
        return ScreenMedia.own().directory().resolve("menu_state.json");
    }

    private int u(float units) {
        return Math.max(1, Math.round(units * k));
    }

    private String search() {
        return searchField.value().trim().toLowerCase(Locale.ROOT);
    }

    // Height of the text/dropdown box drawn inside a row
    private int valueBoxH() {
        return rowH - Math.max(6, u(8));
    }

    // Top of the value box in a row starting at rowY
    private int valueBoxY(int rowY) {
        return rowY + rowH / 2 - valueBoxH() / 2;
    }

    private int registryItemH() {
        return Math.max(Math.round(cap) + 8, u(18));
    }

    // Where text starts in a search box, after the magnifier icon
    private int searchTextX() {
        return listX + u(10) + DungeonsDraw.magnifierWidth(p) + u(8);
    }

    private String allEntriesSearch() {
        return allEntriesSearchField.value().trim().toLowerCase(Locale.ROOT);
    }

    private void layout() {
        scale = DungeonsUi.scaleFor(this.width, this.height);
        vw = Math.round(this.width / scale);
        vh = Math.round(this.height / scale);
        k = DungeonsUi.fit(vw, vh, 0.6F);

        int text = Math.round(cap);
        marginX = Math.max(10, Math.round(vw * 0.042F));
        tabH = Math.max(text * 2 + 14, u(40));
        footH = Math.max(text * 2 + 16, u(38));
        bodyTop = tabH + Math.max(8, u(16));
        bodyBottom = vh - footH - Math.max(4, u(8));

        showDetail = vw >= 620;
        int usable = vw - marginX * 2;
        int gap = Math.max(12, Math.round(vw * 0.03F));
        listX = marginX;
        listW = showDetail ? Math.round(usable * 0.665F) : usable;
        detailX = listX + listW + gap;
        detailW = vw - marginX - detailX;

        searchY = bodyTop;
        searchH = Math.max(text + 12, u(22));
        if (listMode()) {
            entrySearchY = searchY + searchH + Math.max(4, u(6));
            headerY = entrySearchY + searchH + Math.max(8, u(12));
        } else {
            entrySearchY = -1000;
            headerY = searchY + searchH + Math.max(10, u(16));
        }
        contentY = headerY + text + Math.max(6, u(10));
        contentH = Math.max(20, bodyBottom - contentY);
        scrollW = 2 * p;
        rowW = listW - scrollW - Math.max(4, u(8));
        rowH = Math.max(text + 16, u(29));
        rowGap = Math.max(1, u(3));
        rowPad = Math.max(8, u(14));

        boxX = listX + Math.round(rowW * 0.5F);
        boxW = listX + rowW - Math.max(6, u(10)) - boxX;
        DungeonsCanvas m = measure();
        valueW = Math.round(Math.max(m.width("100.0X", Role.VALUE, cap), Math.max(m.width("100000", Role.VALUE, cap), m.width("1440 M", Role.VALUE, cap)))) + 2;
        sliderX = boxX + valueW + Math.max(8, u(12));
        sliderW = Math.max(30, listX + rowW - rowPad - sliderX);
    }

    // Appends a section heading at y and returns the y below it
    private int addHeading(String text, int y) {
        int sectionH = Math.round(cap) + Math.max(10, u(14));
        if (y > 0) y += Math.max(4, u(6));
        items.add(new Item(text, null, y, sectionH));
        return y + sectionH;
    }

    // Appends an option row at y and returns the y below it
    private int addRow(ConfigEntry<?> entry, int y) {
        items.add(new Item(null, entry, y, rowH));
        return y + rowH + rowGap;
    }

    private void rebuildItems() {
        items.clear();
        int y = 0;
        String search = search();
        if (listMode()) {
            String allSearch = allEntriesSearch();
            if (!allSearch.isEmpty()) {
                for (DungeonConfig mod : DungeonConfigs.forList()) {
                    boolean headerAdded = false;
                    for (ConfigEntry<?> entry : mod.entries()) {
                        if (!matchesEntry(entry, allSearch)) continue;
                        if (!headerAdded) {
                            y = addHeading(mod.displayName().toUpperCase(Locale.ROOT), y);
                            headerAdded = true;
                        }
                        y = addRow(entry, y);
                    }
                }
            } else {
                for (ConfigEntry<?> entry : entries()) {
                    if (search.isEmpty() || matches(entry, search)) y = addRow(entry, y);
                }
            }
        } else if (search.isEmpty()) {
            String lastSection = null;
            for (ConfigEntry<?> entry : entries()) {
                if (!entry.getCategory().equals(selectedCategory)) continue;
                String section = entry.getSection();
                if (section != null && !section.equals(lastSection)) y = addHeading(sectionName(section), y);
                lastSection = section;
                y = addRow(entry, y);
            }
        } else {
            for (String category : categories) {
                boolean headerAdded = false;
                for (ConfigEntry<?> entry : entries()) {
                    if (!entry.getCategory().equals(category) || !matches(entry, search)) continue;
                    if (!headerAdded) {
                        y = addHeading(tabLabel(category), y);
                        headerAdded = true;
                    }
                    y = addRow(entry, y);
                }
            }
        }
        contentHeight = y == 0 ? 0 : y - rowGap + Math.max(2, u(4));
        if (selectedEntry != null && items.stream().noneMatch(i -> i.entry() == selectedEntry)) selectedEntry = null;
        clampScroll();
    }

    // Search across every mod: also matches the key, tab and section
    private boolean matchesEntry(ConfigEntry<?> entry, String query) {
        return contains(entry.getName(), query) || matches(entry, query)
                || contains(entry.getCategory(), query) || contains(entry.getSection(), query);
    }

    private boolean matches(ConfigEntry<?> entry, String search) {
        return contains(optionName(entry), search) || contains(optionDescription(entry), search);
    }

    private static boolean contains(@Nullable String text, String lowerCaseQuery) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(lowerCaseQuery);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - contentH);
    }

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private void draw(DungeonsCanvas c, int mx, int my) {
        int realX = mx, realY = my;
        if (confirming) {
            mx = -10000;
            my = -10000;
        }
        long now = DungeonsAnim.millis();
        DungeonsFont.collect();
        if (lastFrame == 0) openTimer.start();
        dt = lastFrame == 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;
        float seconds = (now % 10_000_000L) / 1000.0F;
        float open = DungeonsAnim.easeOut(openTimer.progress(OPEN_MS));

        hoveredEntry = null;
        if (backgroundDueAt != 0 && now >= backgroundDueAt) requestBackground();
        watchInstaller();
        DungeonsMusic.update(music.folders(), music.spec(), music.volume());

        c.alpha = DungeonsAnim.easeInOut(backgroundTimer.progress(BACKGROUND_FADE_MS));
        if (!DungeonsBackground.ready()) DungeonsDraw.backdrop(c, vw, vh);
        DungeonsBackground.draw(c, vw, vh, background.dim() / 100.0F, seconds);
        DungeonsDraw.blocks(c, vw, vh, Math.max(16, u(28)), seconds);
        if (config == null || config.fireflies()) {
            DungeonsDraw.streaks(c, vw, vh, seconds, 26);
            DungeonsDraw.motes(c, vw, vh, seconds, 34);
        }

        c.alpha = open;
        c.push();
        c.translate(Math.round(-(1 - open) * u(18)), 0);
        drawList(c, mx, my);
        c.pop();
        if (showDetail) {
            c.push();
            c.translate(Math.round((1 - open) * u(18)), 0);
            drawDetail(c);
            c.pop();
        }
        c.alpha = 1;

        c.push();
        c.translate(0, Math.round(-(1 - open) * tabH));
        drawTabs(c, mx, my);
        c.pop();
        c.push();
        c.translate(0, Math.round((1 - open) * footH));
        drawFooter(c, mx, my);
        c.pop();

        if (hoveredEntry != lastHovered) {
            lastHovered = hoveredEntry;
            hoverStart = now;
        }
        if (choiceOpen != null) {
            c.beginOverlay();
            drawChoices(c, mx, my);
            c.endOverlay();
        }
        if (registryOpen != null) {
            c.beginOverlay();
            drawRegistryPopup(c, mx, my);
            c.endOverlay();
        }
        boolean tip = !showDetail && choiceOpen == null && registryOpen == null && hoveredEntry != null && draggingSlider == null && now - hoverStart >= TOOLTIP_DELAY_MS;
        tooltipAlpha.set(tip ? 1 : 0);
        tooltipAlpha.update(dt);
        if (tip && tooltipAlpha.value > 0.01F) {
            c.beginOverlay();
            tooltipFor(hoveredEntry).draw(c, mx, my, vw, vh, cap, p, tooltipAlpha.value);
            c.endOverlay();
        }
        if (confirming) {
            c.beginOverlay();
            drawConfirm(c, realX, realY);
            c.endOverlay();
        }
    }

    private record ConfirmLayout(Rect box, Rect[] buttons) {}

    private record Dialog(String id, String title, String body, String[] labels, IntConsumer answer) {}

    @Nullable private Dialog dialog;

    private void showDialog(Dialog d) {
        dialog = d;
        confirming = true;
        confirmTimer.start();
        confirmCacheKey = null;
        searchField.blur();
    }

    private Dialog unsavedDialog() {
        return new Dialog("unsaved", t("dungeonconfig.screen.confirm.title", "Unsaved Changes"),
                t("dungeonconfig.screen.confirm.body", "Some settings have changed since you last saved. What would you like to do?"),
                new String[]{t("dungeonconfig.screen.confirm.save", "Save and Exit"), t("dungeonconfig.screen.confirm.discard", "Discard"),
                        t("dungeonconfig.screen.confirm.keep", "Keep Editing")},
                choice -> {
                    if (choice == 0) {
                        save(this::leave);
                    } else if (choice == 1) {
                        for (Map.Entry<ConfigEntry<?>, Object> saved : savedValues.entrySet()) restore(saved.getKey(), saved.getValue());
                        requestBackground();
                        leave();
                    }
                });
    }

    // Shown after saving changes that only apply once the world or game restarts, like NeoForge's restart notice
    private Dialog restartDialog(RestartType needed, Runnable then) {
        String body = needed == RestartType.GAME
                ? t("dungeonconfig.screen.restart.game", "Some of the settings you saved only take effect after you restart the game.")
                : t("dungeonconfig.screen.restart.world", "Some of the settings you saved only take effect after you leave and rejoin the world.");
        return new Dialog("restart_" + needed, t("dungeonconfig.screen.restart.title", "Restart Needed"), body,
                new String[]{t("dungeonconfig.screen.restart.ok", "Understood")}, choice -> then.run());
    }

    private int buttonCount() {
        return dialog == null ? 0 : dialog.labels().length;
    }

    private String confirmLabel(int which) {
        return dialog == null ? "" : dialog.labels()[which];
    }

    private String confirmKey(int which) {
        if (buttonCount() == 1) return keyEnter();
        return switch (which) {
            case 0 -> keyEnter();
            case 1 -> t("dungeonconfig.screen.key.backspace", "Backspace").toUpperCase(Locale.ROOT);
            default -> keyEsc();
        };
    }

    private String confirmBody() {
        return dialog == null ? "" : dialog.body();
    }

    private List<String> wrapped(DungeonsCanvas c, String text, Role role, float width) {
        String key = c.measureKey(role, cap) + "|" + role + "|" + width + "|" + text;
        List<String> lines = wrapCache.get(key);
        if (lines == null) {
            if (wrapCache.size() > 128) wrapCache.clear();
            lines = c.wrap(text, role, cap, width);
            wrapCache.put(key, lines);
        }
        return lines;
    }

    private ConfirmLayout confirmLayout(DungeonsCanvas c) {
        Object key = c.measureKey(Role.BODY, cap) + "|" + c.measureKey(Role.VALUE, cap) + "|" + vw + "x" + vh + "|" + k
                + "|" + (dialog == null ? "" : dialog.id());
        if (confirmCache == null || !key.equals(confirmCacheKey)) {
            confirmCache = computeConfirmLayout(c);
            confirmCacheKey = key;
        }
        return confirmCache;
    }

    private ConfirmLayout computeConfirmLayout(DungeonsCanvas c) {
        int pad = u(18), gap = u(10);
        int bh = DungeonsDraw.chipHeight(c, cap) + u(12);
        int n = buttonCount();
        int[] widths = new int[n];
        int total = 0;
        for (int i = 0; i < n; i++) {
            widths[i] = DungeonsDraw.promptWidth(c, confirmLabel(i), confirmKey(i), cap, p) + u(20);
            total += widths[i];
        }
        total += gap * Math.max(0, n - 1);
        int w = Math.min(vw - u(20), Math.max(total + pad * 2, u(320)));
        boolean stacked = total + pad * 2 > w;
        float line = c.capHeight(Role.BODY, cap) * 2.15F;
        int frame = 6 * p + u(10);
        int lines = wrapped(c, confirmBody(), Role.BODY, w - frame * 2).size();
        int textH = Math.round(c.capHeight(Role.BODY, cap) * 2.4F + lines * line);
        int buttonsH = stacked ? bh * n + gap * Math.max(0, n - 1) : bh;
        int h = frame * 2 + u(8) + textH + u(6) + buttonsH;
        int x = (vw - w) / 2, y = (vh - h) / 2;
        int by = y + h - frame - buttonsH;
        Rect[] r = new Rect[n];
        int bx = x + (w - total) / 2;
        for (int i = 0; i < n; i++) {
            if (stacked) {
                r[i] = new Rect(x + frame, by + i * (bh + gap), w - frame * 2, bh);
            } else {
                r[i] = new Rect(bx, by, widths[i], bh);
                bx += widths[i] + gap;
            }
        }
        return new ConfirmLayout(new Rect(x, y, w, h), r);
    }

    private void drawConfirm(DungeonsCanvas c, int mx, int my) {
        float in = DungeonsAnim.easeOut(confirmTimer.progress(220));
        c.rect(0, 0, vw, vh, withAlpha(0xC0020A10, in));
        float old = c.alpha;
        c.alpha = old * in;
        c.push();
        c.translate(0, Math.round((1 - in) * u(10)));
        ConfirmLayout l = confirmLayout(c);
        Rect box = l.box();
        c.rect(box.x(), box.y(), box.w(), box.h(), 0xF0061820);
        int inset = DungeonsDraw.panel(c, box.x(), box.y(), box.w(), box.h(), p);
        int tx = box.x() + inset + u(10);
        float ty = box.y() + inset + u(8);
        c.text((dialog == null ? "" : dialog.title()).toUpperCase(Locale.ROOT), Role.HEADING, cap, tx, ty, PANEL_TITLE);
        ty += c.capHeight(Role.BODY, cap) * 2.4F;
        for (String line : wrapped(c, confirmBody(), Role.BODY, box.w() - (inset + u(10)) * 2)) {
            c.text(line, Role.BODY, cap, tx, ty, PANEL_TEXT);
            ty += c.capHeight(Role.BODY, cap) * 2.15F;
        }
        Rect[] buttons = l.buttons();
        for (int i = 0; i < buttons.length; i++) {
            Rect b = buttons[i];
            boolean hover = b.contains(mx, my);
            DungeonsDraw.valueBox(c, b.x(), b.y(), b.w(), b.h(), p, hover, i == 0);
            int pw = DungeonsDraw.promptWidth(c, confirmLabel(i), confirmKey(i), cap, p);
            DungeonsDraw.prompt(c, confirmLabel(i), confirmKey(i), b.x() + (b.w() - pw) / 2, b.y() + b.h() / 2, cap, p, hover, 0);
        }
        c.pop();
        c.alpha = old;
    }

    private void answerConfirm(int choice) {
        DungeonsUi.click();
        confirming = false;
        Dialog d = dialog;
        dialog = null;
        if (d != null) d.answer().accept(choice);
    }

    private static boolean ffmpegAsked = false;
    @Nullable private DungeonsFfmpegInstaller.State installerSeen = null;
    private final DungeonsAnim.Timer installerTimer = new DungeonsAnim.Timer();

    private void maybeOfferFfmpeg() {
        if (ffmpegAsked || confirming) return;
        DungeonsFfmpegInstaller.State state = DungeonsFfmpegInstaller.state();
        if (state == DungeonsFfmpegInstaller.State.DOWNLOADING || state == DungeonsFfmpegInstaller.State.INSTALLING) return;
        DungeonsFfmpegInstaller.Build build = DungeonsFfmpegInstaller.forThisSystem();
        if (build == null || DungeonsUiState.get("ffmpegNeverAsk", false)) return;
        if (!ScreenMedia.needsFfmpeg(media()) || DungeonsFfmpeg.available()) return;
        ffmpegAsked = true;
        String body = t("dungeonconfig.screen.ffmpeg.body1", "Your menu background or music needs FFmpeg, a free program that plays videos and most audio formats. Dungeon Config can download it into config/dungeonconfig/bin for you, where every mod's settings menu can use it.")
                + "\n\n" + t("dungeonconfig.screen.ffmpeg.from", "Download from: %s", build.url())
                + "\n" + t("dungeonconfig.screen.ffmpeg.provider", "Build provided by %s, about %s MB.", build.provider(), String.valueOf(build.approxMb()))
                + "\n\n" + t("dungeonconfig.screen.ffmpeg.disclaimer", "FFmpeg is third-party software made by the FFmpeg project and licensed under the GPL. It is not made by Dungeon Config, which only checks the download against the checksum the provider publishes. Only continue if you trust this source. You can also install FFmpeg yourself instead.");
        showDialog(new Dialog("ffmpeg", t("dungeonconfig.screen.ffmpeg.title", "Download FFmpeg?"), body,
                new String[]{t("dungeonconfig.screen.ffmpeg.download", "Download"), t("dungeonconfig.screen.ffmpeg.never", "Don't Ask Again"),
                        t("dungeonconfig.screen.ffmpeg.later", "Not Now")},
                choice -> {
                    if (choice == 0) DungeonsFfmpegInstaller.install(DungeonsFfmpeg.installFolder());
                    else if (choice == 1) DungeonsUiState.set("ffmpegNeverAsk", true);
                }));
    }

    private void watchInstaller() {
        DungeonsFfmpegInstaller.State state = DungeonsFfmpegInstaller.state();
        if (state == installerSeen) return;
        boolean first = installerSeen == null;
        installerSeen = state;
        if (first) return;
        installerTimer.start();
        if (state == DungeonsFfmpegInstaller.State.DONE) {
            DungeonsBackground.release();
            requestBackground();
            DungeonsMusic.retry();
        }
    }

    @SuppressWarnings("unchecked")
    private static void restore(ConfigEntry<?> entry, Object value) {
        ((ConfigEntry<Object>) entry).revertTo(value);
    }

    private enum Control { TOGGLE, CHOICES, SLIDER, TEXT }

    // Picks the widget for an option: toggle, dropdown, slider for bounded numbers, text box for everything else
    private static Control control(ConfigEntry<?> entry) {
        if (entry.getType() == Boolean.class) return Control.TOGGLE;
        if (entry.hasChoices() && !entry.isList()) return Control.CHOICES;
        if (entry.isNumber() && entry.hasRange()) return Control.SLIDER;
        return Control.TEXT;
    }

    private void leave() {
        if (parent instanceof DungeonConfigScreen) handoff = true;
        this.minecraft.setScreen(parent);
    }

    private void openMod(DungeonConfig mod) {
        handoff = true;
        this.minecraft.setScreen(new DungeonConfigScreen(this, mod));
    }

    private void openModEntry(DungeonConfig mod, ConfigEntry<?> entry) {
        handoff = true;
        this.minecraft.setScreen(new DungeonConfigScreen(this, mod, entry));
    }

    private DungeonsTooltip tooltipFor(ConfigEntry<?> entry) {
        if (entry != tooltipEntry || tooltipCache == null) {
            tooltipEntry = entry;
            tooltipCache = buildTooltip(entry);
        }
        return tooltipCache;
    }

    private DungeonsTooltip buildTooltip(ConfigEntry<?> entry) {
        DungeonConfig mod = modRows.get(entry);
        if (mod != null) {
            DungeonsTooltip tip = new DungeonsTooltip(mod.displayName()).body(modDescription(mod));
            if (!mod.version().isEmpty()) tip.row(t("dungeonconfig.screen.version", "Version"), mod.version(), VALUE);
            return tip.footer(t("dungeonconfig.screen.open_hint", "Click to open its settings"));
        }
        DungeonsTooltip tip = new DungeonsTooltip(optionName(entry)).body(optionDescription(entry));
        String range = range(entry);
        if (range != null) tip.row(rangeLabel(entry), range, VALUE);
        tip.row(t("dungeonconfig.screen.detail.default", "Default"), describe(entry, entry.getDefaultValue()), VALUE);
        String restart = restartText(entry);
        if (restart != null) tip.body(optionDescription(entry) + "\n" + restart);
        return tip.footer(t("dungeonconfig.screen.detail.reset_hint", "Right-click to reset to default"));
    }

    @Nullable
    private static String restartText(ConfigEntry<?> entry) {
        RestartType type = entry.getRestartType();
        if (type == RestartType.NONE) return null;
        boolean world = type == RestartType.WORLD;
        if (entry.isRestartPending()) {
            return world ? t("dungeonconfig.screen.restart.pending.world", "Saved. Applies after you rejoin the world.")
                    : t("dungeonconfig.screen.restart.pending.game", "Saved. Applies after you restart the game.");
        }
        return world ? t("dungeonconfig.screen.restart.needs.world", "Changes apply after rejoining the world.")
                : t("dungeonconfig.screen.restart.needs.game", "Changes apply after restarting the game.");
    }

    private float pressOffset(String key) {
        DungeonsAnim.Timer timer = presses.get(key);
        if (timer == null || !timer.running(140)) return 0;
        return 1;
    }

    private void press(String key) {
        presses.computeIfAbsent(key, x -> new DungeonsAnim.Timer()).start();
    }

    private String titleText() {
        return this.title.getString().toUpperCase(Locale.ROOT);
    }

    private Rect closeButton() {
        int s = DungeonsDraw.closeSize(p);
        return new Rect(vw - marginX - s, (tabH - s) / 2, s, s);
    }

    private void measureTabs(DungeonsCanvas c) {
        Object key = c.measureKey(Role.HEADING, cap) + "|" + c.measureKey(Role.TITLE, titleCap);
        if (key.equals(tabKey) && tabWidths.length == categories.size()) return;
        tabKey = key;
        tabWidths = new int[categories.size()];
        for (int i = 0; i < categories.size(); i++) tabWidths[i] = Math.round(c.width(tabLabel(categories.get(i)), Role.HEADING, cap));
        titleWidth = Math.round(c.width(titleText(), Role.TITLE, titleCap));
    }

    private int titleX(DungeonsCanvas c) {
        measureTabs(c);
        return closeButton().x() - u(18) - titleWidth;
    }

    private Rect tabArea(DungeonsCanvas c) {
        int chip = DungeonsDraw.chipWidth(c, "Q", cap) + u(12);
        int left = marginX + chip;
        int right = titleX(c) - u(24) - chip;
        if (right - left < tabsWidth(c)) right = closeButton().x() - u(16) - chip;
        return new Rect(left, 0, Math.max(10, right - left), tabH);
    }

    private int tabsWidth(DungeonsCanvas c) {
        measureTabs(c);
        int total = 0;
        for (int w : tabWidths) total += w + u(26);
        return Math.max(0, total - u(26));
    }

    private boolean titleVisible(DungeonsCanvas c) {
        Rect area = tabArea(c);
        return area.x() + area.w() < titleX(c);
    }

    private String tabLabel(String category) {
        return categoryName(category).toUpperCase(Locale.ROOT);
    }

    private List<Rect> tabRects(DungeonsCanvas c) {
        Rect area = tabArea(c);
        int gap = u(26);
        int total = 0, selStart = 0, selEnd = 0;
        measureTabs(c);
        int[] widths = tabWidths;
        for (int i = 0; i < categories.size(); i++) {
            int w = widths[i];
            if (categories.get(i).equals(selectedCategory)) {
                selStart = total;
                selEnd = total + w;
            }
            total += w + gap;
        }
        total -= gap;
        int x;
        if (total <= area.w()) {
            tabScroll = 0;
            x = area.x();
        } else {
            if (selStart - gap < tabScroll) tabScroll = selStart - gap;
            if (selEnd + gap > tabScroll + area.w()) tabScroll = selEnd + gap - area.w();
            tabScroll = DungeonsUi.clamp(tabScroll, 0, total - area.w());
            x = area.x() - tabScroll;
        }
        List<Rect> rects = new ArrayList<>(categories.size());
        for (int w : widths) {
            rects.add(new Rect(x - gap / 2, 0, w + gap, tabH));
            x += w + gap;
        }
        return rects;
    }

    private void drawTabs(DungeonsCanvas c, int mx, int my) {
        DungeonsDraw.topBar(c, vw, tabH);
        Rect area = tabArea(c);
        List<Rect> rects = tabRects(c);
        int cy = tabH / 2;
        int gap = u(26);
        boolean searching = !search().isEmpty();

        if (!categories.isEmpty()) {
            int qw = DungeonsDraw.chipWidth(c, "Q", cap);
            int firstX = rects.isEmpty() ? area.x() : rects.get(0).x() + gap / 2;
            DungeonsDraw.chip(c, "Q", Math.max(marginX, Math.min(firstX, area.x()) - qw - u(12)), cy, cap, pressOffset("Q"));
            int lastX = rects.isEmpty() ? area.x() : rects.get(rects.size() - 1).x() + rects.get(rects.size() - 1).w() - gap / 2;
            DungeonsDraw.chip(c, "E", Math.min(lastX, area.x() + area.w()) + u(12), cy, cap, pressOffset("E"));
        }

        c.scissor(area.x() - u(8), 0, area.x() + area.w() + u(8), tabH);
        for (int i = 0; i < categories.size(); i++) {
            Rect r = rects.get(i);
            boolean selected = !searching && categories.get(i).equals(selectedCategory);
            boolean hover = r.contains(mx, my) && area.contains(mx, my);
            int color = selected ? TAB_SELECTED : hover ? TAB_HOVER : TAB_TEXT;
            int tx0 = r.x() + gap / 2, tx1 = r.x() + r.w() - gap / 2;
            float shown = (Math.min(tx1, area.x() + area.w()) - Math.max(tx0, area.x())) / (float) Math.max(1, tx1 - tx0);
            if (shown < 1) color = withAlpha(color, shown * shown * 0.8F);
            c.textMid(tabLabel(categories.get(i)), Role.HEADING, cap, r.x() + gap / 2.0F, cy - 1, color);
            if (selected) {
                float[] ink = c.ink(tabLabel(categories.get(i)), Role.HEADING, cap);
                underlineX.set(r.x() + gap / 2.0F + ink[0] - u(6));
                underlineW.set(ink[1] - ink[0] + u(12));
            }
        }
        if (!searching) {
            underlineX.update(dt);
            underlineW.update(dt);
            DungeonsDraw.tabUnderline(c, Math.round(underlineX.value), Math.round(underlineW.value), tabH, p);
        }
        c.endScissor();

        if (titleVisible(c)) {
            c.textMid(titleText(), Role.TITLE, titleCap, titleX(c), cy, VALUE, SHADOW);
        }
        Rect close = closeButton();
        DungeonsDraw.close(c, close.x(), close.y(), p, close.contains(mx, my));
    }

    private float tabSlide() {
        return DungeonsAnim.easeOut(tabTimer.progress(TAB_MS));
    }

    private void drawSearchBox(DungeonsCanvas c, DungeonsTextField field, int y, int mx, int my, String hint) {
        boolean hover = DungeonsUi.inside(mx, my, listX, y, rowW, searchH);
        DungeonsDraw.valueBox(c, listX, y, rowW, searchH, p, hover, field.focused);
        int cy = y + searchH / 2;
        DungeonsDraw.magnifier(c, listX + u(10), cy, p, field.focused ? VALUE : LABEL_DIM);
        int textX = searchTextX();
        field.draw(c, textX, cy, listX + rowW - u(10) - textX, Role.BODY, cap, VALUE, hint, MUTED);
    }

    private void drawList(DungeonsCanvas c, int mx, int my) {
        drawSearchBox(c, searchField, searchY, mx, my,
                listMode() ? t("dungeonconfig.screen.search.mods_hint", "Search mods...") : t("dungeonconfig.screen.search.hint", "Search options..."));
        if (listMode()) {
            drawSearchBox(c, allEntriesSearchField, entrySearchY, mx, my,
                    t("dungeonconfig.screen.search.all_entries_hint", "Search all mod options..."));
        }

        float slide = tabSlide();
        float oldAlpha = c.alpha;
        c.alpha = oldAlpha * slide;
        c.push();
        c.translate(Math.round((1 - slide) * u(22) * tabDirection), 0);

        String header = !allEntriesSearch().isEmpty() || !search().isEmpty()
                ? t("dungeonconfig.screen.search.results", "Search Results").toUpperCase(Locale.ROOT)
                : listMode() ? t("dungeonconfig.screen.mods", "Mods").toUpperCase(Locale.ROOT) : tabLabel(selectedCategory);
        DungeonsDraw.header(c, header, listX, headerY, cap);

        scrollAnim.set(scroll);
        float scrolled = scrollAnim.update(dt);
        c.scissor(listX - 2, contentY - 2, listX + listW + 2, contentY + contentH);
        if (items.isEmpty()) {
            String none = listMode() && !allEntriesSearch().isEmpty()
                    ? t("dungeonconfig.screen.no_entry_results", "No options match \"%s\"", allEntriesSearchField.value().trim())
                    : listMode() && searchField.value().isBlank()
                    ? t("dungeonconfig.screen.no_mods", "No mods use Dungeon Config yet")
                    : listMode()
                    ? t("dungeonconfig.screen.no_mod_results", "No mods match \"%s\"", searchField.value().trim())
                    : t("dungeonconfig.screen.no_results", "No options match \"%s\"", searchField.value().trim());
            c.text(c.trim(none, Role.BODY, cap, rowW), Role.BODY, cap, listX + rowPad, contentY + u(12), MUTED);
        }
        boolean mouseInContent = DungeonsUi.inside(mx, my, listX, contentY, rowW, contentH);
        float focusY = Float.NaN;
        ConfigEntry<?> focus = keyboardFocus ? selectedEntry : null;
        for (Item item : items) {
            int y = Math.round(contentY + item.y() - scrolled);
            if (y + item.h() < contentY - rowH || y > contentY + contentH + rowH) continue;
            if (item.section() != null) {
                c.text(item.section(), Role.HEADING, cap, listX, y + (item.h() - cap) / 2.0F, LABEL_DIM);
                continue;
            }
            ConfigEntry<?> entry = item.entry();
            boolean hover = mouseInContent && draggingSlider == null && DungeonsUi.inside(mx, my, listX, y, rowW, rowH);
            if (hover) {
                hoveredEntry = entry;
                if (!keyboardFocus) focus = entry;
            }
            if (draggingSlider == entry) focus = entry;
            if (entry == focus) focusY = contentY + item.y();
            DungeonsDraw.row(c, listX, y, rowW, rowH, false);
        }

        if (!Float.isNaN(focusY)) {
            if (selectAlpha.value < 0.05F) selectY.snap(focusY);
            else selectY.set(focusY);
            selectAlpha.set(1);
        } else {
            selectAlpha.set(0);
        }
        selectY.update(dt);
        selectAlpha.update(dt);
        DungeonsDraw.selection(c, listX, Math.round(selectY.value - scrolled), rowW, rowH, p, selectAlpha.value);

        for (Item item : items) {
            if (item.entry() == null) continue;
            int y = Math.round(contentY + item.y() - scrolled);
            if (y + rowH < contentY || y > contentY + contentH) continue;
            drawRow(c, item.entry(), y, mx, my, item.entry() == focus);
        }
        c.endScissor();
        c.pop();
        c.alpha = oldAlpha;

        if (maxScroll() > 0) {
            int trackX = listX + listW - scrollW;
            int thumbH = scrollThumbHeight();
            boolean hover = draggingScrollbar || DungeonsUi.inside(mx, my, trackX - u(4), contentY, scrollW + u(8), contentH);
            DungeonsDraw.scrollbar(c, trackX, contentY, contentH, scrollThumbY(thumbH, scrolled), thumbH, p, hover);
        }
    }

    private int scrollThumbHeight() {
        return Math.max(u(24), (int) ((float) contentH / Math.max(1, contentHeight) * contentH));
    }

    private int scrollThumbY(int thumbH, float scrolled) {
        return contentY + (int) ((contentH - thumbH) * (scrolled / Math.max(1, maxScroll())));
    }

    private int toggleX() {
        return listX + rowW - rowPad - DungeonsDraw.toggleWidth(p);
    }

    private float flash(ConfigEntry<?> entry) {
        DungeonsAnim.Timer timer = flashes.get(entry);
        return timer == null ? 0 : 1 - timer.progress(260);
    }

    private void drawRow(DungeonsCanvas c, ConfigEntry<?> entry, int y, int mx, int my, boolean focused) {
        int cy = y + rowH / 2;
        int labelX = listX + rowPad;
        DungeonConfig mod = modRows.get(entry);
        if (mod != null) {
            boolean hover = DungeonsUi.inside(mx, my, listX, y, rowW, rowH);
            int aw = DungeonsDraw.arrowWidth(p);
            int ax = listX + rowW - rowPad - aw;
            DungeonsDraw.arrow(c, ax, cy, p, true, hover || focused ? 0xFFFFFFFF : LABEL_DIM);
            String count = settingsCount(mod).toUpperCase(Locale.ROOT);
            float countW = c.width(count, Role.VALUE, cap);
            c.textRight(count, Role.VALUE, cap, ax - u(10), cy, hover || focused ? VALUE : MUTED);

            int iconSize = Math.max(14, u(16));
            drawModIcon(c, mod, labelX, cy - iconSize / 2, iconSize);
            labelX += iconSize + u(8);

            int room = Math.round(ax - u(24) - countW - labelX);
            c.textMid(c.trim(mod.displayName(), Role.BODY, cap, room), Role.BODY, cap, labelX, cy, focused ? VALUE : LABEL);
            return;
        }
        Control control = control(entry);
        int labelRoom = boxX - u(12) - labelX;
        if (control == Control.TOGGLE) labelRoom = toggleX() - u(70) - labelX;

        String name = optionName(entry);
        String shown = c.trim(name, Role.BODY, cap, labelRoom - (entry.isDefault() ? 0 : u(12)));
        float nameW = c.textMid(shown, Role.BODY, cap, labelX, cy, focused ? VALUE : LABEL);
        if (!entry.isDefault()) DungeonsDraw.modified(c, labelX + Math.round(nameW) + u(7), cy, p);

        float flash = flash(entry);
        if (control == Control.TOGGLE) {
            boolean on = (Boolean) entry.getLocalValue();
            DungeonsAnim.Smooth knob = knobs.computeIfAbsent(entry, e -> new DungeonsAnim.Smooth(22));
            knob.set(on ? 1 : 0);
            knob.update(dt);
            int tx = toggleX();
            boolean hover = DungeonsUi.inside(mx, my, tx - u(4), y, DungeonsDraw.toggleWidth(p) + u(8), rowH);
            DungeonsDraw.toggle(c, tx, cy, p, knob.value, hover);
            String value = (on ? t("dungeonconfig.screen.value.on", "On") : t("dungeonconfig.screen.value.off", "Off")).toUpperCase(Locale.ROOT);
            int color = lerp(VALUE, ACCENT_LIGHT, flash);
            c.textRight(value, Role.VALUE, cap, tx - u(9), cy, color);
        } else if (control == Control.TEXT) {
            int bh = valueBoxH();
            int by = cy - bh / 2;
            boolean editing = entry == editingEntry;
            boolean hover = DungeonsUi.inside(mx, my, boxX, by, boxW, bh);
            DungeonsDraw.valueBox(c, boxX, by, boxW, bh, p, hover, editing || registryOpen == entry);
            int arrowW = entry.hasRegistry() ? DungeonsDraw.arrowDownWidth(p) + u(6) : 0;
            int inner = boxW - u(16) - arrowW;
            if (editing) {
                editField.draw(c, boxX + u(8), cy, inner, Role.BODY, cap, VALUE, "", MUTED);
            } else {
                String text;
                Object val = entry.getLocalValue();
                if (entry.isList()) {
                    text = val instanceof List<?> l ? l.toString() : "[]";
                } else if (val instanceof String value) {
                    text = value.isEmpty() ? t("dungeonconfig.screen.text.empty", "Empty") : value;
                } else {
                    text = entry.isNumber() ? formatValue(entry, val) : entry.toEditText();
                }
                c.textMid(c.trim(text, Role.BODY, cap, inner), Role.BODY, cap, boxX + u(8), cy,
                        text.isEmpty() ? MUTED : lerp(LABEL, ACCENT_LIGHT, flash));
            }
            if (entry.hasRegistry()) {
                int ax = boxX + boxW - u(8) - DungeonsDraw.arrowDownWidth(p);
                DungeonsDraw.arrowDown(c, ax, cy, p, hover || registryOpen == entry ? ACCENT_LIGHT : LABEL_DIM);
            }
        } else if (control == Control.CHOICES) {
            Rect box = choiceBox(c, entry, y);
            boolean hover = box.contains(mx, my) && choiceOpen == null;
            DungeonsDraw.valueBox(c, box.x(), box.y(), box.w(), box.h(), p, hover, choiceOpen == entry);
            String value = formatValue(entry, entry.getLocalValue()).toUpperCase(Locale.ROOT);
            c.textMid(value, Role.VALUE, cap, box.x() + u(8), cy, lerp(VALUE, ACCENT_LIGHT, flash));
            DungeonsDraw.arrowDown(c, box.x() + box.w() - u(8) - DungeonsDraw.arrowDownWidth(p), cy, p, hover || choiceOpen == entry ? ACCENT_LIGHT : LABEL_DIM);
        } else {
            DungeonsAnim.Smooth fill = sliders.computeIfAbsent(entry, e -> new DungeonsAnim.Smooth(20));
            float target = sliderFraction(entry);
            if (draggingSlider == entry) fill.snap(target);
            else fill.set(target);
            fill.update(dt);
            String value = formatValue(entry, entry.getLocalValue()).toUpperCase(Locale.ROOT);
            c.textMid(value, Role.VALUE, cap, boxX, cy, lerp(VALUE, ACCENT_LIGHT, flash));
            boolean hover = DungeonsUi.inside(mx, my, sliderX - u(4), y, sliderW + u(8), rowH);
            DungeonsDraw.slider(c, sliderX, cy, sliderW, p, fill.value, hover, draggingSlider == entry);
        }
    }

    @Nullable
    private ConfigEntry<?> focusEntry() {
        if (draggingSlider != null) return draggingSlider;
        if (editingEntry != null) return editingEntry;
        if (!keyboardFocus && hoveredEntry != null) return hoveredEntry;
        if (selectedEntry != null) return selectedEntry;
        for (Item item : items) {
            if (item.entry() != null) return item.entry();
        }
        return null;
    }

    private void drawDetail(DungeonsCanvas c) {
        int top = bodyTop, h = bodyBottom - bodyTop;
        int inset = DungeonsDraw.panel(c, detailX, top, detailW, h, p);
        ConfigEntry<?> entry = focusEntry();
        if (entry == null) return;
        if (modRows.containsKey(entry)) {
            drawModDetail(c, entry, modRows.get(entry), top, h, inset);
            return;
        }
        float old = beginDetail(c, entry);
        int pad = inset + u(12);
        int x = detailX + pad;
        int w = detailW - pad * 2;
        float line = Math.round(c.capHeight(Role.BODY, cap) * 2.15F);
        float y = top + pad + u(2);

        for (String l : wrapped(c, optionName(entry).toUpperCase(Locale.ROOT), Role.HEADING, w)) {
            c.text(l, Role.HEADING, cap, x, y, PANEL_TITLE);
            y += line;
        }
        if (!entry.isDefault()) {
            DungeonsDraw.modified(c, x, Math.round(y + cap / 2), p);
            c.text(t("dungeonconfig.screen.badge.modified", "Modified"), Role.BODY, cap, x + DungeonsDraw.modifiedWidth(p) + u(6), y, MODIFIED);
            y += line;
        }
        String restart = restartText(entry);
        if (restart != null) {
            for (String l : wrapped(c, restart, Role.BODY, w)) {
                c.text(l, Role.BODY, cap, x, y, entry.isRestartPending() ? ACCENT_LIGHT : LABEL_DIM);
                y += line;
            }
        }
        if (entry.isServerControlled()) {
            Object server = entry.getValue();
            String text = t("dungeonconfig.screen.badge.server", "Server uses %s", describe(entry, server));
            for (String l : wrapped(c, text, Role.BODY, w)) {
                c.text(l, Role.BODY, cap, x, y, ACCENT_LIGHT);
                y += line;
            }
        }
        y += line * 0.15F;

        int footerTop = top + h - pad - Math.round(line * 1.2F);
        String range = range(entry);
        int reserved = range != null ? Math.round(line * 1.3F) : 0;
        for (String l : wrapped(c, optionDescription(entry), Role.BODY, w)) {
            if (y + line > footerTop - reserved) {
                break;
            }
            c.text(l, Role.BODY, cap, x, y, PANEL_TEXT);
            y += line;
        }
        if (range != null) {
            float ry = Math.max(y + line * 0.3F, footerTop - reserved);
            float lw = c.text(rangeLabel(entry) + ":", Role.BODY, cap, x, ry, LABEL_DIM);
            c.text(range.toUpperCase(Locale.ROOT), Role.VALUE, cap, x + lw + u(8), ry + c.baselineShift(Role.BODY, Role.VALUE, cap), VALUE);
        }

        DungeonsDraw.divider(c, x - u(4), x + w + u(4), footerTop - Math.round(line * 0.5F), p);
        float lw = c.text(t("dungeonconfig.screen.detail.default", "Default") + ":", Role.BODY, cap, x, footerTop + line * 0.2F, PANEL_TEXT);
        String def = c.trim(describe(entry, entry.getDefaultValue()).toUpperCase(Locale.ROOT), Role.VALUE, cap, w - lw - u(8));
        c.text(def, Role.VALUE, cap, x + lw + u(8), footerTop + line * 0.2F + c.baselineShift(Role.BODY, Role.VALUE, cap), VALUE);
        c.pop();
        c.alpha = old;
    }

    // Fades and slides the detail panel in whenever it starts showing another entry; returns the alpha to restore
    private float beginDetail(DungeonsCanvas c, ConfigEntry<?> entry) {
        if (entry != detailShown) {
            detailShown = entry;
            detailTimer.start();
        }
        float in = DungeonsAnim.easeOut(detailTimer.progress(DETAIL_MS));
        float old = c.alpha;
        c.alpha = old * in;
        c.push();
        c.translate(0, Math.round((1 - in) * u(6)));
        return old;
    }

    private void drawModDetail(DungeonsCanvas c, ConfigEntry<?> entry, DungeonConfig mod, int top, int h, int inset) {
        float old = beginDetail(c, entry);
        int pad = inset + u(12);
        int x = detailX + pad;
        int w = detailW - pad * 2;
        float line = Math.round(c.capHeight(Role.BODY, cap) * 2.15F);
        float y = top + pad + u(2);

        int iconSize = Math.max(36, u(48));
        drawModIcon(c, mod, x, Math.round(y), iconSize);
        y += iconSize + Math.max(8, u(12));

        int footerTop = top + h - pad - Math.round(line * 1.2F);

        for (String l : wrapped(c, mod.displayName().toUpperCase(Locale.ROOT), Role.HEADING, w)) {
            if (y + line > footerTop) break;
            c.text(l, Role.HEADING, cap, x, y, PANEL_TITLE);
            y += line;
        }
        if (!mod.modId().isEmpty() && y + line <= footerTop) {
            c.text(c.trim(mod.modId(), Role.BODY, cap, w), Role.BODY, cap, x, y, LABEL_DIM);
            y += line;
        }
        if (!mod.version().isEmpty() && y + line <= footerTop) {
            c.text(c.trim(mod.version(), Role.BODY, cap, w), Role.BODY, cap, x, y, LABEL_DIM);
            y += line;
        }
        String authors = mod.authors();
        if (!authors.isEmpty()) {
            String text = t("dungeonconfig.screen.authors", "By %s", authors);
            for (String l : wrapped(c, text, Role.BODY, w)) {
                if (y + line > footerTop) break;
                c.text(l, Role.BODY, cap, x, y, LABEL_DIM);
                y += line;
            }
        }
        String license = mod.license();
        if (!license.isEmpty()) {
            String text = t("dungeonconfig.screen.license", "License: %s", license);
            for (String l : wrapped(c, text, Role.BODY, w)) {
                if (y + line > footerTop) break;
                c.text(l, Role.BODY, cap, x, y, LABEL_DIM);
                y += line;
            }
        }
        y += line * 0.15F;
        for (String l : wrapped(c, modDescription(mod), Role.BODY, w)) {
            if (y + line > footerTop) break;
            c.text(l, Role.BODY, cap, x, y, PANEL_TEXT);
            y += line;
        }
        DungeonsDraw.divider(c, x - u(4), x + w + u(4), footerTop - Math.round(line * 0.5F), p);
        String count = settingsCount(mod).toUpperCase(Locale.ROOT);
        c.text(c.trim(count, Role.VALUE, cap, w), Role.VALUE, cap, x, footerTop + line * 0.2F, VALUE);
        c.pop();
        c.alpha = old;
    }

    private void drawModIcon(DungeonsCanvas c, DungeonConfig mod, int x, int y, int size) {
        DungeonsCanvas.Texture icon = ModIcons.get(mod.modId());
        if (icon != null) {
            c.image(icon, x, y, size, size);
            return;
        }

        int borderThick = size >= 32 ? Math.max(1, p * 2) : p;
        c.rect(x, y, size, size, PANEL);
        c.outline(x, y, size, size, borderThick, PANEL_FRAME);

        String letter = ModIcons.getInitial(mod.displayName());
        float letterCap = Math.max(8.0F, Math.round(size * 0.65F));
        float[] ink = c.ink(letter, Role.TITLE, letterCap);
        float glyphW = (ink != null && ink.length >= 2 && ink[1] > ink[0]) ? (ink[1] - ink[0]) : c.width(letter, Role.TITLE, letterCap);
        if (glyphW > size - 4 * p && glyphW > 0) {
            letterCap = Math.max(6.0F, letterCap * ((size - 4 * p) / glyphW));
            ink = c.ink(letter, Role.TITLE, letterCap);
        }
        float cx = x + size / 2.0F;
        float cy = y + size / 2.0F;
        float drawX = (ink != null && ink.length >= 2 && ink[1] > ink[0])
                ? (cx - (ink[0] + ink[1]) / 2.0F)
                : (cx - c.width(letter, Role.TITLE, letterCap) / 2.0F);
        c.textMid(letter, Role.TITLE, letterCap, drawX, cy, VALUE, SHADOW);
    }

    private static String modDescription(DungeonConfig mod) {
        String text = mod.description();
        return text.isEmpty() ? t("dungeonconfig.screen.no_description", "No description.") : DungeonsUi.sentence(text);
    }

    private static String settingsCount(DungeonConfig mod) {
        int n = mod.entries().size();
        return n == 1 ? t("dungeonconfig.screen.one_setting", "1 setting") : t("dungeonconfig.screen.settings", "%s settings", n);
    }

    private int footerY() {
        return vh - footH / 2;
    }

    private Rect backPrompt(DungeonsCanvas c) {
        String label = t("dungeonconfig.screen.back", "Back");
        int w = DungeonsDraw.promptWidth(c, label, keyEsc(), cap, p);
        int h = DungeonsDraw.chipHeight(c, cap) + 4;
        return new Rect(marginX, footerY() - h / 2, w, h);
    }

    private String saveLabel() {
        return listMode() ? t("dungeonconfig.screen.open", "Open") : t("dungeonconfig.screen.save", "Save");
    }

    private Rect savePrompt(DungeonsCanvas c) {
        String label = saveLabel();
        int w = DungeonsDraw.promptWidth(c, label, keyEnter(), cap, p);
        int h = DungeonsDraw.chipHeight(c, cap) + 4;
        return new Rect(vw - marginX - w, footerY() - h / 2, w, h);
    }

    private String muteLabel() {
        return DungeonsMusic.muted() ? t("dungeonconfig.screen.music.unmute", "Unmute") : t("dungeonconfig.screen.music.mute", "Mute");
    }

    @Nullable
    private Rect mutePrompt(DungeonsCanvas c) {
        if (!DungeonsMusic.available()) return null;
        Rect back = backPrompt(c);
        int w = DungeonsDraw.speakerWidth(p) + u(6) + DungeonsDraw.promptWidth(c, muteLabel(), "M", cap, p);
        int h = DungeonsDraw.chipHeight(c, cap) + 4;
        return new Rect(back.x() + back.w() + u(24), footerY() - h / 2, w, h);
    }

    private void toggleMute() {
        press("M");
        DungeonsUi.click();
        DungeonsMusic.toggleMute();
    }

    private Rect resetPrompt(DungeonsCanvas c) {
        String label = t("dungeonconfig.screen.reset", "Reset");
        int w = DungeonsDraw.promptWidth(c, label, null, cap, p);
        int h = DungeonsDraw.chipHeight(c, cap) + 4;
        return new Rect(savePrompt(c).x() - u(28) - w, footerY() - h / 2, w, h);
    }

    private static String keyEsc() {
        return t("dungeonconfig.screen.key.esc", "Esc").toUpperCase(Locale.ROOT);
    }

    private static String keyEnter() {
        return t("dungeonconfig.screen.key.enter", "Enter").toUpperCase(Locale.ROOT);
    }

    private void drawFooter(DungeonsCanvas c, int mx, int my) {
        c.gradientV(0, vh - footH - u(10), vw, vh, 0x00000000, 0x70000000);
        int cy = footerY();
        Rect back = backPrompt(c);
        DungeonsDraw.prompt(c, t("dungeonconfig.screen.back", "Back"), keyEsc(), back.x(), cy, cap, p, back.contains(mx, my), pressOffset("ESC"));
        Rect save = savePrompt(c);
        DungeonsDraw.prompt(c, saveLabel(), keyEnter(), save.x(), cy, cap, p, save.contains(mx, my), pressOffset("ENTER"));
        Rect reset = resetPrompt(c);
        if (!listMode() && reset.x() > back.x() + back.w() + u(24)) {
            DungeonsDraw.prompt(c, t("dungeonconfig.screen.reset", "Reset"), null, reset.x(), cy, cap, p, false, 0);
        }

        Rect mute = mutePrompt(c);
        if (mute != null) {
            boolean hover = mute.contains(mx, my);
            boolean muted = DungeonsMusic.muted();
            int sw = DungeonsDraw.speaker(c, mute.x(), cy, p, muted, hover ? 0xFFFFFFFF : DungeonsTheme.FOOTER_TEXT);
            DungeonsDraw.prompt(c, muteLabel(), "M", mute.x() + sw + u(6), cy, cap, p, hover, pressOffset("M"));
        }

        Rect leftOf = mute != null ? mute : back;
        int left = leftOf.x() + leftOf.w() + u(24), right = (!listMode() && reset.x() > left ? reset.x() : save.x()) - u(24);
        String status = null;
        int color = 0;
        float fade = 1;
        DungeonsFfmpegInstaller.State installing = DungeonsFfmpegInstaller.state();
        if (installing == DungeonsFfmpegInstaller.State.DOWNLOADING) {
            status = t("dungeonconfig.screen.ffmpeg.downloading", "Downloading FFmpeg... %s MB", String.valueOf(DungeonsFfmpegInstaller.receivedMb()));
            color = VALUE;
        } else if (installing == DungeonsFfmpegInstaller.State.INSTALLING) {
            status = t("dungeonconfig.screen.ffmpeg.installing", "Installing FFmpeg...");
            color = VALUE;
        } else if (installing == DungeonsFfmpegInstaller.State.DONE && installerTimer.running(4000)) {
            status = t("dungeonconfig.screen.ffmpeg.done", "FFmpeg installed");
            color = TOGGLE_ON;
        } else if (installing == DungeonsFfmpegInstaller.State.FAILED && installerTimer.running(6000)) {
            status = t("dungeonconfig.screen.ffmpeg.failed", "FFmpeg download failed, see the log");
            color = 0xFFFF6B5E;
        } else if (saveFailed && savedOnce && savedTimer.running(4000)) {
            status = t("dungeonconfig.screen.save_failed", "Couldn't save settings, see the log");
            color = 0xFFFF6B5E;
        } else if (unsaved()) {
            status = t("dungeonconfig.screen.unsaved", "Unsaved changes");
            color = ACCENT_LIGHT;
        } else if (savedOnce && savedTimer.running(2200)) {
            status = t("dungeonconfig.screen.saved", "Settings saved");
            color = TOGGLE_ON;
            float pr = savedTimer.progress(2200);
            fade = pr < 0.12F ? pr / 0.12F : pr > 0.75F ? (1 - pr) / 0.25F : 1;
        }
        if (status != null) {
            boolean marker = color == ACCENT_LIGHT;
            int dot = marker ? DungeonsDraw.modifiedWidth(p) + u(6) : 0;
            float[] ink = c.ink(status, Role.BODY, cap);
            float tw = dot + ink[1];
            if (right - left > tw + u(16)) {
                float sx = vw / 2.0F - tw / 2.0F;
                sx = Math.max(left, Math.min(sx, right - tw));
                float old = c.alpha;
                c.alpha = old * fade;
                if (marker) DungeonsDraw.modified(c, Math.round(sx), cy, p);
                c.textMid(status, Role.BODY, cap, sx + dot, cy, color);
                c.alpha = old;
            }
        }
    }

    private DungeonsCanvas measure() {
        return new DungeonsCanvas(null, this.font, scale);
    }

    private boolean onClick(double mx, double my, int button) {
        DungeonsCanvas c = measure();
        if (confirming) {
            ConfirmLayout l = confirmLayout(c);
            for (int i = 0; i < l.buttons().length; i++) {
                if (l.buttons()[i].contains(mx, my)) {
                    answerConfirm(i);
                    return true;
                }
            }
            if (!l.box().contains(mx, my)) answerConfirm(buttonCount() - 1);
            return true;
        }
        keyboardFocus = false;

        if (choiceOpen != null) {
            ConfigEntry<?> open = choiceOpen;
            int pick = choiceAt(c, mx, my);
            choiceOpen = null;
            if (pick >= 0 && button == 0) pickChoice(open, pick);
            return true;
        }

        if (registryOpen != null) {
            Rect popup = registryPopupRect(c);
            if (popup.contains(mx, my)) {
                if (button == 0) {
                    int itemH = registryItemH();
                    int clickedIdx = (int) ((my - popup.y() + registryScroll) / itemH);
                    if (clickedIdx >= 0 && clickedIdx < filteredRegistryIds.size()) {
                        pickRegistryItem(registryOpen, filteredRegistryIds.get(clickedIdx));
                    }
                }
                return true;
            }
            if (!DungeonsUi.inside(mx, my, boxX, valueBoxY(entryY(registryOpen)), boxW, valueBoxH())) {
                closeRegistry();
            }
        }

        if (editingEntry != null) {
            if (DungeonsUi.inside(mx, my, boxX, valueBoxY(entryY(editingEntry)), boxW, valueBoxH())) {
                editField.click(c, Role.BODY, cap, (float) (mx - boxX - u(8)));
                if (editingEntry.hasRegistry()) {
                    openRegistry(editingEntry);
                }
                return true;
            }
            stopEditing(true);
        }

        if (listMode() && DungeonsUi.inside(mx, my, listX, entrySearchY, rowW, searchH)) {
            clickSearch(c, allEntriesSearchField, searchField, mx);
            return true;
        }
        allEntriesSearchField.blur();

        if (DungeonsUi.inside(mx, my, listX, searchY, rowW, searchH)) {
            clickSearch(c, searchField, allEntriesSearchField, mx);
            return true;
        }
        searchField.blur();

        if (button == 0) {
            if (closeButton().contains(mx, my)) {
                DungeonsUi.click();
                onClose();
                return true;
            }
            Rect area = tabArea(c);
            List<Rect> rects = tabRects(c);
            int cy = tabH / 2, chipH = DungeonsDraw.chipHeight(c, cap);
            if (my < tabH && mx < area.x() && mx >= marginX - 4) {
                press("Q");
                cycleCategory(-1);
                return true;
            }
            if (my < tabH && mx >= area.x() + area.w() && mx < area.x() + area.w() + DungeonsDraw.chipWidth(c, "E", cap) + u(16)
                    && Math.abs(my - cy) <= chipH) {
                press("E");
                cycleCategory(1);
                return true;
            }
            if (area.contains(mx, my)) {
                for (int i = 0; i < rects.size(); i++) {
                    if (rects.get(i).contains(mx, my)) {
                        selectCategory(categories.get(i));
                        return true;
                    }
                }
            }
            Rect mute = mutePrompt(c);
            if (mute != null && mute.contains(mx, my)) {
                toggleMute();
                return true;
            }
            if (backPrompt(c).contains(mx, my)) {
                press("ESC");
                DungeonsUi.click();
                onClose();
                return true;
            }
            if (savePrompt(c).contains(mx, my)) {
                press("ENTER");
                DungeonsUi.click();
                confirmAction();
                return true;
            }
            int trackX = listX + listW - scrollW;
            if (maxScroll() > 0 && DungeonsUi.inside(mx, my, trackX - u(4), contentY, scrollW + u(8), contentH)) {
                draggingScrollbar = true;
                scrollToMouse(my);
                return true;
            }
        }

        ConfigEntry<?> entry = entryAt(mx, my);
        if (entry == null) return false;

        if (listMode()) {
            if (button != 0) return false;
            selectedEntry = entry;
            activate(entry);
            return true;
        }
        if (button == 1) {
            DungeonsUi.click();
            entry.resetToDefault();
            selectedEntry = entry;
            changed(entry);
            return true;
        }
        if (button != 0) return false;

        selectedEntry = entry;
        int y = entryY(entry);
        Control control = control(entry);
        if (control == Control.TOGGLE) {
            activate(entry);
            return true;
        }
        if (control == Control.TEXT) {
            if (DungeonsUi.inside(mx, my, boxX, valueBoxY(y), boxW, valueBoxH())) {
                activate(entry);
                if (entry.hasRegistry()) openRegistry(entry);
            }
            return true;
        }
        if (control == Control.CHOICES) {
            if (choiceBox(c, entry, y).contains(mx, my)) openChoices(entry);
            return true;
        }
        if (mx >= sliderX - u(6) && mx <= sliderX + sliderW + u(6)) {
            draggingSlider = entry;
            setSliderFromMouse(entry, mx);
        }
        return true;
    }

    // The two search boxes are exclusive: focusing one clears and blurs the other
    private void clickSearch(DungeonsCanvas c, DungeonsTextField field, DungeonsTextField other, double mx) {
        if (!field.focused) {
            if (!other.value().isEmpty()) other.setValue("");
            other.blur();
            field.focus(false);
        }
        field.click(c, Role.BODY, cap, (float) (mx - searchTextX()));
    }

    private void activate(ConfigEntry<?> entry) {
        DungeonsUi.click();
        DungeonConfig mod = modRows.get(entry);
        if (mod != null) {
            openMod(mod);
        } else if (listMode() && entry.getOwner() != null) {
            openModEntry(entry.getOwner(), entry);
        } else if (control(entry) == Control.TOGGLE) {
            @SuppressWarnings("unchecked") ConfigEntry<Boolean> toggle = (ConfigEntry<Boolean>) entry;
            toggle.setValue(!toggle.getLocalValue());
            changed(entry);
        } else if (control(entry) == Control.TEXT) {
            startEditing(entry);
        } else if (control(entry) == Control.CHOICES) {
            openChoices(entry);
        }
    }

    private void step(ConfigEntry<?> entry, int dir) {
        if (modRows.containsKey(entry)) return;
        Control control = control(entry);
        if (control == Control.TOGGLE) {
            @SuppressWarnings("unchecked") ConfigEntry<Boolean> toggle = (ConfigEntry<Boolean>) entry;
            if (toggle.getLocalValue() != (dir > 0)) {
                DungeonsUi.click();
                toggle.setValue(dir > 0);
                changed(entry);
            }
        } else if (control == Control.CHOICES) {
            int index = entry.getChoices().indexOf(entry.getLocalValue());
            int next = Mth.clamp(index < 0 ? 0 : index + dir, 0, entry.getChoices().size() - 1);
            if (next != index) pickChoice(entry, next);
        } else if (control == Control.SLIDER) {
            double v = ((Number) entry.getLocalValue()).doubleValue();
            entry.setClamped(snap(entry, v + dir * entry.getStep()));
            changed(entry);
        }
    }

    private boolean onDrag(double mx, double my) {
        if (draggingSlider != null) {
            setSliderFromMouse(draggingSlider, mx);
            return true;
        }
        if (draggingScrollbar) {
            scrollToMouse(my);
            return true;
        }
        return false;
    }

    private void onRelease(int button) {
        if (button != 0) return;
        if (draggingSlider != null) {
            DungeonsUi.click();
            changed(draggingSlider);
        }
        draggingSlider = null;
        draggingScrollbar = false;
    }

    private boolean onScroll(double mx, double my, double dy) {
        if (confirming) return true;
        choiceOpen = null;
        if (registryOpen != null) {
            Rect popup = registryPopupRect(measure());
            if (popup.contains(mx, my)) {
                int itemH = registryItemH();
                registryScroll -= (int) (dy * itemH * 2);
                int maxScroll = Math.max(0, filteredRegistryIds.size() * itemH - popup.h());
                registryScroll = Mth.clamp(registryScroll, 0, maxScroll);
                return true;
            }
        }
        if (DungeonsUi.inside(mx, my, listX, contentY, listW, contentH)) {
            stopEditing(true);
            scroll -= (int) (dy * (rowH + rowGap));
            clampScroll();
            return true;
        }
        if (my < tabH && dy != 0) {
            press(dy > 0 ? "Q" : "E");
            cycleCategory(dy > 0 ? -1 : 1);
            return true;
        }
        return false;
    }

    private boolean onChar(int codePoint) {
        if (confirming) return true;
        if (editingEntry != null) {
            boolean typed = editField.charTyped(codePoint);
            if (typed && editingEntry.hasRegistry()) {
                if (registryOpen == null) openRegistry(editingEntry);
                else updateRegistryFilter();
            }
            return typed;
        }
        if (allEntriesSearchField.focused) {
            searchEdited(allEntriesSearchField, searchField, f -> f.charTyped(codePoint));
            return true;
        }
        if (searchField.focused) {
            searchEdited(searchField, allEntriesSearchField, f -> f.charTyped(codePoint));
            return true;
        }
        return false;
    }

    // Applies an edit to one search box and, if its text changed, clears the other and refilters
    private boolean searchEdited(DungeonsTextField field, DungeonsTextField other, Predicate<DungeonsTextField> edit) {
        String before = field.value();
        boolean handled = edit.test(field);
        if (!before.equals(field.value())) {
            if (!other.value().isEmpty()) other.setValue("");
            searchChanged();
        }
        return handled;
    }

    @Nullable
    private Boolean searchKey(DungeonsTextField field, DungeonsTextField other, int key, boolean control, boolean confirm, boolean escape) {
        if (confirm || key == GLFW.GLFW_KEY_DOWN) {
            field.blur();
            moveSelection(0);
            return true;
        }
        if (escape) {
            if (!field.value().isEmpty()) {
                field.setValue("");
                searchChanged();
            } else {
                field.blur();
            }
            return true;
        }
        return searchEdited(field, other, f -> f.keyPressed(key, control)) ? Boolean.TRUE : null;
    }

    @Nullable
    private Boolean onKey(int key, boolean control, boolean shift) {
        boolean confirm = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        boolean escape = key == GLFW.GLFW_KEY_ESCAPE;
        if (confirming) {
            if (confirm) answerConfirm(0);
            else if ((key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) && buttonCount() > 2) answerConfirm(1);
            else if (escape) answerConfirm(buttonCount() - 1);
            return true;
        }
        if (choiceOpen != null) {
            int count = choiceOpen.getChoices().size();
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                choiceHover = Mth.clamp((choiceHover < 0 ? 0 : choiceHover) + (key == GLFW.GLFW_KEY_UP ? -1 : 1), 0, count - 1);
                return true;
            }
            ConfigEntry<?> open = choiceOpen;
            if (confirm || key == GLFW.GLFW_KEY_SPACE) {
                choiceOpen = null;
                pickChoice(open, choiceHover);
                return true;
            }
            if (escape) {
                choiceOpen = null;
                return true;
            }
            return true;
        }
        if (registryOpen != null) {
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                int count = filteredRegistryIds.size();
                if (count > 0) {
                    registryHover = Mth.clamp((registryHover < 0 ? 0 : registryHover) + (key == GLFW.GLFW_KEY_UP ? -1 : 1), 0, count - 1);
                    int itemH = registryItemH();
                    int iy = registryHover * itemH;
                    Rect popup = registryPopupRect(measure());
                    if (iy < registryScroll) registryScroll = iy;
                    if (iy + itemH > registryScroll + popup.h()) registryScroll = iy + itemH - popup.h();
                }
                return true;
            }
            if (confirm || key == GLFW.GLFW_KEY_TAB) {
                if (registryHover >= 0 && registryHover < filteredRegistryIds.size()) {
                    pickRegistryItem(registryOpen, filteredRegistryIds.get(registryHover));
                    return true;
                } else if (filteredRegistryIds.size() == 1) {
                    pickRegistryItem(registryOpen, filteredRegistryIds.get(0));
                    return true;
                } else if (confirm) {
                    closeRegistry();
                    stopEditing(true);
                    return true;
                }
            }
            if (escape) {
                closeRegistry();
                return true;
            }
        }
        if (editingEntry != null) {
            if (confirm) {
                stopEditing(true);
                return true;
            }
            if (escape) {
                stopEditing(false);
                return true;
            }
            String before = editField.value();
            if (editField.keyPressed(key, control)) {
                if (!before.equals(editField.value()) && editingEntry.hasRegistry()) {
                    updateRegistryFilter();
                }
                return true;
            }
            return null;
        }
        if (allEntriesSearchField.focused) {
            return searchKey(allEntriesSearchField, searchField, key, control, confirm, escape);
        }
        if (searchField.focused) {
            return searchKey(searchField, allEntriesSearchField, key, control, confirm, escape);
        }
        if (control && key == GLFW.GLFW_KEY_F) {
            if (listMode() && shift) {
                allEntriesSearchField.focus(true);
            } else {
                searchField.focus(true);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_M && DungeonsMusic.available()) {
            toggleMute();
            return true;
        }
        if (key == GLFW.GLFW_KEY_Q) {
            press("Q");
            cycleCategory(-1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_E) {
            press("E");
            cycleCategory(1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            moveSelection(key == GLFW.GLFW_KEY_UP ? -1 : 1);
            return true;
        }
        ConfigEntry<?> focus = keyboardFocus ? selectedEntry : null;
        if (focus != null && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
            step(focus, key == GLFW.GLFW_KEY_RIGHT ? 1 : -1);
            return true;
        }
        if (focus != null && key == GLFW.GLFW_KEY_SPACE) {
            activate(focus);
            return true;
        }
        if (focus != null && !listMode() && (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE)) {
            DungeonsUi.click();
            focus.resetToDefault();
            changed(focus);
            return true;
        }
        if (confirm) {
            press("ENTER");
            DungeonsUi.click();
            confirmAction();
            return true;
        }
        if (escape) press("ESC");
        return null;
    }

    private void moveSelection(int dir) {
        List<ConfigEntry<?>> entries = new ArrayList<>();
        for (Item item : items) if (item.entry() != null) entries.add(item.entry());
        if (entries.isEmpty()) return;
        int index = selectedEntry == null || !keyboardFocus ? -1 : entries.indexOf(selectedEntry);
        if (index < 0) {
            ConfigEntry<?> start = hoveredEntry != null ? hoveredEntry : null;
            index = start != null ? entries.indexOf(start) : (dir < 0 ? entries.size() : -1);
            if (start != null) dir = 0;
        }
        index = Mth.clamp(index + dir, 0, entries.size() - 1);
        selectedEntry = entries.get(index);
        keyboardFocus = true;
        DungeonsUi.click();
        int y = entryY(selectedEntry) - contentY + scroll;
        if (y < scroll) scroll = y;
        if (y + rowH > scroll + contentH) scroll = y + rowH - contentH;
        clampScroll();
    }

    private void searchChanged() {
        scroll = 0;
        rebuildItems();
    }

    private void changed(ConfigEntry<?> entry) {
        flashes.computeIfAbsent(entry, e -> new DungeonsAnim.Timer()).start();
        Appearance a = config == null ? null : config.appearance();
        if (a == null) return;
        if (entry == a.background() || entry == a.fps()) requestBackground();
        if (entry == a.music()) music = ScreenMedia.music(media());
        if (entry == a.background() || entry == a.music()) maybeOfferFfmpeg();
        if (entry == a.blur()) backgroundDueAt = DungeonsAnim.millis() + 450;
    }

    private void cycleCategory(int step) {
        if (categories.isEmpty()) return;
        int index = Math.max(0, categories.indexOf(selectedCategory));
        selectCategory(categories.get(Math.floorMod(index + step, categories.size())));
    }

    private void selectCategory(String category) {
        DungeonsUi.click();
        stopEditing(true);
        int from = categories.indexOf(selectedCategory), to = categories.indexOf(category);
        tabDirection = to >= from ? 1 : -1;
        tabTimer.start();
        selectedCategory = category;
        selectedEntry = null;
        keyboardFocus = false;
        if (!searchField.value().isEmpty()) searchField.setValue("");
        if (!allEntriesSearchField.value().isEmpty()) allEntriesSearchField.setValue("");
        searchField.blur();
        allEntriesSearchField.blur();
        scroll = 0;
        scrollAnim.snap(0);
        selectAlpha.snap(0);
        rebuildItems();
    }

    // Saves, then runs `then` (after a restart notice if any saved change needs one); nothing runs if saving fails
    private void save(Runnable then) {
        stopEditing(true);
        if (config == null) {
            then.run();
            return;
        }
        RestartType needed = restartNeededForChanges();
        saveFailed = !config.save();
        savedOnce = true;
        savedTimer.start();
        if (saveFailed) return;
        config.notifySaved();
        snapshot();
        if (needed != RestartType.NONE) showDialog(restartDialog(needed, then));
        else then.run();
    }

    // The strictest restart the unsaved changes need: world restarts only matter while a world is open
    private RestartType restartNeededForChanges() {
        RestartType needed = RestartType.NONE;
        for (ConfigEntry<?> entry : entries()) {
            if (Objects.equals(entry.getLocalValue(), savedValues.get(entry))) continue;
            RestartType type = entry.getRestartType();
            if (type == RestartType.GAME || (type == RestartType.WORLD && ConfigSync.inWorld())) needed = needed.with(type);
        }
        return needed;
    }

    private void confirmAction() {
        if (!listMode()) {
            save(() -> {});
            return;
        }
        ConfigEntry<?> target = focusEntry();
        if (target != null) {
            if (modRows.containsKey(target)) openMod(modRows.get(target));
            else if (target.getOwner() != null) openModEntry(target.getOwner(), target);
        }
    }

    private void snapshot() {
        savedValues.clear();
        for (ConfigEntry<?> entry : entries()) savedValues.put(entry, entry.getLocalValue());
    }

    private boolean unsaved() {
        if (listMode()) return false;
        for (ConfigEntry<?> entry : entries()) {
            if (!Objects.equals(entry.getLocalValue(), savedValues.get(entry))) return true;
        }
        return editingEntry != null && !editField.value().equals(editingEntry.toEditText());
    }

    private void startEditing(ConfigEntry<?> entry) {
        stopEditing(true);
        editingEntry = entry;
        editField.setValue(entry.toEditText());
        editField.focus(true);
        searchField.blur();
        allEntriesSearchField.blur();
        if (entry.hasRegistry()) {
            openRegistry(entry);
        }
    }

    private void stopEditing(boolean commit) {
        if (editingEntry == null) return;
        ConfigEntry<?> entry = editingEntry;
        editingEntry = null;
        editField.blur();
        closeRegistry();
        if (commit && entry.setFromEditText(editField.value())) changed(entry);
    }

    private void scrollToMouse(double mouseY) {
        int thumbH = scrollThumbHeight();
        float f = (float) ((mouseY - contentY - thumbH / 2.0) / Math.max(1, contentH - thumbH));
        scroll = Math.round(Mth.clamp(f, 0f, 1f) * maxScroll());
    }

    private int entryY(ConfigEntry<?> entry) {
        for (Item item : items) {
            if (item.entry() == entry) return contentY + item.y() - scroll;
        }
        return -1000;
    }

    @Nullable
    private ConfigEntry<?> entryAt(double mx, double my) {
        if (!DungeonsUi.inside(mx, my, listX, contentY, rowW, contentH)) return null;
        for (Item item : items) {
            if (item.entry() == null) continue;
            int y = contentY + item.y() - scroll;
            if (DungeonsUi.inside(mx, my, listX, y, rowW, item.h())) return item.entry();
        }
        return null;
    }

    private void setSliderFromMouse(ConfigEntry<?> entry, double mouseX) {
        if (!entry.hasRange()) return;
        float f = Mth.clamp((float) ((mouseX - sliderX) / sliderW), 0f, 1f);
        double min = ((Number) entry.getMinValue()).doubleValue(), max = ((Number) entry.getMaxValue()).doubleValue();
        entry.setClamped(snap(entry, min + f * (max - min)));
    }

    // Clamp stepped numeric values to prevent floating-point drift
    private static double snap(ConfigEntry<?> entry, double v) {
        double step = entry.getStep();
        double min = entry.hasRange() ? ((Number) entry.getMinValue()).doubleValue() : 0;
        double snapped = min + Math.round((v - min) / step) * step;
        return (entry.getType() == Integer.class || entry.getType() == Long.class)
                ? Math.round(snapped)
                : Math.round(snapped * 1e6) / 1e6;
    }

    private float sliderFraction(ConfigEntry<?> entry) {
        if (!entry.hasRange()) return 0f;
        double min = ((Number) entry.getMinValue()).doubleValue();
        double max = ((Number) entry.getMaxValue()).doubleValue();
        double v = ((Number) entry.getLocalValue()).doubleValue();
        return max <= min ? 0f : (float) Mth.clamp((v - min) / (max - min), 0, 1);
    }

    private Rect choiceBox(DungeonsCanvas c, ConfigEntry<?> entry, int rowY) {
        float widest = 0;
        for (Object choice : entry.getChoices()) {
            widest = Math.max(widest, c.width(formatValue(entry, choice).toUpperCase(Locale.ROOT), Role.VALUE, cap));
        }
        int w = Math.min(boxW, Math.max(u(90), Math.round(widest) + u(24) + DungeonsDraw.arrowDownWidth(p)));
        return new Rect(boxX, valueBoxY(rowY), w, valueBoxH());
    }

    private Rect choiceList(DungeonsCanvas c) {
        ConfigEntry<?> entry = choiceOpen;
        Rect box = choiceBox(c, entry, entryY(entry));
        int h = box.h() * entry.getChoices().size();
        int y = box.y() + box.h() + u(2);
        if (y + h > bodyBottom && box.y() - u(2) - h >= bodyTop) y = box.y() - u(2) - h;
        return new Rect(box.x(), y, box.w(), h);
    }

    private int choiceAt(DungeonsCanvas c, double mx, double my) {
        if (choiceOpen == null) return -1;
        Rect list = choiceList(c);
        if (!list.contains(mx, my)) return -1;
        int rowHeight = list.h() / choiceOpen.getChoices().size();
        return Math.min(choiceOpen.getChoices().size() - 1, (int) ((my - list.y()) / rowHeight));
    }

    private void openChoices(ConfigEntry<?> entry) {
        DungeonsUi.click();
        choiceOpen = entry;
        choiceHover = entry.getChoices().indexOf(entry.getLocalValue());
        choiceTimer.start();
    }

    @SuppressWarnings("unchecked")
    private void pickChoice(ConfigEntry<?> entry, int index) {
        List<?> choices = entry.getChoices();
        if (index < 0 || index >= choices.size()) return;
        DungeonsUi.click();
        ((ConfigEntry<Object>) entry).setValue(choices.get(index));
        changed(entry);
    }

    private void drawChoices(DungeonsCanvas c, int mx, int my) {
        ConfigEntry<?> entry = choiceOpen;
        if (entry == null) return;
        Rect list = choiceList(c);
        float in = DungeonsAnim.easeOut(choiceTimer.progress(140));
        float old = c.alpha;
        c.alpha = old * in;
        List<?> choices = entry.getChoices();
        int rowHeight = list.h() / choices.size();
        c.rect(list.x() - p, list.y() - p, list.w() + 2 * p, list.h() + 2 * p, DROPDOWN_BG);
        c.boxOutline(list.x() - p, list.y() - p, list.w() + 2 * p, list.h() + 2 * p, p, DROPDOWN_BORDER);
        int hovered = choiceAt(c, mx, my);
        if (hovered >= 0) choiceHover = hovered;
        for (int i = 0; i < choices.size(); i++) {
            int ry = list.y() + i * rowHeight;
            boolean current = choices.get(i).equals(entry.getLocalValue());
            if (current) c.rect(list.x(), ry, list.w(), rowHeight, DROPDOWN_SELECTED);
            if (i == choiceHover) c.rect(list.x(), ry, list.w(), rowHeight, DROPDOWN_HOVER);
            String text = formatValue(entry, choices.get(i)).toUpperCase(Locale.ROOT);
            c.textMid(text, Role.VALUE, cap, list.x() + u(8), ry + rowHeight / 2, current ? ACCENT_LIGHT : i == choiceHover ? VALUE : LABEL);
        }
        c.alpha = old;
    }

    private void openRegistry(ConfigEntry<?> entry) {
        if (!entry.hasRegistry()) return;
        registryOpen = entry;
        registryHover = -1;
        registryScroll = 0;
        registryTimer.start();
        updateRegistryFilter();
    }

    private void closeRegistry() {
        registryOpen = null;
        registryHover = -1;
        registryScroll = 0;
        filteredRegistryIds = List.of();
        lastRegistryQuery = "";
    }

    // {start, end} of the comma separated list item under the caret, without leading spaces
    private int[] tokenBounds() {
        String text = editField.value();
        int caret = Math.min(editField.caret(), text.length());
        int start = text.lastIndexOf(',', Math.max(0, caret - 1));
        int end = text.indexOf(',', caret);
        if (end == -1) end = text.length();
        int tokenStart = start == -1 ? 0 : start + 1;
        while (tokenStart < end && Character.isWhitespace(text.charAt(tokenStart))) tokenStart++;
        return new int[]{tokenStart, end};
    }

    private String currentEditingToken() {
        if (editingEntry == null) return "";
        if (!editingEntry.isList()) return editField.value().trim();
        int[] token = tokenBounds();
        return editField.value().substring(token[0], token[1]).trim();
    }

    private void updateRegistryFilter() {
        if (registryOpen == null) {
            filteredRegistryIds = List.of();
            return;
        }
        String query = currentEditingToken().toLowerCase(Locale.ROOT);
        lastRegistryQuery = query;
        List<String> all = registryOpen.getRegistryIds();
        if (query.isEmpty()) {
            filteredRegistryIds = all;
        } else {
            List<String> matches = new ArrayList<>();
            for (String id : all) {
                if (id.toLowerCase(Locale.ROOT).contains(query)) {
                    matches.add(id);
                }
            }
            filteredRegistryIds = matches;
        }
        registryScroll = 0;
        registryHover = -1;
    }

    private void pickRegistryItem(ConfigEntry<?> entry, String pickedId) {
        DungeonsUi.click();
        if (!entry.isList()) {
            editField.setValue(pickedId);
            closeRegistry();
            stopEditing(true);
            changed(entry);
        } else {
            String text = editField.value();
            int[] token = tokenBounds();
            String before = text.substring(0, token[0]);
            String after = text.substring(token[1]);

            List<String> currentItems = parseCommaSeparated(before + pickedId + after);
            int max = entry.getMaxEntries() != null ? entry.getMaxEntries() : 0;
            if (max > 0 && currentItems.size() > max) return;

            String nextVal;
            if (after.isEmpty() || after.matches("^\\s*,?\\s*$")) {
                if (max <= 0 || currentItems.size() < max) {
                    nextVal = before + pickedId + ", ";
                } else {
                    nextVal = before + pickedId;
                }
            } else {
                nextVal = before + pickedId + after;
            }
            editField.setValue(nextVal);
            if (max > 0 && currentItems.size() >= max) {
                closeRegistry();
            } else {
                updateRegistryFilter();
            }
            entry.setListFromCommaSeparated(editField.value());
            changed(entry);
        }
    }

    private static List<String> parseCommaSeparated(String text) {
        if (text == null || text.isBlank()) return List.of();
        String[] parts = text.split(",");
        List<String> list = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) list.add(trimmed);
        }
        return list;
    }

    private Rect registryPopupRect(DungeonsCanvas c) {
        if (registryOpen == null) return new Rect(0, 0, 0, 0);
        int bh = valueBoxH();
        int by = valueBoxY(entryY(registryOpen));
        int itemH = registryItemH();
        int visibleCount = Math.min(7, Math.max(1, filteredRegistryIds.size()));
        int h = visibleCount * itemH;
        int y = by + bh + u(2);
        if (y + h > bodyBottom && by - u(2) - h >= bodyTop) {
            y = by - u(2) - h;
        }
        return new Rect(boxX, y, boxW, h);
    }

    private void drawRegistryPopup(DungeonsCanvas c, int mx, int my) {
        if (registryOpen == null) return;
        Rect popup = registryPopupRect(c);
        float in = DungeonsAnim.easeOut(registryTimer.progress(140));
        float old = c.alpha;
        c.alpha = old * in;

        c.rect(popup.x() - p, popup.y() - p, popup.w() + 2 * p, popup.h() + 2 * p, DROPDOWN_BG);
        c.boxOutline(popup.x() - p, popup.y() - p, popup.w() + 2 * p, popup.h() + 2 * p, p, DROPDOWN_BORDER);

        int itemH = registryItemH();
        if (filteredRegistryIds.isEmpty()) {
            String empty = t("dungeonconfig.screen.registry.no_matches", "No matching registry entries");
            c.textMid(c.trim(empty, Role.BODY, cap, popup.w() - u(16)), Role.BODY, cap, popup.x() + u(8), popup.y() + popup.h() / 2, MUTED);
            c.alpha = old;
            return;
        }

        int totalH = filteredRegistryIds.size() * itemH;
        int maxScroll = Math.max(0, totalH - popup.h());
        registryScroll = Mth.clamp(registryScroll, 0, maxScroll);

        if (popup.contains(mx, my)) {
            int idx = (int) ((my - popup.y() + registryScroll) / itemH);
            if (idx >= 0 && idx < filteredRegistryIds.size()) registryHover = idx;
        }

        String currentToken = currentEditingToken().toLowerCase(Locale.ROOT);
        c.scissor(popup.x(), popup.y(), popup.x() + popup.w(), popup.y() + popup.h());
        int first = Math.max(0, registryScroll / itemH);
        int last = Math.min(filteredRegistryIds.size() - 1, (registryScroll + popup.h()) / itemH + 1);

        int barW = maxScroll > 0 ? 2 * p : 0;
        int textRoom = popup.w() - u(16) - barW;

        for (int i = first; i <= last; i++) {
            int iy = popup.y() + i * itemH - registryScroll;
            String id = filteredRegistryIds.get(i);
            boolean current = id.equalsIgnoreCase(currentToken);
            if (current) c.rect(popup.x(), iy, popup.w() - barW, itemH, DROPDOWN_SELECTED);
            if (i == registryHover) c.rect(popup.x(), iy, popup.w() - barW, itemH, DROPDOWN_HOVER);

            c.textMid(c.trim(id, Role.VALUE, cap, textRoom), Role.VALUE, cap, popup.x() + u(8), iy + itemH / 2,
                    current ? ACCENT_LIGHT : i == registryHover ? VALUE : LABEL);
        }

        if (maxScroll > 0) {
            int trackX = popup.x() + popup.w() - barW;
            int thumbH = Math.max(u(12), (int) ((float) popup.h() / totalH * popup.h()));
            int thumbY = popup.y() + (int) ((popup.h() - thumbH) * ((float) registryScroll / maxScroll));
            DungeonsDraw.scrollbar(c, trackX, popup.y(), popup.h(), thumbY, thumbH, p, false);
        }

        c.endScissor();
        c.alpha = old;
    }

    private String describe(ConfigEntry<?> entry, Object value) {
        if (value instanceof Boolean b) {
            return b ? t("dungeonconfig.screen.value.on", "On") : t("dungeonconfig.screen.value.off", "Off");
        }
        if (value instanceof String s) {
            return s.isEmpty() ? t("dungeonconfig.screen.text.empty", "Empty") : s;
        }
        return formatValue(entry, value);
    }

    private static String rangeLabel(ConfigEntry<?> entry) {
        return entry.hasChoices() ? t("dungeonconfig.screen.detail.options", "Options") : t("dungeonconfig.screen.detail.range", "Range");
    }

    @Nullable
    private String range(ConfigEntry<?> entry) {
        if (entry.hasChoices()) {
            List<String> names = new ArrayList<>();
            for (Object choice : entry.getChoices()) names.add(formatValue(entry, choice));
            return String.join(", ", names);
        }
        if (!entry.hasRange()) return null;
        return formatValue(entry, entry.getMinValue()) + " - " + formatValue(entry, entry.getMaxValue());
    }

    private static String formatValue(ConfigEntry<?> entry, Object v) {
        if (entry.isList() && v instanceof List<?> list) {
            List<String> items = new ArrayList<>();
            for (Object item : list) items.add(String.valueOf(item));
            return "[" + String.join(", ", items) + "]";
        }
        String number = v instanceof Double d ? decimal(d) : String.valueOf(v);
        if (entry.getUnit() != null) return number + entry.getUnit();
        if (entry.getName().endsWith("Percent")) {
            return number + "%";
        }
        if (entry.getName().endsWith("Seconds")) {
            return number + " s";
        }
        if (entry.getName().endsWith("Minutes")) {
            return number + " m";
        }
        if (entry.getName().endsWith("Fps")) {
            return number + " FPS";
        }
        return number;
    }

    private static String decimal(double d) {
        String text = BigDecimal.valueOf(Math.round(d * 1000) / 1000.0).stripTrailingZeros().toPlainString();
        return text.contains(".") ? text : text + ".0";
    }

    private static String t(String key, String fallback, Object... args) {
        return Component.translatableWithFallback(key, fallback, args).getString();
    }

    @Override
    public void removed() {
        if (!handoff) DungeonsBackground.release();
        DungeonsMusic.close();
        DungeonsFont.collectAll();
        super.removed();
    }

    @Override
    public void onClose() {
        stopEditing(false);
        if (unsaved()) {
            // Warn user if closing with unsaved edits
            if (!confirming) showDialog(unsavedDialog());
            return;
        }
        leave();
    }

    private String lang(String suffix) {
        String own = (config == null ? DungeonConfigMod.MOD_ID : config.modId()) + ".config." + suffix;
        if (I18n.exists(own)) return own;
        String shared = DungeonConfigMod.MOD_ID + ".config." + suffix;
        return I18n.exists(shared) ? shared : null;
    }

    private String text(String key) {
        return Language.getInstance().getOrDefault(key).replace("%s", folderArg());
    }

    private String folderArg() {
        return "config/" + (config == null ? DungeonConfigMod.MOD_ID : config.modId());
    }

    String optionName(ConfigEntry<?> entry) {
        DungeonConfig mod = modRows.get(entry);
        if (mod != null) return mod.displayName();
        String key = lang("option." + entry.getName());
        if (key != null) return text(key);
        if (entry.getOwner() != null) {
            String modKey = entry.getOwner().modId() + ".config.option." + entry.getName();
            if (I18n.exists(modKey)) return I18n.get(modKey);
        }
        return DungeonsUi.titleCase(displayName(entry));
    }

    String optionDescription(ConfigEntry<?> entry) {
        DungeonConfig mod = modRows.get(entry);
        if (mod != null) return mod.description();
        String key = lang("option." + entry.getName() + ".desc");
        if (key != null) return DungeonsUi.sentence(text(key));
        if (entry.getOwner() != null) {
            String modKey = entry.getOwner().modId() + ".config.option." + entry.getName() + ".desc";
            if (I18n.exists(modKey)) return DungeonsUi.sentence(I18n.get(modKey));
        }
        return DungeonsUi.sentence(entry.getComment());
    }

    String sectionName(String section) {
        String key = lang("section." + section.toLowerCase(Locale.ROOT).replace(' ', '_'));
        return (key != null ? text(key) : section).toUpperCase(Locale.ROOT);
    }

    String categoryName(String category) {
        String key = lang("category." + category.toLowerCase(Locale.ROOT).replace(' ', '_'));
        return key != null ? text(key) : category;
    }

    static String displayName(ConfigEntry<?> entry) {
        if (entry.getLabel() != null) return entry.getLabel();
        String name = entry.getName();
        if (name.endsWith("Enabled")) name = name.substring(0, name.length() - 7);
        if (name.endsWith("_enabled")) name = name.substring(0, name.length() - 8);
        if (name.endsWith("Percent")) name = name.substring(0, name.length() - 7);
        if (name.endsWith("Seconds")) name = name.substring(0, name.length() - 7);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch == '_') {
                result.append(' ');
                continue;
            }
            if (i > 0 && Character.isUpperCase(ch) && name.charAt(i - 1) != '_') result.append(' ');
            result.append(ch);
        }
        return result.toString();
    }

    @Override
    public void extractBackground(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft.level == null && !DungeonsBackground.ready()) this.extractPanorama(graphics, partialTick);
        DungeonsCanvas c = new DungeonsCanvas(graphics, this.font, scale);
        c.begin();
        draw(c, Math.round(mouseX / scale), Math.round(mouseY / scale));
        c.end();
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubleClick) {
        return onClick(event.x() / scale, event.y() / scale, event.button());
    }

    @Override
    public boolean mouseDragged(@NotNull MouseButtonEvent event, double dx, double dy) {
        return onDrag(event.x() / scale, event.y() / scale);
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent event) {
        onRelease(event.button());
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        return onScroll(mouseX / scale, mouseY / scale, deltaY);
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent event) {
        Boolean handled = onKey(event.key(), event.hasControlDown(), event.hasShiftDown());
        return handled != null ? handled : super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent event) {
        return onChar(event.codepoint()) || super.charTyped(event);
    }
}
