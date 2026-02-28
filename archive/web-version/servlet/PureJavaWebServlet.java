package com.portfolio.servlet;

import com.portfolio.model.*;
import com.portfolio.service.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.List;

/**
 * Pure Java Web Servlet - Generates ALL HTML/CSS/JavaScript from Java code
 * You only write Java, but it runs on localhost in browser
 */
public class PureJavaWebServlet extends HttpServlet {
    
    private PortfolioService portfolioService;
    
    @Override
    public void init() throws ServletException {
        StockPriceService priceService = new AlphaVantageService();
        this.portfolioService = new PortfolioService(priceService);
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("text/html; charset=UTF-8");
        PrintWriter out = response.getWriter();
        
        String action = request.getParameter("action");
        if (action == null) action = "dashboard";
        
        // Generate complete HTML page from Java
        out.println(generateHTMLPage(action));
    }
    
    private String generateHTMLPage(String page) {
        StringBuilder html = new StringBuilder();
        
        // HTML Header
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n<head>\n");
        html.append("<title>Portfolio Tracker - Pure Java</title>\n");
        html.append(generateCSS());
        html.append("</head>\n<body>\n");
        
        // Sidebar
        html.append(generateSidebar());
        
        // Main content
        html.append("<div class='main-content'>\n");
        html.append(generateTopBar());
        html.append("<div class='content'>\n");
        
        switch (page) {
            case "dashboard":
                html.append(generateDashboard());
                break;
            case "portfolio":
                html.append(generatePortfolio());
                break;
            case "market":
                html.append(generateMarket());
                break;
            case "transactions":
                html.append(generateTransactions());
                break;
            default:
                html.append(generateDashboard());
        }
        
        html.append("</div>\n</div>\n");
        html.append(generateJavaScript());
        html.append("</body>\n</html>");
        
        return html.toString();
    }
    
    private String generateCSS() {
        return "<style>\n" +
            "* { margin: 0; padding: 0; box-sizing: border-box; }\n" +
            "body { font-family: 'Segoe UI', Arial, sans-serif; background: #0f0f0f; color: #fff; }\n" +
            ".sidebar { position: fixed; left: 0; top: 0; width: 250px; height: 100vh; background: #1a1a1a; border-right: 1px solid #333; }\n" +
            ".sidebar-logo { padding: 20px; font-size: 24px; font-weight: bold; color: #667eea; border-bottom: 1px solid #333; }\n" +
            ".menu-item { padding: 15px 20px; color: #999; cursor: pointer; transition: all 0.3s; }\n" +
            ".menu-item:hover { background: #667eea; color: #fff; }\n" +
            ".main-content { margin-left: 250px; }\n" +
            ".topbar { background: #1a1a1a; padding: 20px 30px; border-bottom: 1px solid #333; }\n" +
            ".search-input { padding: 12px 20px; background: #0f0f0f; border: 1px solid #333; border-radius: 25px; color: #fff; width: 500px; }\n" +
            ".content { padding: 30px; }\n" +
            ".page-title { font-size: 32px; margin-bottom: 30px; }\n" +
            ".stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; margin-bottom: 30px; }\n" +
            ".stat-card { background: #1a1a1a; padding: 25px; border-radius: 15px; border: 1px solid #333; }\n" +
            ".stat-label { color: #999; font-size: 14px; margin-bottom: 10px; }\n" +
            ".stat-value { font-size: 32px; font-weight: bold; }\n" +
            ".positive { color: #00c853; }\n" +
            ".negative { color: #ff3b30; }\n" +
            "table { width: 100%; background: #1a1a1a; border-radius: 15px; border-collapse: collapse; }\n" +
            "th, td { padding: 15px; text-align: left; border-bottom: 1px solid #333; }\n" +
            "th { color: #999; font-size: 12px; text-transform: uppercase; }\n" +
            ".btn { padding: 12px 24px; background: #667eea; color: white; border: none; border-radius: 8px; cursor: pointer; text-decoration: none; display: inline-block; margin: 5px; }\n" +
            ".btn:hover { background: #764ba2; }\n" +
            ".stock-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; }\n" +
            ".stock-card { background: #1a1a1a; padding: 20px; border-radius: 10px; border: 1px solid #333; cursor: pointer; transition: all 0.3s; }\n" +
            ".stock-card:hover { border-color: #667eea; transform: translateY(-5px); }\n" +
            ".stock-symbol { font-size: 20px; font-weight: bold; color: #667eea; margin-bottom: 5px; }\n" +
            ".stock-name { color: #999; font-size: 14px; }\n" +
            "</style>\n";
    }
    
