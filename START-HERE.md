# 🎯 START HERE - Beginner's Guide

## Welcome to StockVault Portfolio Tracker!

This is a **100% pure Java desktop application** for managing your stock portfolio. No web browser needed!

---

## ⚡ Quick Start (3 Steps)

### Step 1: Run the Application
Double-click this file:
```
RUN-PREMIUM-DASHBOARD.bat
```

### Step 2: Add Your First Stock
1. Click "My Portfolio" in the left sidebar
2. Click the "+ Add Stock" button
3. Enter:
   - Symbol: `AAPL` (for Apple)
   - Quantity: `10`
   - Price: `150`
4. Click "Add Stock"

### Step 3: Explore!
- Click "Dashboard" to see your portfolio summary
- Click "Market" to browse popular stocks
- Click "Analytics" to see charts

**That's it! You're ready to go!** 🎉

---

## 📱 What You'll See

### When You First Open
You'll see a window with:
- **Left side**: Purple sidebar with menu items
- **Top**: Page title and search bar
- **Center**: Dashboard with stats cards

### The Sidebar Menu
```
◈ StockVault          ← App logo

⊞ Dashboard           ← Overview of your portfolio
◈ My Portfolio        ← Manage your stocks
◉ Market              ← Browse popular stocks
★ Watchlist           ← Track favorites
↕ Transactions        ← See your trade history
▲ Analytics           ← View charts
⚙ Settings            ← App preferences

v2.1.0 • Market Open  ← Version info
```

---

## 🎓 Understanding the Pages

### 📊 Dashboard
**What it shows:**
- Total Value: How much your portfolio is worth now
- Invested: How much money you put in
- Profit/Loss: How much you gained or lost
- Return %: Your profit as a percentage
- Recent Stocks: Your top 5 holdings

**When to use:** Quick overview of your portfolio

---

### 💼 My Portfolio
**What it shows:**
- Complete table of all your stocks
- Each row shows: Symbol, Name, Quantity, Buy Price, Current Price, Total Value, Gain/Loss, Return %

**What you can do:**
- Click "+ Add Stock" to buy a new stock
- Click "↻ Refresh Prices" to update all prices from the internet

**When to use:** Managing your holdings, adding stocks, checking details

---

### 🌐 Market
**What it shows:**
- Grid of popular stock cards
- Each card shows: Symbol, Name, Price, Change %, Mini chart

**What you can do:**
- Click "View Chart" on any stock to see 30-day price history

**When to use:** Browsing stocks, researching before buying

---

### ⭐ Watchlist
**What it shows:**
- Stocks you want to track (coming soon)

**When to use:** Keeping an eye on stocks you might buy later

---

### 📜 Transactions
**What it shows:**
- Complete history of all your trades
- Each row shows: Date, Type (BUY/SELL), Symbol, Quantity, Price, Total

**When to use:** Reviewing your trading history

---

### 📈 Analytics
**What it shows:**
- 4 professional charts:
  1. Portfolio Distribution (pie chart)
  2. Profit vs Loss (pie chart)
  3. Stock Values (bar chart)
  4. Gain/Loss by Stock (bar chart)

**When to use:** Analyzing your portfolio performance

---

### ⚙️ Settings
**What it shows:**
- Currency: Indian Rupee (₹)
- Theme: Dark Mode
- Auto-refresh: Enabled
- Notifications: Enabled

**When to use:** Checking app configuration

---

## 🎯 Common Tasks

### Task 1: Add a Stock
```
1. Click "My Portfolio" in sidebar
2. Click "+ Add Stock" button
3. Enter stock details:
   - Symbol: AAPL (stock ticker)
   - Quantity: 10 (how many shares)
   - Price: 150 (what you paid per share)
4. Click "Add Stock"
5. Done! Stock appears in your portfolio
```

### Task 2: Update Prices
```
1. Click "My Portfolio" in sidebar
2. Click "↻ Refresh Prices" button
3. Wait 5-10 seconds
4. Progress dialog shows "Updating..."
5. Done! All prices updated
```

### Task 3: View a Stock Chart
```
1. Click "Market" in sidebar
2. Find a stock card (e.g., AAPL)
3. Click "View Chart" button
4. See 30-day price history
5. Close chart window when done
```

### Task 4: Check Your Profit
```
1. Click "Dashboard" in sidebar
2. Look at the stat cards:
   - Green numbers = Profit
   - Red numbers = Loss
3. See "Profit/Loss" card for total
4. See "Return %" for percentage gain
```

