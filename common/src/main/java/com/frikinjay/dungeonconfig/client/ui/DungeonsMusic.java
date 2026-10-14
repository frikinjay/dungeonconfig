package com.frikinjay.dungeonconfig.client.ui;

import com.frikinjay.dungeonconfig.util.SafeDownloader;
import com.frikinjay.dungeonconfig.util.Threads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class DungeonsMusic {

    private static final Logger LOGGER = LoggerFactory.getLogger("DungeonsUi");
    public static final List<String> EXTENSIONS = List.of(".ogg", ".oga", ".wav", ".mp3", ".flac", ".m4a", ".aac", ".opus", ".wma", ".aiff", ".aif", ".mka", ".webm");
    private static final List<String> NATIVE = List.of(".ogg", ".oga", ".wav", ".aiff", ".aif");
    private static final long FADE_IN_MS = 1500;
    private static final long FADE_OUT_MS = 700;
    private static final long MAX_DOWNLOAD = 128L * 1024 * 1024;
    private static final int RATE = 48000;
    private static final ExecutorService LOADER = Threads.daemonExecutor("DungeonsUi music loader");
    private static final Random RANDOM = new Random();

    private static final Map<String, AudioStream> PENDING = new ConcurrentHashMap<>();
    static final String STREAM_FOLDER = "dungeonsui_stream/";

    private static List<Path> folders = List.of();
    @Nullable private static Path stateFile;
    @Nullable private static String spec;
    private static float volume = 0.5F;
    private static boolean muted, stateLoaded, open;
    @Nullable private static Track track;
    private static int generation;
    private static boolean loading;
    @Nullable private static String lastPick;
    private static long retryAt;
    private static int nextId;

    private DungeonsMusic() {}

    private static final class Track {
        final String pendingId;
        final String label;
        final TrackStream stream;
        final Object instance;
        final long startedAt = DungeonsAnim.millis();
        long fadeOutAt;
        float applied = -1;

        Track(String pendingId, String label, TrackStream stream, Object instance) {
            this.pendingId = pendingId;
            this.label = label;
            this.stream = stream;
            this.instance = instance;
        }
    }

    public static void open(List<Path> musicFolders, Path state, String value, int volumePercent) {
        stateFile = state;
        if (!musicFolders.isEmpty()) {
            try {
                Files.createDirectories(musicFolders.get(0));
            } catch (Exception ignored) {
            }
        }
        if (!stateLoaded) loadState();
        open = true;
        configure(musicFolders, value, volumePercent);
        if (track != null && !muted) track.fadeOutAt = 0;
    }

    public static void update(List<Path> musicFolders, String value, int volumePercent) {
        if (!open) return;
        configure(musicFolders, value, volumePercent);
        Track t = track;
        if (t != null) {
            if (t.fadeOutAt == 0 && finished(t)) {
                stopTrack();
                startNext();
            } else {
                apply(t);
            }
        } else if (!muted && !loading && DungeonsAnim.millis() >= retryAt) {
            startNext();
        }
    }

    public static void close() {
        open = false;
        generation++;
        loading = false;
        Track t = track;
        if (t == null) return;
        if (DungeonsAudio.paused()) {
            stopTrack();
        } else if (t.fadeOutAt == 0) {
            t.fadeOutAt = DungeonsAnim.millis();
        }
    }

    public static boolean muted() {
        if (!stateLoaded && stateFile != null) loadState();
        return muted;
    }

    public static void toggleMute() {
        muted = !muted;
        saveState();
        Track t = track;
        if (muted) {
            generation++;
            loading = false;
            if (t != null && t.fadeOutAt == 0) t.fadeOutAt = DungeonsAnim.millis();
        } else if (t != null) {
            t.fadeOutAt = 0;
        }
    }

    public static void retry() {
        retryAt = 0;
    }

    public static boolean needsFfmpeg(String value, List<Path> musicFolders) {
        String v = value == null ? "" : value.trim();
        if (DungeonsMediaFiles.isOff(v)) return false;
        if (!v.isEmpty()) return !DungeonsFfmpeg.hasExtension(v, NATIVE);
        for (Path folder : musicFolders) {
            List<Path> files = listMusic(folder, true);
            if (files.isEmpty()) continue;
            return files.stream().anyMatch(f -> !DungeonsFfmpeg.hasExtension(f.getFileName().toString(), NATIVE));
        }
        return false;
    }

    public static boolean hasFiles(List<Path> musicFolders) {
        boolean ffmpeg = DungeonsFfmpeg.available();
        for (Path folder : musicFolders) {
            if (!listMusic(folder, ffmpeg).isEmpty()) return true;
        }
        return false;
    }

    private static boolean availableCache;
    private static long availableAt;
    @Nullable private static String availableSpec;

    public static boolean available() {
        if (spec == null || DungeonsMediaFiles.isOff(spec)) return false;
        if (!spec.isEmpty()) return true;
        long now = DungeonsAnim.millis();
        if (!spec.equals(availableSpec) || now - availableAt > 2000) {
            availableSpec = spec;
            availableAt = now;
            availableCache = !playableFiles().isEmpty();
        }
        return availableCache;
    }

    public static boolean active() {
        if (loading) return true;
        Track t = track;
        if (t == null) return false;
        return open || DungeonsAudio.playing(t.instance);
    }

    @Nullable
    public static AudioStream takeStream(String namespace, String path) {
        if (!namespace.equals(DungeonsFonts.namespace) || !path.startsWith("sounds/" + STREAM_FOLDER)) return null;
        String id = path.substring(("sounds/" + STREAM_FOLDER).length());
        if (id.endsWith(".ogg")) id = id.substring(0, id.length() - 4);
        return PENDING.remove(id);
    }

    static void tick(Object instance) {
        Track t = track;
        if (t == null || t.instance != instance || open) return;
        if (!DungeonsAudio.playing(instance)) {
            stopTrack();
            return;
        }
        apply(t);
    }

    private static void configure(List<Path> musicFolders, String value, int volumePercent) {
        String trimmed = value == null ? "" : value.trim();
        volume = Math.max(0, Math.min(100, volumePercent)) / 100.0F;
        if (!trimmed.equals(spec) || !musicFolders.equals(folders)) {
            spec = trimmed;
            folders = List.copyOf(musicFolders);
            availableSpec = null;
            generation++;
            loading = false;
            retryAt = 0;
            lastPick = null;
            stopTrack();
        }
    }

    private static void apply(Track t) {
        long now = DungeonsAnim.millis();
        float fade = DungeonsAnim.easeInOut((now - t.startedAt) / (float) FADE_IN_MS);
        if (t.fadeOutAt != 0) {
            float out = 1 - (now - t.fadeOutAt) / (float) FADE_OUT_MS;
            if (out <= 0) {
                stopTrack();
                return;
            }
            fade *= out;
        }
        float v = (muted && t.fadeOutAt == 0 ? 0 : volume) * fade;
        if (Math.abs(v - t.applied) > 0.002F) {
            t.applied = v;
            DungeonsAudio.volume(t.instance, v);
        }
    }

    private static boolean finished(Track t) {
        TrackStream s = t.stream;
        if (!s.eof) return !DungeonsAudio.playing(t.instance) && DungeonsAnim.millis() - t.startedAt > 3000;
        double played = (DungeonsAnim.millis() - t.startedAt) / 1000.0;
        return played >= s.seconds() + 0.5 || !DungeonsAudio.playing(t.instance);
    }

    private static void stopTrack() {
        Track t = track;
        track = null;
        if (t == null) return;
        // The sound engine normally takes the stream at once; drop it if it never did
        PENDING.remove(t.pendingId);
        DungeonsAudio.stop(t.instance);
        t.stream.closeQuietly();
    }

    private static void startNext() {
        if (spec == null || DungeonsMediaFiles.isOff(spec)) return;
        Source source = pick();
        if (source == null) {
            retryAt = DungeonsAnim.millis() + 5000;
            return;
        }
        int gen = ++generation;
        loading = true;
        String label = source.label();
        CompletableFuture.supplyAsync(() -> {
            try {
                return openStream(source);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
            }
        }, LOADER).whenComplete((stream, error) -> Minecraft.getInstance().execute(() -> {
            if (gen != generation || !open || muted) {
                if (stream != null) stream.closeQuietly();
                if (gen == generation) loading = false;
                return;
            }
            loading = false;
            if (error != null || stream == null) {
                LOGGER.warn("Couldn't play menu music \"{}\": {}", label, error == null ? "nothing to play" : Threads.rootMessage(error));
                retryAt = DungeonsAnim.millis() + 8000;
                return;
            }
            String id = Integer.toString(nextId++);
            PENDING.put(id, stream);
            Object instance;
            try {
                DungeonsAudio.stopVanillaMusic();
                instance = DungeonsAudio.play(DungeonsFonts.namespace, STREAM_FOLDER + id, 0);
            } catch (RuntimeException e) {
                LOGGER.warn("Couldn't start menu music \"{}\": {}", label, e.toString());
                instance = null;
            }
            if (instance == null) {
                PENDING.remove(id);
                stream.closeQuietly();
                retryAt = DungeonsAnim.millis() + 8000;
                return;
            }
            track = new Track(id, label, stream, instance);
        }));
    }

    private record Source(@Nullable Path file, @Nullable String url) {
        String label() {
            return file != null ? file.getFileName().toString() : url;
        }
    }

    @Nullable
    private static Source pick() {
        if (SafeDownloader.isUrl(spec)) return new Source(null, spec);
        if (!spec.isEmpty()) {
            Path file = DungeonsMediaFiles.named(folders, spec, "Menu music");
            return file == null ? null : new Source(file, null);
        }
        List<Path> files = playableFiles();
        if (files.isEmpty()) return null;
        if (files.size() > 1 && lastPick != null) files.removeIf(f -> f.getFileName().toString().equals(lastPick));
        Path chosen = files.get(RANDOM.nextInt(files.size()));
        lastPick = chosen.getFileName().toString();
        return new Source(chosen, null);
    }

    private static List<Path> playableFiles() {
        boolean ffmpeg = DungeonsFfmpeg.available();
        for (Path folder : folders) {
            List<Path> files = listMusic(folder, ffmpeg);
            if (!files.isEmpty()) return files;
        }
        return new ArrayList<>();
    }

    private static List<Path> listMusic(Path folder, boolean ffmpeg) {
        return DungeonsMediaFiles.list(folder, name -> DungeonsFfmpeg.hasExtension(name, EXTENSIONS)
                && (ffmpeg || DungeonsFfmpeg.hasExtension(name, NATIVE)));
    }

    private static TrackStream openStream(Source source) throws Exception {
        Path ffmpeg = DungeonsFfmpeg.find();
        if (source.file() != null) return openFile(source.file(), ffmpeg);
        String url = source.url();
        // Live streams (.m3u8) cannot be cached locally
        if (DungeonsFfmpeg.hasExtension(url, List.of(".m3u8"))) {
            if (ffmpeg == null) throw new IOException("live streams need FFmpeg");
            return new TrackStream(FfmpegStream.start(ffmpeg, url, true));
        }
        Path local = DungeonsMediaCache.fetch(url, MAX_DOWNLOAD).get();
        byte[] data = Files.readAllBytes(local);
        if (DungeonsMediaFiles.isTextUrlList(data)) {
            String picked = DungeonsMediaFiles.pickRandomUrl(data);
            if (picked != null) {
                Path pickedLocal = DungeonsMediaCache.fetch(picked, MAX_DOWNLOAD).get();
                return openFile(pickedLocal, ffmpeg);
            }
        }
        return openFile(local, ffmpeg);
    }

    private static TrackStream openFile(Path file, @Nullable Path ffmpeg) throws Exception {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean known = DungeonsFfmpeg.hasExtension(name, EXTENSIONS);
        if (DungeonsFfmpeg.hasExtension(name, List.of(".ogg", ".oga")) || (!known && ffmpeg == null)) {
            try {
                return new TrackStream(new JOrbisAudioStream(new BufferedInputStream(Files.newInputStream(file))));
            } catch (IOException notVorbis) {
                if (ffmpeg == null && known) throw notVorbis;
            }
        }
        if (ffmpeg == null && (DungeonsFfmpeg.hasExtension(name, NATIVE) || !known)) {
            return new TrackStream(pcm(AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(file)))));
        }
        if (ffmpeg == null) throw new IOException("this format needs FFmpeg, install it or put the ffmpeg program in " + DungeonsFfmpeg.installFolder());
        return new TrackStream(FfmpegStream.start(ffmpeg, file.toAbsolutePath().toString(), false));
    }

    private static AudioStream pcm(AudioInputStream in) throws IOException {
        AudioFormat src = in.getFormat();
        int channels = src.getChannels();
        if (channels < 1 || channels > 2) {
            in.close();
            throw new IOException(channels + " channel audio needs FFmpeg to be mixed down to stereo");
        }
        AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16, channels, channels * 2, src.getSampleRate(), false);
        AudioInputStream converted = src.matches(target) ? in : AudioSystem.getAudioInputStream(target, in);
        return new PcmStream(converted, target);
    }

    private static void loadState() {
        stateLoaded = true;
        if (stateFile != null) DungeonsUiState.configure(stateFile);
        muted = DungeonsUiState.get("musicMuted", false);
    }

    private static void saveState() {
        if (stateFile == null) return;
        DungeonsUiState.configure(stateFile);
        DungeonsUiState.set("musicMuted", muted);
    }

    private static final class TrackStream implements AudioStream {
        private final AudioStream inner;
        private final double bytesPerSecond;
        private volatile long bytes;
        volatile boolean eof;
        private volatile boolean closed;

        TrackStream(AudioStream inner) {
            this.inner = inner;
            AudioFormat f = inner.getFormat();
            this.bytesPerSecond = Math.max(1, f.getSampleRate() * f.getChannels() * Math.max(1, f.getSampleSizeInBits() / 8));
        }

        double seconds() {
            return bytes / bytesPerSecond;
        }

        @Override
        public AudioFormat getFormat() {
            return inner.getFormat();
        }

        @Override
        public ByteBuffer read(int size) throws IOException {
            if (closed) {
                eof = true;
                return BufferUtils.createByteBuffer(0);
            }
            ByteBuffer b = inner.read(size);
            if (b == null || !b.hasRemaining()) {
                eof = true;
                return b == null ? BufferUtils.createByteBuffer(0) : b;
            }
            bytes += b.remaining();
            return b;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            inner.close();
        }

        void closeQuietly() {
            try {
                close();
            } catch (IOException ignored) {
            }
        }
    }

    private static class PcmStream implements AudioStream {
        private final InputStream in;
        private final AudioFormat format;
        private final byte[] scratch = new byte[64 * 1024];

        PcmStream(InputStream in, AudioFormat format) {
            this.in = in;
            this.format = format;
        }

        @Override
        public AudioFormat getFormat() {
            return format;
        }

        @Override
        public ByteBuffer read(int size) throws IOException {
            int frame = Math.max(1, format.getFrameSize());
            size = Math.max(frame, size - size % frame);
            ByteBuffer out = BufferUtils.createByteBuffer(size);
            while (out.hasRemaining()) {
                int n = in.read(scratch, 0, Math.min(scratch.length, out.remaining()));
                if (n < 0) break;
                out.put(scratch, 0, n);
            }
            int usable = out.position() - out.position() % frame;
            out.position(usable);
            out.flip();
            return out;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    // FFmpeg pipe decoding to 48 kHz stereo PCM
    private static final class FfmpegStream implements AudioStream {
        private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 2, true, false);
        private static final byte[] END = new byte[0];
        private static final long WAIT_MS = 2000;
        private final Process process;
        private final BlockingQueue<byte[]> chunks = new ArrayBlockingQueue<>(48);
        @Nullable private byte[] current;
        private int offset;
        private boolean ended;

        private FfmpegStream(Process process) {
            this.process = process;
        }

        static FfmpegStream start(Path ffmpeg, String source, boolean remote) throws Exception {
            List<String> args = new ArrayList<>(DungeonsFfmpeg.input(source, remote, false));
            args.addAll(List.of("-vn", "-sn", "-dn", "-ac", "2", "-ar", Integer.toString(RATE), "-acodec", "pcm_s16le", "-f", "s16le", "pipe:1"));
            Process p = DungeonsFfmpeg.start(ffmpeg, args);
            String[] lastError = new String[1];
            DungeonsFfmpeg.drainErrors(p, lastError);
            FfmpegStream stream = new FfmpegStream(p);
            Thread reader = new Thread(stream::pump, "DungeonsUi music decoder");
            reader.setDaemon(true);
            reader.start();
            byte[] first = stream.chunks.poll(20, TimeUnit.SECONDS);
            if (first == null || first == END) {
                p.destroyForcibly();
                throw new IOException(lastError[0] != null ? lastError[0] : "FFmpeg produced no audio");
            }
            stream.current = first;
            return stream;
        }

        private void pump() {
            try (InputStream in = process.getInputStream()) {
                while (true) {
                    byte[] buf = new byte[32 * 1024];
                    int filled = 0;
                    while (filled < buf.length) {
                        int n = in.read(buf, filled, buf.length - filled);
                        if (n < 0) break;
                        filled += n;
                    }
                    if (filled > 0) chunks.put(filled == buf.length ? buf : java.util.Arrays.copyOf(buf, filled));
                    if (filled < buf.length) break;
                }
            } catch (IOException | InterruptedException ignored) {
            } finally {
                chunks.offer(END);
            }
        }

        @Override
        public AudioFormat getFormat() {
            return FORMAT;
        }

        @Override
        public ByteBuffer read(int size) throws IOException {
            size -= size % 4;
            ByteBuffer out = BufferUtils.createByteBuffer(Math.max(4, size));
            try {
                while (out.hasRemaining() && !ended) {
                    if (current == null || offset >= current.length) {
                        byte[] next = out.position() == 0 ? chunks.poll(WAIT_MS, TimeUnit.MILLISECONDS) : chunks.poll();
                        if (next == null) break;
                        if (next == END) {
                            ended = true;
                            break;
                        }
                        current = next;
                        offset = 0;
                    }
                    int n = Math.min(out.remaining(), current.length - offset);
                    out.put(current, offset, n);
                    offset += n;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            out.position(out.position() - out.position() % 4);
            out.flip();
            return out;
        }

        @Override
        public void close() {
            ended = true;
            process.destroyForcibly();
            chunks.clear();
        }
    }
}
