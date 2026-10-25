# Database decision and migration procedure

Nothing here has been applied to a live database. Both original `portfolio.db` files are untouched (SHA-256 and timestamps
unchanged) and also preserved in the recovery directory. Inspected were disposable copies, read-only, printing only metadata.

## Which database is current (sanitised evidence)
| | **jirp** `portfolio.db` | **stock_project** `portfolio.db` |
|---|---|---|
| File | 45,056 bytes, 11 pages, modified 2026-03-21, integrity ok | 16,384 bytes, 4 pages, modified 2026-02-28, integrity ok |
| Tables | portfolio_items, transactions, watchlist, users, email_settings, wallet, wallet_transactions | portfolio_items, transactions only |
| Rows | portfolio_items 23 (4 symbols), transactions 23 (all buys, 0 sells), wallet_transactions 21, watchlist 1, wallet 1, users 0, email_settings 0 | all tables 0 rows |
| Dates | holdings and trades 2026-03-06 to 2026-03-20; wallet history 2026-03-07 to 2026-03-20 | none |
| Wallet | balance positive, deposits recorded | no wallet table |
| AUTOINCREMENT high-water marks | equal to the row counts (23 / 23 / 21): no deletions | none (never used) |
| Currencies | all holdings INR | n/a |

**Conclusion:** the jirp file is the only one containing user data (23 purchases, a funded wallet with history, a
watchlist entry), is the newer file, and has the schema of the app version you used. The stock_project file is an empty
schema created on 2026-02-28 by the first run of that line; it holds nothing to migrate and need not be merged.
Observations, not problems:
* The wallet history is 6 deposits, 2 withdrawals and 13 "STOCK BUY" debits, against 23 purchases in the ledger. So 10
  purchases have no matching wallet debit. The wallet history starts on 2026-03-07 and the first purchases are dated
  2026-03-06, so some predate the wallet feature, but that is an inference: the wallet balance therefore may not equal
  "deposits minus purchases", and migration does not (and must not) try to reconcile it. Nothing is changed in the wallet.
* It holds one row per purchase (23 rows, 4 symbols). The new code merges these by weighted average when loaded and
  consolidates them on the next trade of that symbol.

## Procedure (for your approval; run only on a copy)
1. **Close** any running StockVault. Confirm no `portfolio.db-wal/-shm/-journal` beside the file (journal mode is `delete`;
   none exist today).
2. **Hash and back up.** Record `Get-FileHash -Algorithm SHA256` of `jirp\...\portfolio.db`; copy it to a new protected folder
   outside every repo (suggested `D:\Projects\StockVault-live\backup\`), re-hash the copy and confirm the hashes match. Keep the
   recovery-directory copy as a second backup. Never edit those two.
3. **Make the working copy** `D:\Projects\StockVault-live\portfolio.db` (a copy of the verified backup). The app is run with
   `-Dportfolio.db.path=D:\Projects\StockVault-live\portfolio.db` (or with that folder as the working directory).
4. **Dry run first, on a separate disposable copy:**
   `java -cp "target\stock-portfolio-tracker-1.0.0-SNAPSHOT.jar;target\lib\*" MigrationDryRun <copy>` (`tools/MigrationDryRun.java`).
   It prints only pass/fail. Last result on a copy of the jirp file: **22 of 22 passed**: backup written first;
   integrity ok; row counts, per-symbol quantity and cost, wallet, wallet history and ledger identical before and after;
   holdings merge by weighted average with total cost preserved; wallet migrates as INR and every holding is INR (so no
   exchange rate is ever needed for this data); second open is a no-op with no second backup; restoring the backup
   reproduces the legacy data exactly.
5. **Upgrade the working copy** by starting the app on it once (or the dry-run tool). The app writes
   `portfolio.db.pre-migration-<timestamp>.bak` next to it before changing anything, then migrates in one transaction.
6. **Verify** with the same checks against the working copy: integrity_check ok; counts unchanged; wallet balance and history
   unchanged; every holding shows quote status "Unavailable" until the first successful refresh (the stored prices are
   old sample/last values with no freshness stamp and must not be trusted).
7. **Rollback:** close the app and copy the `.bak` (or the step-2 backup) over the working copy, re-hash against the recorded
   hash. The original jirp file was never opened for writing, so rollback of the original is not needed.
8. **Cut-over** is a separate decision: only after step 6, and only on your approval, point your normal launcher at the
   working copy. The original and recovery copies stay as they are.

## Not part of this procedure
* The stock_project database is not merged or migrated.
* `email_settings` and `users` are empty in the jirp file, so no stored e-mail password or account needs protecting there.

## Status: working copy prepared and verified, NOT activated (2026-10-25)
Folder `D:\Projects\StockVault-live\` (outside every repository):
* `backup\portfolio-jirp-C1C15CE1.db`: read-only copy of the jirp file; its SHA-256 equals the original's (recorded in `HASHES.txt`).
* `portfolio.db`: the working copy, upgraded by the application code. Dry run on a separate disposable copy first (22/22), then
  the same 22 checks on the working copy itself (22/22). `portfolio.db.pre-migration-*.bak` was written before the upgrade, and
  `verify\restored-check.db` proves the documented restore reproduces the legacy data exactly.
* `HASHES.txt`: hashes of the original, backup, working copy before and after, and the `.bak`; rollback instruction.
* The original `jirp\...\portfolio.db` is unchanged (hash and timestamp). No launcher, setting or shortcut uses `StockVault-live`.

Pre-existing wallet discrepancy (reported, not altered): 10 of the 23 ledger purchases have no wallet debit. All 10 are dated
2026-03-06; the wallet history begins 2026-03-07 04:11 and every purchase after that has an exactly matching debit (13 of 13,
no amount differences, no orphan debits). The wallet is internally consistent (each balance follows from the previous entry;
stored balance and totals equal the history). No transactions were added and no balance was changed.
Activation (pointing the app at the working copy) remains a separate step awaiting your approval.
