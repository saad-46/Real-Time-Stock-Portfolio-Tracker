# 📁 Project Structure

## Current Active Files

### 🎯 Main Application
```
RUN-PREMIUM-DASHBOARD.bat          # Run script for the application
```

### 💻 Source Code (`src/com/portfolio/`)

#### Models (`model/`)
```
Stock.java                         # Stock data model (symbol, name, price)
PortfolioItem.java                 # Portfolio holding (stock + quantity + purchase price)
Transaction.java                   # Transaction record (buy/sell history)
```

#### Services (`service/`)
```
StockPriceService.java             # Interface for stock price services
AlphaVantageService.java           # Alpha Vantage API implementation
PortfolioService.java              # Portfolio business logic
StockValidator.java                # Stock symbol validation
StockSearchService.java            # Stock search functionality
```

#### Database (`database/`)
```
DatabaseManager.java               # SQLite connection manager
PortfolioDAO.java                  # Data access object (CRUD operations)
```

#### User Interface (`ui/`)
```
PremiumStockDashboard.java         # ⭐ MAIN APPLICATION (Current)
PremiumPortfolioUI.java            # Alternative UI (if exists)
```

#### Entry Points (`root/`)
```
Main.java                          # Console-based entry point
MainUI.java                        # GUI entry point (if exists)
```

### 📚 Libraries (`lib/`)
```
jfreechart-1.5.4.jar              # Professional charting library
sqlite-jdbc-3.45.1.0.jar          # SQLite database driver
json-20231013.jar                 # JSON parsing
slf4j-api-2.0.9.jar               # Logging API
slf4j-simple-2.0.9.jar            # Simple logging implementation
jakarta.servlet-api-6.0.0.jar     # Servlet API (not used in desktop app)
```

### 💾 Data
```
portfolio.db                       # SQLite database (auto-created)
```

### 📖 Documentation
```
README.md                          # Main project documentation
PREMIUM-DASHBOARD-README.md        # Detailed feature guide
WHATS-NEW-PREMIUM.md              # Feature comparison
PROJECT-STRUCTURE.md              # This file
INDEX.md                          # Quick reference
START-HERE.md                     # Getting started guide
```

### 🗂️ Compiled Classes (`com/portfolio/`)
```
com/portfolio/model/*.class        # Compiled model classes
com/portfolio/service/*.class      # Compiled service classes
com/portfolio/database/*.class     # Compiled database classes
com/portfolio/ui/*.class          # Compiled UI classes
```

### 📦 Archive (`archive/`)
```
archive/old-ui/                    # Previous UI versions
  ├── PortfolioUI.java            # Original basic UI
  ├── ChartWindow.java            # Original chart window
  ├── EnhancedPortfolioUI.java    # Enhanced version
  ├── ModernPortfolioUI.java      # Modern version (before Premium)
  ├── ModernChartWindow.java      # Modern chart window
  └── StockDashboard.java         # Incomplete dashboard

archive/web-version/               # Web application (not used)
  ├── webapp/                     # Web files (HTML/JSP)
  └── servlet/                    # Servlet classes

archive/old-docs/                  # Old documentation
  ├── CHART-FIX.md
  ├── DEPLOY-*.bat/md/ps1
  ├── FINAL-*.md
  ├── FIXES-APPLIED.md
  └── Various deployment guides
```

---

## 🏗️ Architecture

### MVC Pattern

```
┌─────────────────────────────────────────────────┐
│                    VIEW (UI)                    │
│  PremiumStockDashboard.java                     │
│  - Sidebar navigation                           │
│  - Dashboard, Portfolio, Market pages           │
│  - Charts, tables, forms                        │
└─────────────────┬───────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────┐
│              CONTROLLER (Service)               │
│  PortfolioService.java                          │
│  - Business logic                               │
│  - Coordinates between UI and data              │
│  - Manages portfolio operations                 │
└─────────────────┬───────────────────────────────┘
                  │
        ┌─────────┴─────────┐
        ▼                   ▼
┌──────────────┐    ┌──────────────┐
│    MODEL     │    │   DATABASE   │
│              │    │              │
│ Stock.java   │    │ PortfolioDAO │
│ Portfolio    │◄───┤ Database     │
│ Item.java    │    │ Manager      │
│ Transaction  │    │              │
│ .java        │    │ SQLite       │
└──────────────┘    └──────────────┘
        ▲
        │
        ▼
┌──────────────────────┐
│   EXTERNAL API       │
│ AlphaVantageService  │
│ - Stock prices       │
│ - Historical data    │
└──────────────────────┘
```

---

## 🔄 Data Flow

### Adding a Stock
```
User clicks "+ Add Stock"
    ↓
PremiumStockDashboard shows dialog
    ↓
User enters: Symbol, Quantity, Price
    ↓
portfolioService.buyStock(symbol, quantity, price)
    ↓
PortfolioDAO.savePortfolioItem(item)
    ↓
SQLite database (portfolio.db)
    ↓
UI refreshes to show new stock
```

