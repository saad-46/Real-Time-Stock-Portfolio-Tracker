package com.portfolio.service;

import com.portfolio.model.Stock;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real HTTP code path against a local loopback fake; no internet needed. */
class AlphaVantageHttpTest {
    private HttpServer server;
    private volatile int status;
    private volatile String body;
    private volatile long delayMs;
    private final AtomicReference<String> lastQuery = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        status = 200;
        delayMs = 0;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/query", ex -> {
            lastQuery.set(ex.getRequestURI().getRawQuery());
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            try {
                ex.sendResponseHeaders(status, b.length);
                try (var out = ex.getResponseBody()) {
                    out.write(b);
                }
            } catch (java.io.IOException ignored) {
                // client gave up (timeout / oversized body); expected in some tests
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private AlphaVantageService svc(String key, Duration timeout) {
        return new AlphaVantageService(key, "http://127.0.0.1:" + server.getAddress().getPort() + "/query", timeout);
    }

    @Test
    void successReturnsLiveQuoteAndEncodesSymbolAndKey() {
        body = "{\"Global Quote\":{\"05. price\":\"10.50\"}}";
        Stock s = svc("k&ey=1", Duration.ofSeconds(5)).getStock("BRK B&x=1");
        assertEquals(10.50, s.getCurrentPrice(), 0.0);
        String q = lastQuery.get();
        assertTrue(q.contains("symbol=BRK+B%26x%3D1"), q);
        assertTrue(q.contains("apikey=k%26ey%3D1"), q);
    }

    @Test
    void httpErrorIsUnavailable() {
        status = 500;
        body = "{}";
        assertThrows(PriceUnavailableException.class, () -> svc("k", Duration.ofSeconds(5)).getStock("AAPL"));
    }

    @Test
    void rateLimitWithHttp200IsUnavailable() {
        body = "{\"Note\":\"rate limit\"}";
        assertThrows(PriceUnavailableException.class, () -> svc("k", Duration.ofSeconds(5)).getStock("AAPL"));
    }

    @Test
    void slowServerTimesOutAsUnavailable() {
        body = "{\"Global Quote\":{\"05. price\":\"1\"}}";
        delayMs = 2000;
        assertThrows(PriceUnavailableException.class, () -> svc("k", Duration.ofMillis(300)).getStock("AAPL"));
    }

    @Test
    void oversizedResponseIsRejected() {
        body = "{\"x\":\"" + "a".repeat(AlphaVantageService.MAX_BODY_BYTES + 10) + "\"}";
        PriceUnavailableException e = assertThrows(PriceUnavailableException.class,
                () -> svc("k", Duration.ofSeconds(5)).getStock("AAPL"));
        assertTrue(e.getMessage().contains("too large"));
    }

    @Test
    void connectionRefusedIsUnavailable() {
        server.stop(0);
        assertThrows(PriceUnavailableException.class, () -> svc("k", Duration.ofSeconds(2)).getStock("AAPL"));
    }
}
