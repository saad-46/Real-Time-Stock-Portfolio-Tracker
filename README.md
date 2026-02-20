<<<<<<< HEAD
# 💼 Real-Time Stock Portfolio Tracker

A professional Java desktop application for tracking stock portfolios with real-time prices, database persistence, and modern UI.

![Java](https://img.shields.io/badge/Java-SE-orange)
![JDBC](https://img.shields.io/badge/JDBC-SQLite-blue)
![Swing](https://img.shields.io/badge/GUI-Swing-green)

## 🚀 Quick Start

```bash
java -cp ".;lib/*" com.portfolio.MainUI
```

## ✨ Features

- 📊 **Real-time Stock Prices** - Fetch live prices from Alpha Vantage API
- 💾 **Database Persistence** - All data automatically saved to SQLite
- 🎨 **Modern Dark Theme UI** - Beautiful, professional interface
- 📈 **Interactive Charts** - 4 different chart types for visualization
- ✅ **Stock Validation** - Verify stocks exist before adding
- 💰 **Profit/Loss Tracking** - See your gains and losses in real-time
- 📜 **Transaction History** - Complete audit trail of all trades
- 🔄 **Auto-save/Load** - Data persists between sessions

## 📸 Screenshots

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

## 🎓 Technologies Used

- **Java SE** - Core programming language
- **Swing** - GUI framework
- **JDBC** - Database connectivity
- **SQLite** - Lightweight embedded database
- **JFreeChart** - Chart visualization library
- **Alpha Vantage API** - Real-time stock price data
- **SLF4J** - Logging framework

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
├── docs/                   # Documentation
└── portfolio.db            # SQLite database (auto-created)
```

## 🔧 Requirements

- **Java JDK 8+** (tested with JDK 25)
- **Internet connection** (for fetching stock prices)
- **Alpha Vantage API key** (free tier: 25 requests/day)

## 💡 Usage

### First Time Setup
1. Clone the repository
2. Navigate to project directory
3. Run: `java -cp ".;lib/*" com.portfolio.MainUI`

### Add Your First Stock
1. Click **"+ Add Stock"**
2. Enter symbol: `AAPL`
3. Click **"🔍 Validate"**
4. Enter quantity: `10`
5. Price auto-fills from validation
6. Click **"Add Stock"**

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

## 🎯 Key Classes

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

## 🎓 Learning Outcomes

This project demonstrates:
- ✅ Object-Oriented Programming
- ✅ JDBC & Database Programming
- ✅ GUI Development with Swing
- ✅ API Integration
- ✅ Multi-threading (SwingWorker)
- ✅ Exception Handling
- ✅ Collections Framework
- ✅ Design Patterns (DAO, Service Layer, MVC)

## 📚 Documentation

- **[START-HERE.md](START-HERE.md)** - Quick start guide
- **[PROJECT-STRUCTURE.md](PROJECT-STRUCTURE.md)** - Code organization
- **[docs/DATABASE-README.md](docs/DATABASE-README.md)** - Database guide
- **[docs/UI-README.md](docs/UI-README.md)** - UI guide
- **[docs/PROJECT-SUMMARY.md](docs/PROJECT-SUMMARY.md)** - Complete overview

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

## 📝 License

Educational project for learning Java, JDBC, and GUI programming.

## 🙏 Credits

- **Alpha Vantage** - Stock price API
- **JFreeChart** - Chart library
- **SQLite** - Database engine

## 🌟 Features Showcase

- Professional-quality code with comprehensive comments
- Enterprise-level architecture (MVC, DAO patterns)
- Real-world API integration
- Modern, beautiful UI design
- Complete database persistence
- Production-ready application

---

**Made with ❤️ for learning enterprise Java development**

## 🚀 Get Started Now!

```bash
git clone https://github.com/saad-46/Real-Time-Stock-Portfolio-Tracker.git
cd Real-Time-Stock-Portfolio-Tracker
java -cp ".;lib/*" com.portfolio.MainUI
```

Enjoy tracking your portfolio! 💼📊
=======
# Real-Time-Stock-Portfolio-Tracker
IRP JAVA 
>>>>>>> f929ab51bad68ce0c8fba448bf931bf3543dafe6