    private String generateSidebar() {
        return "<div class='sidebar'>\n" +
            "<div class='sidebar-logo'>📈 Portfolio</div>\n" +
            "<div class='menu-item' onclick=\"location.href='?action=dashboard'\">🏠 Dashboard</div>\n" +
            "<div class='menu-item' onclick=\"location.href='?action=portfolio'\">💼 My Portfolio</div>\n" +
            "<div class='menu-item' onclick=\"location.href='?action=market'\">📊 Market</div>\n" +
            "<div class='menu-item' onclick=\"location.href='?action=transactions'\">📜 Transactions</div>\n" +
            "<div class='menu-item' onclick=\"location.href='?action=analytics'\">📈 Analytics</div>\n" +
            "<div class='menu-item' onclick=\"location.href='?action=settings'\">⚙️ Settings</div>\n" +
            "</div>\n";
    }
    
    private String generateTopBar() {
        return "<div class='topbar'>\n" +
            "<input type='text' class='search-input' placeholder='🔍 Search stocks (AAPL, Tesla, Microsoft...)'>\n" +
            "</div>\n";
    }
    
    private String generateDashboard() {
        double totalInvestment = portfolioService.calculateTotalInvestment();
        double currentValue = portfolioService.calculateCurrentValue();
        double profitLoss = portfolioService.calculateProfitLoss();
        int stockCount = portfolioService.getPortfolioItems().size();
        
        String profitClass = profitLoss >= 0 ? "positive" : "negative";
        
        return "<h1 class='page-title'>Dashboard</h1>\n" +
            "<div class='stats-grid'>\n" +
            generateStatCard("💰 Total Investment", String.format("₹%.2f", totalInvestment), "") +
            generateStatCard("📊 Current Value", String.format("₹%.2f", currentValue), "") +
            generateStatCard("📈 Profit/Loss", String.format("₹%.2f", profitLoss), profitClass) +
            generateStatCard("📊 Total Stocks", String.valueOf(stockCount), "") +
            "</div>\n" +
            "<div style='background:#1a1a1a; padding:40px; border-radius:15px; text-align:center;'>\n" +
            "<h2>Welcome to Your Portfolio Tracker</h2>\n" +
            "<p style='color:#999; margin:20px 0;'>Track your investments, view market trends, and manage your portfolio</p>\n" +
            "<a href='?action=market' class='btn'>Browse Market</a>\n" +
            "</div>\n";
    }
    
    private String generateStatCard(String label, String value, String cssClass) {
        return "<div class='stat-card'>\n" +
            "<div class='stat-label'>" + label + "</div>\n" +
            "<div class='stat-value " + cssClass + "'>" + value + "</div>\n" +
            "</div>\n";
    }
    
