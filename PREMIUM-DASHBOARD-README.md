# 🚀 StockVault Premium Dashboard

## 100% Pure Java - NO HTML/CSS/JavaScript

This is your **premium stock portfolio tracker** built entirely in Java Swing. No web technologies - just pure Java desktop application.

---

## ✨ Features

### 📊 Dashboard Page
- **Real-time stats cards**: Total Value, Investment, Profit/Loss, Return %
- **Recent stocks table**: Quick view of your top 5 holdings
- **All in Indian Rupees (₹)**

### 💼 My Portfolio Page
- **Complete holdings table**: All your stocks with detailed info
- **Add Stock button**: Easy dialog to add new stocks
- **Refresh Prices button**: Updates all stock prices from Alpha Vantage API
- **Shows**: Symbol, Name, Quantity, Buy Price, Current Price, Total Value, Gain/Loss, Return %

### 🌐 Market Page
- **Stock cards grid**: Browse popular stocks
- **Mini charts**: Visual price trends for each stock
- **View Chart button**: Opens detailed 30-day price history chart
- **Real-time price changes**: Green for gains, Red for losses

### ⭐ Watchlist Page
- Track your favorite stocks (coming soon)

### 📜 Transactions Page
- **Complete transaction history**: Every buy/sell you've made
- **Shows**: Date, Type (BUY/SELL), Symbol, Quantity, Price, Total

### 📈 Analytics Page
- **4 Professional Charts**:
  1. Portfolio Distribution (Pie Chart)
  2. Profit vs Loss (Pie Chart)
  3. Stock Values (Bar Chart)
  4. Gain/Loss by Stock (Bar Chart)
- **Powered by JFreeChart**

### ⚙️ Settings Page
- Currency, Theme, Auto-refresh, Notifications

---

## 🎨 Design

- **Dark Theme**: Modern dark purple/blue theme
- **Sidebar Navigation**: Always visible menu on the left
- **Search Bar**: Autocomplete for stock symbols
- **Smooth Animations**: Hover effects, color transitions
- **Professional Layout**: Cards, tables, charts

---

## 🏃 How to Run

### Option 1: Double-click the batch file
```
RUN-PREMIUM-DASHBOARD.bat
```

### Option 2: Command line
```cmd
java -cp ".;lib/*" com.portfolio.ui.PremiumStockDashboard
```

---

## 📝 How to Use

### 1. View Dashboard
- Opens automatically when you start
- See your portfolio summary at a glance

### 2. Add Stocks
- Click "My Portfolio" in sidebar
- Click "+ Add Stock" button
- Enter: Symbol (e.g., AAPL), Quantity, Purchase Price
- Click "Add Stock"

### 3. Refresh Prices
- Click "My Portfolio" in sidebar
- Click "↻ Refresh Prices" button
- Wait for API to fetch latest prices
- All prices update automatically

### 4. Browse Market
- Click "Market" in sidebar
- See popular stocks with mini charts
- Click "View Chart" on any stock to see 30-day history

### 5. View Analytics
- Click "Analytics" in sidebar
- See 4 professional charts analyzing your portfolio

### 6. Check Transactions
- Click "Transactions" in sidebar
- See complete history of all your trades

---

## 🔧 Technical Details

### Architecture
- **100% Java Swing** - No HTML/CSS/JS
- **MVC Pattern**: Model, Service, UI layers
- **SQLite Database**: Persistent storage (portfolio.db)
- **Alpha Vantage API**: Real-time stock prices
- **JFreeChart**: Professional charting library

### Files
- `src/com/portfolio/ui/PremiumStockDashboard.java` - Main UI (1000+ lines)
- `src/com/portfolio/service/PortfolioService.java` - Business logic
- `src/com/portfolio/service/AlphaVantageService.java` - API integration
- `src/com/portfolio/database/PortfolioDAO.java` - Database operations
- `src/com/portfolio/model/*.java` - Data models

### Libraries Used
- `jfreechart-1.5.4.jar` - Charts
- `sqlite-jdbc-3.45.1.0.jar` - Database
- `json-20231013.jar` - JSON parsing
- `slf4j-*.jar` - Logging

---

## 🎯 Key Differences from Web Version

| Feature | Web Version | Premium Dashboard |
|---------|-------------|-------------------|
| Technology | HTML/CSS/JS | 100% Java Swing |
| Runs in | Web Browser | Desktop Window |
| Sidebar | ✅ | ✅ |
| Dark Theme | ✅ | ✅ |
| Charts | ✅ | ✅ (JFreeChart) |
| Search | ✅ | ✅ (Autocomplete) |
| Add Stocks | ✅ | ✅ (Dialog) |
| Market Page | ✅ | ✅ (Grid Cards) |
| Transactions | ✅ | ✅ (Table) |
| Analytics | ✅ | ✅ (4 Charts) |

---

## 🚀 What's Better Than Web Version

1. **No Browser Required** - Runs as native desktop app
2. **Faster Performance** - No HTTP overhead
3. **Better Integration** - Direct database access
4. **More Reliable** - No network issues for UI
5. **Professional Look** - Native Java Swing components
6. **Offline Capable** - UI works without internet (except price updates)

---

## 📸 Screenshots

### Dashboard
- Stats cards showing Total Value, Investment, Profit/Loss, Return %
- Recent stocks table

### My Portfolio
- Complete holdings table
- Add Stock and Refresh Prices buttons

### Market
- 3x3 grid of stock cards
- Each card shows: Symbol, Name, Price, Change %, Mini Chart, View Chart button

### Analytics
- 2x2 grid of professional charts
- Portfolio Distribution, Profit vs Loss, Stock Values, Gain/Loss

---

## 🎓 For Your Project

This application is **100% compliant** with your project requirements:

✅ **Pure Java** - No HTML, CSS, or JavaScript  
✅ **Desktop Application** - Runs in window, not browser  
✅ **Modern UI** - Matches quality of web version  
✅ **Sidebar Navigation** - Dashboard, Portfolio, Market, etc.  
✅ **Search Bar** - Autocomplete for stocks  
✅ **Charts** - Professional JFreeChart integration  
✅ **Database** - SQLite persistence  
✅ **API Integration** - Real-time stock prices  
✅ **Indian Currency** - All prices in ₹ (Rupees)  

---

## 🐛 Troubleshooting

### Application won't start
- Make sure all `.class` files are compiled
- Check that `lib/` folder has all JAR files
- Run: `javac -encoding UTF-8 -cp "lib/*" -d . src/com/portfolio/model/*.java src/com/portfolio/service/*.java src/com/portfolio/database/*.java src/com/portfolio/ui/PremiumStockDashboard.java`

### Prices not updating
- Check your internet connection
- Alpha Vantage API has rate limits (5 calls/minute, 500 calls/day)
- Wait a minute and try again

### Database errors
- Delete `portfolio.db` file to start fresh
- Application will create new database automatically

---

## 🎉 Enjoy Your Premium Dashboard!

This is a **professional-grade** stock portfolio tracker built entirely in Java. No compromises, no web technologies - just pure Java Swing at its best.

**Happy Trading! 📈**
