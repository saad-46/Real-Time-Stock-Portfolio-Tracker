<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body { 
        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
        background: #0f0f0f; 
        color: #fff; 
        margin: 0;
        padding: 0;
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
        border-right: 1px solid #333;
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
        text-decoration: none;
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
        position: sticky;
        top: 0;
        z-index: 100;
    }
    .burger-menu {
        font-size: 24px;
        cursor: pointer;
        padding: 10px;
        color: #fff;
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

<!-- Sidebar -->
<div class="sidebar" id="sidebar">
    <div class="sidebar-header">
        <div class="sidebar-logo">📈 Portfolio</div>
    </div>
    <ul class="sidebar-menu">
        <li><a href="portfolio?action=dashboard" class="menu-item">
            <span class="menu-icon">🏠</span>
            <span>Dashboard</span>
        </a></li>
        <li><a href="portfolio?action=view" class="menu-item">
            <span class="menu-icon">💼</span>
            <span>My Portfolio</span>
        </a></li>
        <li><a href="portfolio?action=market" class="menu-item">
            <span class="menu-icon">📊</span>
            <span>Market</span>
        </a></li>
        <li><a href="portfolio?action=watchlist" class="menu-item">
            <span class="menu-icon">⭐</span>
            <span>Watchlist</span>
        </a></li>
        <li><a href="portfolio?action=transactions" class="menu-item">
            <span class="menu-icon">📜</span>
            <span>Transactions</span>
        </a></li>
        <li><a href="portfolio?action=analytics" class="menu-item">
            <span class="menu-icon">📈</span>
            <span>Analytics</span>
        </a></li>
        <li><a href="portfolio?action=news" class="menu-item">
            <span class="menu-icon">📰</span>
            <span>Market News</span>
        </a></li>
        <li><a href="portfolio?action=settings" class="menu-item">
            <span class="menu-icon">⚙️</span>
            <span>Settings</span>
        </a></li>
    </ul>
</div>

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
        
        if (confirm('View details for ' + symbol + ' (' + name + ')?')) {
            window.location.href = 'portfolio?action=chart&symbol=' + symbol;
        }
    }
    
    // Close search results when clicking outside
    document.addEventListener('click', function(event) {
        const searchContainer = document.querySelector('.search-container');
        if (searchContainer && !searchContainer.contains(event.target)) {
            document.getElementById('searchResults').classList.remove('show');
        }
    });
</script>
