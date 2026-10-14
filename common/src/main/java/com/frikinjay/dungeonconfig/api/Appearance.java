package com.frikinjay.dungeonconfig.api;

/**
 * The settings screen's own background and music options, which every config gets in its
 * {@link DungeonConfig.Builder#appearanceCategory(String) appearance tab} unless built with
 * {@link DungeonConfig.Builder#noAppearance()}. Players change them in game; the builder's
 * {@code default*} methods set their defaults.
 * <p>
 * Players can put their own files in {@code config/<modid>/bg} and {@code config/<modid>/bgmusic}.
 * Files a mod ships in {@code assets/<modid>/dungeonconfig/bg} and {@code bgmusic} are also used for
 * the random pick.
 * <pre>{@code
 * DungeonConfig config = DungeonConfig.builder("mymod")
 *         .defaultBackground("castle.png")
 *         .defaultBackgroundBlur(15)
 *         .defaultMusic("none")
 *         .build();
 *
 * Appearance looks = config.appearance();
 * if (looks != null) {
 *     String background = looks.background().get(); // "castle.png" until the player changes it
 *     looks.volume().set(0);                        // mute this mod's menu music
 *     config.save();
 * }
 * }</pre>
 *
 * @param background the background: a file in {@code bg}, a link, a link to a {@code .txt} list of links,
 *                   {@code "none"}, or empty for a random file
 * @param blur       how blurred the background is, 0 to 100
 * @param dim        how much the background is darkened, 0 to 100
 * @param fps        frame rate of video and GIF backgrounds, 30 or 60
 * @param music      the music: a file in {@code bgmusic}, a link, {@code "none"}, or empty for random tracks
 * @param volume     music volume, 0 to 100
 */
public record Appearance(ConfigEntry<String> background, ConfigEntry<Integer> blur, ConfigEntry<Integer> dim,
                         ConfigEntry<Integer> fps, ConfigEntry<String> music, ConfigEntry<Integer> volume) {

    static Appearance create(DungeonConfig config, String category, String defaultBackground, String defaultMusic,
                             int defaultBlur, int defaultDim, int defaultFps, int defaultVolume) {
        String dir = "config/" + config.modId();
        return new Appearance(
                config.add(new ConfigEntry<>("menuBackground", "Picture, animated GIF or video behind this settings menu: a file in " + dir
                        + "/bg, a link, a link to a .txt list of links, or empty for a random one from that folder (none turns it off). Videos need FFmpeg", category, defaultBackground, String.class)),
                config.add(new ConfigEntry<>("menuBackgroundBlurPercent", "How strongly the menu background is blurred, 0 for sharp", category, defaultBlur, Integer.class, 0, 100)),
                config.add(new ConfigEntry<>("menuBackgroundDimPercent", "How much the menu background picture is darkened", category, defaultDim, Integer.class, 0, 100)),
                config.add(new ConfigEntry<>("menuBackgroundFps", "Frame rate for video and animated GIF menu backgrounds", category, defaultFps, Integer.class, 30, 60).withChoices(30, 60)),
                config.add(new ConfigEntry<>("menuMusic", "Music while this settings menu is open: a file in " + dir
                        + "/bgmusic, a link, or empty for random tracks from that folder (none turns it off). Formats other than ogg and wav need FFmpeg", category, defaultMusic, String.class)),
                config.add(new ConfigEntry<>("menuMusicVolumePercent", "Volume of the settings menu music", category, defaultVolume, Integer.class, 0, 100)));
    }
}
