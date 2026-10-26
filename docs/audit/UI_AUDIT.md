# UI audit, framework decision and demo mode

## Framework decision

The UI is Java Swing + Java2D + FlatLaf (22 source files use `javax.swing`; `PremiumStockDashboard` alone is about 7,300 lines).

- JavaFX is **not available offline**: it is not part of JDK 21 (Eclipse Adoptium), and `~/.m2` and `lib-repo` contain no `org.openjfx` artifacts. A migration therefore cannot be built or verified here, and nothing was downloaded.
- A migration is a rewrite of every screen, not a port. It should be planned as staged work once the dependencies can be installed:
  1. Install JavaFX 21 (`javafx-controls`, `javafx-graphics`, `javafx-swing` if a mixed phase is needed) into the Maven repository, then add the dependencies and the module/launcher flags.
  2. Build a shared stylesheet (the tokens below), then the shell (sidebar, top bar), then one screen at a time, keeping services and DAOs unchanged.
  3. Remove Swing only after every screen has moved.
- **Swing remains in use.** The changes below are an interim improvement inside Swing.

## Design tokens (dark theme, `PremiumStockDashboard`)

Background `14,16,36`, sidebar `19,21,44`, top bar `17,19,41`, card `29,31,54`, hover `41,44,74`, border `56,60,98`, accent `102,126,234`. The previous near-black background (`15,15,15`) was the cause of the "black" regions; every page inherits the new navy.

## Findings and changes

| Finding | Change |
|---|---|
| Navigation used emoji; several drew as empty squares and the Settings gear sat off-centre | `NavIcons`: Java2D vector icons, one size and stroke, theme-aware colour, fixed x so icons never shift while the bar expands; active bar, hover and focus states; tooltips and accessible names |
| Emoji elsewhere (headings, buttons) drew as squares | `Glyphs.sweep` removes characters the component's font cannot draw, on build and on every navigation |
| Wallet looked different (hard-coded slate cards, clipped titles, hover jitter, duplicate title, ungrouped amounts) | Wallet summary cards use the theme surface, muted title, large value (Indian digit grouping) and coloured caption; duplicate page title replaced by a subtitle |
| Market cards invented figures: a "+0.0123 x price" change, a risk label from that, and a rising sparkline with no data | Only values from a real quote are shown; otherwise "No quote available", no risk badge, no chart |
| News screen showed a hard-coded green "LIVE" badge and silently fell back to built-in placeholder headlines | Badge reflects the real state (LIVE / DEMO DATA / UNAVAILABLE) and the reason is shown; Market screen shows headlines only after "Refresh News" (on-demand, to save quota) |

## Market News: where it comes from

`NewsService.fetchLive()` calls GNews and then NewsData, using keys from `.env` / environment (`GNEWS_API_KEY`, `NEWSDATA_API_KEY`). It runs on a background thread; there is no real-time stream and no cache between screen visits. The Market News screen is where headlines belong; the Market screen also has a small news section that loads on demand. Without keys it reports "News API key not configured" instead of showing placeholder text.

## Demo mode

`.\run-stockvault.ps1 -Mode Demo` (or `java -jar ... --demo`). Before anything opens, demo mode points the database, `.env`/config lookup, UI preference and market cache at a new folder under the system temp directory, which is deleted on exit. It uses `DemoPriceService` (fixed quotes, no network, no keys). The sample data: deposits 500,000 + 250,000, withdrawal 50,000, six buys (total 370,000), one sale of 10 INFY at 1,575, giving a wallet balance of 345,750.00, invested 354,600.00, value 367,241.30, profit/loss 12,641.30 (3.56 %), three watchlist entries. `DEMO DATA` appears in the top bar and window title; demo headlines are labelled "StockVault demo data". Real data cannot be written: the seeding code refuses to run unless demo mode is active.

## Not changed / remaining

- Pages other than Dashboard, Wallet, Market and Market News received the shared palette, icons and glyph clean-up only, not a layout redesign.
- Market page lists a fixed set of US tickers with no quotes unless a provider answers.
- Market cards' tag chips are slightly clipped at their lower edge.
- Light-theme rendering of the dashboard was not re-inspected; the dashboard window is dark by design.
- Analytics and Market (demo) have no demo-specific datasets.

## Runtime reliability (network limits and process lifecycle)

