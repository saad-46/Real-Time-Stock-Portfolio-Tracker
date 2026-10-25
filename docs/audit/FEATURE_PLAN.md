# Feature Plan and Acceptance Results

Chosen from the real architecture (single provider with a tiny free quota, one local SQLite file, no price history stored).

| # | Feature | Value | Needs | Effort | Risk | Status |
|---|---|---|---|---|---|---|
| 1 | Dashboard incl. realized P/L | High | existing data | Low | Low | **Done** |
| 9 | Price freshness / stale flag | High (prevents silently trusting old prices) | one new column | Low | Low | **Done** |
| 5 | Searchable transaction ledger | High | existing data | Low | Low | **Done** |
| 7 | CSV **export** (transactions, holdings) | Medium | none | Low | Low (formula injection handled) | **Done** (UI exports transactions; `CsvExporter.holdingsCsv` has no GUI entry yet) |
| 7b | CSV **import** with duplicate protection | Medium | transaction identity rules, replay of cost basis | High | High (corrupts cost basis if wrong) | Not done |
| 3 | Historical snapshots / performance chart | High | needs periodic price history; free API quota makes daily capture for many holdings unreliable | Medium-High | Medium | Not done |
| 4 | Watchlist + target-price alerts | Medium | polling loop, quota awareness, notification channel | Medium | Medium | Not done |
| 2 | Allocation by sector | Medium | sector data is not available from the current provider call | - | - | Not feasible without another data source |
| 6 | Risk metrics (volatility, Sharpe) | Medium | long price history (see 3) | Medium | High (misleading with little data) | Not done |
| 8 | Configurable refresh interval / quota awareness | Medium | scheduler, quota counter | Medium | Medium | Not done (manual refresh with overlap guard only) |
| 10 | Notifications | Low | SMTP credentials, secure storage | Medium | High | Not justified now |

Recommended next: 3 (snapshots; start recording a daily portfolio-value row on refresh), then 8, then 4.

## Acceptance criteria and evidence

| Feature | Criteria | Evidence |
|---|---|---|
| Realized P/L card | Equals the sum of SELL `realized_pl`; unaffected by buys; shown next to unrealized P/L | `TradeTest`, GUI render: 10@100 + 10@120, sell 5@130 -> avg 110, realized $100.00, unrealized -$150.00 vs. current price $100 (never refreshed) |
| Freshness | Never-refreshed prices say "Not refreshed"; refreshed ones show age; > 24 h flagged STALE; timestamp survives restart and trades; failed refresh does not stamp | `PriceAgeTest` (3), `ReliabilityAndFeatureTest` (refresh stamping, persistence, trades preserve) |
| Ledger | Filter by symbol substring, BUY/SELL, inclusive dates; newest first; invalid dates rejected in the UI; times identical before and after restart | `ReliabilityAndFeatureTest.ledgerFiltersBySymbolTypeAndDate`, `transactionTimeIsStableAcrossRestart`; filters and CSV export driven by the scripted GUI harness (31 checks, `GUI_ACCEPTANCE_CHECKLIST.md`); no human visual acceptance |
| CSV export | RFC 4180 quoting, UTF-8, plain-decimal numbers, empty realized P/L for buys, cells starting `= + - @` neutralised, overwrite requires confirmation | `ReliabilityAndFeatureTest` (3 CSV tests); real file chooser and overwrite prompt driven by the harness |

Behavior note: a holding that was never refreshed shows its first purchase price as "current price"; the "Not refreshed" label marks it.
