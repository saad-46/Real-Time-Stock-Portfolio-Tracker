# 💾 Database Integration Guide

## Overview
Your Portfolio Tracker now has **persistent storage** using SQLite database! All your stocks and transactions are automatically saved and loaded.

## What is JDBC?
**JDBC (Java Database Connectivity)** is Java's standard API for connecting to databases. Think of it like a universal translator between Java and databases.

### Key JDBC Concepts:
- **Connection**: Like a phone line to the database
- **Statement**: Commands you send to the database (SQL)
- **ResultSet**: Data returned from the database (like rows in Excel)
- **PreparedStatement**: Safer statements that prevent SQL injection attacks

## Database Features

### ✅ Automatic Persistence
- **Every stock you add** is automatically saved to database
- **Every transaction** (buy/sell) is recorded
- **Price updates** are saved when you refresh
- **Data loads automatically** when you start the app

### 📊 Two Tables Created

#### 1. portfolio_items
Stores your current stock holdings:
```
- id: Unique identifier (1, 2, 3...)
- symbol: Stock symbol (ex: "AAPL")
- name: Company name (ex: "Apple Inc.")
- quantity: Number of shares (ex: 10)
- purchase_price: Price you paid (ex: 150.00)
- current_price: Latest price (ex: 278.12)
- created_at: When you bought it
```

#### 2. transactions
Stores your transaction history:
```
- id: Unique identifier
- symbol: Stock symbol
- type: "BUY" or "SELL"
- quantity: Number of shares
- price: Price per share
- timestamp: When transaction happened
```

## How It Works

### 1. DatabaseManager.java
**Purpose**: Manages database connection and table creation

**Key Methods**:
- `getConnection()`: Opens connection to database file (portfolio.db)
- `initializeTables()`: Creates tables if they don't exist
- `closeConnection()`: Closes database when app shuts down

**Example**:
```java
// Get connection to database
Connection conn = DatabaseManager.getConnection();
// Database file "portfolio.db" is created in your project folder
```

### 2. PortfolioDAO.java
**Purpose**: Data Access Object - handles all database operations

**DAO Pattern**: Separates database logic from business logic (like a librarian managing books)

**Key Methods**:
- `savePortfolioItem()`: Saves a stock to database
- `loadAllPortfolioItems()`: Loads all stocks from database
- `updateStockPrice()`: Updates price in database
- `deletePortfolioItem()`: Removes stock from database
- `saveTransaction()`: Saves transaction history
- `loadAllTransactions()`: Loads transaction history

**Example**:
```java
// Save a stock
PortfolioDAO dao = new PortfolioDAO();
dao.savePortfolioItem(appleStock);

// Load all stocks
List<PortfolioItem> stocks = dao.loadAllPortfolioItems();
```

### 3. PortfolioService.java (Updated)
**Purpose**: Now uses database for persistence

**What Changed**:
- Added `portfolioDAO` field for database access
- Added `loadFromDatabase()` method - runs automatically on startup
- `buyStock()` now saves to database
- `updateAllPrices()` now saves updated prices to database

**Flow**:
```
1. App starts → PortfolioService created
2. Constructor calls loadFromDatabase()
3. Loads all stocks and transactions from database
4. UI displays loaded data
5. When you add stock → Saves to database
6. When you refresh prices → Updates database
```

## Database File Location

The database is stored as: **portfolio.db**
- Located in your project root folder
- Single file contains all your data
- Can be backed up by copying this file
- Can be deleted to start fresh

## SQL Examples Used

### Insert Stock
```sql
INSERT INTO portfolio_items (symbol, name, quantity, purchase_price, current_price) 
VALUES ('AAPL', 'Apple Inc.', 10, 150.0, 278.12)
```

### Load All Stocks
```sql
SELECT * FROM portfolio_items
```

### Update Price
```sql
UPDATE portfolio_items SET current_price = 280.50 WHERE symbol = 'AAPL'
```

