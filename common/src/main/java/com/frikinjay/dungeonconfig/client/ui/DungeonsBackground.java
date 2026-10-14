package com.frikinjay.dungeonconfig.client.ui;

import com.frikinjay.dungeonconfig.util.ImageDecoder;
import com.frikinjay.dungeonconfig.util.SafeDownloader;
import com.frikinjay.dungeonconfig.util.Threads;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static com.frikinjay.dungeonconfig.client.ui.DungeonsTheme.*;

public final class DungeonsBackground {

    private static final Logger LOGGER = LoggerFactory.getLogger("DungeonsUi");
    private static final List<String> EXTENSIONS = List.of(".png", ".jpg", ".jpeg", ".gif", ".bmp", ".tga", ".psd", ".tif", ".tiff", ".ico", ".webp", ".apng");
    private static final int MAX_WIDTH = 2560;
    private static final int GIF_MAX_WIDTH = 1280;
    private static final int BLUR_WIDTH = 640;
    private static final long FADE_MS = 380;
    private static final ExecutorService LOADER = Threads.daemonExecutor("DungeonsUi background loader", Thread.NORM_PRIORITY - 1);
    private static final long MAX_PICTURE_BYTES = 32L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 512L * 1024 * 1024;
    private static final Random RANDOM = new Random();

    @Nullable private static String loadedKey;
    @Nullable private static DungeonsCanvas.Texture texture;
    @Nullable private static DungeonsVideo video;
    @Nullable private static String randomPick;
    private static int generation;
    private static long shownAt;

    private DungeonsBackground() {}