    private String generatePortfolio() {
        StringBuilder html = new StringBuilder();
        html.append("<h1 class='page-title'>My Portfolio</h1>\n");
        
        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        
        if (items.isEmpty()) {
            html.append("<div style='text-align:center; padding:60px; color:#999;'>\n");
            html.append("<h2>📭 No stocks in your portfolio yet</h2>\n");
            html.append("<p>Start building your portfolio by adding stocks</p>\n");
            html.append("<a href='?action=market' class='btn'>Browse Market</a>\n");
            html.append("</div>\n");
        } else {
            html.append("<table>\n<thead>\n<tr>\n");
            html.append("<th>Symbol</th><th>Quantity</th><th>Avg Cost</th><th>Current Price</th><th>Total Value</th><th>Gain/Loss</th>\n");
            html.append("</tr>\n</thead>\n<tbody>\n");
            
            for (PortfolioItem item : items) {
                Stock stock = item.getStock();
                double gainLoss = item.getGainLoss();
                String gainClass = gainLoss >= 0 ? "positive" : "negative";
                
                html.append("<tr>\n");
                html.append("<td><strong>").append(stock.getSymbol()).append("</strong></td>\n");
                html.append("<td>").append(item.getQuantity()).append("</td>\n");
                html.append("<td>₹").append(String.format("%.2f", item.getPurchasePrice())).append("</td>\n");
                html.append("<td>₹").append(String.format("%.2f", stock.getCurrentPrice())).append("</td>\n");
                html.append("<td>₹").append(String.format("%.2f", item.getTotalValue())).append("</td>\n");
                html.append("<td class='").append(gainClass).append("'>₹").append(String.format("%.2f", gainLoss)).append("</td>\n");
                html.append("</tr>\n");
            }
            
            html.append("</tbody>\n</table>\n");
        }
        
        return html.toString();
    }
    
    private String generateMarket() {
        String[][] stocks = {
            {"AAPL", "Apple Inc.", "Technology"},
            {"GOOGL", "Alphabet Inc.", "Technology"},
            {"MSFT", "Microsoft Corp.", "Technology"},
            {"TSLA", "Tesla Inc.", "Automotive"},
            {"AMZN", "Amazon.com Inc.", "E-commerce"},
            {"META", "Meta Platforms", "Technology"},
            {"NVDA", "NVIDIA Corp.", "Technology"},
            {"NFLX", "Netflix Inc.", "Entertainment"},
            {"JPM", "JPMorgan Chase", "Finance"}
        };
        
        StringBuilder html = new StringBuilder();
        html.append("<h1 class='page-title'>📊 Stock Market</h1>\n");
        html.append("<div class='stock-grid'>\n");
        
        for (String[] stock : stocks) {
            html.append("<div class='stock-card'>\n");
            html.append("<div class='stock-symbol'>").append(stock[0]).append("</div>\n");
            html.append("<div class='stock-name'>").append(stock[1]).append("</div>\n");
            html.append("<p style='color:#999; margin:10px 0;'>").append(stock[2]).append("</p>\n");
            html.append("<button class='btn' style='width:100%;'>View Details</button>\n");
            html.append("</div>\n");
        }
        
        html.append("</div>\n");
        return html.toString();
    }
    
    private String generateTransactions() {
        StringBuilder html = new StringBuilder();
        html.append("<h1 class='page-title'>📜 Transaction History</h1>\n");
        
        List<Transaction> transactions = portfolioService.getTransactions();
        
        if (transactions.isEmpty()) {
            html.append("<div style='text-align:center; padding:60px; color:#999;'>\n");
            html.append("<h2>📜 No transactions yet</h2>\n");
            html.append("<p>Your transaction history will appear here</p>\n");
            html.append("</div>\n");
        } else {
            html.append("<table>\n<thead>\n<tr>\n");
            html.append("<th>Date</th><th>Type</th><th>Symbol</th><th>Quantity</th><th>Price</th><th>Total</th>\n");
            html.append("</tr>\n</thead>\n<tbody>\n");
            
            for (Transaction t : transactions) {
                html.append("<tr>\n");
                html.append("<td>").append(t.getTimestamp()).append("</td>\n");
                html.append("<td>").append(t.getType()).append("</td>\n");
                html.append("<td><strong>").append(t.getSymbol()).append("</strong></td>\n");
                html.append("<td>").append(t.getQuantity()).append("</td>\n");
                html.append("<td>₹").append(String.format("%.2f", t.getPrice())).append("</td>\n");
                html.append("<td>₹").append(String.format("%.2f", t.getPrice() * t.getQuantity())).append("</td>\n");
                html.append("</tr>\n");
            }
            
            html.append("</tbody>\n</table>\n");
        }
        
        return html.toString();
    }
    
    private String generateJavaScript() {
        return "<script>\n" +
            "console.log('Portfolio Tracker - Generated from Pure Java!');\n" +
            "</script>\n";
    }
}
