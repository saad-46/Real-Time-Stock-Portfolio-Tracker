<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
    String symbol = (String) request.getAttribute("symbol");
    String historicalData = (String) request.getAttribute("historicalData");
    
    // Check if data exists
    boolean hasData = (historicalData != null && historicalData.length() > 0);
%>
<!DOCTYPE html>
<html>
<head>
    <title><%= symbol %> - Chart</title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <style>
        body { font-family: Arial; background: #1a1a1a; color: #fff; padding: 20px; margin: 0; }
        .container { max-width: 1200px; margin: 0 auto; }
        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 20px; border-radius: 10px; margin-bottom: 20px; }
        .btn { padding: 10px 20px; background: #667eea; color: white; border: none; border-radius: 8px; text-decoration: none; display: inline-block; margin: 5px; }
        .chart-box { background: #2a2a2a; padding: 30px; border-radius: 10px; margin: 20px 0; }
        .stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 15px; margin: 20px 0; }
        .stat-card { background: #2a2a2a; padding: 15px; border-radius: 8px; }
        .stat-label { color: #999; font-size: 12px; }
        .stat-value { font-size: 24px; font-weight: bold; margin-top: 5px; }
        .error { background: #ff3b30; padding: 20px; border-radius: 10px; margin: 20px 0; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>📊 <%= symbol %> - 6 Month Chart</h1>
        </div>
        
        <div>
            <a href="portfolio?action=dashboard" class="btn">← Dashboard</a>
            <a href="portfolio?action=market" class="btn">Market</a>
            <button class="btn" onclick="location.reload()">🔄 Refresh</button>
        </div>
        
        <% if (!hasData) { %>
            <div class="error">
                <h3>⚠️ No Data Available</h3>
                <p>Unable to fetch chart data. This could be due to:</p>
                <ul>
                    <li>API rate limit (25 requests/day)</li>
                    <li>Invalid stock symbol</li>
                    <li>Network issues</li>
                </ul>
                <button class="btn" onclick="location.reload()">Try Again</button>
            </div>
        <% } else { %>
            <div class="stats">
                <div class="stat-card">
                    <div class="stat-label">Current Price</div>
                    <div class="stat-value" id="currentPrice">Loading...</div>
                </div>
                <div class="stat-card">
                    <div class="stat-label">6M High</div>
                    <div class="stat-value" id="highPrice">Loading...</div>
                </div>
                <div class="stat-card">
                    <div class="stat-label">6M Low</div>
                    <div class="stat-value" id="lowPrice">Loading...</div>
                </div>
                <div class="stat-card">
                    <div class="stat-label">Change</div>
                    <div class="stat-value" id="priceChange">Loading...</div>
                </div>
            </div>
            
            <div class="chart-box">
                <h3>📈 Price History</h3>
                <canvas id="priceChart"></canvas>
            </div>
            
            <div class="chart-box">
                <h3>🤖 AI Analysis</h3>
                <div id="insights">Analyzing...</div>
            </div>
            
            <script>
                try {
                    const data = <%= historicalData %>;
                    const timeSeries = data["Time Series (Daily)"];
                    
                    if (!timeSeries) {
                        throw new Error("No time series data");
                    }
                    
                    const dates = Object.keys(timeSeries).slice(0, 180).reverse();
                    const prices = dates.map(d => parseFloat(timeSeries[d]["4. close"]));
                    
                    const current = prices[prices.length - 1];
                    const high = Math.max(...prices);
                    const low = Math.min(...prices);
                    const change = ((current - prices[0]) / prices[0] * 100).toFixed(2);
                    
                    document.getElementById('currentPrice').textContent = '₹' + current.toFixed(2);
                    document.getElementById('highPrice').textContent = '₹' + high.toFixed(2);
                    document.getElementById('lowPrice').textContent = '₹' + low.toFixed(2);
                    document.getElementById('priceChange').textContent = change + '%';
                    document.getElementById('priceChange').style.color = change >= 0 ? '#00c853' : '#ff3b30';
                    
                    new Chart(document.getElementById('priceChart'), {
                        type: 'line',
                        data: {
                            labels: dates,
                            datasets: [{
                                label: 'Price (₹)',
                                data: prices,
                                borderColor: '#667eea',
                                backgroundColor: 'rgba(102, 126, 234, 0.1)',
                                borderWidth: 2,
                                fill: true,
                                tension: 0.4
                            }]
                        },
                        options: {
                            responsive: true,
                            plugins: { legend: { labels: { color: '#fff' } } },
                            scales: {
                                x: { ticks: { color: '#999', maxTicksLimit: 12 }, grid: { color: '#333' } },
                                y: { ticks: { color: '#999' }, grid: { color: '#333' } }
                            }
                        }
                    });
                    
                    // Simple insights
                    let insights = '<ul style="line-height:2;">';
                    if (change > 10) insights += '<li>📈 Strong growth: +' + change + '% in 6 months</li>';
                    else if (change > 0) insights += '<li>➡️ Modest gain: +' + change + '% in 6 months</li>';
                    else insights += '<li>📉 Decline: ' + change + '% in 6 months</li>';
                    
                    const pos = ((current - low) / (high - low) * 100).toFixed(0);
                    if (pos > 90) insights += '<li>⚠️ Near 6-month high (' + pos + '%)</li>';
                    else if (pos < 10) insights += '<li>💡 Near 6-month low (' + pos + '%)</li>';
                    else insights += '<li>📊 Mid-range position (' + pos + '%)</li>';
                    
                    insights += '<li>💼 Tip: Always diversify your portfolio</li>';
                    insights += '</ul>';
                    document.getElementById('insights').innerHTML = insights;
                    
                } catch (e) {
                    document.querySelector('.chart-box').innerHTML = '<div class="error"><h3>Error: ' + e.message + '</h3><p>Unable to display chart</p></div>';
                }
            </script>
        <% } %>
    </div>
</body>
</html>
