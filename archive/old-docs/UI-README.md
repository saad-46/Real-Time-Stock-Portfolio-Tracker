# 📊 Stock Portfolio Tracker - UI Version

## What You Have Now

Your portfolio tracker now has a **beautiful graphical user interface (GUI)** with:

✅ **Main Window** - Shows all your stocks in a table
✅ **Summary Panel** - Displays total investment, current value, and profit/loss
✅ **Charts** - 4 different charts to visualize your portfolio
✅ **Buttons** - Refresh prices, add stocks, view charts
✅ **Database** - All data is automatically saved and persists between sessions
✅ **Stock Validation** - Verify stocks exist before adding them

## Files Created

### UI Files
1. **PortfolioUI.java** - Basic window with table and buttons
2. **ModernPortfolioUI.java** - Modern dark theme UI (recommended)
3. **ChartWindow.java** - Basic charts window
4. **ModernChartWindow.java** - Modern dark theme charts
5. **MainUI.java** - Launches the UI application

### Database Files
6. **DatabaseManager.java** - Manages database connection
7. **PortfolioDAO.java** - Handles database operations (save/load)

### Service Files
8. **StockValidator.java** - Validates stock symbols before adding

## How to Run

### Step 1: Libraries Already Included

All required libraries are in the `lib` folder:
- ✅ `jfreechart-1.5.4.jar` - For charts
- ✅ `sqlite-jdbc-3.45.1.0.jar` - For database
- ✅ `slf4j-api-2.0.9.jar` - For logging
- ✅ `slf4j-simple-2.0.9.jar` - For logging

### Step 2: Compile Everything

```bash
javac -encoding UTF-8 -cp "lib/*" -d . src/com/portfolio/database/*.java src/com/portfolio/model/*.java src/com/portfolio/service/*.java src/com/portfolio/ui/*.java src/com/portfolio/*.java
```

### Step 3: Run the UI

```bash
java -cp ".;lib/*" com.portfolio.MainUI
```

**Note**: On Windows, use semicolon (`;`) in classpath. On Mac/Linux, use colon (`:`).

## Features

### Main Window (ModernPortfolioUI)
- **Modern Dark Theme**: Professional look with gradients
- **Table**: Shows all your stocks with current prices
- **Summary Cards**: Total investment, current value, profit/loss, return %
- **Refresh Button**: Updates prices from Alpha Vantage API
- **Add Stock Button**: Buy new stocks with validation
- **View Charts Button**: Opens modern chart window
- **Color Coding**: Green for profit, red for loss

### Add Stock Dialog
- **Stock Validation**: Click "Validate" to check if stock exists
- **Real-time Feedback**: Shows ✓ for valid, ✗ for invalid
- **Auto-fill Price**: Automatically fills current price after validation
- **Prevents Invalid Stocks**: Can't add stocks that don't exist

### Chart Window (ModernChartWindow)
1. **Portfolio Distribution** - Pie chart showing % of each stock
2. **Profit vs Loss** - Pie chart showing total profit and loss
3. **Stock Values** - Bar chart comparing stock values
4. **Gain/Loss** - Bar chart showing profit/loss per stock

### Database Persistence
- **Auto-save**: Every stock you add is saved to database
- **Auto-load**: Data loads automatically when you start the app
- **Transaction History**: All buy/sell transactions are recorded
- **Price Updates**: Updated prices are saved to database
- **Database File**: `portfolio.db` in your project folder

See **DATABASE-README.md** for detailed database documentation.

## Understanding the Code

Every line has comments explaining:
- What it does
- Why it's there
- Examples of how it works

Example:
```java
setTitle("📊 Stock Portfolio Tracker");  // Window title at the top
```

## Troubleshooting

**Problem**: Charts don't show
**Solution**: Make sure JFreeChart JAR is in the `lib` folder

**Problem**: Window doesn't open
**Solution**: Check console for error messages

**Problem**: "Class not found" error
**Solution**: Make sure you compiled with `-cp "lib/*;."`

## Next Steps

You can customize:
- Colors (search for `new Color(...)`)
- Window size (search for `setSize(...)`)
- Button text (search for `createStyledButton(...)`)
- Chart titles (in ChartWindow.java)
