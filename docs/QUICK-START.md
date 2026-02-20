# 🚀 Quick Start Guide

## Run Your Portfolio Tracker in 2 Steps!

### Step 1: Compile
```bash
javac -encoding UTF-8 -cp "lib/*" -d . src/com/portfolio/database/*.java src/com/portfolio/model/*.java src/com/portfolio/service/*.java src/com/portfolio/ui/*.java src/com/portfolio/*.java
```

### Step 2: Run
```bash
java -cp ".;lib/*" com.portfolio.MainUI
```

## What You'll See

1. **Console Output**:
```
✅ Database connected successfully!
✅ Portfolio table ready
✅ Transactions table ready
✅ Loaded X stocks from database
✅ Portfolio UI launched successfully!
```

2. **Modern UI Window** with:
   - Dark theme
   - Your saved stocks (if any)
   - Summary cards showing totals
   - Buttons to add stocks, refresh prices, view charts

## First Time Using?

### Add Your First Stock:

1. Click **"+ Add Stock"** button
2. Enter symbol (ex: **AAPL**)
3. Click **"🔍 Validate"** button
4. Wait for validation (shows ✓ if valid)
5. Enter quantity (ex: **10**)
6. Price auto-fills from validation
7. Click **"Add Stock"**

### Refresh Prices:

1. Click **"🔄 Refresh"** button
2. Wait while prices update from API
3. See updated prices in table

### View Charts:

1. Click **"📊 Charts"** button
2. See 4 different charts:
   - Portfolio distribution
   - Profit vs loss
   - Stock values
   - Gain/loss per stock

## Your Data is Saved!

- Every stock you add is saved to `portfolio.db`
- Close and reopen the app - your data is still there!
- No need to re-enter stocks every time

## Need Help?

- **UI Guide**: See `UI-README.md`
- **Database Guide**: See `DATABASE-README.md`
- **Code Comments**: Every line has detailed comments

## Troubleshooting

**Problem**: Window doesn't open
- Check console for error messages
- Make sure all libraries are in `lib` folder

**Problem**: Can't add stock
- Click "Validate" first to check if stock exists
- Make sure you have internet connection

**Problem**: Prices don't update
- Check your Alpha Vantage API key
- Free tier has 25 requests per day limit

## What's Next?

Try these features:
- Add multiple stocks
- Refresh prices to see profit/loss
- View charts to visualize your portfolio
- Close and reopen - your data persists!

🎉 **Enjoy your professional portfolio tracker!**
