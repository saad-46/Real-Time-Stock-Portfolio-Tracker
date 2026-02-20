# 🔧 API Troubleshooting Guide

## Issue: UI Freezes When Clicking "Refresh Prices"

### ✅ FIXED!

The issue was that the loading dialog was **modal** (blocking), which prevented the background thread from running properly.

### What Was Changed:

1. **Loading Dialog Made Non-Modal**
   - Changed from `new JDialog(this, "Updating Prices", true)` 
   - To: `new JDialog(this, "Updating Prices", false)`
   - This allows the UI to remain responsive

2. **Added Timeout to API Calls**
   - Added `.timeout(java.time.Duration.ofSeconds(10))`
   - Prevents hanging if API is slow or unresponsive

3. **Improved Threading**
   - Loading dialog now shows on UI thread using `SwingUtilities.invokeLater()`
   - API calls run in background thread via `SwingWorker`

## How It Works Now:

```
User clicks "Refresh" button
    ↓
SwingWorker starts background thread
    ↓
Loading dialog appears (non-blocking)
    ↓
API calls happen in background (10 second timeout per stock)
    ↓
UI remains responsive during fetch
    ↓
When done: dialog closes, table updates, success message shows
```

## Testing the Fix:

1. **Compile the updated code:**
```bash
javac -encoding UTF-8 -cp "lib/*" -d . src/com/portfolio/database/*.java src/com/portfolio/model/*.java src/com/portfolio/service/*.java src/com/portfolio/ui/*.java src/com/portfolio/*.java
```

2. **Run the application:**
```bash
java -cp ".;lib/*" com.portfolio.MainUI
```

3. **Test refresh:**
   - Click "🔄 Refresh" button
   - Loading dialog should appear
   - UI should remain responsive (you can move the window)
   - After a few seconds, prices update
   - Success message appears

## Common API Issues:

### 1. API Rate Limit Exceeded
**Symptom**: Error message about rate limit
**Cause**: Alpha Vantage free tier allows 25 requests per day
**Solution**: 
- Wait 24 hours for limit to reset
- Or upgrade to paid API plan
- Or use mock prices for testing

### 2. Slow Internet Connection
**Symptom**: Takes long time to update
**Cause**: Slow network or API server
**Solution**: 
- 10 second timeout now prevents hanging
- Check your internet connection
- Try again later

### 3. Invalid API Key
**Symptom**: API returns error status
**Cause**: API key is wrong or expired
**Solution**: 
- Check API key in `AlphaVantageService.java`
- Get new key from: https://www.alphavantage.co/support/#api-key

### 4. Stock Symbol Not Found
**Symptom**: Error parsing price
**Cause**: Stock symbol doesn't exist or is delisted
**Solution**: 
- Use "Validate" button before adding stocks
- Returns mock price as fallback

## API Response Example:

**Successful Response:**
```json
{
  "Global Quote": {
    "01. symbol": "AAPL",
    "05. price": "278.12",
    "07. latest trading day": "2024-01-15",
    "09. change": "+2.50",
    "10. change percent": "+0.91%"
  }
}
```

**Error Response (Rate Limit):**
```json
{
  "Note": "Thank you for using Alpha Vantage! Our standard API call frequency is 5 calls per minute and 500 calls per day."
}
```

## Monitoring API Calls:

Watch the console output when refreshing:
```
📡 Fetching price for AAPL...
Updated AAPL: $278.12
📡 Fetching price for TSLA...
Updated TSLA: $245.67
📡 Fetching price for GOOGL...
Updated GOOGL: $142.89
```

## Performance Tips:

1. **Don't refresh too often** - API has rate limits
2. **Add stocks gradually** - Each stock = 1 API call
3. **Use validation** - Prevents adding invalid stocks
4. **Check console** - Shows API call progress

## Fallback Behavior:

If API fails, the code uses **mock prices**:
- Random price between $100-$200
- Allows testing without API
- Console shows warning: "⚠️ Using mock price"

## Threading Explanation:

### Before (Blocking):
```
Main UI Thread
    ↓
Show modal dialog (BLOCKS everything)
    ↓
Try to run API calls (CAN'T - thread is blocked!)
    ↓
UI freezes ❌
```

### After (Non-blocking):
```
Main UI Thread                Background Thread
    ↓                              ↓
Show non-modal dialog         Run API calls
    ↓                              ↓
UI stays responsive ✅        Fetch prices
    ↓                              ↓
User can interact             Update database
    ↓                              ↓
Dialog closes when done       Return results
```

## Code Changes Summary:

### ModernPortfolioUI.java:
```java
// OLD (blocking):
JDialog dialog = new JDialog(this, "Updating Prices", true);

// NEW (non-blocking):
JDialog dialog = new JDialog(this, "Updating Prices", false);
```

### AlphaVantageService.java:
```java
// OLD (no timeout):
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create(url))
    .GET()
    .build();

// NEW (with timeout):
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create(url))
    .GET()
    .timeout(java.time.Duration.ofSeconds(10))  // 10 second timeout
    .build();
```

## Testing Checklist:

- ✅ UI doesn't freeze when clicking refresh
- ✅ Loading dialog appears
- ✅ Can move window while refreshing
- ✅ Prices update after API calls
- ✅ Success message appears
- ✅ Console shows API call progress
- ✅ Timeout prevents hanging

## If Still Having Issues:

1. **Check console output** - Look for error messages
2. **Verify internet connection** - API needs internet
3. **Check API key** - Make sure it's valid
4. **Try with fewer stocks** - Reduces API calls
5. **Wait for rate limit reset** - If exceeded daily limit

## Success Indicators:

When working correctly, you'll see:
```
📊 Updating stock prices...
📡 Fetching price for AAPL...
Updated AAPL: $278.12
✅ Updated price in database: AAPL = $278.12
📡 Fetching price for TSLA...
Updated TSLA: $245.67
✅ Updated price in database: TSLA = $245.67
```

🎉 **The UI freeze issue is now fixed!**
