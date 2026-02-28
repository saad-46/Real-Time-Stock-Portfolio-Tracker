# 📊 StockVault - Portfolio Tracker

**100% Pure Java Desktop Application**  
A professional stock portfolio management system with real-time price updates, charts, and analytics.

---

## 🚀 Quick Start

### Run the Application
```cmd
RUN-PREMIUM-DASHBOARD.bat
```

Or manually:
```cmd
java -cp ".;lib/*" com.portfolio.ui.PremiumStockDashboard
```

---

## ✨ Features

### 📱 Pages
- **Dashboard** - Portfolio overview with stats cards
- **My Portfolio** - Manage your holdings, add stocks, refresh prices
- **Market** - Browse popular stocks with charts
- **Watchlist** - Track favorite stocks
- **Transactions** - Complete trade history
- **Analytics** - 4 professional charts (JFreeChart)
- **Settings** - Application preferences

### 🎨 UI Features
- Dark theme with purple accents
- Sidebar navigation (always visible)
- Search bar with autocomplete
- Responsive layout
- Professional charts and tables

### 💰 Data Features
- Real-time stock prices (Alpha Vantage API)
- SQLite database persistence
- Indian Rupee (₹) currency
- Automatic price updates
- Transaction history tracking

---

## 📁 Project Structure

```
├── src/com/portfolio/          # Source code
│   ├── model/                  # Data models (Stock, PortfolioItem, Transaction)
│   ├── service/                # Business logic (PortfolioService, AlphaVantageService)
│   ├── database/               # Database operations (PortfolioDAO, DatabaseManager)
│   └── ui/                     # User interface
│       ├── PremiumStockDashboard.java  # Main application (CURRENT)
│       └── PremiumPortfolioUI.java     # Alternative UI
│
├── lib/                        # External libraries
│   ├── jfreechart-1.5.4.jar   # Charts
│   ├── sqlite-jdbc-3.45.1.0.jar # Database
│   ├── json-20231013.jar      # JSON parsing
│   └── slf4j-*.jar            # Logging
│
├── com/                        # Compiled classes
├── portfolio.db                # SQLite database
├── RUN-PREMIUM-DASHBOARD.bat   # Run script
├── PREMIUM-DASHBOARD-README.md # Detailed documentation
├── WHATS-NEW-PREMIUM.md        # Feature comparison
└── archive/                    # Old/unused files
    ├── old-ui/                 # Previous UI versions
    ├── web-version/            # Web app (not used)
    └── old-docs/               # Old documentation
```

---

## 🛠️ Technical Stack

- **Language**: Java (100% - No HTML/CSS/JS)
- **UI Framework**: Java Swing
- **Database**: SQLite (JDBC)
- **Charts**: JFreeChart 1.5.4
- **API**: Alpha Vantage (Stock prices)
- **Architecture**: MVC Pattern

---

## 📖 How to Use

### 1. Add Stocks
1. Click "My Portfolio" in sidebar
2. Click "+ Add Stock" button
3. Enter symbol (e.g., AAPL), quantity, and purchase price
4. Click "Add Stock"

### 2. Refresh Prices
1. Go to "My Portfolio"
2. Click "↻ Refresh Prices"
3. Wait for API to fetch latest prices

### 3. View Charts
1. Go to "Market" page
2. Click "View Chart" on any stock card
3. See 30-day price history

### 4. Check Analytics
1. Click "Analytics" in sidebar
2. View 4 professional charts:
   - Portfolio Distribution
   - Profit vs Loss
   - Stock Values
   - Gain/Loss by Stock

---

## 🔧 Compilation

If you need to recompile:

```cmd
javac -encoding UTF-8 -cp "lib/*" -d . src/com/portfolio/model/*.java src/com/portfolio/service/*.java src/com/portfolio/database/*.java src/com/portfolio/ui/PremiumStockDashboard.java
```

---

## 📊 API Information

**Alpha Vantage API**
- Free tier: 5 calls/minute, 500 calls/day
- API Key: Included in code
- Used for: Real-time stock prices and historical data

---

## 💾 Database

**SQLite Database** (`portfolio.db`)
- Stores: Portfolio items, transactions, stock prices
- Auto-created on first run
- Persistent across sessions
- To reset: Delete `portfolio.db` file

---

## 🎓 Project Requirements

✅ **100% Pure Java** - No HTML, CSS, or JavaScript  
✅ **Desktop Application** - Runs in window, not browser  
✅ **Modern UI** - Professional dark theme with sidebar  
✅ **Real-time Data** - API integration for stock prices  
✅ **Database** - SQLite persistence  
✅ **Charts** - JFreeChart integration  
✅ **Indian Currency** - All prices in ₹ (Rupees)  

---

## 📚 Documentation

- **PREMIUM-DASHBOARD-README.md** - Complete feature documentation
- **WHATS-NEW-PREMIUM.md** - Comparison with previous versions
- **PROJECT-STRUCTURE.md** - Detailed project structure
- **INDEX.md** - Quick reference guide

---

## 🐛 Troubleshooting

### Application won't start
- Ensure all `.class` files are compiled
- Check `lib/` folder has all JAR files
- Recompile using command above

### Prices not updating
- Check internet connection
- API has rate limits (wait 1 minute between refreshes)
- Verify API key is valid

### Database errors
- Delete `portfolio.db` to start fresh
- Application will recreate database automatically

---

## 📝 Notes

- **Currency**: All prices displayed in Indian Rupees (₹)
- **Theme**: Dark mode with purple accents (#667eea)
- **Fonts**: Segoe UI (Windows), fallback to SansSerif
- **Resolution**: Optimized for 1280x800 and above

---

## 🎉 Version

**v2.1.0** - Premium Stock Dashboard  
Pure Java Desktop Application

---

## 📧 Support

For issues or questions, refer to:
- PREMIUM-DASHBOARD-README.md (detailed guide)
- WHATS-NEW-PREMIUM.md (feature list)
- Source code comments (detailed explanations)

---

**Happy Trading! 📈**
