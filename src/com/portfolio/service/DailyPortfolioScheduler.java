package com.portfolio.service;

import java.util.*;
import java.util.concurrent.*;

/**
 * DailyPortfolioScheduler - Schedules daily portfolio update emails
 * Runs at a specific time each day (e.g., 9:00 AM)
 */
public class DailyPortfolioScheduler {

    private final PortfolioService portfolioService;
    private final EmailService emailService;
    private ScheduledExecutorService scheduler;
    private volatile String recipientEmail;
    private volatile boolean isRunning = false;

    public DailyPortfolioScheduler(PortfolioService portfolioService, EmailService emailService) {
        this.portfolioService = portfolioService;
        this.emailService = emailService;
    }

    /**
     * Start daily email scheduler at 8:00 PM
     * 
     * @param email Recipient email address
     */
    public synchronized void start(String email, String timeStr) {
        if (email == null || email.isBlank()) {
            System.out.println("Daily e-mail scheduler not started: no recipient is configured");
            return;
        }
        if (isRunning) {
            System.out.println("⚠️ Scheduler already running");
            return;
        }

        this.recipientEmail = email;
        this.scheduler = Executors.newScheduledThreadPool(1, com.portfolio.AppThreads.daemonFactory("daily-email"));

        // Parse time (e.g., "09:00")
        String[] parts = timeStr.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);

        // Calculate delay until next run
        Calendar now = Calendar.getInstance();
        Calendar nextRun = Calendar.getInstance();
        nextRun.set(Calendar.HOUR_OF_DAY, hour);
        nextRun.set(Calendar.MINUTE, minute);
        nextRun.set(Calendar.SECOND, 0);

        if (now.after(nextRun)) {
            nextRun.add(Calendar.DAY_OF_MONTH, 1);
        }

        long initialDelay = nextRun.getTimeInMillis() - now.getTimeInMillis();
        long period = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

        scheduler.scheduleAtFixedRate(() -> {
            try {
                System.out.println("📧 Sending daily portfolio update at " + timeStr + "...");
                emailService.sendDailyPortfolioUpdate(recipientEmail, portfolioService);
            } catch (Exception e) {
                System.err.println("❌ Failed to send scheduled email: " + e.getMessage());
            }
        }, initialDelay, period, TimeUnit.MILLISECONDS);

        isRunning = true;
        System.out.println("✅ Daily email scheduler started. Next email at: " + nextRun.getTime());
        System.out.println("📧 Will send to: " + recipientEmail + " every day at " + timeStr);
    }

    /**
     * Stop the scheduler
     */
    public synchronized void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow(); // cancels the pending run and interrupts one in progress
            System.out.println("⏹️ Daily email scheduler stopped");
        }
        isRunning = false;
    }

    /**
     * Send immediate test email
     */
    public void sendTestEmail() {
        try {
            System.out.println("📧 Sending test email...");
            emailService.sendDailyPortfolioUpdate(recipientEmail, portfolioService);
            System.out.println("✅ Test email sent successfully!");
        } catch (Exception e) {
            System.err.println("❌ Test email failed: " + e.getMessage());
        }
    }

    /**
     * Check if scheduler is running
     */
    public boolean isRunning() {
        return isRunning;
    }
}
