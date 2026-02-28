<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <title>Stock Market - Browse Stocks</title>
    <style>
        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 20px; border-radius: 10px; margin-bottom: 20px; }
        .header h1 { margin: 0; }
        .search-bar { background: #2a2a2a; padding: 20px; border-radius: 10px; margin-bottom: 20px; }
        .search-bar input { padding: 12px; width: 100%; max-width: 500px; background: #1e1e1e; border: 1px solid #444; color: #fff; border-radius: 8px; font-size: 16px; }
        .stock-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 20px; }
        .stock-item { background: #2a2a2a; padding: 20px; border-radius: 10px; cursor: pointer; transition: all 0.3s; border: 2px solid transparent; }
        .stock-item:hover { border-color: #667eea; transform: translateY(-5px); box-shadow: 0 8px 16px rgba(102, 126, 234, 0.3); }
        .stock-symbol { font-size: 24px; font-weight: bold; color: #667eea; margin-bottom: 5px; }
        .stock-name { color: #999; font-size: 14px; margin-bottom: 10px; }
        .stock-price { font-size: 28px; font-weight: bold; margin: 10px 0; }
        .stock-change { font-size: 14px; padding: 5px 10px; border-radius: 5px; display: inline-block; }
        .positive { background: rgba(0, 200, 83, 0.2); color: #00c853; }
        .negative { background: rgba(255, 59, 48, 0.2); color: #ff3b30; }
        .btn { padding: 10px 20px; background: #667eea; color: white; border: none; border-radius: 8px; cursor: pointer; margin: 5px; text-decoration: none; display: inline-block; }
        .btn:hover { background: #764ba2; }
        .btn-secondary { background: #444; }
        .btn-secondary:hover { background: #555; }
        .category-tabs { display: flex; gap: 10px; margin-bottom: 20px; flex-wrap: wrap; }
        .tab { padding: 10px 20px; background: #2a2a2a; border-radius: 8px; cursor: pointer; transition: all 0.3s; }
        .tab:hover, .tab.active { background: #667eea; }
        .loading { text-align: center; padding: 40px; color: #999; }
    </style>
</head>
<body>
    <%@ include file="/WEB-INF/includes/sidebar.jsp" %>
    
    <div class="main-content" id="mainContent">
        <div style="padding: 30px;">
            <div class="header">
                <h1>📈 Stock Market</h1>
                <p style="margin: 5px 0 0 0; opacity: 0.9;">Browse and search stocks to add to your portfolio</p>
            </div>
    
    <div class="search-bar">
        <input type="text" id="searchInput" placeholder="🔍 Search stocks by symbol or company name..." onkeyup="filterStocks()">
    </div>
    
    <div class="category-tabs">
        <div class="tab active" onclick="filterCategory('all')">All Stocks</div>
        <div class="tab" onclick="filterCategory('tech')">Technology</div>
        <div class="tab" onclick="filterCategory('finance')">Finance</div>
        <div class="tab" onclick="filterCategory('healthcare')">Healthcare</div>
        <div class="tab" onclick="filterCategory('energy')">Energy</div>
    </div>
    
    <div class="stock-grid" id="stockGrid">
        <!-- Technology Stocks -->
        <div class="stock-item" data-category="tech" onclick="addStockFromMarket('AAPL', 'Apple Inc.', 260.58)">
            <div class="stock-symbol">AAPL</div>
            <div class="stock-name">Apple Inc.</div>
            <div class="stock-price">₹260.58</div>
            <div class="stock-change positive">+2.5%</div>
            <p style="color:#999; font-size:12px; margin-top:10px;">iPhone, Mac, Services</p>
            <button class="btn" style="width:100%; margin-top:10px;" onclick="event.stopPropagation(); location.href='portfolio?action=chart&symbol=AAPL'">📊 View Chart</button>
        </div>
        
        <div class="stock-item" data-category="tech" onclick="addStockFromMarket('GOOGL', 'Alphabet Inc.', 142.89)">
            <div class="stock-symbol">GOOGL</div>
            <div class="stock-name">Alphabet Inc. (Google)</div>
            <div class="stock-price">₹142.89</div>
            <div class="stock-change positive">+1.8%</div>
            <p style="color:#999; font-size:12px; margin-top:10px;">Search, Ads, Cloud</p>
            <button class="btn" style="width:100%; margin-top:10px;" onclick="event.stopPropagation(); location.href='portfolio?action=chart&symbol=GOOGL'">📊 View Chart</button>
        </div>
        
        <div class="stock-item" data-category="tech" onclick="addStockFromMarket('MSFT', 'Microsoft Corp.', 425.17)">
            <div class="stock-symbol">MSFT</div>
            <div class="stock-name">Microsoft Corporation</div>
            <div class="stock-price">₹425.17</div>
            <div class="stock-change positive">+3.2%</div>
            <p style="color:#999; font-size:12px; margin-top:10px;">Windows, Azure, Office</p>
            <button class="btn" style="width:100%; margin-top:10px;" onclick="event.stopPropagation(); location.href='portfolio?action=chart&symbol=MSFT'">📊 View Chart</button>
        </div>
        
        <div class="stock-item" data-category="tech" onclick="addStockFromMarket('TSLA', 'Tesla Inc.', 245.67)">
            <div class="stock-symbol">TSLA</div>
            <div class="stock-name">Tesla Inc.</div>
            <div class="stock-price">₹245.67</div>
            <div class="stock-change negative">-1.2%</div>
            <p style="color:#999; font-size:12px; margin-top:10px;">Electric Vehicles, Energy</p>
            <button class="btn" style="width:100%; margin-top:10px;" onclick="event.stopPropagation(); location.href='portfolio?action=chart&symbol=TSLA'">📊 View Chart</button>
        </div>
        
        <div class="stock-item" data-category="tech" onclick="addStockFromMarket('META', 'Meta Platforms', 478.32)">
            <div class="stock-symbol">META</div>
            <div class="stock-name">Meta Platforms Inc.</div>
            <div class="stock-price">₹478.32</div>
            <div class="stock-change positive">+4.1%</div>
            <p style="color:#999; font-size:12px; margin-top:10px;">Facebook, Instagram, WhatsApp</p>
        </div>
        
        <div class="stock-item" data-category="tech" onclick="addStockFromMarket('NVDA', 'NVIDIA Corp.', 875.28)">
            <div class="stock-symbol">NVDA</div>
            <div class="stock-name">NVIDIA Corporation</div>
            <div class="stock-price">₹875.28</div>
            <div class="stock-change positive">+5.7%</div>
            <p style="color:#999; font-size:12px; margin-top:10px;">GPUs, AI Chips</p>
        </div>
        
        <div class="stock-item" data-category="tech" onclick="addStockFromMarket('AMZN', 'Amazon.com Inc.', 178.25)">
            <div class="stock-symbol">AMZN</div>
            <div class="stock-name">Amazon.com Inc.</div>
            <div class="stock-price">₹178.25</div>
            <div class="stock-change positive">+2.9%</div>
            <p style="color:#999; font-size:12px; margin-top:10px;">E-commerce, AWS Cloud</p>
        </div>
        
        <div class="stock-item" data-category="tech" onclick="addStockFromMarket('NFLX', 'Netflix Inc.', 625.50)">
            <div class="stock-symbol">NFLX</div>
            <div class="stock-name">Netflix Inc.</div>
            <div class="stock-price">₹625.50</div>
            <div class="stock-change positive">+1.5%</div>
            <p style="color:#999; font-size:12px; margin-top:10px;">Streaming Entertainment</p>
        </div>
        
        <!-- Finance Stocks -->
        <div class="stock-item" data-category="finance" onclick="addStockFromMarket('JPM', 'JPMorgan Chase', 185.42)">
            <div class="stock-symbol">JPM</div>
            <div class="stock-name">JPMorgan Chase & Co.</div>
            <div class="stock-price">₹185.42</div>
            <div class="stock-change positive">+0.8%</div>
            <p style="color:#999; font-size:12px; margin-top:10px;">Investment Banking</p>
        </div>
        
        <div class="stock-item" data-category="finance" onclick="addStockFromMarket('BAC', 'Bank of America', 34.56)">
            <div class="stock-symbol">BAC</div>
            <div class="stock-name">Bank of America Corp.</div>
            <div class="stock-price">₹34.56</div>
            <div class="stock-change positive">+1.2%</div>
            <p style="color:#999; font-size:12px; margin-top:10px;">Commercial Banking</p>
        </div>
        
        <div class="stock-item" data-category="finance" onclick="addStockFromMarket('V', 'Visa Inc.', 275.89)">
            <div class="stock-symbol">V</div>
            <div class="stock-name">Visa Inc.</div>
            <div class="stock-price">₹275.89</div>
            <div class="stock-change positive">+2.1%</div>
            <p style="color:#999; font-size:12px; margin-top:10px;">Payment Processing</p>
        </div>
        
        <!-- Healthcare Stocks -->
        <div class="stock-item" data-category="healthcare" onclick="addStockFromMarket('JNJ', 'Johnson & Johnson', 158.75)">
            <div class="stock-symbol">JNJ</div>
            <div class="stock-name">Johnson & Johnson</div>
            <div class="stock-price">₹158.75</div>
            <div class="stock-change positive">+0.5%</div>
            <p style="color:#999; font-size:12px; margin-top:10px;">Pharmaceuticals, Medical</p>
        </div>
        
        <div class="stock-item" data-category="healthcare" onclick="addStockFromMarket('PFE', 'Pfizer Inc.', 28.92)">
            <div class="stock-symbol">PFE</div>
            <div class="stock-name">Pfizer Inc.</div>
            <div class="stock-price">₹28.92</div>
            <div class="stock-change negative">-0.3%</div>
            <p style="color:#999; font-size:12px; margin-top:10px;">Pharmaceuticals, Vaccines</p>
        </div>
        
        <!-- Energy Stocks -->
        <div class="stock-item" data-category="energy" onclick="addStockFromMarket('XOM', 'Exxon Mobil', 112.45)">
            <div class="stock-symbol">XOM</div>
            <div class="stock-name">Exxon Mobil Corporation</div>
            <div class="stock-price">₹112.45</div>
            <div class="stock-change positive">+1.9%</div>
            <p style="color:#999; font-size:12px; margin-top:10px;">Oil & Gas</p>
        </div>
        
        <div class="stock-item" data-category="energy" onclick="addStockFromMarket('CVX', 'Chevron Corp.', 158.32)">
            <div class="stock-symbol">CVX</div>
            <div class="stock-name">Chevron Corporation</div>
            <div class="stock-price">₹158.32</div>
            <div class="stock-change positive">+2.3%</div>
            <p style="color:#999; font-size:12px; margin-top:10px;">Energy, Petroleum</p>
        </div>
    </div>
    
    <script>
        let currentCategory = 'all';
        
        function filterStocks() {
            const searchTerm = document.getElementById('searchInput').value.toLowerCase();
            const items = document.querySelectorAll('.stock-item');
            
            items.forEach(item => {
                const symbol = item.querySelector('.stock-symbol').textContent.toLowerCase();
                const name = item.querySelector('.stock-name').textContent.toLowerCase();
                const category = item.getAttribute('data-category');
                
                const matchesSearch = symbol.includes(searchTerm) || name.includes(searchTerm);
                const matchesCategory = currentCategory === 'all' || category === currentCategory;
                
                if (matchesSearch && matchesCategory) {
                    item.style.display = 'block';
                } else {
                    item.style.display = 'none';
                }
            });
        }
        
        function filterCategory(category) {
            currentCategory = category;
            
            // Update active tab
            document.querySelectorAll('.tab').forEach(tab => tab.classList.remove('active'));
            event.target.classList.add('active');
            
            filterStocks();
        }
        
        function addStockFromMarket(symbol, name, price) {
            if (confirm(`Add ${symbol} (${name}) to your portfolio?\n\nCurrent Price: ₹${price}\n\nYou'll be able to enter quantity on the next page.`)) {
                // Redirect to portfolio with pre-filled data
                window.location.href = `portfolio?action=view&addSymbol=${symbol}&addPrice=${price}`;
            }
        }
    </script>
        </div>
    </div>
</body>
</html>
