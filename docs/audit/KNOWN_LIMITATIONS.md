# Known Limitations

- **Alpha Vantage is not live-verified.** Failure classification (invalid key vs. quota vs. other) relies on the provider's message wording as documented/commonly seen; if it differs, the app still reports "unavailable" and never invents a price. No quota counter or daily-limit tracking; retries are bounded (3 attempts, only for network errors and HTTP 5xx). Quotes may be delayed.
- A holding that was never refreshed shows its first purchase price as "current price" (labelled "Not refreshed"); its unrealized P/L is not meaningful.
- Weighted-average cost only (no FIFO/LIFO lots), no fees, taxes, splits, dividends or currencies. Whole shares only. Money is stored as SQLite REAL; averages rounded to 4 dp, P/L to cents.
- Transaction times are local time, second precision. Rows written by the first version were stamped in UTC by SQLite and display shifted by your UTC offset.
- **Migration:** older databases are upgraded automatically after an automatic `.pre-migration-*.bak` copy, inside one transaction (additive columns `realized_pl`, `price_updated_at`; duplicate symbol rows merged in memory and consolidated on the next trade in that symbol). Tested only on synthetic legacy databases, never on your real `portfolio.db`. Follow `docs/BACKUP_AND_RESTORE.md` first. If the backup directory is read-only, the upgrade is refused and the app will not start until you fix permissions or restore access.
- Refresh is sequential, single-flight and cannot be cancelled. A refresh stops early on a missing/invalid key or quota hit.
- `PortfolioService.removeStock` exists but is not exposed in the GUI; it is not a sale.
- Single static JDBC connection; one process at a time.
- Not implemented: CSV import, price history/performance charts, alerts, risk metrics, sector allocation, notifications (see `FEATURE_PLAN.md`). GUI export covers transactions only.
- GUI: scripted interaction passed (31 checks), but no human visual/usability acceptance; closing the window during a refresh was not tested.
- The GitHub Actions workflow has not been executed (nothing was pushed). OWASP Dependency-Check was not completed.
- `lib/` and `test-lib/` JARs are unused stale copies kept pending your approval to remove (see `DEPENDENCY_AUDIT.md`).
- One earlier intermittent test failure's report was lost; a probable cause was found and fixed, but it cannot be proven to be that run's cause (see `TEST_RESULTS.md`).

---

## Consolidated project (supersedes any conflicting statement above)
The items above describe the stock_project line before integration. Differences now:
- Alerts, notifications, sector/risk views, news, AI, voice, e-mail and multi-provider quotes **exist** (from jirp); only the
  ones listed in `INTEGRATION_REPORT.md` as "Verified" have automated evidence. Price history charts use real provider
  history (30 days from Twelve Data, about 100 from Alpha Vantage); they show "unavailable" with the reason otherwise.
- `lib/` and `test-lib/` are kept. Some JARs are unused by the current code (see the dependency map in `INTEGRATION_REPORT.md`).
- GUI evidence is two scripted in-process runs (classic window 31 checks, premium dashboard 14 checks), not human acceptance.
- **Wallet currency (resolved):** the wallet has one stored currency (default INR); trades in another currency need a validated,
  fresh exchange rate or are blocked; see `docs/CURRENCY_AND_QUOTES.md`. Remaining limits: the live rate source needs an
  `EXCHANGERATE_API_KEY` (no key = foreign-currency trades are blocked); a holding cannot be traded at a live quote in a
  different currency than it is held in (it is blocked, not converted), so record a holding in the currency its provider quotes;
  Alpha Vantage and Finnhub do not state a currency, so a bare US ticker is assumed USD and suffixed symbols are blocked.
- **FX fallback is display-only:** portfolio totals shown in a display currency may use fixed approximate rates (USD/INR 83,
  EUR 0.92, SAR 3.75) if the exchange-rate API fails. Such figures are now marked: a "≈" prefix on the dashboard totals, stat
  cards and table cells (with a tooltip), an amber "≈ APPROXIMATE RATES" header banner that appears while any approximation is in
  use (and clears five minutes after live rates work again), and an explicit note in exports, e-mails and AI context.
  Validated totals carry no mark. These rates never move money.