- **Timeouts.** Every outgoing HTTP call uses `java.net.http.HttpClient` built by `Net.client()` (10 s connect timeout) and sets a per-request timeout: 10-30 s for quotes, rates, news and AI calls, 30 s for speech, 120 s for an audio upload. News images are fetched with `Net.readImage` (8 s connect, 12 s read, 8 MB cap, http/https only). E-mail already had connect/read/write timeouts. A timeout is reported as "unavailable" on a background thread, never on the event thread.
- **Cause of the process that would not exit.** News-image downloads used `ImageIO.read(URL)` with no timeout; stalled connections kept non-daemon threads alive after every window had closed. Fixed at the source (timeouts), and background threads are daemon threads as a second line of defence (they are not a substitute for timeouts).
- **Scheduler.** `DailyPortfolioScheduler` no longer starts without a recipient, is synchronized, uses `shutdownNow()` on stop, and is stopped when the dashboard window closes. Fixed-rate runs on a single thread cannot overlap.
- **Watchdog.** `AppThreads.exitWhenNoWindowsRemain` exits after two consecutive 2-second checks with no showing window (minimized windows count as showing; a replacement window is shown before the old one closes). It is a safety net, not the primary shutdown path.
- **Not covered by timeouts:** `Desktop.browse` (opens the system browser) and the speech-recording threads, which are user-driven.

## Manual GUI checklist (not automated here)

1. `.\run-stockvault.ps1 -Mode Demo`: the title and top bar say DEMO DATA.
2. Click each sidebar icon (Dashboard, Portfolio, Wallet, Market, Watchlist, Transactions, Analytics, Market News, Settings): no exception dialog, no empty squares.
3. Toggle the theme; resize the window small and large; Tab through the top bar and sidebar.
4. Market News shows the "DEMO DATA" badge; Market shows "No quote available" for symbols without a quote.
5. Do **not** press Test Email, Pay Tax, Add/Withdraw funds in a normal (non-demo) session.
6. Close the window: the process ends within a few seconds.

## Charts and history data (wallet / dashboard / analysis overhaul)

| Screen | Chart | Data source | Interactive |
|---|---|---|---|
| Dashboard, My Portfolio | Portfolio Performance: market value and invested capital | `PortfolioHistory`: your transaction ledger + real daily closes from `HistoryService` (value of a day = quantity held x last close on or before it; a symbol with no history is valued at its own trade price and the footnote says so) | hover crosshair + tooltip (also arrow keys), range buttons |
| Recent Stocks, My Portfolio | Trend sparkline per holding (last 90 daily closes) | `HistoryService` | hover marker + tooltip with date, exact close, change |
| My Wallet | Balance history (step chart) | `wallet_transactions` ledger (`balance_after`, timestamp) | hover, range buttons |
| Stock analysis dialog | Price history with 1W/1M/6M/1Y/ALL | `HistoryService` | hover, range buttons |

- `HistoryService` asks the configured provider chain for daily closes, stores them in the `price_history` table (additive, created automatically) and reuses them for 12 hours, so charts survive restarts and respect free-tier quotas. A failed symbol is not retried for 5 minutes. If a refresh fails the stored series is shown and labelled cached; if there is none the chart says "unavailable" and why.
- A range button is enabled only when the loaded data covers it. 1D is never offered for daily data.
- The analysis dialog no longer shows fixed sample figures (market cap, P/E, 52-week range, volume, dividend, industry). It shows the quote's own high/low when the provider supplied them, the high/low of the loaded history, the sector when known, and "Not provided" otherwise.
- Wallet: the duplicate "Available for Trading" card is gone (it always equalled the balance). Add Funds / Withdraw are small dialogs. **Add Funds records a deposit in the StockVault wallet; no payment gateway exists, and the dialog says so.**
- Demo mode: trades are dated over the previous 88 days and the demo price history is a deterministic walk that ends at the demo quote and passes through each price paid; everything is labelled DEMO DATA.

## Release verification (symbol resolution, Market catalogue, timers)

