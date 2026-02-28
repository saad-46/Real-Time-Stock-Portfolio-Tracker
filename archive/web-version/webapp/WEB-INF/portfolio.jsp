<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.List" %>
<%@ page import="com.portfolio.model.*" %>
<!DOCTYPE html>
<html>
<head>
    <title>Portfolio Tracker</title>
    <style>
        body { font-family: Arial; background: #1a1a1a; color: #fff; padding: 20px; }
        .card { background: #2a2a2a; padding: 20px; margin: 10px; border-radius: 10px; display: inline-block; min-width: 200px; }
        .card h3 { margin: 0; color: #999; font-size: 14px; }
        .card .value { font-size: 28px; font-weight: bold; margin-top: 10px; }
        table { width: 100%; background: #2a2a2a; border-radius: 10px; margin-top: 20px; }
        th, td { padding: 15px; text-align: left; }
        th { background: #1e1e1e; color: #999; }
        .btn { padding: 12px 24px; background: #667eea; color: white; border: none; border-radius: 8px; cursor: pointer; margin: 5px; text-decoration: none; display: inline-block; }
        .btn:hover { background: #764ba2; }
        .profit { color: #00c853; }
        .loss { color: #ff3b30; }
        .stock-card { background: #1e1e1e; padding: 15px; border-radius: 8px; cursor: pointer; transition: all 0.3s; border: 2px solid transparent; }
        .stock-card:hover { border-color: #667eea; transform: translateY(-2px); }
    </style>
</head>
<body>
    <h1>💼 Stock Portfolio Tracker</h1>
    
    <div>
        <div class="card">
            <h3>💰 Total Investment</h3>
            <div class="value">₹<%= String.format("%.2f", request.getAttribute("totalInvestment")) %></div>
        </div>
        <div class="card">
            <h3>📊 Current Value</h3>
            <div class="value">₹<%= String.format("%.2f", request.getAttribute("currentValue")) %></div>
        </div>
        <div class="card">
            <h3>📈 Profit/Loss</h3>
            <%
                double profitLoss = (Double) request.getAttribute("profitLoss");
                String profitClass = profitLoss >= 0 ? "profit" : "loss";
            %>
            <div class="value <%= profitClass %>">₹<%= String.format("%.2f", profitLoss) %></div>
        </div>
    </div>
    
    <div style="margin: 20px 0;">
        <button class="btn" onclick="location.href='portfolio?action=refresh'">🔄 Refresh Prices</button>
        <button class="btn" onclick="location.href='portfolio?action=market'">📈 Browse Market</button>
        <button class="btn" onclick="showBrowseStocks()">🔍 Quick Browse</button>
        <button class="btn" onclick="showAddForm()">+ Add Stock</button>
    </div>
    
    <div id="browseStocks" style="display:none; background:#2a2a2a; padding:20px; border-radius:10px; margin:20px 0;">
        <h3>🔍 Popular Stocks</h3>
        <p style="color:#999; margin-bottom:15px;">Click on any stock to add it to your portfolio</p>
        <div style="display:grid; grid-template-columns:repeat(auto-fill, minmax(200px, 1fr)); gap:15px;">
            <div class="stock-card" onclick="selectStock('AAPL', 'Apple Inc.')">
                <strong>AAPL</strong><br>
                <small style="color:#999;">Apple Inc.</small>
            </div>
            <div class="stock-card" onclick="selectStock('GOOGL', 'Alphabet Inc.')">
                <strong>GOOGL</strong><br>
                <small style="color:#999;">Alphabet (Google)</small>
            </div>
            <div class="stock-card" onclick="selectStock('MSFT', 'Microsoft Corp.')">
                <strong>MSFT</strong><br>
                <small style="color:#999;">Microsoft</small>
            </div>
            <div class="stock-card" onclick="selectStock('TSLA', 'Tesla Inc.')">
                <strong>TSLA</strong><br>
                <small style="color:#999;">Tesla</small>
            </div>
            <div class="stock-card" onclick="selectStock('AMZN', 'Amazon.com Inc.')">
                <strong>AMZN</strong><br>
                <small style="color:#999;">Amazon</small>
            </div>
            <div class="stock-card" onclick="selectStock('META', 'Meta Platforms')">
                <strong>META</strong><br>
                <small style="color:#999;">Meta (Facebook)</small>
            </div>
            <div class="stock-card" onclick="selectStock('NVDA', 'NVIDIA Corp.')">
                <strong>NVDA</strong><br>
                <small style="color:#999;">NVIDIA</small>
            </div>
            <div class="stock-card" onclick="selectStock('NFLX', 'Netflix Inc.')">
                <strong>NFLX</strong><br>
                <small style="color:#999;">Netflix</small>
            </div>
        </div>
        <button class="btn" onclick="document.getElementById('browseStocks').style.display='none'" style="margin-top:15px;">Close</button>
    </div>
    
    <div id="addForm" style="display:none; background:#2a2a2a; padding:20px; border-radius:10px; margin:20px 0;">
        <h3>Add New Stock</h3>
        <p style="color:#999; font-size:14px; margin-bottom:15px;">💡 Tip: Click "Browse Stocks" to see popular stocks, or enter a symbol manually</p>
        <form action="portfolio" method="post">
            <input type="hidden" name="action" value="add">
            <div style="margin-bottom:10px;">
                <label style="display:block; color:#999; margin-bottom:5px;">Stock Symbol (e.g., AAPL, GOOGL)</label>
                <input type="text" id="symbolInput" name="symbol" placeholder="Enter stock symbol" required style="padding:10px; width:200px; background:#1e1e1e; border:1px solid #444; color:#fff; border-radius:5px;">
            </div>
            <div style="margin-bottom:10px;">
                <label style="display:block; color:#999; margin-bottom:5px;">Quantity (Number of shares)</label>
                <input type="number" name="quantity" placeholder="e.g., 10" required min="1" style="padding:10px; width:200px; background:#1e1e1e; border:1px solid #444; color:#fff; border-radius:5px;">
                <small style="display:block; color:#666; margin-top:5px;">How many shares do you want to buy?</small>
            </div>
            <div style="margin-bottom:10px;">
                <label style="display:block; color:#999; margin-bottom:5px;">Purchase Price (₹ per share, NOT total)</label>
                <input type="number" step="0.01" name="price" placeholder="e.g., 250.00" required min="0.01" style="padding:10px; width:200px; background:#1e1e1e; border:1px solid #444; color:#fff; border-radius:5px;">
                <small style="display:block; color:#666; margin-top:5px;">⚠️ Enter price PER SHARE, not total amount</small>
                <small style="display:block; color:#667eea; margin-top:5px;">Example: If 1 share costs ₹250, enter 250 (not 2500)</small>
            </div>
            <div style="background:#1e1e1e; padding:15px; border-radius:8px; margin:15px 0; border-left:3px solid #667eea;">
                <strong style="color:#667eea;">💡 Quick Calculation:</strong>
                <p style="color:#999; font-size:13px; margin-top:5px;">If you buy 10 shares at ₹250 each:</p>
                <p style="color:#fff; margin-top:5px;">Total Investment = 10 × ₹250 = ₹2,500</p>
            </div>
            <button type="submit" class="btn">Add to Portfolio</button>
            <button type="button" class="btn" onclick="document.getElementById('addForm').style.display='none'">Cancel</button>
        </form>
    </div>
    
    <%
        List<PortfolioItem> items = (List<PortfolioItem>) request.getAttribute("portfolioItems");
        if (items != null && !items.isEmpty()) {
    %>
    <table>
        <thead>
            <tr>
                <th>Symbol</th>
                <th>Quantity</th>
                <th>Avg Cost</th>
                <th>Current Price</th>
                <th>Total Value</th>
                <th>Gain/Loss</th>
            </tr>
        </thead>
        <tbody>
            <% for (PortfolioItem item : items) {
                Stock stock = item.getStock();
                double gainLoss = item.getGainLoss();
                String gainClass = gainLoss >= 0 ? "profit" : "loss";
            %>
            <tr>
                <td><strong><%= stock.getSymbol() %></strong></td>
                <td><%= item.getQuantity() %></td>
                <td>₹<%= String.format("%.2f", item.getPurchasePrice()) %></td>
                <td>₹<%= String.format("%.2f", stock.getCurrentPrice()) %></td>
                <td>₹<%= String.format("%.2f", item.getTotalValue()) %></td>
                <td class="<%= gainClass %>">₹<%= String.format("%.2f", gainLoss) %></td>
            </tr>
            <% } %>
        </tbody>
    </table>
    <% } else { %>
    <div style="text-align:center; padding:60px; color:#999;">
        <h2>📭</h2>
        <p>No stocks in your portfolio yet.</p>
        <p>Click "Add Stock" to get started!</p>
    </div>
    <% } %>
    
    <script>
        function showAddForm() {
            document.getElementById('addForm').style.display = 'block';
            document.getElementById('browseStocks').style.display = 'none';
        }
        
        function showBrowseStocks() {
            document.getElementById('browseStocks').style.display = 'block';
            document.getElementById('addForm').style.display = 'none';
        }
        
        function selectStock(symbol, name) {
            document.getElementById('browseStocks').style.display = 'none';
            document.getElementById('addForm').style.display = 'block';
            document.getElementById('symbolInput').value = symbol;
            document.getElementById('symbolInput').focus();
        }
    </script>
</body>
</html>
