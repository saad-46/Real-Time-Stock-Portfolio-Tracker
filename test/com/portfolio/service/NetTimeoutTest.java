package com.portfolio.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

/** Local test server only: nothing leaves the machine. */
class NetTimeoutTest {
    private HttpServer server;
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/stall", ex -> {
            try {
                release.await(); // never answers until the test ends
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            ex.close();
        });
        server.createContext("/missing", ex -> {
            ex.sendResponseHeaders(404, -1);
            ex.close();
        });
        server.createContext("/text", ex -> {
            byte[] body = "not an image".getBytes();
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        release.countDown();
        server.stop(0);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    @Test
    void aStalledImageServerTimesOutInsteadOfWaitingForever() {
        long t0 = System.nanoTime();
        assertThrows(IOException.class, () -> Net.readImage(url("/stall"), 500, 600));
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 5_000, "gave up quickly");
    }

    @Test
    void badAnswersAreErrorsNotHangsOrFakeImages() throws IOException {
        assertThrows(IOException.class, () -> Net.readImage(url("/missing"), 500, 600));
        assertNull(Net.readImage(url("/text"), 500, 600), "data that is not an image decodes to nothing");
        assertThrows(IOException.class, () -> Net.readImage("file:///C:/Windows/win.ini"));
        assertThrows(IOException.class, () -> Net.readImage("not a url"));
    }

    @Test
    void clientsAndLimitsAreFinite() {
        assertEquals(Net.CONNECT_TIMEOUT, Net.client().connectTimeout().orElseThrow());
        assertTrue(Net.REQUEST_TIMEOUT.compareTo(Net.UPLOAD_TIMEOUT) < 0);
        assertFalse(Net.REQUEST_TIMEOUT.isZero() || Net.REQUEST_TIMEOUT.isNegative());
    }

    @Test
    void anHttpRequestToAStalledServerEndsWithinItsTimeout() {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url("/stall")))
                .timeout(java.time.Duration.ofMillis(600)).GET().build();
        long t0 = System.nanoTime();
        assertThrows(java.net.http.HttpTimeoutException.class,
                () -> Net.client().send(request, java.net.http.HttpResponse.BodyHandlers.ofString()));
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 5_000);
    }
}
