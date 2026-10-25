# Remediation Log

| Commit | Change |
|---|---|
| 61a7759 | Track source (fix `.gitignore`), remove random mock prices, env-based API key, response validation, atomic buy, validation, BigDecimal totals, 14 tests |
| cb5ad7c | Sell transactions, weighted-average cost basis, realized P/L, additive migration, GUI/console sell, injectable HTTP layer, 32 tests |
| 6a8cc13 | Maven build, CI workflow, SpotBugs fixes, dependency upgrades, schema-init failure handling, README rewrite, legacy docs archived, audit documents, 35 tests |
| d06e95c | DB errors propagate (failed load aborts startup, never an empty portfolio); single-flight SwingWorker refresh with disabled button; price-freshness column; transaction ledger; CSV export; realized P/L card; explicit local transaction timestamps; **fix: toolbar buttons were never shown** (both panels added to `BorderLayout.NORTH`); emoji removed from labels; 47 tests |
| 1aed164 | Integration status, feature plan, GUI checklist, dependency/license review, Vercel research, reports |
| baa86ec | **Ledger tie-order bug fixed** (probable cause of the intermittent test failure; deterministic regression test via injectable Clock); Alpha Vantage failure reasons, invalid key no longer reported as "symbol not found", bounded retries for network/5xx only, batch stops on key/quota problems; 19 new tests |
| bcaf6e8 | Pre-migration backup (VACUUM INTO, bound parameter) and all-or-nothing schema upgrade; 7 migration tests (rollback proven by mutation); CSV timestamps at second precision; `tools/GuiAccept.java` (31 scripted GUI checks); 74 tests |
| next | Final-gate documentation: backup/restore guide, integration/dependency/test/GUI reports |

Decisions: `TRADE_MODEL.md`, `FEATURE_PLAN.md`. Evidence: `TEST_RESULTS.md`, `DEPENDENCY_AUDIT.md`, `INTEGRATION_STATUS.md`.
