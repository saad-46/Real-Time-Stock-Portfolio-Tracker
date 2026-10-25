package com.portfolio.service;

import com.portfolio.model.PortfolioItem;
import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;
import javax.mail.internet.InternetAddress; // Explicit for clarification

/**
 * EmailService - Sends real emails using Gmail SMTP with JavaMail
 */
public class EmailService {

    private String smtpHost = "smtp.gmail.com";
    private String smtpPort = "587";
    /** The application's sender account. */
    public static final String DEFAULT_SENDER = "stockvault104@gmail.com";
    /** The previous sender; its App Password was revoked, so it is never used. */
    static final String REVOKED_SENDER = "stockvault123@gmail.com";

    private String senderEmail = resolveSender(ApiKeyManager.get("GMAIL_ADDRESS"));
    // The App Password comes from GMAIL_APP_PASSWORD (environment, stockvault.local.properties or .env), which takes precedence,
    // or from Windows Credential Manager (see GmailCredentialService). It is never stored in source files.
    private String senderPassword = normalizePassword(ApiKeyManager.getGmailAppPassword());

    /** The configured address, or the default sender when none is configured or the revoked old account is named. */
    public static String resolveSender(String configured) {
        if (!ApiKeyManager.isConfigured(configured)) {
            return DEFAULT_SENDER;
        }
        String trimmed = configured.trim();
        return REVOKED_SENDER.equalsIgnoreCase(trimmed) ? DEFAULT_SENDER : trimmed;
    }

    /** Google shows App Passwords in groups ("abcd efgh ..."); the spaces are not part of the password. */
    static String normalizePassword(String password) {
        return password == null ? null : password.replaceAll("\\s+", "");
    }

    /** A non-sensitive description of what is wrong with the sender configuration, or null when it looks usable. */
    public String configurationProblem() {
        String at = senderEmail == null ? "" : senderEmail.toLowerCase(java.util.Locale.ROOT);
        if (!at.matches("[^@\\s]+@(gmail|googlemail)\\.com")) {
            return "The sender address is not a valid Gmail address (check GMAIL_ADDRESS for a typo).";
        }
        if (!ApiKeyManager.isConfigured(senderPassword)) {
            return "No Gmail App Password is configured: save it under Settings or set GMAIL_APP_PASSWORD.";
        }
        if (senderPassword.length() != 16) {
            return "The Gmail App Password does not look right: Google App Passwords have 16 characters"
                    + " (this one has " + senderPassword.length() + "). Create a new one at https://myaccount.google.com/apppasswords.";
        }
        return null;
    }

    public EmailService() {
        // Default configuration with provided credentials
    }

