<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <title>Watchlist - Portfolio Tracker</title>
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
        .watchlist-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 20px; margin-top: 30px; }
        .watchlist-card { background: #1a1a1a; padding: 25px; border-radius: 15px; border: 1px solid #333; transition: all 0.3s; }
        .watchlist-card:hover { border-color: #667eea; transform: translateY(-5px); }
        .stock-symbol { font-size: 24px; font-weight: 700; color: #667eea; margin-bottom: 5px; }
        .stock-name { color: #999; font-size: 14px; margin-bottom: 15px; }
        .stock-price { font-size: 28px; font-weight: 700; margin: 10px 0; }
        .stock-change { font-size: 14px; padding: 5px 10px; border-radius: 5px; display: inline-block; margin-top: 10px; }
        .positive { background: rgba(0, 200, 83, 0.2); color: #00c853; }
        .negative { background: rgba(255, 59, 48, 0.2); color: #ff3b30; }
        .empty-state { text-align: center; padding: 100px 20px; color: #999; }
        .empty-icon { font-size: 80px; margin-bottom: 20px; }
        .action-buttons { display: flex; gap: 10px; margin-top: 15px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1 class="title">⭐ Watchlist</h1>
            <p class="subtitle">Track stocks you're interested in without buying them</p>
        </div>
        
        <div>
            <a href="portfolio?action=dashboard" class="btn btn-secondary">← Back to Dashboard</a>
            <a href="portfolio?action=market" class="btn">+ Add to Watchlist</a>
        </div>
        
        <div class="empty-state">
            <div class="empty-icon">⭐</div>
            <h2>Your Watchlist is Empty</h2>
            <p style="margin: 20px 0;">Add stocks to your watchlist to monitor their prices without investing</p>
            <a href="portfolio?action=market" class="btn">Browse Market</a>
        </div>
        
        <!-- Sample watchlist items (will be dynamic later) -->
        <div class="watchlist-grid" style="display: none;">
            <div class="watchlist-card">
                <div class="stock-symbol">AAPL</div>
                <div class="stock-name">Apple Inc.</div>
                <div class="stock-price">₹260.58</div>
                <div class="stock-change positive">+2.5%</div>
                <div class="action-buttons">
                    <button class="btn" onclick="location.href='portfolio?action=chart&symbol=AAPL'">📊 View Chart</button>
                    <button class="btn btn-secondary">🗑️ Remove</button>
                </div>
            </div>
        </div>
    </div>
</body>
</html>
