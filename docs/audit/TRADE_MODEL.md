# Trade and Cost-Basis Model

- **One holding per symbol.** `portfolio_items.purchase_price` stores the weighted-average cost per share.
- **Buy:** `newQty = q1 + q2`; `newAvg = (q1*c1 + q2*c2) / newQty`, BigDecimal, 4 dp HALF_UP. Existing live `current_price` is kept.
- **Sell:** allowed only for `0 < qty <= held` and price > 0. `realizedPL = (sellPrice - avgCost) * qty`, 2 dp HALF_UP, stored on the SELL transaction (`transactions.realized_pl`; NULL for BUY). Average cost of the remainder is unchanged. Selling everything deletes the holding row.
- **Fees, taxes, lot-level (FIFO/LIFO) accounting, splits, dividends, currencies: not modelled.**
- **Total realized P/L** is derived only from SELL transactions, so nothing is counted twice. Total investment/value/unrealized P/L cover current holdings only.
- **Atomicity:** each trade is one SQL transaction (replace the symbol's holding row + insert the transaction). Memory is updated only after commit.
- **Removing a holding** (`removeStock`) is a record deletion, not a sale: no transaction, proceeds or realized P/L. It is not exposed in the GUI.
- **Migration (additive, idempotent; automatic `.pre-migration-*.bak` backup first and one transaction, see `docs/BACKUP_AND_RESTORE.md`):** on connect, `ALTER TABLE transactions ADD COLUMN realized_pl REAL` if missing. Old databases with several rows for one symbol are merged by weighted average when loaded, and the rows are consolidated on the next trade in that symbol. Old SELL rows (none could be created by the app) have NULL realized P/L.
- Money is stored as SQLite REAL (binary floating point); all arithmetic is done in BigDecimal and rounded before storage.