    /**
     * Send daily portfolio summary email
     */
    public void sendDailyPortfolioUpdate(String recipientEmail, PortfolioService portfolioService) {
        try {
            System.out.println("📧 Preparing email for: " + recipientEmail);

            // Calculate metrics
            double totalValue = portfolioService.calculateCurrentValue();
            double totalInvestment = portfolioService.calculateTotalInvestment();
            double profitLoss = portfolioService.calculateProfitLoss();
            double profitPercent = totalInvestment > 0 ? (profitLoss / totalInvestment) * 100 : 0;

            List<PortfolioItem> items = portfolioService.getPortfolioItems();

            // Generate HTML
            String emailBody = generateEmailHTML(recipientEmail, totalValue, totalInvestment, profitLoss, profitPercent,
                    items);
            if (portfolioService.profitLossChecked().approximate()) {
                // totals that used fixed fallback rates must say so
                emailBody = emailBody.replace("<p style='color: #666;'>Daily Portfolio Report</p>",
                        "<p style='color: #666;'>Daily Portfolio Report</p><p style='color: #b45309;'>&asymp; Totals use approximate "
                                + "fixed exchange rates (live rates were unavailable); they are not validated figures.</p>");
            }

            // Send email
            sendEmail(recipientEmail, "📊 Daily Portfolio Update - StockVault", emailBody);

            System.out.println("✅ Email sent successfully to: " + recipientEmail);

        } catch (Exception e) {
            System.err.println("❌ Failed to send email: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Generate HTML email
     */
    private String generateEmailHTML(String recipientEmail, double totalValue, double totalInvestment,
            double profitLoss, double profitPercent, List<PortfolioItem> items) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head>");
        html.append("<body style='font-family: Arial, sans-serif; background: #f5f5f5; margin: 0; padding: 20px;'>");
        html.append(
                "<div style='max-width: 600px; margin: 0 auto; background: white; border-radius: 15px; padding: 30px;'>");

        // Header
        html.append("<div style='text-align: center; margin-bottom: 30px;'>");
        html.append("<h1 style='color: #667eea; margin: 0;'>💎 StockVault</h1>");
        html.append("<p style='color: #666;'>Daily Portfolio Report</p>");
        html.append("<p style='color: #999; font-size: 14px;'>").append(new Date()).append("</p>");
        html.append("</div>");

        html.append("<hr style='border: none; border-top: 3px solid #667eea; margin: 25px 0;'>");

        // Performance Cards
        html.append("<h2 style='color: #333;'>📊 Performance Summary</h2>");
        html.append("<table style='width: 100%;'><tr>");

        html.append(
                "<td style='padding: 10px;'><div style='background: #667eea; padding: 20px; border-radius: 12px; color: white; text-align: center;'>");
        html.append("<div style='font-size: 14px;'>TOTAL VALUE</div>");
        html.append("<div style='font-size: 28px; font-weight: bold;'>₹").append(String.format("%.2f", totalValue))
                .append("</div></div></td>");

        html.append(
                "<td style='padding: 10px;'><div style='background: #4facfe; padding: 20px; border-radius: 12px; color: white; text-align: center;'>");
        html.append("<div style='font-size: 14px;'>INVESTED</div>");
        html.append("<div style='font-size: 28px; font-weight: bold;'>₹").append(String.format("%.2f", totalInvestment))
                .append("</div></div></td>");

        html.append("</tr><tr>");

        String plColor = profitLoss >= 0 ? "#4ade80" : "#f87171";
        html.append("<td style='padding: 10px;'><div style='background: ").append(plColor)
                .append("; padding: 20px; border-radius: 12px; color: white; text-align: center;'>");
        html.append("<div style='font-size: 14px;'>PROFIT/LOSS</div>");
        html.append("<div style='font-size: 28px; font-weight: bold;'>₹").append(String.format("%.2f", profitLoss))
                .append("</div></div></td>");

        html.append("<td style='padding: 10px;'><div style='background: ").append(plColor)
                .append("; padding: 20px; border-radius: 12px; color: white; text-align: center;'>");
        html.append("<div style='font-size: 14px;'>RETURN</div>");
        html.append("<div style='font-size: 28px; font-weight: bold;'>").append(String.format("%.2f%%", profitPercent))
                .append("</div></div></td>");

        html.append("</tr></table>");

        // Holdings
        if (!items.isEmpty()) {
            html.append("<h2 style='color: #333; margin-top: 30px;'>📈 Your Holdings</h2>");
            html.append("<table style='width: 100%; border-collapse: collapse;'>");
            html.append("<tr style='background: #667eea; color: white;'>");
            html.append("<th style='padding: 12px; text-align: left;'>Stock</th>");
            html.append("<th style='padding: 12px; text-align: center;'>Qty</th>");
            html.append("<th style='padding: 12px; text-align: right;'>Price</th>");
            html.append("<th style='padding: 12px; text-align: right;'>P/L</th></tr>");

            for (PortfolioItem item : items) {
                double gainLoss = item.getGainLoss();
                String color = gainLoss >= 0 ? "#22c55e" : "#ef4444";
                String icon = gainLoss >= 0 ? "📈" : "📉";

                html.append("<tr style='border-bottom: 1px solid #e2e8f0;'>");
                html.append("<td style='padding: 12px;'><strong>").append(item.getStock().getSymbol())
                        .append("</strong></td>");
                html.append("<td style='padding: 12px; text-align: center;'>").append(item.getQuantity())
                        .append("</td>");
                html.append("<td style='padding: 12px; text-align: right;'>₹")
                        .append(String.format("%.2f", item.getStock().getCurrentPrice())).append("</td>");
                html.append("<td style='padding: 12px; text-align: right; color: ").append(color).append(";'>");
                html.append(icon).append(" ₹").append(String.format("%.2f", gainLoss)).append("</td></tr>");
            }
            html.append("</table>");
        }

        // Alerts
        html.append(
                "<div style='background: #fbbf24; padding: 20px; margin: 30px 0; border-radius: 12px; color: white;'>");
        html.append("<h3 style='margin: 0 0 15px 0;'>⚠️ Market Alerts</h3>");

        if (profitLoss < 0) {
            html.append("<p>📉 Portfolio showing loss. Review underperforming stocks.</p>");
        } else {
            html.append("<p>📈 Portfolio in profit! Keep monitoring trends.</p>");
        }

        for (PortfolioItem item : items) {
            double changePercent = item.getStock().getChangePercent();
            if (Math.abs(changePercent) > 5) {
                String icon = changePercent > 0 ? "🚀" : "⚡";
                html.append("<p>").append(icon).append(" <strong>").append(item.getStock().getSymbol())
                        .append("</strong> moved ").append(String.format("%.2f%%", changePercent)).append("</p>");
            }
        }
        html.append("</div>");

        // Footer
        html.append(
                "<div style='text-align: center; margin-top: 30px; padding-top: 20px; border-top: 2px solid #e2e8f0;'>");
        html.append("<p style='color: #667eea; font-weight: bold;'>StockVault Portfolio Tracker</p>");
        html.append("<p style='color: #64748b; font-size: 12px;'>Automated daily report at 8:00 PM</p>");
        html.append("</div></div></body></html>");

        return html.toString();
    }

    /**
     * Send email using Gmail SMTP
     */
    /**
     * SMTP session settings. TLS is REQUIRED and the server certificate and host name are validated (no
     * "mail.smtp.ssl.trust": trusting the host by name would skip certificate checks and let a man in the middle read
     * the app password). Debug output is off: it prints the SMTP conversation to the console. Timeouts stop a dead
     * server from hanging a thread forever.
     */
    static Properties mailProperties(String host, String port) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.checkserveridentity", "true");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "20000");
        props.put("mail.smtp.writetimeout", "20000");
        props.put("mail.debug", "false");
        return props;
    }

    private void sendEmail(String recipient, String subject, String htmlBody) throws Exception {
        String problem = configurationProblem();
        if (problem != null) {
            throw new Exception(problem); // nothing is sent and no connection is made
        }
        Properties props = mailProperties(smtpHost, smtpPort);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(senderEmail, senderPassword);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(senderEmail));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
            message.setSubject(subject);
            message.setContent(htmlBody, "text/html; charset=utf-8");

            Transport.send(message);
            System.out.println("✅ Email sent successfully!");
        } catch (AuthenticationFailedException e) {
            System.err.println("❌ Gmail rejected the login. Gmail needs an App Password, not your account password:");
            System.err.println("   create one at https://myaccount.google.com/apppasswords and provide it as GMAIL_APP_PASSWORD");
            System.err.println("   (environment variable or stockvault.local.properties). It is never stored in source files.");
            throw new Exception("Gmail authentication failed. A Gmail App Password is required (save it under Settings, or set GMAIL_APP_PASSWORD).", e);
        }
    }

    /**
     * Check if email is configured
     */
    public boolean isConfigured() {
        // an EMPTY password (no key configured) is not a configuration
        return ApiKeyManager.isConfigured(senderEmail) && ApiKeyManager.isConfigured(senderPassword);
    }

    /**
     * Get sender email
     */
    public String getEmail() {
        return senderEmail;
    }

    /**
     * Configure email settings
     */
    public void configure(String email, String password) {
        this.senderEmail = ApiKeyManager.isConfigured(email) ? resolveSender(email) : email; // an empty address stays "not configured"
        this.senderPassword = normalizePassword(password);
    }

    /**
     * Send test email
     */
    public boolean sendTestEmail() {
        try {
            String testBody = "<html><body style='font-family: Arial; padding: 20px;'>" +
                    "<h2 style='color: #667eea;'>✅ Email Test Successful</h2>" +
                    "<p>Your StockVault email notifications are working!</p>" +
                    "<p>Daily updates will be sent at 8:00 PM.</p>" +
                    "</body></html>";

            sendEmail(senderEmail, "StockVault - Email Test", testBody);
            return true;
        } catch (Exception e) {
            System.err.println("❌ Test email failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
