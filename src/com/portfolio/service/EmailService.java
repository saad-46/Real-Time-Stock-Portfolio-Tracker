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
    private String senderEmail = "stockvault123@gmail.com";
    // IMPORTANT: This MUST be a Gmail App Password, not your regular password!
    // Regular Gmail passwords don't work with SMTP anymore.
    // To generate an App Password:
    // 1. Go to https://myaccount.google.com/security
    // 2. Enable 2-Step Verification if not already enabled
    // 3. Go to https://myaccount.google.com/apppasswords
    // 4. Select "Mail" and "Other (Custom name)" - name it "StockVault"
    // 5. Copy the 16-character password (no spaces) and paste it below
    private String senderPassword = ApiKeyManager.getGmailAppPassword();

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
    private void sendEmail(String recipient, String subject, String htmlBody) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.ssl.trust", smtpHost);
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        props.put("mail.debug", "true"); // Enable debug output

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
            System.err.println("❌ AUTHENTICATION FAILED!");
            System.err.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.err.println("⚠️  Gmail requires an APP PASSWORD, not your regular password!");
            System.err.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.err.println("📋 STEPS TO FIX:");
            System.err.println("1. Go to: https://myaccount.google.com/security");
            System.err.println("2. Enable '2-Step Verification' (if not already enabled)");
            System.err.println("3. Go to: https://myaccount.google.com/apppasswords");
            System.err.println("4. Select 'Mail' and 'Other (Custom name)' - name it 'StockVault'");
            System.err.println("5. Copy the 16-character password (format: xxxx xxxx xxxx xxxx)");
            System.err.println("6. Open: src/com/portfolio/service/EmailService.java");
            System.err.println("7. Replace 'Stockvault321' with your App Password (remove spaces)");
            System.err.println("8. Recompile: javac -cp ... EmailService.java");
            System.err.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            throw new Exception("Gmail authentication failed. App Password required. See console for instructions.", e);
        }
    }

    /**
     * Check if email is configured
     */
    public boolean isConfigured() {
        return senderEmail != null && senderPassword != null;
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
        this.senderEmail = email;
        this.senderPassword = password;
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