- **Watchlist auto-refresh.** One `ManagedTimer` per window (60 s), started once, working only while the Watchlist page is showing, stopped by `dispose()`. Before, every page rebuild started another timer. Test: six full rebuilds keep exactly one timer and closing the window stops it.
- **Mixed currencies.** The portfolio history chart is drawn only when every holding is in the display currency; otherwise it says why (no historical exchange rates exist to convert with).
- **Obsolete dialog.** `DetailedMarketAnalysisDialog` (never instantiated, hard-coded figures) was removed.
- **Symbol resolution.** `SymbolResolver` + `MultiSourceStockService`: exchange-aware spelling per provider for quotes and history; legacy bare symbols unchanged; an unsupported listing is reported, not substituted.
- **Market page.** Lists the catalogue (no prices), with an exchange filter (India by default for a rupee portfolio), search and sector/cap filters. Prices appear only for holdings, watchlist, or after "Load quotes" (at most 12 requests, confirmed first when a real provider is configured).
- **Fabricated figures removed.** Watchlist and Market "trend" lines (two-point lines) are gone: the Watchlist uses real daily-close sparklines; the Market table has no trend column. The Analytics asset-allocation donut no longer invents 9.7 % ETF / 8.7 % cash / 9.1 % other slices (it shows holdings and wallet cash). Sharpe is labelled illustrative; Beta and Risk level read "N/A" (the old beta was a constant 1.00). The Transactions "Trend: Live" column became "Type", and dates are readable.
- **Evidence level.** All screens were rendered off-screen from the real components in demo mode and reviewed as images. The rebuilt JAR was launched for real in demo mode and closed normally. Real mouse and keyboard interaction with the launched window was **not** possible with the available tools: hover, focus order, resizing and theme switching in a live window are still manual checks (list above).
- **Known limits.** Alpha Vantage lists no NSE exchange; Twelve Data and Finnhub free plans have no Indian listings, so on free plans Indian quotes/history work only through Alpha Vantage `.BSE` symbols. Fundamentals (volume, market cap, P/E, dividend, industry) are not supplied by the configured providers and read "Not provided".

## Dashboard overhaul and data-status treatment (second release)

- **Yellow DEMO banner removed.** The amber outlined "DEMO DATA" box in the top bar is gone and the bar is slimmer (10 px padding, 25 pt title). Simulation is still stated, quietly: a neutral outlined chip "Demo data · simulated" in the top bar, the same chip on every chart (`StatusChip`), a grey dot and "Demo data (simulated)" in the sidebar footer, and the window title. Live data carries no such chip. The hard-coded "Market Open" in the sidebar footer (an invented status) was removed.
- **One status vocabulary** (`DataStatus`): Provider data (updated HH:mm), Cached (as of …, shown when the stored series is older than 12 h or a refresh failed), Demo data, Your records (wallet ledger, invested-capital-only chart), Unavailable (with the reason). When no price history can be loaded the performance chart shows only invested capital, labelled "Your records · invested capital only"; it never draws a market-value line from trade prices.
- **Tables.** Neutral header, calmer typography, row hover, click-to-sort by the number or date inside the text. Colour now means something: only an explicitly signed figure is green (+) or red (−); prices, totals and dates are neutral (before, any price was green and any date was red). Currency uses digit grouping everywhere (₹3,67,241.30).
- **Cards and layout.** Compact summary cards, performance chart directly under them, minimum window 1000 × 640; the Market card grid follows the window width (3, 2 or 1 columns) and the filter buttons wrap to their own row. Verified in off-screen renders at 1400 × 900 and 1000 × 640.
- **Provider reliability.**
  - Twelve Data error bodies are parsed as JSON (code 429 → rate limit, 401 → key, 403 → plan, 404 → unknown symbol) instead of matched as text; an error is never returned as a series.
  - When every provider fails, the user sees each provider's message and the most actionable reason (quota > key > network > not-in-plan > no key).
  - History retries are paused by cause: a daily quota 6 h, a rejected or missing key 30 s, anything else 5 min; concurrent requests for one symbol make one provider call.
- **Known free-tier limits (unchanged).** Free Twelve Data and Finnhub plans carry no Indian listings; Alpha Vantage covers BSE only (`.BSE`) with a small daily quota; `outputsize=full` may be refused by the plan (the compact ~100-day series is used); NIFTY 50 / SENSEX are unavailable; fundamentals are "Not provided". Whether your keys serve a symbol is only known by trying them.
- **Evidence.** Automated tests cover the status/colour/sort rules, chip behaviour, demo-vs-live separation of the dashboard, a hanging provider (the window builds in under 4 s and the chart never invents a value line), provider error bodies against a local server, chain reporting and cooldowns. Screens were reviewed as off-screen renders; real-window hover, focus and resize checks remain manual.
