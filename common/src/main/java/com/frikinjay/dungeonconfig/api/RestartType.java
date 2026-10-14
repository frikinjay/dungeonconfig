package com.frikinjay.dungeonconfig.api;

/**
 * When a change to an option takes effect, matching NeoForge's {@code ModConfigSpec.RestartType}.
 * <p>
 * For {@link #WORLD} and {@link #GAME} options, {@link ConfigEntry#get()} keeps returning the value it
 * first returned until the world is left or the game is restarted, even after the player saves a new
 * value. The settings screen marks such options and says when a saved change is still waiting.
 * <pre>{@code
 * // Read once per world, e.g. because it decides how chunks are generated
 * public static final ConfigEntry<Integer> ISLAND_SIZE =
 *         CONFIG.addInt("islandSize", "World", 64, 16, 256, "Size of the starting island")
 *                 .withRestart(RestartType.WORLD);
 * }</pre>
 */
public enum RestartType {
    /**
     * Changes apply as soon as they are saved.
     */
    NONE,
    /**
     * Changes apply the next time a world is loaded or a server is joined.
     */
    WORLD,
    /**
     * Changes apply the next time the game starts.
     */
    GAME;

    /**
     * Combines two restart requirements into the stricter one.
     * <pre>{@code
     * RestartType needed = RestartType.NONE.with(RestartType.WORLD); // WORLD
     * }</pre>
     *
     * @param other the other requirement
     * @return the stricter of the two
     */
    public RestartType with(RestartType other) {
        return other == NONE ? this : (other == GAME || this == GAME) ? GAME : WORLD;
    }
}
