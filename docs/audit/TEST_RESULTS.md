# Test Results (final gate, 2026-10-24)

Environment: Windows 11, Temurin JDK 21.0.12, Apache Maven 3.9.9 (from Maven Central, SHA-512 verified).

| Command / check | Exit | Result |
|---|---|---|
| `mvn -B clean verify spotbugs:check` | 0 | **74 tests**, 0 failures, 0 errors, 0 skipped; SpotBugs 0 reported (3 reviewed exclusion groups) |
| `mvn test` x4 normally, x4 with 8 busy-loop processes | 0 each | 8/8 runs passed (74 tests each) |
| Temporary always-failing test (earlier) | 1 | Failures fail the build |
| Scripted GUI harness `tools/GuiAccept.java` (temp DB, in-process Swing components) | 0 | **31/31 checks passed**, no uncaught exceptions (see `GUI_ACCEPTANCE_CHECKLIST.md`) |
| Console run from the built JAR with a temp DB | 0 | Starts and exits cleanly |
| `jpackage --type app-image` | 0 | `StockTracker.exe` + runtime built in a scratch directory (not committed; the exe was not launched) |
| OWASP Dependency-Check | not completed | no NVD key; **not a clean result** |

Test classes (74): AlphaVantageServiceTest 8, AlphaVantageHttpTest 6, **AlphaVantageProviderTest 17**, PortfolioServiceTest 6, TradeTest 12, DatabaseManagerTest 3, **MigrationSafetyTest 7**, ReliabilityAndFeatureTest 10, **LedgerOrderingTest 2**, PriceAgeTest 3. All use temporary databases, loopback fakes and no API key or internet. The real `portfolio.db` was never opened (modification time unchanged: 2026-02-28 00:37).

## The intermittent failure
- **Original run:** one `mvn clean verify spotbugs:check` reported "Tests run: 47, Failures: 1". Its Surefire report was overwritten by the next run, so the failing test name and message were **never captured**. The only leftover Surefire file is an unrelated classpath warning.
- **Most probable cause (inferred, not proven for that run):** `PortfolioService.searchTransactions` ordered "newest first" by timestamp, then by id. Unsaved transactions all have id 0 and consecutive `LocalDateTime.now()` calls can be equal on Windows, so ties kept insertion order (oldest first). The test `ledgerFiltersBySymbolTypeAndDate` asserts the SELL is first, which matches a `Failures: 1` (assertion) outcome, not an error.
- **Reproduced deterministically** with an injectable `Clock`: `LedgerOrderingTest` failed before the fix (`expected [SELL, BUY, BUY] but was [BUY, BUY, SELL]`) and passes after. Fix: iterate newest-inserted first and use a stable sort on time.
- Other suspects (timing in the loopback HTTP tests, refresh single-flight test) were reviewed: no shared state beyond the per-test temp DB and the static connection that each test closes; they passed in all 8 later repeats. I cannot prove that the original failure was this defect rather than another.

## Not verified
A human click-through of the GUI; live Alpha Vantage responses (wording heuristics for quota/key messages are unverified); GitHub Actions run; Linux/macOS; real `portfolio.db` migration; OWASP scan.
