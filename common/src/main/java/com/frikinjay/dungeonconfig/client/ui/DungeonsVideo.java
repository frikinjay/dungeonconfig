package com.frikinjay.dungeonconfig.client.ui;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class DungeonsVideo {

    private static final Logger LOGGER = LoggerFactory.getLogger("DungeonsUi");
    public static final List<String> EXTENSIONS = List.of(".mp4", ".webm", ".mkv", ".mov", ".m4v", ".avi", ".wmv", ".flv", ".mpg", ".mpeg", ".ts", ".m3u8");
    private static final int MAX_FAILS = 3;

    private static final int SLOTS = 5;
    private static final long PREROLL_NANOS = 150_000_000L;
    private static final int FREE = 0, WRITING = 1, READY = 2, SHOWN = 3;

    public final int width, height;
    private final DungeonsCanvas.Texture[] textures = new DungeonsCanvas.Texture[SLOTS];
    private final long[] addresses = new long[SLOTS];
    private boolean direct;
    private final AtomicInteger[] states = new AtomicInteger[SLOTS];
    private final java.util.concurrent.atomic.AtomicLongArray due = new java.util.concurrent.atomic.AtomicLongArray(SLOTS);
    private int front = -1;
    private volatile boolean stopped;
    private final Object pixelsLock = new Object();
    private boolean released;
    @Nullable private volatile Process process;
    private Thread thread;

    private DungeonsVideo(int width, int height) {
        this.width = width;
        this.height = height;
        for (int i = 0; i < SLOTS; i++) states[i] = new AtomicInteger(FREE);
    }

    private static DungeonsVideo create(String namespace, String path, int w, int h, Runnable body, String name) {
        DungeonsVideo video = new DungeonsVideo(w, h);
        boolean direct = true;
        for (int i = 0; i < SLOTS; i++) {
            video.textures[i] = DungeonsCanvas.createStream(namespace, path + "_" + i, w, h, true);
            video.addresses[i] = DungeonsCanvas.streamAddress(video.textures[i]);
            direct &= video.addresses[i] != 0;
        }
        video.direct = direct;
        video.thread = new Thread(() -> {
            try {
                body.run();
            } catch (RuntimeException e) {
                LOGGER.warn("Background animation stopped: {}", e.toString());
            }
        }, name);
        video.thread.setDaemon(true);
        return video;
    }

    @Nullable
    public static DungeonsVideo ffmpeg(String namespace, String path, String source, boolean remote,
                                       int screenW, int screenH, int blur, int fps) {
        Path ffmpeg = DungeonsFfmpeg.find();
        if (ffmpeg == null) {
            LOGGER.warn("Can't play the video background \"{}\": FFmpeg wasn't found. Install it, or put the ffmpeg program in {}",
                    source, DungeonsFfmpeg.installFolder());
            return null;
        }
        int w = blur >= 15 ? 640 : Math.min(1920, Math.max(320, screenW));
        int h = Math.max(2, Math.round(w * (screenH / (float) Math.max(1, screenW))));
        w &= ~1;
        h &= ~1;
        int fw = w, fh = h;
        DungeonsVideo[] self = new DungeonsVideo[1];
        DungeonsVideo video = create(namespace, path, w, h, () -> self[0].runFfmpeg(ffmpeg, source, remote, fw, fh, blur, fps), "DungeonsUi video");
        self[0] = video;
        video.thread.start();
        return video;
    }

    public static DungeonsVideo frames(String namespace, String path, int w, int h, int[][] frames, int[] delays, int fps) {
        DungeonsVideo[] self = new DungeonsVideo[1];
        DungeonsVideo video = create(namespace, path, w, h, () -> self[0].runFrames(frames, delays, fps), "DungeonsUi animation");
        self[0] = video;
        video.thread.start();
        return video;
    }

    @Nullable
    public DungeonsCanvas.Texture frame() {
        long now = System.nanoTime();
        int best = -1;
        long bestDue = Long.MIN_VALUE;
        for (int i = 0; i < SLOTS; i++) {
            if (states[i].get() == READY && due.get(i) <= now && due.get(i) > bestDue) {
                best = i;
                bestDue = due.get(i);
            }
        }
        if (best >= 0 && states[best].compareAndSet(READY, SHOWN)) {
            DungeonsCanvas.uploadStream(textures[best]);
            if (front >= 0 && front != best) states[front].set(FREE);
            front = best;
            // Drop frame if decoding falls behind playback clock
            for (int i = 0; i < SLOTS; i++) {
                if (i != best && due.get(i) < bestDue) states[i].compareAndSet(READY, FREE);
            }
        }
        return front < 0 ? null : textures[front];
    }

    public void stop() {
        stopped = true;
        Process p = process;
        if (p != null) p.destroyForcibly();
        if (thread != null) thread.interrupt();
        synchronized (pixelsLock) {
            released = true;
        }
        for (DungeonsCanvas.Texture t : textures) {
            if (t != null) DungeonsFont.retire(t);
        }
    }

    private boolean publish(IntBuffer pixels, long showAt) throws InterruptedException {
        int slot = claimSlot();
        if (slot < 0) return false;
        synchronized (pixelsLock) {
            if (released) return false;
            pixels.rewind();
            DungeonsCanvas.writeStream(textures[slot], pixels);
        }
        due.set(slot, showAt);
        states[slot].set(READY);
        return true;
    }

    private boolean publishDirect(ByteBuffer rgba, long showAt) throws InterruptedException {
        int slot = claimSlot();
        if (slot < 0) return false;
        long bytes = (long) width * height * 4;
        synchronized (pixelsLock) {
            if (released) return false;
            if (rgba.capacity() < bytes || DungeonsCanvas.streamPointer(textures[slot]) != addresses[slot]) {
                LOGGER.warn("Background video texture changed underneath it, stopping playback");
                stopped = true;
                return false;
            }
            org.lwjgl.system.MemoryUtil.memCopy(org.lwjgl.system.MemoryUtil.memAddress(rgba, 0), addresses[slot], bytes);
        }
        due.set(slot, showAt);
        states[slot].set(READY);
        return true;
    }

    private int claimSlot() throws InterruptedException {
        while (!stopped) {
            for (int i = 0; i < SLOTS; i++) {
                if (states[i].compareAndSet(FREE, WRITING)) return i;
            }
            Thread.sleep(2);
        }
        return -1;
    }

    private void runFrames(int[][] frames, int[] delays, int fps) {
        IntBuffer[] buffers = new IntBuffer[frames.length];
        for (int i = 0; i < frames.length; i++) buffers[i] = IntBuffer.wrap(frames[i]);
        long[] ends = new long[frames.length];
        long total = 0;
        for (int i = 0; i < frames.length; i++) {
            total += delays[i];
            ends[i] = total;
        }
        long tickMs = Math.max(1, 1000 / Math.max(1, fps));
        long start = System.nanoTime();
        int shown = -1;
        try {
            while (!stopped) {
                long t = ((System.nanoTime() - start) / 1_000_000L) % Math.max(1, total);
                int index = 0;
                while (index < frames.length - 1 && t >= ends[index]) index++;
                if (index != shown) {
                    if (!publish(buffers[index], System.nanoTime())) return;
                    shown = index;
                    if (frames.length == 1) return;
                }
                long untilNext = ends[index] - t;
                Thread.sleep(Math.max(tickMs, untilNext));
            }
        } catch (InterruptedException ignored) {
        }
    }

    private void runFfmpeg(Path ffmpeg, String source, boolean remote, int width, int height, int blur, int fps) {
        long frameNanos = 1_000_000_000L / Math.max(1, fps);
        int fails = 0;
        byte[] bytes = direct ? new byte[0] : new byte[width * height * 4];
        IntBuffer pixels = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        ByteBuffer frame = direct ? ByteBuffer.allocateDirect(width * height * 4) : null;
        String[] lastError = new String[1];
        while (!stopped) {
            long started = System.nanoTime();
            int frames = 0;
            String error = null;
            try {
                String input = source;
                boolean fromLink = remote;
                if (remote) {
                    Path local = DungeonsMediaCache.cached(source);
                    if (local != null) {
                        input = local.toAbsolutePath().toString();
                        fromLink = false;
                    }
                }
                // Allocate decoder threads generously to prevent video playback stalls
                List<String> args = new ArrayList<>(List.of("-hwaccel", "auto"));
                args.addAll(DungeonsFfmpeg.input(input, fromLink, true));
                args.addAll(List.of("-an", "-sn", "-dn"));
                String filter = "fps=" + fps + ",scale=" + width + ":" + height + ":force_original_aspect_ratio=increase:flags=bilinear,crop="
                        + width + ":" + height + (blur > 0 ? ",gblur=sigma=" + String.format(java.util.Locale.ROOT, "%.1f", blur / 100.0F * 20) : "");
                args.addAll(List.of("-vf", filter, "-f", "rawvideo", "-pix_fmt", direct ? "rgba" : DungeonsCanvas.STREAM_PIXEL_FORMAT, "pipe:1"));
                Process p = DungeonsFfmpeg.start(ffmpeg, args);
                process = p;
                Thread drain = DungeonsFfmpeg.drainErrors(p, lastError);
                try (DataInputStream in = new DataInputStream(p.getInputStream());
                     java.nio.channels.ReadableByteChannel channel = java.nio.channels.Channels.newChannel(in)) {
                    long start = 0;
                    for (long k = 0; !stopped; k++) {
                        if (frame != null) {
                            frame.clear();
                            while (frame.hasRemaining()) {
                                if (channel.read(frame) < 0) throw new EOFException();
                            }
                        } else {
                            in.readFully(bytes);
                        }
                        frames++;
                        long now = System.nanoTime();
                        if (k == 0) start = now + PREROLL_NANOS;
                        long showAt = start + k * frameNanos;
                        if (now - showAt > frameNanos) {
                            start = now - k * frameNanos;
                            showAt = now;
                        }
                        if (!(frame != null ? publishDirect(frame, showAt) : publish(pixels, showAt))) break;
                    }
                } catch (EOFException e) {
                } finally {
                    p.destroyForcibly();
                    drain.join(200);
                    error = lastError[0];
                }
            } catch (InterruptedException e) {
                return;
            } catch (IOException | RuntimeException e) {
                error = e.toString();
            }
            if (stopped) return;
            boolean quickFail = frames < fps && System.nanoTime() - started < 5_000_000_000L;
            fails = quickFail ? fails + 1 : 0;
            if (fails >= MAX_FAILS) {
                LOGGER.warn("Couldn't play the video background \"{}\"{}", source, error == null ? "" : ": " + error);
                return;
            }
            try {
                Thread.sleep(quickFail ? 1000L * fails : 50);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    public boolean hasFrame() {
        if (front >= 0) return true;
        long now = System.nanoTime();
        for (int i = 0; i < SLOTS; i++) {
            if (states[i].get() == READY && due.get(i) <= now) return true;
        }
        return false;
    }

    public static boolean isVideo(String name) {
        return DungeonsFfmpeg.hasExtension(name, EXTENSIONS);
    }
}
