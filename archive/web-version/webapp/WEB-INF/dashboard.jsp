<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.List" %>
<%@ page import="com.portfolio.model.*" %>
<!DOCTYPE html>
<html>
<head>
    <title>Dashboard - Portfolio Tracker</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { 
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: #0f0f0f; 
            color: #fff; 
        }
        
        /* Sidebar Styles */
        .sidebar {
            position: fixed;
            left: 0;
            top: 0;
            width: 250px;
            height: 100vh;
            background: #1a1a1a;
            padding: 20px 0;
            transition: transform 0.3s;
            z-index: 1000;
            overflow-y: auto;
        }
        .sidebar.hidden {
            transform: translateX(-250px);
        }
        .sidebar-header {
            padding: 0 20px 20px 20px;
            border-bottom: 1px solid #333;
        }
        .sidebar-logo {
            font-size: 24px;
            font-weight: 700;
            color: #667eea;
        }
        .sidebar-menu {
            list-style: none;
            padding: 20px 0;
        }
        .menu-item {
            padding: 15px 20px;
            cursor: pointer;
            transition: all 0.3s;
            display: flex;
            align-items: center;
            gap: 15px;
            color: #999;
        }
        .menu-item:hover, .menu-item.active {
            background: #667eea;
            color: #fff;
        }
        .menu-icon {
            font-size: 20px;
            width: 25px;
        }
        
        /* Main Content */
        .main-content {
            margin-left: 250px;
            transition: margin-left 0.3s;
            min-height: 100vh;
        }
        .main-content.expanded {
            margin-left: 0;
        }
        
        /* Top Bar */
        .topbar {
            background: #1a1a1a;
            padding: 20px 30px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 1px solid #333;
        }
        .burger-menu {
            font-size: 24px;
            cursor: pointer;
            padding: 10px;
        }
        .search-container {
            flex: 1;
            max-width: 500px;
            margin: 0 20px;
            position: relative;
        }
        .search-input {
            width: 100%;
            padding: 12px 20px;
            background: #0f0f0f;
            border: 1px solid #333;
            border-radius: 25px;
            color: #fff;
            font-size: 14px;
        }
        .search-input:focus {
            outline: none;
            border-color: #667eea;
        }
        .search-results {
            position: absolute;
            top: 100%;
            left: 0;
            right: 0;
            background: #1a1a1a;
            border: 1px solid #333;
            border-radius: 10px;
            margin-top: 5px;
            max-height: 400px;
            overflow-y: auto;
            display: none;
            z-index: 100;
        }
        .search-results.show {
            display: block;
        }
        .search-result-item {
            padding: 15px 20px;
            cursor: pointer;
            border-bottom: 1px solid #333;
            transition: background 0.2s;
        }
        .search-result-item:hover {
            background: #667eea;
        }
        .search-result-symbol {
            font-weight: 700;
            font-size: 16px;
        }
        .search-result-name {
            font-size: 12px;
            color: #999;
            margin-top: 3px;
        }
        .user-profile {
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .user-avatar {
            width: 40px;
            height: 40px;
            border-radius: 50%;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 18px;
        }
        
        /* Content Area */
        .content {
            padding: 30px;
        }
        .page-title {
            font-size: 32px;
            margin-bottom: 10px;
        }
        .page-subtitle {
            color: #999;
            margin-bottom: 30px;
        }
        
        /* Stats Cards */
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }
        .stat-card {
            background: #1a1a1a;
            padding: 25px;
            border-radius: 15px;
            border: 1px solid #333;
        }
        .stat-label {
            color: #999;
            font-size: 14px;
            margin-bottom: 10px;
        }
        .stat-value {
            font-size: 32px;
            font-weight: 700;
        }
        .stat-change {
            font-size: 14px;
            margin-top: 10px;
        }
        .positive { color: #00c853; }
        .negative { color: #ff3b30; }
        
        /* Portfolio Table */
        .portfolio-section {
            background: #1a1a1a;
            border-radius: 15px;
            padding: 25px;
            border: 1px solid #333;
        }
        .section-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 20px;
        }
        .section-title {
            font-size: 20px;
            font-weight: 600;
        }
        .btn {
            padding: 10px 20px;
            background: #667eea;
            color: white;
            border: none;
            border-radius: 8px;
            cursor: pointer;
            text-decoration: none;
            display: inline-block;
            font-size: 14px;
        }
        .btn:hover {
            background: #764ba2;
        }
        .btn-secondary {
            background: #333;
        }
        .btn-secondary:hover {
            background: #444;
        }
        table {
            width: 100%;
            border-collapse: collapse;
        }
        th, td {
            padding: 15px;
            text-align: left;
            border-bottom: 1px solid #333;
        }
        th {
            color: #999;
            font-weight: 600;
            font-size: 12px;
            text-transform: uppercase;
        }
        td {
            font-size: 14px;
        }
        .stock-symbol {
            font-weight: 700;
            font-size: 16px;
        }
        .empty-state {
            text-align: center;
            padding: 60px 20px;
            color: #999;
        }
        .empty-icon {
            font-size: 64px;
            margin-bottom: 20px;
        }
        
        /* Mobile Responsive */
        @media (max-width: 768px) {
            .sidebar {
                transform: translateX(-250px);
            }
            .sidebar.show {
                transform: translateX(0);
            }
            .main-content {
                margin-left: 0;
            }
        }
    </style>
</head>
<body>
    <!-- Sidebar -->
    <div class="sidebar" id="sidebar">
        <div class="sidebar-header">
            <div class="sidebar-logo">📈 Portfolio</div>
        </div>
        <ul class="sidebar-menu">
            <li class="menu-item active" onclick="navigate('dashboard')">
                <span class="menu-icon">🏠</span>
                <span>Dashboard</span>
            </li>
            <li class="menu-item" onclick="navigate('portfolio')">
                <span class="menu-icon">💼</span>
                <span>My Portfolio</span>
            </li>
            <li class="menu-item" onclick="navigate('market')">
                <span class="menu-icon">📊</span>
                <span>Market</span>
            </li>
            <li class="menu-item" onclick="navigate('watchlist')">
                <span class="menu-icon">⭐</span>
                <span>Watchlist</span>
            </li>
            <li class="menu-item" onclick="navigate('transactions')">
                <span class="menu-icon">📜</span>
                <span>Transactions</span>
            </li>
            <li class="menu-item" onclick="navigate('analytics')">
                <span class="menu-icon">📈</span>
                <span>Analytics</span>
            </li>
            <li class="menu-item" onclick="navigate('news')">
                <span class="menu-icon">📰</span>
                <span>Market News</span>
            </li>
            <li class="menu-item" onclick="navigate('settings')">
                <span class="menu-icon">⚙️</span>
                <span>Settings</span>
            </li>
        </ul>
    </div>
    
    <!-- Main Content -->
    <div class="main-content" id="mainContent">
        <!-- Top Bar -->
        <div class="topbar">
            <div class="burger-menu" onclick="toggleSidebar()">☰</div>
            <div class="search-container">
                <input type="text" class="search-input" id="searchInput" placeholder="🔍 Search stocks (e.g., AAPL, Tesla, Microsoft...)" onkeyup="searchStocks()" onfocus="showSearchResults()" />
                <div class="search-results" id="searchResults"></div>
            </div>
            <div class="user-profile">
                <div class="user-avatar">👤</div>
            </div>
        </div>
        
        <!-- Content -->
        <div class="content">
            <h1 class="page-title">Dashboard</h1>
            <p class="page-subtitle">Welcome back! Here's your portfolio overview</p>
            
            <!-- Stats Grid -->
            <div class="stats-grid">
                <div class="stat-card">
                    <div class="stat-label">💰 Total Investment</div>
                    <div class="stat-value">₹<%= String.format("%.2f", request.getAttribute("totalInvestment") != null ? request.getAttribute("totalInvestment") : 0.0) %></div>
                </div>
                <div class="stat-card">
                    <div class="stat-label">📊 Current Value</div>
                    <div class="stat-value">₹<%= String.format("%.2f", request.getAttribute("currentValue") != null ? request.getAttribute("currentValue") : 0.0) %></div>
                </div>
                <div class="stat-card">
                    <div class="stat-label">📈 Profit/Loss</div>
                    <%
                        double profitLoss = request.getAttribute("profitLoss") != null ? (Double) request.getAttribute("profitLoss") : 0.0;
                        String profitClass = profitLoss >= 0 ? "positive" : "negative";
                        String profitSign = profitLoss >= 0 ? "+" : "";
                    %>
                    <div class="stat-value <%= profitClass %>">₹<%= profitSign %><%= String.format("%.2f", profitLoss) %></div>
                </div>
                <div class="stat-card">
                    <div class="stat-label">📊 Total Stocks</div>
                    <%
                        List<PortfolioItem> items = (List<PortfolioItem>) request.getAttribute("portfolioItems");
                        int stockCount = items != null ? items.size() : 0;
                    %>
                    <div class="stat-value"><%= stockCount %></div>
                </div>
            </div>
            
            <!-- Portfolio Section -->
            <div class="portfolio-section">
                <div class="section-header">
                    <h2 class="section-title">Your Holdings</h2>
                    <div>
                        <button class="btn btn-secondary" onclick="navigate('market')">+ Add Stock</button>
                        <button class="btn" onclick="navigate('refresh')">🔄 Refresh</button>
                    </div>
                </div>
                
                <%
                    if (items != null && !items.isEmpty()) {
                %>
                <table>
                    <thead>
                        <tr>
                            <th>Stock</th>
                            <th>Quantity</th>
                            <th>Avg Cost</th>
                            <th>Current Price</th>
                            <th>Total Value</th>
                            <th>Gain/Loss</th>
                            <th>Action</th>
                        </tr>
                    </thead>
                    <tbody>
                        <% for (PortfolioItem item : items) {
                            Stock stock = item.getStock();
                            double gainLoss = item.getGainLoss();
                            String gainClass = gainLoss >= 0 ? "positive" : "negative";
                            String gainSign = gainLoss >= 0 ? "+" : "";
                        %>
                        <tr>
                            <td><span class="stock-symbol"><%= stock.getSymbol() %></span></td>
                            <td><%= item.getQuantity() %></td>
                            <td>₹<%= String.format("%.2f", item.getPurchasePrice()) %></td>
                            <td>₹<%= String.format("%.2f", stock.getCurrentPrice()) %></td>
                            <td>₹<%= String.format("%.2f", item.getTotalValue()) %></td>
                            <td class="<%= gainClass %>">₹<%= gainSign %><%= String.format("%.2f", gainLoss) %></td>
                            <td><button class="btn btn-secondary" onclick="viewChart('<%= stock.getSymbol() %>')">📊 Chart</button></td>
                        </tr>
                        <% } %>
                    </tbody>
                </table>
                <% } else { %>
                <div class="empty-state">
                    <div class="empty-icon">📭</div>
                    <h3>No stocks in your portfolio yet</h3>
                    <p style="margin: 10px 0 20px 0;">Start building your portfolio by adding your first stock</p>
                    <button class="btn" onclick="navigate('market')">Browse Market</button>
                </div>
                <% } %>
            </div>
        </div>
    </div>
    
    <script>
        // Stock database for autocomplete
        const stockDatabase = [
            { symbol: 'AAPL', name: 'Apple Inc.', category: 'Technology' },
            { symbol: 'GOOGL', name: 'Alphabet Inc. (Google)', category: 'Technology' },
            { symbol: 'MSFT', name: 'Microsoft Corporation', category: 'Technology' },
            { symbol: 'TSLA', name: 'Tesla Inc.', category: 'Automotive' },
            { symbol: 'AMZN', name: 'Amazon.com Inc.', category: 'E-commerce' },
            { symbol: 'META', name: 'Meta Platforms Inc.', category: 'Technology' },
            { symbol: 'NVDA', name: 'NVIDIA Corporation', category: 'Technology' },
            { symbol: 'NFLX', name: 'Netflix Inc.', category: 'Entertainment' },
            { symbol: 'JPM', name: 'JPMorgan Chase & Co.', category: 'Finance' },
            { symbol: 'BAC', name: 'Bank of America Corp.', category: 'Finance' },
            { symbol: 'V', name: 'Visa Inc.', category: 'Finance' },
            { symbol: 'JNJ', name: 'Johnson & Johnson', category: 'Healthcare' },
            { symbol: 'PFE', name: 'Pfizer Inc.', category: 'Healthcare' },
            { symbol: 'XOM', name: 'Exxon Mobil Corporation', category: 'Energy' },
            { symbol: 'CVX', name: 'Chevron Corporation', category: 'Energy' },
            { symbol: 'DIS', name: 'The Walt Disney Company', category: 'Entertainment' },
            { symbol: 'INTC', name: 'Intel Corporation', category: 'Technology' },
            { symbol: 'AMD', name: 'Advanced Micro Devices', category: 'Technology' },
            { symbol: 'ORCL', name: 'Oracle Corporation', category: 'Technology' },
            { symbol: 'IBM', name: 'IBM Corporation', category: 'Technology' }
        ];
        
        function toggleSidebar() {
            document.getElementById('sidebar').classList.toggle('hidden');
            document.getElementById('mainContent').classList.toggle('expanded');
        }
        
        function navigate(page) {
            if (page === 'dashboard') {
                window.location.href = 'portfolio?action=dashboard';
            } else if (page === 'portfolio') {
                window.location.href = 'portfolio?action=view';
            } else if (page === 'market') {
                window.location.href = 'portfolio?action=market';
            } else if (page === 'refresh') {
                window.location.href = 'portfolio?action=refresh';
            } else if (page === 'watchlist') {
                window.location.href = 'portfolio?action=watchlist';
            } else if (page === 'transactions') {
                window.location.href = 'portfolio?action=transactions';
            } else if (page === 'analytics') {
                window.location.href = 'portfolio?action=analytics';
            } else if (page === 'news') {
                window.location.href = 'portfolio?action=news';
            } else if (page === 'settings') {
                window.location.href = 'portfolio?action=settings';
            }
        }
        
        function viewChart(symbol) {
            window.location.href = 'portfolio?action=chart&symbol=' + symbol;
        }
        
        function searchStocks() {
            const query = document.getElementById('searchInput').value.toLowerCase();
            const resultsDiv = document.getElementById('searchResults');
            
            if (query.length === 0) {
                resultsDiv.classList.remove('show');
                return;
            }
            
            const filtered = stockDatabase.filter(stock => 
                stock.symbol.toLowerCase().includes(query) || 
                stock.name.toLowerCase().includes(query)
            );
            
            if (filtered.length === 0) {
                resultsDiv.innerHTML = '<div style="padding:20px; text-align:center; color:#999;">No stocks found</div>';
                resultsDiv.classList.add('show');
                return;
            }
            
            let html = '';
            filtered.slice(0, 10).forEach(stock => {
                html += `
                    <div class="search-result-item" onclick="selectStock('${stock.symbol}', '${stock.name}')">
                        <div class="search-result-symbol">${stock.symbol}</div>
                        <div class="search-result-name">${stock.name} • ${stock.category}</div>
                    </div>
                `;
            });
            
            resultsDiv.innerHTML = html;
            resultsDiv.classList.add('show');
        }
        
        function showSearchResults() {
            const query = document.getElementById('searchInput').value;
            if (query.length > 0) {
                searchStocks();
            }
        }
        
        function selectStock(symbol, name) {
            document.getElementById('searchResults').classList.remove('show');
            document.getElementById('searchInput').value = '';
            
            if (confirm(`View details for ${symbol} (${name})?`)) {
                window.location.href = 'portfolio?action=chart&symbol=' + symbol;
            }
        }
        
        // Close search results when clicking outside
        document.addEventListener('click', function(event) {
            const searchContainer = document.querySelector('.search-container');
            if (!searchContainer.contains(event.target)) {
                document.getElementById('searchResults').classList.remove('show');
            }
        });
    </script>
</body>
</html>
