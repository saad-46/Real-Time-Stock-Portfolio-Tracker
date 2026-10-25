package com.portfolio.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Shared network limits. Every outgoing request must be bounded, so a stalled server can never leave a worker (or the program)
 * waiting forever: HTTP clients get a connect timeout here, and each request sets its own overall timeout.
 */
public final class Net {
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    /** Uploading a recording can legitimately take longer than an ordinary request. */
    public static final Duration UPLOAD_TIMEOUT = Duration.ofSeconds(120);
    static final int IMAGE_CONNECT_MS = 8_000;
    static final int IMAGE_READ_MS = 12_000;
    static final int IMAGE_MAX_BYTES = 8 * 1024 * 1024;

    private Net() {
    }

    /** An HTTP client with a finite connect timeout. */
    public static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    /**
     * Downloads an image over http(s) with connect and read timeouts and a size cap.
     *
     * @return the decoded image, or null if the data is not an image
     * @throws IOException on a timeout, an HTTP error, a non-http URL or an over-sized response
     */
    public static java.awt.image.BufferedImage readImage(String url) throws IOException {
        return readImage(url, IMAGE_CONNECT_MS, IMAGE_READ_MS);
    }

    static java.awt.image.BufferedImage readImage(String url, int connectMs, int readMs) throws IOException {
        URL parsed;
        try {
            parsed = URI.create(url).toURL();
        } catch (IllegalArgumentException e) {
            throw new IOException("Not a valid image address");
        }
        String protocol = parsed.getProtocol();
        if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
            throw new IOException("Only http and https images are loaded");
        }
        URLConnection connection = parsed.openConnection();
        connection.setConnectTimeout(connectMs);
        connection.setReadTimeout(readMs);
        try {
            if (connection instanceof HttpURLConnection http && http.getResponseCode() / 100 != 2) {
                throw new IOException("Image server answered HTTP " + http.getResponseCode());
            }
            try (InputStream in = connection.getInputStream()) {
                byte[] data = in.readNBytes(IMAGE_MAX_BYTES + 1);
                if (data.length > IMAGE_MAX_BYTES) {
                    throw new IOException("Image is too large");
                }
                return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data));
            }
        } finally {
            if (connection instanceof HttpURLConnection http) {
                http.disconnect();
            }
        }
    }
}
