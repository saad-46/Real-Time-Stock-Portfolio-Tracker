package com.portfolio.ui;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A repeating Swing timer with an explicit, idempotent lifecycle: starting twice never creates a second schedule, and a stopped
 * timer stays stopped. Every window owns its timers through this class so none outlives the window.
 */
final class ManagedTimer {
    private static final AtomicInteger RUNNING = new AtomicInteger();

    private final javax.swing.Timer timer;
    private boolean started;

    ManagedTimer(int periodMillis, Runnable task) {
        timer = new javax.swing.Timer(periodMillis, e -> task.run());
        timer.setRepeats(true);
        timer.setCoalesce(true);
    }

    synchronized void start() {
        if (!started) {
            started = true;
            RUNNING.incrementAndGet();
            timer.start();
        }
    }

    synchronized void stop() {
        if (started) {
            started = false;
            RUNNING.decrementAndGet();
            timer.stop();
        }
    }

    synchronized boolean isRunning() {
        return started;
    }

    /** Number of managed timers currently running in this JVM (used by tests). */
    static int running() {
        return RUNNING.get();
    }
}
