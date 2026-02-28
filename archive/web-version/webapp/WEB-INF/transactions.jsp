<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.List" %>
<%@ page import="com.portfolio.model.Transaction" %>
<!DOCTYPE html>
<html>
<head>
    <title>Transactions - Portfolio Tracker</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #0f0f0f; color: #fff; }
        .container { max-width: 1200px; margin: 0 auto; padding: 40px 20px; }
        .header { margin-bottom: 40px; }
        .title { font-size: 36px; margin-bottom: 10px; }
        .subtitle { color: #999; font-size: 16px; }
        .btn { padding: 12px 24px; background: #667eea; color: white; border: none; border-radius: 8px; cursor: pointer; text-decoration: none; display: inline-block; margin: 5px; }
        .btn:hover { background: #764ba2; }
        .btn-secondary { background: #333; }
        .btn-secondary:hover { background: #444; }
        .transactions-table { background: #1a1a1a; border-radius: 15px; padding: 25px; border: 1px solid #333; margin-top: 30px; }
        table { width: 100%; border-collapse: collapse; }
        th, td { padding: 15px; text-align: left; border-bottom: 1px solid #333; }
        th { color: #999; font-weight: 600; font-size: 12px; text-transform: uppercase; }
        td { font-size: 14px; }
        .transaction-buy { color: #00c853; font-weight: 600; }
        .transaction-sell { color: #ff3b30; font-weight: 600; }
        .empty-state { text-align: center; padding: 100px 20px; color: #999; }
        .empty-icon { font-size: 80px; margin-bottom: 20px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1 class="title">📜 Transaction History</h1>
            <p class="subtitle">All your buy and sell transactions</p>
        </div>
        
        <div>
            <a href="portfolio?action=dashboard" class="btn btn-secondary">← Back to Dashboard</a>
        </div>
        
        <%
            List<Transaction> transactions = (List<Transaction>) request.getAttribute("transactions");
            if (transactions != null && !transactions.isEmpty()) {
        %>
        <div class="transactions-table">
            <table>
                <thead>
                    <tr>
                        <th>Date & Time</th>
                        <th>Type</th>
                        <th>Symbol</th>
                        <th>Quantity</th>
                        <th>Price per Share</th>
                        <th>Total Amount</th>
                    </tr>
                </thead>
                <tbody>
                    <% for (Transaction transaction : transactions) {
                        String typeClass = transaction.getType().equals("BUY") ? "transaction-buy" : "transaction-sell";
                        double totalAmount = transaction.getPrice() * transaction.getQuantity();
                    %>
                    <tr>
                        <td><%= transaction.getTimestamp() %></td>
                        <td class="<%= typeClass %>"><%= transaction.getType() %></td>
                        <td><strong><%= transaction.getSymbol() %></strong></td>
                        <td><%= transaction.getQuantity() %></td>
                        <td>₹<%= String.format("%.2f", transaction.getPrice()) %></td>
                        <td>₹<%= String.format("%.2f", totalAmount) %></td>
                    </tr>
                    <% } %>
                </tbody>
            </table>
        </div>
        <% } else { %>
        <div class="empty-state">
            <div class="empty-icon">📜</div>
            <h2>No Transactions Yet</h2>
            <p style="margin: 20px 0;">Your transaction history will appear here once you start buying stocks</p>
            <a href="portfolio?action=market" class="btn">Start Investing</a>
        </div>
        <% } %>
    </div>
</body>
</html>