### Refreshing Prices
```
User clicks "↻ Refresh Prices"
    ↓
portfolioService.updateAllPrices()
    ↓
For each stock:
    AlphaVantageService.getCurrentPrice(symbol)
        ↓
    HTTP request to Alpha Vantage API
        ↓
    Parse JSON response
        ↓
    Update stock.currentPrice
        ↓
    PortfolioDAO.updateStockPrice(symbol, price)
        ↓
    SQLite database
    ↓
UI refreshes with new prices
```

---

## 📊 Class Relationships

```
PremiumStockDashboard
    ├── uses → PortfolioService
    │           ├── uses → PortfolioDAO
    │           │           └── uses → DatabaseManager
    │           ├── uses → AlphaVantageService
    │           └── manages → List<PortfolioItem>
    │                           └── contains → Stock
    │
    ├── displays → JTable (portfolio table)
    ├── displays → ChartPanel (JFreeChart)
    └── displays → JDialog (add stock form)
```

---

## 🎯 Key Components

### PremiumStockDashboard (Main UI)
- **Lines**: ~1000+
- **Purpose**: Main application window
- **Features**:
  - Sidebar navigation (7 pages)
  - Dashboard with stats cards
  - Portfolio management
  - Market browser with charts
  - Transaction history
  - Analytics with 4 charts
  - Settings page

### PortfolioService (Business Logic)
- **Lines**: ~150
- **Purpose**: Manages portfolio operations
- **Methods**:
  - buyStock() - Add stock to portfolio
  - calculateTotalValue() - Get current portfolio value
  - calculateProfitLoss() - Calculate gains/losses
  - updateAllPrices() - Refresh stock prices
  - getPortfolioItems() - Get all holdings
  - getTransactions() - Get trade history

### AlphaVantageService (API Integration)
- **Lines**: ~100
- **Purpose**: Fetch real-time stock data
- **Methods**:
  - getCurrentPrice(symbol) - Get current stock price
  - updateStockPrice(stock) - Update stock object
  - getHistoricalData(symbol) - Get price history

### PortfolioDAO (Database)
- **Lines**: ~200
- **Purpose**: Database operations
- **Methods**:
  - savePortfolioItem() - Save stock to DB
  - loadAllPortfolioItems() - Load all stocks
  - saveTransaction() - Save trade
  - loadAllTransactions() - Load trade history
  - updateStockPrice() - Update price in DB

---

## 🗄️ Database Schema

### portfolio_items table
```sql
CREATE TABLE portfolio_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    symbol TEXT NOT NULL,
    name TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    purchase_price REAL NOT NULL,
    current_price REAL NOT NULL
);
```

### transactions table
```sql
CREATE TABLE transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    symbol TEXT NOT NULL,
    type TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    price REAL NOT NULL,
    timestamp TEXT NOT NULL
);
```

---

## 🎨 UI Components

### Custom Components
- **NavButton** - Sidebar navigation button with hover effects
- **RoundedTextField** - Text field with rounded corners
- **StatCard** - Dashboard stat display card
- **StockCard** - Market page stock display card

### Standard Components
- **JFrame** - Main window
- **JPanel** - Container panels
- **JTable** - Data tables
- **JButton** - Action buttons
- **JDialog** - Modal dialogs
- **JScrollPane** - Scrollable areas
- **ChartPanel** - JFreeChart display

---

## 📦 Dependencies

### Runtime Dependencies
```
Java SE 11 or higher
├── javax.swing.*          (Built-in)
├── java.awt.*             (Built-in)
├── java.sql.*             (Built-in)
├── java.net.http.*        (Built-in)
└── External JARs:
    ├── jfreechart-1.5.4.jar
    ├── sqlite-jdbc-3.45.1.0.jar
    ├── json-20231013.jar
    └── slf4j-*.jar
```

### Compile-time Only
```
jakarta.servlet-api-6.0.0.jar  (Not used in desktop app)
```

---

## 🚀 Build Process

### Compilation Order
```
1. Model classes (Stock, PortfolioItem, Transaction)
2. Service interfaces (StockPriceService)
3. Service implementations (AlphaVantageService, PortfolioService)
4. Database classes (DatabaseManager, PortfolioDAO)
5. UI classes (PremiumStockDashboard)
```

### Command
```cmd
javac -encoding UTF-8 -cp "lib/*" -d . src/com/portfolio/model/*.java src/com/portfolio/service/*.java src/com/portfolio/database/*.java src/com/portfolio/ui/PremiumStockDashboard.java
```

---

## 📏 Code Statistics

### Total Lines of Code (Approximate)
```
Models:           ~300 lines
Services:         ~500 lines
Database:         ~400 lines
UI (Premium):    ~1000 lines
─────────────────────────────
Total:           ~2200 lines
```

### File Count
```
Source files:     15 Java files
Compiled:         50+ .class files
Libraries:        6 JAR files
Documentation:    6 MD files
```

---

## 🎯 Active vs Archived

### ✅ Active (In Use)
- PremiumStockDashboard.java
- All model, service, database classes
- RUN-PREMIUM-DASHBOARD.bat
- Current documentation

### 📦 Archived (Not Used)
- Old UI versions (PortfolioUI, ModernPortfolioUI, etc.)
- Web version (webapp/, servlet/)
- Old deployment scripts
- Old documentation

---

This structure keeps the project clean, organized, and focused on the pure Java desktop application.
