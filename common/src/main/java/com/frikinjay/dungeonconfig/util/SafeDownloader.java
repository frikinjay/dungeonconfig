package com.frikinjay.dungeonconfig.util;

import org.jetbrains.annotations.Nullable;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Downloads http(s) links with a size cap and a deadline, refusing local and private network
 * addresses on every redirect hop. Shared by Dungeon Config and mods built on it.
 * <p>
 * The address check is tied to the connection itself: plain http connects to the exact address
 * that was checked, and https checks the address of the socket it connected before any request is
 * sent, so a DNS answer that changes between the check and the connection can't slip through.
 */
public final class SafeDownloader {

    public record Result(byte[] body, String contentType, String path) {
        public boolean looksLikeText() {
            return contentType.startsWith("text/plain") || path.toLowerCase(Locale.ROOT).endsWith(".txt");
        }
    }

    public static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;
    private static final long DEADLINE_MILLIS = 30_000;
    private static final int HOP_TIMEOUT_MILLIS = 20_000;

    // Downloads block while reading, so they get their own threads instead of the common pool
    private static final ExecutorService DOWNLOADS = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "DungeonConfig download");
        t.setDaemon(true);
        return t;
    });

    private SafeDownloader() {}

    public static boolean isUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    public static CompletableFuture<Result> fetch(String url) {
        return fetch(url, MAX_IMAGE_BYTES, DEADLINE_MILLIS);
    }

    public static CompletableFuture<Result> fetch(String url, int maxBytes, long deadlineMillis) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return download(URI.create(url.trim()), maxBytes, deadlineMillis, null, null);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }, DOWNLOADS);
    }

    public static CompletableFuture<Result> fetchToFile(String url, Path target, long maxBytes, long deadlineMillis) {
        return fetchToFile(url, target, maxBytes, deadlineMillis, null);
    }

    public static CompletableFuture<Result> fetchToFile(String url, Path target, long maxBytes, long deadlineMillis,
                                                       @Nullable AtomicLong progress) {
        return CompletableFuture.supplyAsync(() -> {
            Path part = target.resolveSibling(target.getFileName() + ".part");
            try {
                Files.createDirectories(target.getParent());
                Result result = download(URI.create(url.trim()), maxBytes, deadlineMillis, part, progress);
                FileUtil.moveReplacing(part, target);
                return result;
            } catch (Exception e) {
                try {
                    Files.deleteIfExists(part);
                } catch (IOException ignored) {
                }
                throw new RuntimeException(e.getMessage(), e);
            }
        }, DOWNLOADS);
    }

    private static Result download(URI uri, long maxBytes, long deadlineMillis, @Nullable Path file,
                                   @Nullable AtomicLong progress) throws IOException {
        long deadline = System.currentTimeMillis() + deadlineMillis;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            int timeout = (int) Math.min(HOP_TIMEOUT_MILLIS, remaining(deadline));
            Connection connection = open(uri, allowedAddress(uri));
            HttpURLConnection conn = connection.http();
            try {
                conn.setInstanceFollowRedirects(false);
                conn.setUseCaches(false);
                conn.setConnectTimeout(timeout);
                conn.setReadTimeout(timeout);
                conn.setRequestProperty("User-Agent", "DungeonConfig");
                conn.setRequestProperty("Accept", maxBytes > MAX_IMAGE_BYTES ? "*/*" : "image/*, text/plain;q=0.9");
                // Never reuse a pooled connection, so every request goes through the checks above
                conn.setRequestProperty("Connection", "close");
                conn.connect();
                connection.verifyPeer();

                int status = conn.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = conn.getHeaderField("Location");
                    if (location == null || location.isBlank()) throw new IOException("Redirect without a target");
                    URI next = uri.resolve(location.trim());
                    if ("https".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(next.getScheme())) {
                        throw new IOException("Refusing to follow a redirect away from https");
                    }
                    uri = next;
                    continue;
                }
                if (status / 100 != 2) throw new IOException("HTTP " + status);
                long declared = conn.getContentLengthLong();
                if (declared > maxBytes) throw tooLarge(maxBytes);

                byte[] body = read(conn, maxBytes, deadline, file, progress);
                String type = conn.getContentType() == null ? "" : conn.getContentType().toLowerCase(Locale.ROOT);
                String path = uri.getPath() == null ? "" : uri.getPath();
                return new Result(body, type, path);
            } finally {
                conn.disconnect();
            }
        }
        throw new IOException("Too many redirects");
    }

    private static long remaining(long deadline) throws IOException {
        long left = deadline - System.currentTimeMillis();
        if (left <= 0) throw new IOException("Download timed out");
        return left;
    }

    private static byte[] read(HttpURLConnection conn, long maxBytes, long deadline, @Nullable Path file,
                               @Nullable AtomicLong progress) throws IOException {
        ByteArrayOutputStream memory = file == null ? new ByteArrayOutputStream() : null;
        try (InputStream in = conn.getInputStream();
             OutputStream out = file != null ? new BufferedOutputStream(Files.newOutputStream(file), 64 * 1024) : memory) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            for (int n; (n = in.read(buffer)) >= 0; ) {
                total += n;
                if (total > maxBytes) throw tooLarge(maxBytes);
                remaining(deadline);
                out.write(buffer, 0, n);
                if (progress != null) progress.addAndGet(n);
            }
        }
        return memory == null ? new byte[0] : memory.toByteArray();
    }

    private static IOException tooLarge(long limit) {
        return new IOException("Download is larger than " + (limit / 1024 / 1024) + " MB");
    }

    private record Connection(HttpURLConnection http, @Nullable CheckingSocketFactory sockets) {
        // For https, refuses the connection if the socket ended up somewhere it shouldn't, before the request is sent
        void verifyPeer() throws IOException {
            if (sockets == null) return;
            Socket socket = sockets.created;
            InetAddress peer = socket == null ? null : socket.getInetAddress();
            if (peer == null) throw new IOException("Couldn't check where the download connected to");
            if (isBlocked(peer)) throw new IOException("Local and private network addresses are not allowed");
        }
    }

    private static Connection open(URI uri, InetAddress address) throws IOException {
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            HttpsURLConnection conn = (HttpsURLConnection) uri.toURL().openConnection(Proxy.NO_PROXY);
            CheckingSocketFactory sockets = new CheckingSocketFactory(conn.getSSLSocketFactory());
            conn.setSSLSocketFactory(sockets);
            return new Connection(conn, sockets);
        }
        // For plain http, connect to the checked address and send the full URL, like a request through a proxy;
        // the Host header still names the site, so servers answer it as usual
        int port = uri.getPort() != -1 ? uri.getPort() : 80;
        Proxy pinned = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(address, port));
        return new Connection((HttpURLConnection) uri.toURL().openConnection(pinned), null);
    }

    // SSRF guard: every address the host resolves to must be public
    private static InetAddress allowedAddress(URI uri) throws IOException {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) throw new IOException("Only http(s) links are allowed");
        String host = uri.getHost();
        if (host == null || host.isEmpty()) throw new IOException("Link has no host");
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0) throw new IOException("Couldn't resolve " + host);
        for (InetAddress address : addresses) {
            if (isBlocked(address)) throw new IOException("Local and private network addresses are not allowed");
        }
        return addresses[0];
    }

    private static boolean isBlocked(InetAddress address) {
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || address.isAnyLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) return isBlockedV4(b, 0);
        if (address instanceof Inet6Address && b.length == 16) {
            if ((b[0] & 0xFE) == 0xFC) return true;
            // Mapped/compatible IPv4 prefixes
            boolean zeroPrefix = true;
            for (int i = 0; i < 10; i++) zeroPrefix &= b[i] == 0;
            if (zeroPrefix && ((b[10] == (byte) 0xFF && b[11] == (byte) 0xFF) || (b[10] == 0 && b[11] == 0))) {
                return isBlockedV4(b, 12);
            }
            if (b[0] == 0 && b[1] == 0x64 && b[2] == (byte) 0xFF && b[3] == (byte) 0x9B) return isBlockedV4(b, 12);
            if (b[0] == 0x20 && b[1] == 0x02) return isBlockedV4(b, 2);
        }
        return false;
    }

    private static boolean isBlockedV4(byte[] b, int at) {
        int a0 = b[at] & 0xFF, a1 = b[at + 1] & 0xFF, a2 = b[at + 2] & 0xFF;
        return a0 == 0 || a0 == 10 || a0 == 127 || a0 >= 224
                || (a0 == 100 && a1 >= 64 && a1 <= 127)
                || (a0 == 169 && a1 == 254)
                || (a0 == 172 && a1 >= 16 && a1 <= 31)
                || (a0 == 192 && a1 == 168)
                || (a0 == 192 && a1 == 0 && a2 == 0)
                || (a0 == 198 && (a1 == 18 || a1 == 19));
    }

    // Remembers the socket the https connection makes, so its real address can be checked
    private static final class CheckingSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory inner;
        @Nullable volatile Socket created;

        CheckingSocketFactory(SSLSocketFactory inner) {
            this.inner = inner;
        }

        private Socket track(Socket socket) {
            created = socket;
            return socket;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return inner.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return inner.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket() throws IOException {
            return track(inner.createSocket());
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            return track(inner.createSocket(socket, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return track(inner.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return track(inner.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return track(inner.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return track(inner.createSocket(address, port, localAddress, localPort));
        }
    }
}
