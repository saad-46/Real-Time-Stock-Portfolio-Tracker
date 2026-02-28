<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <title>Analytics - Portfolio Tracker</title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #0f0f0f; color: #fff; }
        .container { max-width: 1400px; margin: 0 auto; padding: 40px 20px; }
        .header { margin-bottom: 40px; }
        .title { font-size: 36px; margin-bottom: 10px; }
        .subtitle { color: #999; font-size: 16px; }
        .btn { padding: 12px 24px; background: #667eea; color: white; border: none; border-radius: 8px; cursor: pointer; text-decoration: none; display: inline-block; margin: 5px; }
        .btn:hover { background: #764ba2; }
        .btn-secondary { background: #333; }
        .btn-secondary:hover { background: #444; }
        .charts-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(500px, 1fr)); gap: 30px; margin-top: 30px; }
        .chart-card { background: #1a1a1a; padding: 30px; border-radius: 15px; border: 1px solid #333; }
        .chart-title { font-size: 20px; margin-bottom: 20px; color: #667eea; }
        .stats-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; margin-bottom: 30px; }
        .stat-card { background: #1a1a1a; padding: 20px; border-radius: 10px; border: 1px solid #333; }
        .stat-label { color: #999; font-size: 12px; margin-bottom: 8px; }
        .stat-value { font-size: 28px; font-weight: 700; }
        .coming-soon { text-align: center; padding: 60px 20px; background: #1a1a1a; border-radius: 15px; border: 1px solid #333; }
        .coming-soon-icon { font-size: 64px; margin-bottom: 20px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1 class="title">📈 Analytics</h1>
            <p class="subtitle">Detailed performance analysis of your portfolio</p>
        </div>
        
        <div>
            <a href="portfolio?action=dashboard" class="btn btn-secondary">← Back to Dashboard</a>
        </div>
        
        <div class="stats-row" style="margin-top: 30px;">
            <div class="stat-card">
                <div class="stat-label">📊 Total Return</div>
                <div class="stat-value" style="color: #00c853;">+0.00%</div>
            </div>
            <div class="stat-card">
                <div class="stat-label">📅 Best Day</div>
                <div class="stat-value" style="font-size: 20px;">Coming Soon</div>
            </div>
            <div class="stat-card">
                <div class="stat-label">📉 Worst Day</div>
                <div class="stat-value" style="font-size: 20px;">Coming Soon</div>
            </div>
            <div class="stat-card">
                <div class="stat-label">⏱️ Holding Period</div>
                <div class="stat-value" style="font-size: 20px;">0 days</div>
            </div>
        </div>
        
        <div class="charts-grid">
            <div class="chart-card">
                <h3 class="chart-title">📊 Portfolio Performance</h3>
                <canvas id="performanceChart"></canvas>
            </div>
            
            <div class="chart-card">
                <h3 class="chart-title">🥧 Asset Allocation</h3>
                <canvas id="allocationChart"></canvas>
            </div>
        </div>
        
        <div class="coming-soon" style="margin-top: 30px;">
            <div class="coming-soon-icon">📈</div>
            <h2>Advanced Analytics Coming Soon</h2>
            <p style="color: #999; margin: 20px 0;">We're working on bringing you detailed analytics including:</p>
            <ul style="list-style: none; color: #999; line-height: 2;">
                <li>📊 Portfolio performance over time</li>
                <li>🥧 Sector allocation breakdown</li>
                <li>📈 Risk analysis and metrics</li>
                <li>💰 Dividend tracking</li>
                <li>📉 Drawdown analysis</li>
            </ul>
        </div>
    </div>
    
    <script>
        // Sample Performance Chart
        const ctx1 = document.getElementById('performanceChart').getContext('2d');
        new Chart(ctx1, {
            type: 'line',
            data: {
                labels: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun'],
                datasets: [{
                    label: 'Portfolio Value (₹)',
                    data: [0, 0, 0, 0, 0, 0],
                    borderColor: '#667eea',
                    backgroundColor: 'rgba(102, 126, 234, 0.1)',
                    borderWidth: 2,
                    fill: true,
                    tension: 0.4
                }]
            },
            options: {
                responsive: true,
                plugins: {
                    legend: { labels: { color: '#fff' } }
                },
                scales: {
                    x: { ticks: { color: '#999' }, grid: { color: '#333' } },
                    y: { ticks: { color: '#999' }, grid: { color: '#333' } }
                }
            }
        });
        
        // Sample Allocation Chart
        const ctx2 = document.getElementById('allocationChart').getContext('2d');
        new Chart(ctx2, {
            type: 'doughnut',
            data: {
                labels: ['Technology', 'Finance', 'Healthcare', 'Energy', 'Other'],
                datasets: [{
                    data: [0, 0, 0, 0, 0],
                    backgroundColor: ['#667eea', '#764ba2', '#f093fb', '#4facfe', '#43e97b']
                }]
            },
            options: {
                responsive: true,
                plugins: {
                    legend: { labels: { color: '#fff' } }
                }
            }
        });
    </script>
</body>
</html>
