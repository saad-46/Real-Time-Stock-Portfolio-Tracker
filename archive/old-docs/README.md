# 💼 Stock Portfolio Tracker

A professional Java application for tracking your stock portfolio with real-time prices, beautiful charts, and persistent database storage.

![Java](https://img.shields.io/badge/Java-SE-orange)
![JDBC](https://img.shields.io/badge/JDBC-SQLite-blue)
![Swing](https://img.shields.io/badge/GUI-Swing-green)
![Status](https://img.shields.io/badge/Status-Complete-success)

## 🚀 Quick Start

### Run in 2 Commands:

```bash
# Compile
javac -encoding UTF-8 -cp "lib/*" -d . src/com/portfolio/database/*.java src/com/portfolio/model/*.java src/com/portfolio/service/*.java src/com/portfolio/ui/*.java src/com/portfolio/*.java

# Run
java -cp ".;lib/*" com.portfolio.MainUI
```

## ✨ Features

- 📊 **Real-time Stock Prices** - Fetch live prices from Alpha Vantage API
- 💾 **Database Persistence** - All data automatically saved to SQLite
- 🎨 **Modern UI** - Beautiful dark theme with gradients
- 📈 **Charts** - 4 different chart types for visualization
- ✅ **Stock Validation** - Verify stocks exist before adding
- 💰 **Profit/Loss Tracking** - See your gains and losses
- 📜 **Transaction History** - Complete audit trail
- 🔄 **Auto-save/Load** - Data persists between sessions

## 📸 What You Get

### Main Window
- Stock table with live prices
- Summary cards (investment, value, profit/loss)
- Color-coded gains (green) and losses (red)
- Refresh, Add Stock, and Charts buttons

### Add Stock Dialog
- Stock symbol validation
- Real-time feedback (✓ valid, ✗ invalid)
- Auto-fill current price
- Prevents invalid stocks

### Charts Window
- Portfolio distribution pie chart
- Profit vs loss pie chart
- Stock values bar chart
- Gain/loss bar chart

## 🎓 Syllabus Topics Covered

✅ **JDBC** - Database connectivity with SQLite  
✅ **GUI Programming** - Swing components and layouts  
✅ **Collections Framework** - ArrayList, List interface  
✅ **Exception Handling** - Try-catch blocks  
✅ **Multi-threading** - SwingWorker for background tasks  
✅ **API Integration** - HTTP requests to Alpha Vantage  
✅ **Design Patterns** - DAO, Service Layer, MVC  
✅ **SQL** - CREATE, INSERT, SELECT, UPDATE, DELETE  

## 📁 Project Structure

```
portfolio-tracker/
├── src/com/portfolio/
│   ├── model/              # Data models (Stock, PortfolioItem, Transaction)
│   ├── service/            # Business logic (PortfolioService, API services)
│   ├── database/           # Database layer (DatabaseManager, DAO)
│   ├── ui/                 # User interface (Modern UI, Charts)
│   ├── Main.java           # Console version
│   └── MainUI.java         # GUI launcher
│
├── lib/                    # External libraries
│   ├── jfreechart-1.5.4.jar
│   ├── sqlite-jdbc-3.45.1.0.jar
│   ├── slf4j-api-2.0.9.jar
│   └── slf4j-simple-2.0.9.jar
│
└── portfolio.db            # SQLite database (auto-created)
```

## 📚 Documentation

- **[QUICK-START.md](QUICK-START.md)** - Get started in 2 minutes
- **[UI-README.md](UI-README.md)** - Complete UI guide
- **[DATABASE-README.md](DATABASE-README.md)** - Database integration guide
- **[PROJECT-SUMMARY.md](PROJECT-SUMMARY.md)** - Comprehensive overview

## 🔧 Requirements

- **Java JDK 8+** (tested with JDK 25)
- **Internet connection** (for fetching stock prices)
- **Alpha Vantage API key** (free tier: 25 requests/day)

## 💡 Usage Examples

### Add Your First Stock
1. Click **"+ Add Stock"**
2. Enter symbol: `AAPL`
3. Click **"🔍 Validate"**
4. Enter quantity: `10`
5. Click **"Add Stock"**

### Update Prices
1. Click **"🔄 Refresh"**
2. Wait for API calls
3. See updated prices and profit/loss

### View Charts
1. Click **"📊 Charts"**
2. Explore 4 different visualizations

## 🗄️ Database

### Tables Created Automatically:

**portfolio_items** - Your stock holdings
```sql
- id, symbol, name, quantity
- purchase_price, current_price, created_at
```

**transactions** - Transaction history
```sql
- id, symbol, type (BUY/SELL)
- quantity, price, timestamp
```

### Database File
- **Location**: `portfolio.db` in project root
- **Type**: SQLite (single file)
- **Backup**: Just copy the file!

## 🎨 Code Quality

Every line has detailed comments:
```java
private List<PortfolioItem> portfolioItems;  // List of all stocks you own (ex: [Apple x10, Tesla x5])
```

Comments include:
- What the code does
- Why it's there
- Real examples (AAPL, TSLA, etc.)

## 🔍 Key Classes

### Model Layer
- **Stock.java** - Represents a stock (symbol, name, price)
- **PortfolioItem.java** - Stock + quantity + purchase price
- **Transaction.java** - Buy/sell transaction record

### Service Layer
- **PortfolioService.java** - Portfolio business logic
- **AlphaVantageService.java** - Fetches real stock prices
- **StockValidator.java** - Validates stock symbols

### Database Layer
- **DatabaseManager.java** - Connection management
- **PortfolioDAO.java** - CRUD operations

### UI Layer
- **ModernPortfolioUI.java** - Main window (dark theme)
- **ModernChartWindow.java** - Charts window

## 🚦 How It Works

```
User clicks "Add Stock"
    ↓
ModernPortfolioUI captures input
    ↓
PortfolioService processes business logic
    ↓
PortfolioDAO saves to database
    ↓
SQLite stores in portfolio.db
    ↓
UI refreshes to show new stock
```

## 🎯 Perfect For

- ✅ Academic projects
- ✅ Learning JDBC and databases
- ✅ Understanding GUI programming
- ✅ API integration practice
- ✅ Design pattern examples
- ✅ Portfolio for job applications

## 🐛 Troubleshooting

**Window doesn't open?**
- Check console for errors
- Verify all JARs in `lib` folder

**Can't add stock?**
- Click "Validate" first
- Check internet connection
- Verify API key

**Database errors?**
- Delete `portfolio.db` to start fresh
- Check file permissions

## 📊 Example Output

```
✅ Database connected successfully!
✅ Portfolio table ready
✅ Transactions table ready
✅ Loaded 3 stocks from database
✅ Loaded 3 transactions from database
✅ Portfolio UI launched successfully!
```

## 🌟 Highlights

1. **Professional Quality** - Enterprise-level architecture
2. **Beginner Friendly** - Every line explained
3. **Feature Complete** - All functionality working
4. **Well Documented** - Multiple README files
5. **Persistent Data** - Database integration
6. **Modern Design** - Beautiful dark theme
7. **Real Data** - Live stock prices

## 📝 License

This is an educational project. Feel free to use for learning purposes.

## 🙏 Credits

- **Alpha Vantage** - Stock price API
- **JFreeChart** - Chart library
- **SQLite** - Database engine

## 🎉 You're Ready!

Your portfolio tracker is complete and ready to use. All your data is automatically saved, and you can track your investments like a pro!

**Need help?** Check the documentation files or read the code comments.

---

**Made with ❤️ for learning Java, JDBC, and GUI programming**