### Task 5: Search for a Stock
```
1. Look at top-right corner
2. Click in the search box
3. Type a letter (e.g., "A")
4. See matching stocks appear
5. Click one to select it
```

---

## 💡 Tips for Beginners

### Tip 1: Start Small
- Add 2-3 stocks first
- Get comfortable with the interface
- Then add more

### Tip 2: Refresh Prices Wisely
- Don't refresh too often (API has limits)
- Once per hour is enough
- Wait 1 minute between refreshes

### Tip 3: Use the Dashboard
- Check Dashboard first when you open the app
- Quick overview of everything
- Then go to specific pages for details

### Tip 4: Explore the Market Page
- Browse popular stocks
- Click "View Chart" to see trends
- Research before buying

### Tip 5: Check Analytics
- Go to Analytics page weekly
- See how your portfolio is distributed
- Identify which stocks are performing well

---

## 🔍 Understanding Stock Symbols

**What is a stock symbol?**
A short code for a company's stock. Examples:
- AAPL = Apple Inc.
- GOOGL = Alphabet (Google)
- MSFT = Microsoft
- TSLA = Tesla
- AMZN = Amazon

**Where to find symbols?**
- Use the search bar in the app
- Check the Market page
- Google: "Apple stock symbol"

---

## 💰 Understanding the Numbers

### Total Value
How much your portfolio is worth RIGHT NOW if you sold everything.
```
Example: You own 10 AAPL shares at ₹200 each
Total Value = 10 × ₹200 = ₹2,000
```

### Invested
How much money you PAID when you bought the stocks.
```
Example: You bought 10 AAPL shares at ₹150 each
Invested = 10 × ₹150 = ₹1,500
```

### Profit/Loss
How much money you GAINED or LOST.
```
Example:
Total Value = ₹2,000
Invested = ₹1,500
Profit = ₹2,000 - ₹1,500 = ₹500 (Green = Good!)
```

### Return %
Your profit as a percentage.
```
Example:
Profit = ₹500
Invested = ₹1,500
Return % = (₹500 / ₹1,500) × 100 = 33.33%
```

---

## 🎨 Understanding the Colors

### Green 🟢
- Positive numbers (profit, gains)
- Stock price went UP
- Good news!

### Red 🔴
- Negative numbers (loss)
- Stock price went DOWN
- Not great, but normal in investing

### Purple 💜
- Accent color (buttons, highlights)
- Just for design

### White ⚪
- Regular text
- Neutral information

---

## ❓ Common Questions

### Q: Is my data saved?
**A:** Yes! Everything is saved in `portfolio.db` file. Your data persists even after closing the app.

### Q: Do I need internet?
**A:** Only for updating prices. The UI works offline, but you need internet to refresh stock prices.

### Q: How often should I refresh prices?
**A:** Once per hour is good. The API has limits (5 calls/minute, 500/day).

### Q: Can I delete a stock?
**A:** Not yet in the UI. You can delete `portfolio.db` to start fresh.

### Q: What if I make a mistake?
**A:** Delete `portfolio.db` file and restart. The app will create a new empty database.

### Q: Why Indian Rupees (₹)?
**A:** The app is configured for Indian users. All prices show in ₹.

### Q: Are the prices real?
**A:** Yes! Prices come from Alpha Vantage API (real stock market data).

---

## 🐛 Something Wrong?

### Application won't start
1. Make sure you have Java installed
2. Check that `lib/` folder has JAR files
3. Try running: `java -version` in command prompt

### Prices not updating
1. Check your internet connection
2. Wait 1 minute (API rate limit)
3. Try again

### Blank screen or error
1. Close the application
2. Delete `portfolio.db` file
3. Restart the application

---

## 📚 Next Steps

Once you're comfortable:
1. Read `README.md` for more details
2. Read `PREMIUM-DASHBOARD-README.md` for all features
3. Check `PROJECT-STRUCTURE.md` if you want to understand the code

---

## 🎉 You're Ready!

**Remember:**
1. Double-click `RUN-PREMIUM-DASHBOARD.bat` to start
2. Click "My Portfolio" to add stocks
3. Click "Dashboard" to see overview
4. Explore and have fun!

**Happy investing! 📈**

---

**Need help? Check:**
- README.md (overview)
- PREMIUM-DASHBOARD-README.md (detailed guide)
- INDEX.md (quick reference)