- Sharpe ratio, beta and the 15% tax estimate use fixed assumptions and are illustrative only.
- The welcome/login screen is cosmetic (any input proceeds). Local accounts in `DatabaseManager` use unsalted SHA-256 and the
  e-mail app password is stored in the database in clear text; both are inherited and unchanged.
- Legacy jirp transactions were stamped in UTC by SQLite (`CURRENT_TIMESTAMP`); new ones use local time. Both display as stored.
- A legacy jirp holding's stored "current price" may itself have been fabricated by the old code; it has no freshness stamp, so
  it is treated as "never refreshed" until a real refresh succeeds.
- Sales in the classic window, premium dashboard and console now all use a confirmed live quote. Typed-price BUYS (dashboard
  forms, console, classic "Add Stock") remain: the price is the user's own entry, in a stated currency.
- The classic window shows "$" labels and prices holdings in USD; with the default INR wallet its buys need an exchange rate.
- The premium dashboard shows Fresh / Stale / Unavailable per holding (15-minute display threshold, 60-second trading limit).
- The catalogue lists (`marketData`, `StockDatabase`) still contain sample base prices; they are never displayed as quotes.
- SpotBugs exclusions are documented in `spotbugs-exclude.xml`; the 7,000-line dashboard is only partly covered by automated tests.

## Landing page, market data and accounts
* **Accounts** use the existing `users` table. New accounts store a salted PBKDF2-HMAC-SHA256 hash (JDK built-in, 210,000 iterations).
  Accounts created by the old app (unsalted SHA-256) still sign in; after a *correct* password the stored hash is replaced by a PBKDF2
  hash (documented behaviour, tested). A failed sign-in never changes a stored hash. Still true: no password recovery, no lockout beyond
  a 0.6 s delay after a wrong password, and the account only gates the start page (the data stays in the local database file).
* **Stock cards** (Reliance Industries, HDFC Bank, ICICI Bank, Tata Consultancy Services) ask each configured provider for its own
  symbol: Twelve Data `RELIANCE:NSE` (sent as `symbol=RELIANCE&exchange=NSE`), Finnhub `RELIANCE.NS`, Alpha Vantage `RELIANCE.BSE`.
  Observed on the developer's machine on 2026-10-25 with the user's own keys (free plans): Alpha Vantage answered quotes and daily
  history for the `.BSE` symbols for all four stocks (one history request failed once, a rate limit); Twelve Data and Finnhub did not
  provide these Indian symbols on those plans. Which provider answers on another account depends on its plan. The cards never assume a
  currency: it is shown only when the provider states one (Alpha Vantage does not for `.BSE`).
* **Error classification:** Finnhub answers HTTP 403 "You don't have access to this resource" for a symbol outside the plan and 401
  for a bad key; the first is reported as "does not support this symbol", not "invalid API key". When every provider fails, the card's
  tooltip and the chart caption list each provider's own reason, so one provider's problem never hides another's.
* **Request budget:** one quote per (provider, symbol) per refresh plus one history request when a quote succeeds; results are cached
  5 minutes, a refresh already running is not started twice, and a provider that reports a rate limit is paused for 90 seconds even for
  a forced refresh, and Alpha Vantage is asked at most once per 1.2 seconds (its free plan allows about one request per second). Twelve Data history uses one credit per index.
* **Chart:** the featured chart shows the selected index's daily closes (ranges 1W, 1M, 3M, 1Y, ALL only where the returned history
  covers them; 1D is never available because providers return daily data). With nothing selected it shows a labelled illustrative
  sample indexed to 100. There is no stored portfolio value history, so no real portfolio series is charted.
* **Headlines** come only from GNews or NewsData when a key is set (never the built-in text of the older news screen). A headline
  opens in the browser only after you click it.
* **Keys outside the project tree:** a packaged app that is not inside the project folder reads keys from environment variables,
  `stockvault.local.properties`, or `%APPDATA%\StockVault\.env` (the project-root `.env` is found from where the code lives).

## Published repository
`test-lib/` (a local scratch copy of the JUnit console runner) and `docs/legacy/` (copies of documents that an earlier commit removed on purpose) stay on the developer machine and are not part of the published repository; Maven does not need either. Wherever older reports say these folders are "kept", that refers to the local working copy.