### Delete Stock
```sql
DELETE FROM portfolio_items WHERE symbol = 'AAPL'
```

## Libraries Used

### 1. SQLite JDBC Driver
- **File**: `lib/sqlite-jdbc-3.45.1.0.jar`
- **Purpose**: Allows Java to connect to SQLite database
- **Why SQLite**: Lightweight, no installation needed, single file database

### 2. SLF4J (Simple Logging Facade)
- **Files**: 
  - `lib/slf4j-api-2.0.9.jar`
  - `lib/slf4j-simple-2.0.9.jar`
- **Purpose**: Logging framework required by SQLite JDBC driver
- **What it does**: Handles log messages from the database driver

## Running the Application

### Compile:
```bash
javac -encoding UTF-8 -cp "lib/*" -d . src/com/portfolio/database/*.java src/com/portfolio/model/*.java src/com/portfolio/service/*.java src/com/portfolio/ui/*.java src/com/portfolio/*.java
```

### Run:
```bash
java -cp ".;lib/*" com.portfolio.MainUI
```

## What You'll See

When you run the app, you'll see these messages:
```
✅ Database connected successfully!
✅ Portfolio table ready
✅ Transactions table ready
✅ Loaded 3 stocks from database
✅ Loaded 3 transactions from database
```

## Testing the Database

### Test 1: Add a Stock
1. Click "Add Stock" button
2. Enter: AAPL, 10 shares, $150
3. Check console: "✅ Saved to database: AAPL"
4. Close and restart app
5. Stock should still be there!

### Test 2: Update Prices
1. Click "Refresh" button
2. Prices update from API
3. Check console: "✅ Updated price in database: AAPL = $278.12"
4. Close and restart app
5. Updated prices should be saved!

### Test 3: View Database File
1. Look for `portfolio.db` in your project folder
2. This file contains all your data
3. You can open it with SQLite browser tools

## Exception Handling

The code handles errors gracefully:

```java
try {
    portfolioDAO.savePortfolioItem(item);
    System.out.println("✅ Saved successfully");
} catch (Exception e) {
    System.err.println("❌ Error saving: " + e.getMessage());
    // App continues working even if database fails
}
```

## Benefits of Database Integration

### Before (Without Database):
- ❌ Data lost when app closes
- ❌ Had to re-enter stocks every time
- ❌ No transaction history

### After (With Database):
- ✅ Data persists between sessions
- ✅ Automatic save/load
- ✅ Complete transaction history
- ✅ Can backup data (copy portfolio.db)
- ✅ Professional application behavior

## Syllabus Topics Covered

✅ **JDBC**: Database connectivity using Java
✅ **SQL**: CREATE, INSERT, SELECT, UPDATE, DELETE statements
✅ **Exception Handling**: Try-catch blocks for database errors
✅ **Collections Framework**: ArrayList, List interface
✅ **DAO Pattern**: Separation of concerns
✅ **PreparedStatement**: SQL injection prevention

## Troubleshooting

### Problem: "ClassNotFoundException: org.sqlite.JDBC"
**Solution**: Make sure `lib/sqlite-jdbc-3.45.1.0.jar` exists

### Problem: "NoClassDefFoundError: org/slf4j/LoggerFactory"
**Solution**: Make sure SLF4J jars are in lib folder

### Problem: Database file locked
**Solution**: Close all instances of the app before restarting

### Problem: Want to start fresh
**Solution**: Delete `portfolio.db` file and restart app

## Advanced: Viewing Database

You can view your database using tools like:
- **DB Browser for SQLite** (free, GUI tool)
- **SQLite command line**
- **IntelliJ IDEA** (has built-in database viewer)

## Summary

Your portfolio tracker now has **enterprise-level data persistence**! Every action is automatically saved to a SQLite database, making it a professional-grade application. The JDBC integration demonstrates real-world database programming skills that are essential for Java developers.

🎉 **Your data is now safe and persistent!**
