package com.portfolio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MainUITest {
    @Test
    void startupFailureTextNeverShowsNullAndKeepsTheCauseChain() {
        assertEquals("NullPointerException", MainUI.startupFailureText(new NullPointerException()));
        assertEquals("IllegalStateException: boom", MainUI.startupFailureText(new IllegalStateException("  boom ")));
        String chained = MainUI.startupFailureText(new RuntimeException("outer", new java.io.IOException("disk")));
        assertEquals("RuntimeException: outer <- IOException: disk", chained);
        assertEquals("NoClassDefFoundError: com/x/Y", MainUI.startupFailureText(new NoClassDefFoundError("com/x/Y")));
    }

    @Test
    void theNewsSupplierRemembersASuccessfulAnswerButNeverAnEmptyOne() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicBoolean good = new java.util.concurrent.atomic.AtomicBoolean(false);
        com.portfolio.service.NewsService.NewsItem item = new com.portfolio.service.NewsService.NewsItem("t", "c", "s", "https://x.invalid/");
        java.util.function.Supplier<com.portfolio.service.NewsService.Live> cached = MainUI.cached(() -> {
            calls.incrementAndGet();
            return good.get() ? new com.portfolio.service.NewsService.Live(java.util.List.of(item), null)
                    : new com.portfolio.service.NewsService.Live(java.util.List.of(), "down");
        }, java.time.Duration.ofMinutes(5));
        assertTrue(cached.get().items().isEmpty());
        assertTrue(cached.get().items().isEmpty());
        assertEquals(2, calls.get(), "a failure is retried, not remembered");
        good.set(true);
        assertEquals(1, cached.get().items().size());
        assertEquals(1, cached.get().items().size());
        assertEquals(3, calls.get(), "a good answer is reused (a theme switch does not ask the news provider again)");
    }
}
