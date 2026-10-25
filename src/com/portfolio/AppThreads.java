package com.portfolio;

import java.util.concurrent.ThreadFactory;

/**
 * Background work must never keep the program alive after its last window is gone: a request stuck on a slow network
 * connection would otherwise leave an invisible process holding the program file and the database.
 */
public final class AppThreads {
    private AppThreads() {
    }

    /** A not-yet-started daemon thread running {@code task}. */
    public static Thread daemon(Runnable task) {
        Thread t = new Thread(task, "stockvault-worker");
        t.setDaemon(true);
        return t;
    }

    public static ThreadFactory daemonFactory(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    /** Counts consecutive checks that found no window; true on the second one. A window that appears resets the count. */
    static boolean windowsGone(int[] consecutiveEmpty, boolean anyWindowShowing) {
        consecutiveEmpty[0] = anyWindowShowing ? 0 : consecutiveEmpty[0] + 1;
        return consecutiveEmpty[0] >= 2;
    }

    /**
     * Exits the program once no window has been showing for two consecutive checks (a window being replaced by the next one
     * is shown before the old one closes, so the gap is never seen). Must be started on the event thread.
     */
    public static void exitWhenNoWindowsRemain() {
        final int[] empty = { 0 };
        javax.swing.Timer timer = new javax.swing.Timer(2000, null);
        timer.addActionListener(e -> {
            boolean any = false;
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                if (w.isShowing()) { // a minimized window still counts as showing
                    any = true;
                    break;
                }
            }
            if (windowsGone(empty, any)) {
                System.exit(0); // runs the shutdown hooks, which close the database
            }
        });
        timer.setRepeats(true);
        timer.start();
    }
}
