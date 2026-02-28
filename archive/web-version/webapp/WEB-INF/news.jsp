<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <title>Market News - Portfolio Tracker</title>
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
        .news-grid { display: grid; gap: 20px; margin-top: 30px; }
        .news-card { background: #1a1a1a; padding: 25px; border-radius: 15px; border: 1px solid #333; transition: all 0.3s; cursor: pointer; }
        .news-card:hover { border-color: #667eea; transform: translateY(-2px); }
        .news-category { display: inline-block; padding: 5px 12px; background: #667eea; border-radius: 5px; font-size: 12px; margin-bottom: 10px; }
        .news-title { font-size: 20px; font-weight: 600; margin-bottom: 10px; }
        .news-excerpt { color: #999; font-size: 14px; line-height: 1.6; margin-bottom: 15px; }
        .news-meta { display: flex; justify-content: space-between; align-items: center; color: #666; font-size: 12px; }
        .coming-soon { text-align: center; padding: 100px 20px; color: #999; }
        .coming-soon-icon { font-size: 80px; margin-bottom: 20px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1 class="title">📰 Market News</h1>
            <p class="subtitle">Stay updated with the latest market trends and stock news</p>
        </div>
        
        <div>
            <a href="portfolio?action=dashboard" class="btn btn-secondary">← Back to Dashboard</a>
            <button class="btn">🔄 Refresh News</button>
        </div>
        
        <div class="coming-soon">
            <div class="coming-soon-icon">📰</div>
            <h2>Market News Coming Soon</h2>
            <p style="margin: 20px 0;">We're integrating real-time market news from trusted sources</p>
            <p style="color: #666;">Features coming soon:</p>
            <ul style="list-style: none; color: #999; line-height: 2; margin-top: 20px;">
                <li>📰 Latest market news and updates</li>
                <li>📊 Stock-specific news for your portfolio</li>
                <li>🔔 Breaking news alerts</li>
                <li>📈 Market analysis and insights</li>
                <li>🌍 Global market updates</li>
            </ul>
            <p style="margin-top: 30px; color: #667eea;">💡 Tip: You can integrate news APIs like NewsAPI, Alpha Vantage News, or Financial Modeling Prep</p>
        </div>
        
        <!-- Sample news items (will be dynamic with API) -->
        <div class="news-grid" style="display: none;">
            <div class="news-card">
                <span class="news-category">Technology</span>
                <h3 class="news-title">Apple Announces New Product Line</h3>
                <p class="news-excerpt">Apple Inc. unveiled its latest innovations today, including new iPhone models and updated MacBook lineup...</p>
                <div class="news-meta">
                    <span>📅 2 hours ago</span>
                    <span>🔗 Read more →</span>
                </div>
            </div>
            
            <div class="news-card">
                <span class="news-category">Market</span>
                <h3 class="news-title">Stock Market Reaches New Highs</h3>
                <p class="news-excerpt">Major indices closed at record levels today as investors showed confidence in economic recovery...</p>
                <div class="news-meta">
                    <span>📅 5 hours ago</span>
                    <span>🔗 Read more →</span>
                </div>
            </div>
        </div>
    </div>
</body>
</html>
