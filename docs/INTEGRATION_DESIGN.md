# StockVault integration design (jirp + stock_project)

Written before editing `DatabaseManager`, `PortfolioDAO` and `PortfolioService`. Both original versions of every
conflicting file are preserved in the recovery directory (`conflicts\jirp`, `conflicts\stock_project`).

## 1. Schema migration sequence (`DatabaseManager.initializeTables`)

All statements are idempotent and run in ONE SQLite transaction (DDL is transactional in SQLite).

0. If the database already holds `portfolio_items` or `transactions` AND anything expected is missing (a table or a
   column below), write `<db>.pre-migration-<timestamp>.bak` with `VACUUM INTO` first. This cannot run inside a
   transaction, so it comes before step 1. If the backup fails, migration is aborted and the file is untouched.
1. `BEGIN`.
2. `CREATE TABLE IF NOT EXISTS`: `portfolio_items`, `transactions`, `watchlist`, `users`, `email_settings`, `wallet`,
   `wallet_transactions`.
3. `ALTER TABLE ... ADD COLUMN` if missing (older databases):
   - `portfolio_items`: `price_updated_at`, `original_currency`, `sector`, `market_cap`, `risk_level`, `user_id`
   - `transactions`: `realized_pl`, `user_id`
   - `watchlist`: `user_id`
4. `INSERT OR IGNORE` the single wallet row (id 1, balance 0). An existing wallet is never overwritten.
5. `COMMIT`. Any failure: `ROLLBACK`, connection dropped, `IllegalStateException` (never a half-migrated schema).

Legacy-format databases written by either original app open with no data loss: A's databases gain B's columns,
B's databases gain A's tables. No existing row is modified or deleted by migration.

## 2. Trade transaction boundary (`PortfolioDAO.recordTrade`)

One JDBC transaction, guarded by a single process-wide lock (`DatabaseManager.lock()`), also taken by every
`WalletDAO` write so a deposit on another thread cannot join a half-finished trade on the shared connection:

1. Wallet leg. BUY: `UPDATE wallet SET balance = balance - ? WHERE id = 1 AND balance >= ?`; zero rows updated means
   `InsufficientFundsException` and a rollback. SELL: `balance = balance + ?`.
2. Read the resulting balance, insert a `wallet_transactions` row (`STOCK BUY` / `STOCK SELL`).
3. Replace the symbol's `portfolio_items` row (or delete it when fully sold), keeping currency/sector/market-cap/risk.
4. Insert the `transactions` row (with `realized_pl` for sells).
5. `COMMIT`. Any `SQLException`: `ROLLBACK`; nothing from steps 1-4 is visible.

The in-memory lists in `PortfolioService` and the cached wallet are changed only after the commit succeeds
(wallet is reloaded from the database, not adjusted by arithmetic).

Cost basis and realized P/L come from `CostBasis` (weighted average, BigDecimal, unchanged from stock_project).
Wallet amount = `round(quantity * price, 2)`, HALF_UP, in the same unit as the quoted price (A's existing behavior;
see "Known limitation: wallet currency").

## 3. Compatibility API of `PortfolioService`

jirp signatures kept (so `PremiumStockDashboard`, `GroqAIService`, `DetailedMarketAnalysisDialog` still compile):
`PortfolioService(StockPriceService)`, `buyStock(symbol,name,qty,price[,currency,sector,marketCap,risk])`, `buy(...)`,
`sellStock(symbol,qty)`, `sellAllStock`, watchlist, alerts, currency conversion, `getPortfolioItems`,
`getMergedPortfolioItems`, `getTransactions`, `getWalletService`, analytics.

stock_project signatures kept (tests, `ModernPortfolioUI`, console `Main`): `PortfolioService()`,
`PortfolioService(svc, Clock)`, `buyStock(PortfolioItem)`, `sellStock(symbol,qty,price)` returning realized P/L,
`searchTransactions`, `getTotalRealizedProfitLoss`, `updateAllPrices()` returning the failure count, etc.

Intentional behavior changes (each is a data-safety or no-fake-price fix):
- A's random price jitter timer is removed (it fabricated prices every 30 s).
- `buyStock` / `sellStock` no longer open `JOptionPane` from the service layer or swallow errors; they throw
  `InsufficientFundsException` / `IllegalArgumentException` / `IllegalStateException` and the UI shows them.
- `sellStock(symbol, qty)` (no price) obtains a validated live quote itself and returns `false` (reason in
  `getLastTradeProblem()`) if none is available. The dashboard first calls `fetchValidatedQuote`, shows the price and
  proceeds in a confirmation, then calls `sellStock(symbol, qty, price)` with exactly that price.
- `updateAllPrices()` returns `int` (was `void`), harmless for existing callers.

## 4. Rollback behavior (what the tests must prove)
Insufficient funds, insufficient holdings, non-positive/NaN/infinite price, non-positive quantity, unknown symbol, and a
forced database failure mid-trade (a trigger that raises on `transactions` insert) must each leave wallet, holdings
and ledger exactly as before, in the database and in memory.

## 5. Known limitation: wallet currency
Prices from providers are in the quote currency (USD for US tickers) while the wallet UI shows rupees; the original
app debits `quantity * price` without conversion. That is preserved, not silently changed. See KNOWN_LIMITATIONS.