    public static void request(String namespace, List<Path> folders, String spec, int blur, int fps) {
        String key = spec.trim() + "|" + blur + "|" + fps + "|" + folders;
        if (key.equals(loadedKey)) return;
        loadedKey = key;
        int gen = ++generation;
        String value = spec.trim();

        if (!folders.isEmpty()) {
            try {
                Files.createDirectories(folders.get(0));
            } catch (Exception e) {
                LOGGER.warn("Couldn't create {}", folders.get(0), e);
            }
        }

        if (DungeonsMediaFiles.isOff(value)) {
            clear();
            return;
        }

        var window = Minecraft.getInstance().getWindow();
        int screenW = window.getWidth(), screenH = window.getHeight();

        boolean url = SafeDownloader.isUrl(value);
        if (url && isYoutube(value)) {
            LOGGER.warn("Menu background \"{}\": YouTube links can't be played, use a direct link to a video file instead", value);
            clear();
            return;
        }
        if (url && DungeonsVideo.isVideo(value)) {
            // Live streams (.m3u8) cannot be cached locally
            if (!DungeonsFfmpeg.hasExtension(value, List.of(".m3u8")) && DungeonsFfmpeg.available()) {
                DungeonsMediaCache.fetch(value, MAX_VIDEO_BYTES);
            }
            playVideo(namespace, gen, value, true, screenW, screenH, blur, fps);
            return;
        }

        CompletableFuture<ResolvedMedia> resolved;
        CompletableFuture<Path> download = url ? DungeonsMediaCache.fetch(value, MAX_PICTURE_BYTES) : null;
        if (url) {
            resolved = download.thenApplyAsync(path -> {
                try {
                    byte[] data = Files.readAllBytes(path);
                    if (DungeonsMediaFiles.isTextUrlList(data)) {
                        String imageUrl = DungeonsMediaFiles.pickRandomUrl(data);
                        if (imageUrl != null) {
                            Path imagePath = DungeonsMediaCache.fetch(imageUrl, MAX_PICTURE_BYTES).join();
                            return new ResolvedMedia(Files.readAllBytes(imagePath), imageUrl, imagePath);
                        }
                    }
                    return new ResolvedMedia(data, value, path);
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }, LOADER);
        } else {
            Path file = value.isEmpty() ? randomFile(folders, DungeonsFfmpeg.available()) : DungeonsMediaFiles.named(folders, value, "Menu background");
            if (file == null) {
                clear();
                return;
            }
            if (DungeonsVideo.isVideo(file.getFileName().toString())) {
                playVideo(namespace, gen, file.toAbsolutePath().toString(), false, screenW, screenH, blur, fps);
                return;
            }
            Path chosen = file;
            resolved = CompletableFuture.supplyAsync(() -> {
                try {
                    return new ResolvedMedia(Files.readAllBytes(chosen), null, chosen);
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }, LOADER);
        }

        int radius = blurRadius(blur);
        int stillWidth = radius > 0 ? BLUR_WIDTH : Math.min(MAX_WIDTH, Math.max(640, screenW));
        int gifWidth = radius > 0 ? BLUR_WIDTH : Math.min(GIF_MAX_WIDTH, Math.max(320, screenW));
        // Asynchronously decode and blur so menu opening never blocks render thread
        resolved.thenApplyAsync(media -> {
            try {
                byte[] data = media.data();
                if (isApng(data) && DungeonsFfmpeg.available()) return (Object) media;
                DungeonsGif.Animation animation = DungeonsGif.decode(data, gifWidth, radius);
                if (animation != null) return (Object) animation;
                ImageDecoder.Image image = media.url() != null ? ImageDecoder.decodeUntrusted(data) : ImageDecoder.decode(data);
                return (Object) prepare(image, radius, stillWidth);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }, LOADER).whenComplete((prepared, error) -> Minecraft.getInstance().execute(() -> {
            if (gen != generation) return;
            if (prepared instanceof ResolvedMedia media) {
                String source = media.url() != null ? media.url() : media.path().toAbsolutePath().toString();
                playVideo(namespace, gen, source, media.url() != null, screenW, screenH, blur, fps);
                return;
            }
            String lower = value.toLowerCase(Locale.ROOT);
            boolean nonVideoUrl = isPicture(lower) || lower.endsWith(".txt");
            if (!nonVideoUrl && (error != null || prepared == null) && url && DungeonsFfmpeg.available()) {
                Path local = DungeonsMediaCache.cached(value);
                if (local != null) playVideo(namespace, gen, local.toAbsolutePath().toString(), false, screenW, screenH, blur, fps);
                else playVideo(namespace, gen, value, true, screenW, screenH, blur, fps);
                return;
            }
            if (error != null || prepared == null) {
                LOGGER.warn("Couldn't load menu background \"{}\": {}", value, error == null ? "unreadable" : error.getMessage());
                clear();
                return;
            }
            clear();
            try {
                if (prepared instanceof DungeonsGif.Animation a) {
                    video = DungeonsVideo.frames(namespace, "dynamic/menu_gif_" + gen, a.width(), a.height(), a.frames(), a.delays(), fps);
                } else {
                    ImageDecoder.Image img = (ImageDecoder.Image) prepared;
                    texture = DungeonsCanvas.upload(namespace, "dynamic/menu_background_" + gen, img.argb(), img.width(), img.height(), true);
                    shownAt = DungeonsAnim.millis();
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Couldn't show menu background \"{}\": {}", value, e.toString());
                clear();
            }
        }));
    }

    public static boolean needsFfmpeg(String value, List<Path> folders) {
        String v = value == null ? "" : value.trim();
        if (DungeonsMediaFiles.isOff(v)) return false;
        if (!v.isEmpty()) return DungeonsVideo.isVideo(v);
        for (Path folder : folders) {
            List<Path> files = usable(folder, true);
            if (files.isEmpty()) continue;
            return files.stream().anyMatch(f -> DungeonsVideo.isVideo(f.getFileName().toString()));
        }
        return false;
    }

    public static boolean hasFiles(List<Path> folders) {
        boolean videos = DungeonsFfmpeg.available();
        for (Path folder : folders) {
            if (!usable(folder, videos).isEmpty()) return true;
        }
        return false;
    }

    private static int blurRadius(int amount) {
        return amount <= 0 ? 0 : Math.max(1, Math.round(amount / 100.0F * BLUR_WIDTH / 40.0F));
    }

    private static void playVideo(String namespace, int gen, String source, boolean remote, int screenW, int screenH, int blur, int fps) {
        clear();
        try {
            video = DungeonsVideo.ffmpeg(namespace, "dynamic/menu_video_" + gen, source, remote, screenW, screenH, blur, fps);
        } catch (RuntimeException e) {
            LOGGER.warn("Couldn't start the video background \"{}\": {}", source, e.toString());
            video = null;
        }
    }

    private static boolean isYoutube(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.matches("^https?://([a-z0-9-]+\\.)*(youtube\\.com|youtu\\.be|youtube-nocookie\\.com)(/.*)?$");
    }

    private record ResolvedMedia(byte[] data, @Nullable String url, @Nullable Path path) {}

    // Check if PNG is animated (acTL chunk prior to first IDAT)
    static boolean isApng(byte[] data) {
        if (data.length < 8) return false;
        if ((data[0] & 0xFF) != 0x89 || data[1] != 'P' || data[2] != 'N' || data[3] != 'G'
                || data[4] != 0x0D || data[5] != 0x0A || data[6] != 0x1A || data[7] != 0x0A) return false;
        int limit = Math.min(data.length, 65536);
        int i = 8;
        while (i + 8 <= limit) {
            int length = ((data[i] & 0xFF) << 24) | ((data[i + 1] & 0xFF) << 16)
                    | ((data[i + 2] & 0xFF) << 8) | (data[i + 3] & 0xFF);
            String type = new String(data, i + 4, 4, StandardCharsets.US_ASCII);
            if (type.equals("acTL")) return true;
            if (type.equals("IDAT")) return false;
            if (length < 0 || (long) i + 12 + length > limit) break;
            i += 12 + length;
        }
        return false;
    }

    public static float shown() {
        if (shownAt == 0) return 0;
        return DungeonsAnim.easeInOut((DungeonsAnim.millis() - shownAt) / (float) FADE_MS);
    }

    public static boolean ready() {
        return shown() >= 1;
    }

    public static void release() {
        clear();
        loadedKey = null;
        generation++;
    }

    private static void clear() {
        shownAt = 0;
        if (video != null) {
            video.stop();
            video = null;
        }
        if (texture != null) {
            DungeonsCanvas.release(texture);
            texture = null;
        }
    }

    @Nullable
    private static Path randomFile(List<Path> folders, boolean videos) {
        List<Path> files = List.of();
        for (Path folder : folders) {
            files = usable(folder, videos);
            if (!files.isEmpty()) break;
        }
        if (files.isEmpty()) return null;
        if (randomPick != null) {
            for (Path file : files) {
                if (file.getFileName().toString().equals(randomPick)) return file;
            }
        }
        Path pick = files.get(RANDOM.nextInt(files.size()));
        randomPick = pick.getFileName().toString();
        return pick;
    }

    private static List<Path> usable(Path folder, boolean videos) {
        return DungeonsMediaFiles.list(folder, name -> isPicture(name) || (videos && DungeonsVideo.isVideo(name)));
    }

    private static boolean isPicture(String lowerCaseName) {
        return EXTENSIONS.stream().anyMatch(lowerCaseName::endsWith);
    }

    private static ImageDecoder.Image prepare(ImageDecoder.Image image, int radius, int targetWidth) {
        int width = Math.min(image.width(), targetWidth);
        ImageDecoder.Image scaled = width < image.width() ? downscale(image, width) : image;
        return radius > 0 ? new ImageDecoder.Image(blurPixels(scaled.argb(), scaled.width(), scaled.height(), radius), scaled.width(), scaled.height()) : scaled;
    }

    private static ImageDecoder.Image downscale(ImageDecoder.Image src, int width) {
        int height = Math.max(1, Math.round(src.height() * (width / (float) src.width())));
        return new ImageDecoder.Image(scale(src.argb(), src.width(), src.height(), width, height), width, height);
    }

    static int[] scale(int[] src, int srcW, int srcH, int width, int height) {
        int[] out = new int[width * height];
        float sx = srcW / (float) width, sy = srcH / (float) height;
        for (int y = 0; y < height; y++) {
            int y0 = (int) (y * sy), y1 = Math.max(y0 + 1, (int) ((y + 1) * sy));
            for (int x = 0; x < width; x++) {
                int x0 = (int) (x * sx), x1 = Math.max(x0 + 1, (int) ((x + 1) * sx));
                long a = 0, r = 0, g = 0, b = 0;
                int n = 0;
                for (int yy = y0; yy < y1 && yy < srcH; yy++) {
                    for (int xx = x0; xx < x1 && xx < srcW; xx++) {
                        int p = src[yy * srcW + xx];
                        a += p >>> 24;
                        r += (p >> 16) & 0xFF;
                        g += (p >> 8) & 0xFF;
                        b += p & 0xFF;
                        n++;
                    }
                }
                n = Math.max(1, n);
                out[y * width + x] = (int) (a / n) << 24 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
            }
        }
        return out;
    }

    // Three box blur passes approximate Gaussian blur
    static int[] blurPixels(int[] src, int w, int h, int radius) {
        int[] a = src.clone(), b = new int[a.length];
        for (int pass = 0; pass < 3; pass++) {
            boxPass(a, b, w, h, radius, true);
            boxPass(b, a, w, h, radius, false);
        }
        return a;
    }

    private static void boxPass(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h;
        for (int l = 0; l < lines; l++) {
            long sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = in[index(l, Math.max(0, Math.min(len - 1, i)), w, horizontal)];
                sa += p >>> 24;
                sr += (p >> 16) & 0xFF;
                sg += (p >> 8) & 0xFF;
                sb += p & 0xFF;
            }
            int n = r * 2 + 1;
            for (int i = 0; i < len; i++) {
                out[index(l, i, w, horizontal)] = (int) (sa / n) << 24 | (int) (sr / n) << 16 | (int) (sg / n) << 8 | (int) (sb / n);
                int add = in[index(l, Math.min(len - 1, i + r + 1), w, horizontal)];
                int sub = in[index(l, Math.max(0, i - r), w, horizontal)];
                sa += (add >>> 24) - (sub >>> 24);
                sr += ((add >> 16) & 0xFF) - ((sub >> 16) & 0xFF);
                sg += ((add >> 8) & 0xFF) - ((sub >> 8) & 0xFF);
                sb += (add & 0xFF) - (sub & 0xFF);
            }
        }
    }

    private static int index(int line, int i, int w, boolean horizontal) {
        return horizontal ? line * w + i : i * w + line;
    }

    public static boolean draw(DungeonsCanvas c, int w, int h, float dim, float seconds) {
        DungeonsCanvas.Texture tex;
        boolean still;
        if (video != null) {
            tex = video.frame();
            if (tex != null && shownAt == 0) shownAt = DungeonsAnim.millis();
            still = false;
        } else {
            tex = texture;
            still = true;
        }
        if (tex == null) return false;
        float alpha = shown();
        if (alpha <= 0) return false;
        float old = c.alpha;
        c.alpha = old * alpha;
        if (still) {
            float zoom = 1.06F;
            float scale = Math.max(w / (float) tex.width, h / (float) tex.height) * zoom;
            int uw = Math.max(1, Math.round(w / scale));
            int vh = Math.max(1, Math.round(h / scale));
            float slackX = tex.width - uw, slackY = tex.height - vh;
            float u = slackX * (0.5F + 0.5F * (float) Math.sin(seconds * 0.045F));
            float v = slackY * (0.5F + 0.5F * (float) Math.sin(seconds * 0.031F + 1.3F));
            c.image(tex, 0, 0, w, h, u, v, uw, vh);
        } else {
            float scale = Math.max(w / (float) tex.width, h / (float) tex.height);
            int uw = Math.max(1, Math.round(w / scale)), vh = Math.max(1, Math.round(h / scale));
            c.image(tex, 0, 0, w, h, (tex.width - uw) / 2.0F, (tex.height - vh) / 2.0F, uw, vh);
        }
        c.gradientV(0, 0, w, h, withAlpha(BG_TINT, Math.min(1.0F, dim * 0.9F)), withAlpha(BG_BOTTOM, Math.min(1.0F, dim * 1.25F)));
        c.alpha = old;
        return true;
    }
}
