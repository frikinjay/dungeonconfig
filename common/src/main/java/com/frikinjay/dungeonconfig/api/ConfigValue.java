package com.frikinjay.dungeonconfig.api;

import java.util.function.Supplier;

/**
 * The read and write view of one option, implemented by {@link ConfigEntry}.
 * <p>
 * It is a {@link Supplier}, so an option can be handed to code that wants a {@code Supplier<T>}.
 * <pre>{@code
 * ConfigValue<Integer> radius = MyModConfig.AURA_RADIUS;
 *
 * int current = radius.get();          // the server's value while synced, else the player's
 * radius.set(32);                      // change it; MyModConfig.CONFIG.save() writes it to the file
 * if (!radius.isDefault()) radius.resetToDefault();
 * Supplier<Integer> lazy = radius;     // works anywhere a Supplier is expected
 * }</pre>
 *
 * @param <T> the value type
 */
public interface ConfigValue<T> extends Supplier<T> {

    /**
     * Returns the value to use right now. When the player is on a server that syncs this option,
     * that is the server's value; otherwise the player's own.
     * <pre>{@code
     * if (MyModConfig.PARTICLES.get()) spawnParticles();
     * }</pre>
     *
     * @return the value in effect
     */
    T get();

    /**
     * Changes the player's own value, if it passes the option's checks. Call
     * {@link DungeonConfig#save()} to write it to the file.
     * <pre>{@code
     * MyModConfig.AURA_RADIUS.set(32);
     * MyModConfig.CONFIG.save();
     * }</pre>
     *
     * @param value the new value
     */
    void set(T value);

    /**
     * Returns the value the option starts with.
     * <pre>{@code
     * int original = MyModConfig.AURA_RADIUS.getDefault();
     * }</pre>
     *
     * @return the default
     */
    T getDefault();

    /**
     * Returns the option's key in the config file.
     * <pre>{@code
     * LOGGER.info("{} is {}", option.getPath(), option.get());
     * }</pre>
     *
     * @return the key
     */
    String getPath();

    /**
     * Puts the player's own value back to the default.
     * <pre>{@code
     * MyModConfig.AURA_RADIUS.resetToDefault();
     * }</pre>
     */
    void resetToDefault();

    /**
     * Whether the player's own value equals the default.
     * <pre>{@code
     * if (!MyModConfig.AURA_RADIUS.isDefault()) LOGGER.info("Custom aura radius in use");
     * }</pre>
     *
     * @return {@code true} if unchanged
     */
    boolean isDefault();
}
