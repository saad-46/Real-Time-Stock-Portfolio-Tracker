package com.portfolio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppThreadsTest {
    @Test
    void exitNeedsTwoConsecutiveChecksWithoutAWindow() {
        int[] n = { 0 };
        assertFalse(AppThreads.windowsGone(n, true));
        assertFalse(AppThreads.windowsGone(n, false), "one empty check is a window being replaced");
        assertFalse(AppThreads.windowsGone(n, true), "a window that appears resets the count");
        assertFalse(AppThreads.windowsGone(n, false));
        assertTrue(AppThreads.windowsGone(n, false));
    }

    @Test
    void workerThreadsAreDaemons() {
        assertTrue(AppThreads.daemon(() -> { }).isDaemon());
        assertTrue(AppThreads.daemonFactory("x").newThread(() -> { }).isDaemon());
    }
}
