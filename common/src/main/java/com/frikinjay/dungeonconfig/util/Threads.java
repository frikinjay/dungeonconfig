package com.frikinjay.dungeonconfig.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thread and error helpers shared by Dungeon Config and mods built on it.
 */
public final class Threads {

    private Threads() {}

    /**
     * Creates a single threaded executor whose thread is a daemon, so it never keeps the game running.
     */
    public static ExecutorService daemonExecutor(String name) {
        return daemonExecutor(name, Thread.NORM_PRIORITY);
    }

    /**
     * Creates a single threaded daemon executor running at the given thread priority.
     */
    @SuppressWarnings("resource")
    public static ExecutorService daemonExecutor(String name, int priority) {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            t.setPriority(priority);
            return t;
        });
    }

    /**
     * Returns the message of the innermost cause, which is usually the one worth logging
     * for errors wrapped by {@link java.util.concurrent.CompletableFuture}.
     */
    public static String rootMessage(Throwable error) {
        Throwable t = error;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }
}
