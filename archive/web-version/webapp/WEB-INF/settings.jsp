<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <title>Settings - Portfolio Tracker</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #0f0f0f; color: #fff; }
        .container { max-width: 900px; margin: 0 auto; padding: 40px 20px; }
        .header { margin-bottom: 40px; }
        .title { font-size: 36px; margin-bottom: 10px; }
        .subtitle { color: #999; font-size: 16px; }
        .btn { padding: 12px 24px; background: #667eea; color: white; border: none; border-radius: 8px; cursor: pointer; text-decoration: none; display: inline-block; margin: 5px; }
        .btn:hover { background: #764ba2; }
        .btn-secondary { background: #333; }
        .btn-secondary:hover { background: #444; }
        .settings-section { background: #1a1a1a; padding: 30px; border-radius: 15px; border: 1px solid #333; margin-bottom: 20px; }
        .section-title { font-size: 20px; margin-bottom: 20px; color: #667eea; }
        .setting-item { padding: 20px 0; border-bottom: 1px solid #333; display: flex; justify-content: space-between; align-items: center; }
        .setting-item:last-child { border-bottom: none; }
        .setting-label { flex: 1; }
        .setting-name { font-size: 16px; font-weight: 600; margin-bottom: 5px; }
        .setting-desc { font-size: 14px; color: #999; }
        .toggle-switch { position: relative; width: 60px; height: 30px; background: #333; border-radius: 15px; cursor: pointer; transition: background 0.3s; }
        .toggle-switch.active { background: #667eea; }
        .toggle-slider { position: absolute; top: 3px; left: 3px; width: 24px; height: 24px; background: #fff; border-radius: 50%; transition: transform 0.3s; }
        .toggle-switch.active .toggle-slider { transform: translateX(30px); }
        select, input { padding: 10px; background: #0f0f0f; border: 1px solid #333; border-radius: 8px; color: #fff; font-size: 14px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1 class="title">⚙️ Settings</h1>
            <p class="subtitle">Customize your portfolio tracker experience</p>
        </div>
        
        <div>
            <a href="portfolio?action=dashboard" class="btn btn-secondary">← Back to Dashboard</a>
        </div>
        
        <div class="settings-section" style="margin-top: 30px;">
            <h3 class="section-title">🎨 Appearance</h3>
            
            <div class="setting-item">
                <div class="setting-label">
                    <div class="setting-name">Dark Mode</div>
                    <div class="setting-desc">Use dark theme (currently enabled)</div>
                </div>
                <div class="toggle-switch active" onclick="this.classList.toggle('active')">
                    <div class="toggle-slider"></div>
                </div>
            </div>
            
            <div class="setting-item">
                <div class="setting-label">
                    <div class="setting-name">Currency</div>
                    <div class="setting-desc">Display currency for prices</div>
                </div>
                <select>
                    <option value="INR" selected>₹ Indian Rupee (INR)</option>
                    <option value="USD">$ US Dollar (USD)</option>
                    <option value="EUR">€ Euro (EUR)</option>
                    <option value="GBP">£ British Pound (GBP)</option>
                </select>
            </div>
        </div>
        
        <div class="settings-section">
            <h3 class="section-title">📊 Data & Sync</h3>
            
            <div class="setting-item">
                <div class="setting-label">
                    <div class="setting-name">Auto-Refresh Prices</div>
                    <div class="setting-desc">Automatically update stock prices every 5 minutes</div>
                </div>
                <div class="toggle-switch" onclick="this.classList.toggle('active')">
                    <div class="toggle-slider"></div>
                </div>
            </div>
            
            <div class="setting-item">
                <div class="setting-label">
                    <div class="setting-name">Price Alerts</div>
                    <div class="setting-desc">Get notified when stock prices change significantly</div>
                </div>
                <div class="toggle-switch" onclick="this.classList.toggle('active')">
                    <div class="toggle-slider"></div>
                </div>
            </div>
        </div>
        
        <div class="settings-section">
            <h3 class="section-title">🔔 Notifications</h3>
            
            <div class="setting-item">
                <div class="setting-label">
                    <div class="setting-name">Market News</div>
                    <div class="setting-desc">Receive notifications for important market news</div>
                </div>
                <div class="toggle-switch" onclick="this.classList.toggle('active')">
                    <div class="toggle-slider"></div>
                </div>
            </div>
            
            <div class="setting-item">
                <div class="setting-label">
                    <div class="setting-name">Portfolio Updates</div>
                    <div class="setting-desc">Daily summary of your portfolio performance</div>
                </div>
                <div class="toggle-switch active" onclick="this.classList.toggle('active')">
                    <div class="toggle-slider"></div>
                </div>
            </div>
        </div>
        
        <div class="settings-section">
            <h3 class="section-title">🔐 Account</h3>
            
            <div class="setting-item">
                <div class="setting-label">
                    <div class="setting-name">Email</div>
                    <div class="setting-desc">user@example.com</div>
                </div>
                <button class="btn btn-secondary">Change</button>
            </div>
            
            <div class="setting-item">
                <div class="setting-label">
                    <div class="setting-name">Password</div>
                    <div class="setting-desc">Last changed 30 days ago</div>
                </div>
                <button class="btn btn-secondary">Change</button>
            </div>
        </div>
        
        <div class="settings-section">
            <h3 class="section-title">💾 Data Management</h3>
            
            <div class="setting-item">
                <div class="setting-label">
                    <div class="setting-name">Export Portfolio</div>
                    <div class="setting-desc">Download your portfolio data as CSV</div>
                </div>
                <button class="btn">📥 Export</button>
            </div>
            
            <div class="setting-item">
                <div class="setting-label">
                    <div class="setting-name">Clear All Data</div>
                    <div class="setting-desc">Remove all stocks and transactions (cannot be undone)</div>
                </div>
                <button class="btn" style="background: #ff3b30;" onclick="confirm('Are you sure? This cannot be undone!')">🗑️ Clear Data</button>
            </div>
        </div>
        
        <div style="text-align: center; margin-top: 40px; padding: 30px; background: #1a1a1a; border-radius: 15px; border: 1px solid #333;">
            <p style="color: #999; margin-bottom: 15px;">Portfolio Tracker v1.0</p>
            <p style="color: #666; font-size: 14px;">Built with Java, JSP, and Alpha Vantage API</p>
        </div>
    </div>
</body>
</html>
