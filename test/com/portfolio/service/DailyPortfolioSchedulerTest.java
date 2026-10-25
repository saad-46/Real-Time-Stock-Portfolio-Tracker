package com.portfolio.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DailyPortfolioSchedulerTest {
    private static boolean workerAlive() {
        return Thread.getAllStackTraces().keySet().stream().anyMatch(t -> t.getName().equals("daily-email") && t.isAlive());
    }

    @Test
    void doesNotStartWithoutARecipientAndStopCancelsTheWorker() throws Exception {
        DailyPortfolioScheduler s = new DailyPortfolioScheduler(null, null);
        s.start("", "23:59");
        s.start(null, "23:59");
        assertFalse(s.isRunning());

        s.start("someone@example.invalid", "23:59");
        assertTrue(s.isRunning());
        s.start("someone@example.invalid", "23:59"); // a second start changes nothing
        assertTrue(s.isRunning());
        assertTrue(Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().equals("daily-email")).allMatch(Thread::isDaemon));

        s.stop();
        assertFalse(s.isRunning());
        long end = System.currentTimeMillis() + 3000;
        while (workerAlive() && System.currentTimeMillis() < end) {
            Thread.sleep(25);
        }
        assertFalse(workerAlive(), "the scheduler thread ends when stopped");
        s.stop(); // stopping twice is harmless
    }
}
