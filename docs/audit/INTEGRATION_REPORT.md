# StockVault integration report

Consolidation of two local copies of the same project into `D:\Projects\StockVault`.

* **jirp** (`D:\Projects\jirp\...`): the feature-rich app (premium dashboard, wallet, AI, voice, e-mail, news, currency,
  multi-provider quotes). Commit `c9214ba`.
* **stock_project** (`D:\Projects\stock_project\...`): the hardened core (sell + weighted-average cost + realized P/L,
  safe migrations, Alpha Vantage error handling, ledger, CSV, Maven, tests). Commit `7129cf1` plus 4 uncommitted files.

Common Git ancestor: `d622aa5`. That commit contains **no Java source**, so the ten conflicting files had no merge base and
were reconciled by reading both versions (originals preserved in
`D:\Projects\_recovery_stockvault_20261024-212330\conflicts\`). Other `docs/audit/*` files describe the stock_project line
as it was before integration; this report and `docs/INTEGRATION_DESIGN.md` describe the consolidated project.

## 1. Git state
Branch `integration` (= `main` = jirp `c9214ba`); stock_project history is reachable as `stock_project/main` (`7129cf1`).
46 commits are reachable, none created by this work. Nothing is committed, staged, pushed or fetched; the consolidation is
an uncommitted working tree. The 5 files jirp had deleted locally stay deleted; `sources.txt` stays untracked.

## 2. How the ten conflicting files were reconciled
| File | Result |
|---|---|
| `database/DatabaseManager` | stock_project's backup + all-or-nothing migration engine; jirp's tables (users, watchlist, email_settings, wallet, wallet_transactions) and columns folded into it; jirp's email/user helpers kept. `getConnection()` returns null on failure (stock_project contract); `requireConnection()` added. |
| `database/PortfolioDAO` | stock_project's atomic `recordTrade`, now with a wallet leg; jirp's watchlist and currency/sector/risk columns; per-call connection. |
| `service/PortfolioService` | jirp's API and features (watchlist, alerts, currency, analytics, wallet) over stock_project's trade engine, refresh guard and ledger. |
| `service/AlphaVantageService` | stock_project's hardened client; jirp's history call added (with limit/error detection); key via `ApiKeyManager`. |
| `service/StockPriceService` | stock_project's interface; jirp's methods kept as defaults derived from `getStock`. |
| `model/Transaction`, `PortfolioItem`, `Stock` | `Transaction` = stock_project's (superset). `PortfolioItem`/`Stock` carry both access styles over the same fields. |
| `MainUI` | launches the premium dashboard (`--classic` = stock_project's window). jirp's startup code that bought AAPL/TSLA/GOOGL on every launch is gone. |
| `Main` | stock_project's console app (+ wallet menu, quote-confirmed sell). |
Also merged: `.gitignore`, `README.md`, `pom.xml` (stock_project's, extended), `spotbugs-exclude.xml`.

## 3. Behaviour changed on purpose
Fabricated data and unsafe behaviour found in the jirp code were removed rather than preserved:
1. 30-second **random price jitter timer** in `PortfolioService` (mutated every holding's price).
2. `AlphaVantageService` returned `100 + random*100` when a quote could not be parsed.
3. Dashboard market ticker: random prices for 20+ default symbols, random movement and random daily change.
4. "Real-Time" analysis dialog: random 30-day history, 2-second random walk, high/low = price +/- 2%.
5. Stock detail chart and 30-day chart: random series; hard-coded 646.18 fallback price; random-walk price label.
6. Sparklines (holdings, watchlist, catalogue), portfolio-vs-benchmark chart, risk scatter: random data. Now: entry->now and
   previous-close->now lines, invested capital from the ledger plus current value, and the recorded risk level.
7. Market catalogue prices/changes (static sample numbers) were displayed as quotes; now shown only when the portfolio or
   watchlist holds a real provider price, otherwise a dash.
8. "Export History (PDF)" announced "exported 52 transactions" and wrote nothing; the holdings CSV hard-coded the date
   2026-03-05. Both now write real, UTF-8, formula-injection-safe CSV through `CsvExporter`.
9. Dashboard buys hard-coded sector/cap/risk "Tech/Large/High"; now defaults by symbol.
10. "Set Alert" showed "Alert set" without creating one; it now creates it.
11. The AI action handler reported "Sold ..." without checking the result; it now reports what happened.
12. Refresh always said "Prices updated successfully"; it now reports failures and the reason.
13. The service layer opened `JOptionPane`s and swallowed trade errors; trades now throw, the UI shows the message.
14. API-key literals: `ApiKeyManager.java` (git-ignored, key-bearing) replaced by a clean tracked class that reads environment
    variables / an ignored local file. **The original file with the key is only in the original folders and the recovery
    copy; treat that key as exposed to anything that could read those folders and consider rotating it.**
15. `DatabaseManager.clearAllData()` (test helper) no longer touches the wallet.

## 4. Feature matrix
Status: **Verified** = exercised by an automated test or scripted GUI run listed below. **Present** = code compiles and is
wired in, but was not exercised (needs keys, network, hardware or a human). **Illustrative** = deliberately not market data.

| Feature | Source | Status | Depends on | Evidence |
|---|---|---|---|---|
| Premium Swing dashboard (9 pages), startup | jirp | Verified (smoke) | flatlaf, jfreechart | `DashboardSmoke` 14/14: builds, all pages open, rebuild with data, no EDT errors; packaged JAR and jpackage exe start |
| Classic window (holdings, ledger, charts) | stock_project | Verified (smoke) | jfreechart | `GuiAccept` 31/31 |
| Buy / sell, weighted-average cost, realized P/L | stock_project | Verified | sqlite | `TradeTest` 12, `PortfolioServiceTest`, `WalletTradeIntegrationTest` |
| Wallet debit/credit, atomic with holding + ledger | jirp + new | Verified | sqlite | `WalletTradeIntegrationTest` 14 (insufficient funds, insufficient holdings, bad price/quantity, injected DB failure on buy and sell) |
| Deposits / withdrawals | jirp (now atomic) | Verified | sqlite | `WalletTradeIntegrationTest` |
| SQLite persistence; safe migration + backup | both | Verified | sqlite | `MigrationSafetyTest` 7 (stock_project format), `JirpDatabaseMigrationTest` 4 (jirp format, synthetic) |
| Sell only at a confirmed live quote | new (your decision) | Verified | provider | tests (no quote / invalid quote / valid quote) + `DashboardSmoke` (confirmation shows price+proceeds; no-quote aborts) |
| Alpha Vantage client, error classification, retries | stock_project | Verified vs local fake HTTP | json | `AlphaVantageHttpTest`, `AlphaVantageProviderTest`, `AlphaVantageServiceTest`; **not verified against the live service** |
| Finnhub / Twelve Data clients | jirp | Present | keys, network | only through failover tests with fakes; their HTTP parsing is untested |
| Failover chain, no price when all fail | jirp + new | Verified | | `ProviderLayerTest` |
| Price history parsing (Twelve Data / Alpha Vantage) | new | Verified | json | `ProviderLayerTest` |
| Charts from real history | jirp + new | Present | provider | built and opened in smoke without errors; data path untested live |
| API-key configuration | new | Verified | | `ProviderLayerTest` (env first, ignored file, round-robin, empty means unset); missing-key messages in `GuiAccept` and `DashboardSmoke` |
| Dashboard totals; price freshness ("Not refreshed"/"STALE") | stock_project | Verified in classic window only | | `PriceAgeTest`, `GuiAccept`. **The premium dashboard has no freshness indicator** (open item) |
| Ledger filters / ordering | stock_project | Verified (classic window) | | `LedgerOrderingTest`, `ReliabilityAndFeatureTest`, `GuiAccept`. The premium Transactions page has jirp's own search/filter, not driven |
| CSV export + formula-injection protection | stock_project | Verified (unit + classic); wired into premium | | `ReliabilityAndFeatureTest`, `GuiAccept` #11. Premium export buttons not driven (file chooser) |
| Background refresh, single-flight, Swing-safe | both | Verified | | `ReliabilityAndFeatureTest`, `DashboardSmoke` #8 |
| Watchlist (persist, refresh, dedupe) | jirp | Verified | | `AlertAndWatchlistTest` |
| Price alerts (create, trigger once) | jirp | Verified (service) | notifications | `AlertAndWatchlistTest`; tray notification itself not observed |
| Currency conversion | jirp | Present | exchange-rate key | not tested; **falls back to fixed static rates (USD/INR 83, EUR 0.92, SAR 3.75) when the API fails** |
| Market news | jirp | Present | news keys | not tested |
| AI assistant / rebalancer / rule-based analysis | jirp | Present | Groq key | not tested; trade commands from the AI now report real outcomes |
| Voice input / text-to-speech | jirp | Present | AssemblyAI key, microphone | not tested |
| Daily e-mail report | jirp | Present | JavaMail, Gmail app password | not tested; nothing was sent |
| Sharpe ratio, beta | jirp | Illustrative | | fixed assumptions (volatility 0.15, risk-free 5%, sector betas); labelled in code |
| Tax estimate | jirp | Illustrative | | flat 15% of unrealized profit |
| Login/welcome screen | jirp | Present (cosmetic) | | any input proceeds; only reached via `PremiumStockDashboard.main` |
| Local user accounts (`DatabaseManager.registerUser/loginUser`) | jirp | Present, unused by the UI | | unsalted SHA-256; migrated unchanged |
| Static analysis | stock_project | Verified | | SpotBugs 0 findings (see 6) |
| JAR + jpackage app-image | stock_project | Verified | | see 6 |

## 5. Dependency map
| Library (in `lib/`) | Used by | Maven |
|---|---|---|
| flatlaf 3.4 | `MainUI`, `PremiumStockDashboard` | dependency, from `lib-repo/` |
| javax.mail 1.6.2, activation 1.1 | `EmailService` | dependencies, from `lib-repo/` |
| jfreechart 1.5.4 -> 1.5.6 | all chart code | Central |
| json 20231013 -> 20260814 | Alpha Vantage, AssemblyAI, `PriceHistory` | Central |
| sqlite-jdbc 3.45.1.0 -> 3.53.4.0 | all persistence | Central |
| slf4j-api / -simple 2.0.9 | runtime logging | Central |
| jcommon 1.0.24 | nothing (`org.jfree.ui` import migrated to `org.jfree.chart.ui`) | none; kept in `lib/` |
| gson 2.10.1 | nothing | none; kept in `lib/` |
| jna 5.13.0, vosk 0.3.45 | nothing in the current tree (jirp had already deleted `VoiceRecognitionServer`; only `docs/DOWNLOAD-VOSK*.bat` and `docs/voice-recognition.html` refer to Vosk) | none; kept in `lib/` |
| jakarta.servlet-api 6.0.0 | nothing | none; kept in `lib/` |
| junit-platform-console-standalone (`test-lib/`) | nothing (Maven uses junit-jupiter) | none; kept |
Nothing in `lib/` or `test-lib/` was removed. The `lib-repo/` copies are byte-identical (checked with `cmp`).

## 6. Verification results
All run from `D:\Projects\StockVault` with Maven 3.9.9 (`-o`, offline), JDK 21.0.12, against temporary databases only.
* `mvn clean verify spotbugs:check`: **111 tests, 0 failures, 0 errors, 0 skipped**; SpotBugs **0** findings; JAR built.
* 111 = 76 inherited (74 committed + 2 in stock_project's uncommitted files) + 35 new
  (`WalletTradeIntegrationTest` 14, `ProviderLayerTest` 12, `AlertAndWatchlistTest` 5, `JirpDatabaseMigrationTest` 4).
* Stability: 4 further serial `mvn test` runs, all 111/111. Two overlapping runs I started by mistake failed with "Tests run: 0 /
  junit-jupiter failed to discover tests"; I reproduced that by running `clean` and `test` concurrently, so it is an artifact of
  concurrent builds on one `target/`, not of the code.
* Inherited tests were not weakened. Their only changes: a shared fixture (`TestWallet.fund()`) funds the temporary wallet
  because trades now cost money (6 classes), and one test deletes the pre-funded temp file before building its legacy
  database. No assertion was changed.
* SpotBugs: 45 findings in jirp-origin code at first. Real defects were fixed (default-encoding writers, `\n` in format strings,
  ignored `File.delete()`, dead stores, non-static inner classes, mutable static array, cache exposure, duplicate branch).
  The rest are excluded by pattern and class in `spotbugs-exclude.xml` with reasons (shared-service references held by design,
  singleton accessor, constructor guards, defensive catches in the 7,000-line dashboard).
* Scripted GUI: `GuiAccept` (classic window) 31/31; `DashboardSmoke` (premium dashboard) 14/14. These drive real Swing components
  in-process; they are **not** human acceptance.
* Packaged JAR (`java -jar`) and `jpackage --type app-image` executable (168 MB, built in a scratch folder, not in the project)
  both started against a temporary database, created the schema, reported "Portfolio UI launched", and stayed up until killed.
  Outbound traffic was pointed at an unroutable local proxy during every GUI/JAR run.
* Defects found only by running the packaged JAR and fixed: the manifest `Class-Path` omitted system-scoped libraries (FlatLaf
  missing at startup), which led to the `lib-repo/` solution; `MainUI` now reports linkage errors in its startup dialog.
* Secret scan over every tracked or trackable file found no key-like literal. The real `portfolio.db` files were never opened
  (hashes and timestamps unchanged); no database exists in the project directory.

## 7. Open items and decisions for you
1. **Commit / history.** Nothing is committed. Two ways forward, both without rewriting history, both needing your explicit
   go-ahead: (a) one ordinary commit of the reconciled tree on `integration` (stock_project history stays reachable as the
   `stock_project/main` branch), or (b) a merge commit with `stock_project/main` as second parent, so Git records both lines
   as ancestors. A plain `git merge` would conflict on every file in section 2 (add/add, no common source), so (b) would be made
   by committing this already-reconciled tree.
2. **Rotate the jirp API keys** (section 3, item 14). The key-bearing `ApiKeyManager.java`, stray `.class` files under `src/`
   (including a compiled `ApiKeyManager.class`) and `bin/` still exist in the original folders and in the working tree copied
   into StockVault (git-ignored). They are not needed; remove the stray compiled files from StockVault at cleanup.
3. **Classic window sells** use a price the user types (stock_project behaviour, explicit user input). The premium dashboard
   requires a confirmed live quote. Tell me if the classic window should follow the same rule.
4. **Premium dashboard freshness indicator** ("Not refreshed"/"STALE") was not ported; the data (`priceUpdatedAt`) is there.
5. **Wallet currency.** Quotes are in the provider's currency (USD for US tickers); the wallet is shown in rupees and debited
   `quantity x price` with no conversion (jirp behaviour, preserved). Converting needs a rate source decision.
6. **Maven location.** The Maven I used is in an earlier session's temporary folder
   (`...\Temp\claude\D--Projects-stock-project-...\scratchpad\mvn-dl\apache-maven-3.9.9`). It may be cleaned up. No Maven is installed
   system-wide, and I did not download one.
7. **Not verified at all:** live providers, news, AI, voice, TTS, e-mail sending, currency API, human GUI use, the GitHub Actions
   workflow (it runs on Linux; the `file:///${project.basedir}/lib-repo` repository URL was only exercised on Windows),
   OWASP Dependency-Check, migration of your real databases (only synthetic copies of their formats).

---

## 8. Phase 7 addendum: trading, currency, database, secrets, build
Details: [`../CURRENCY_AND_QUOTES.md`](../CURRENCY_AND_QUOTES.md), [`DATABASE_MIGRATION_PLAN.md`](DATABASE_MIGRATION_PLAN.md),
[`SECRET_CONTAINMENT.md`](SECRET_CONTAINMENT.md), [`CLEANUP_MANIFEST.md`](CLEANUP_MANIFEST.md).

**Resolves open items 3, 4 and 5 above.**
* Classic window sell, console sell and premium dashboard now share one quote flow: fetch, show price/currency/age/wallet
  effect, confirm, execute at exactly that quote. The service itself enforces quote freshness (60 s), currency, and the
  rate rules; a stale, currency-less, mismatched or un-convertible trade throws `TradeBlockedException` before any write.
* The premium dashboard shows Fresh / Stale / Unavailable per holding (replacing a column that said "Live Data" for every row).
* The wallet has an explicit stored currency (`wallet.currency`, default and migration value INR). Conversion needs a validated,
  fresh rate (`CurrencyService.requireRate`); there is no fallback for money. Display-only totals may still approximate.
* Fixed while testing: refresh stamped prices with the wall clock rather than the service clock, and a future timestamp was
  reported as Fresh.

**New tests (17 in `TradePolicyTest`, plus fixture changes):** stale quote at 60 s / 61 s / future; missing, zero, negative, NaN
and infinite quotes; unknown quote currency; currency mismatch on sell, buy, and averaging; conversion at a validated rate with
the rate recorded; failed, invalid, wrong-pair and outdated rates; rate caching and expiry; display fallback flagged but never used
for money; rollback of a converted buy and sell under an injected database failure; refresh across currencies; wallet currency
rules; quote-based buy stamping the quote time. Inherited tests were not weakened; their fixtures now declare the wallet currency
(USD) and give fake quotes a currency.

**Results.** `mvn -o clean verify spotbugs:check`: **128 tests, 0 failures, SpotBugs 0**, JAR built; two further serial runs
128/128. Scripted GUI: classic window **32/32**, premium dashboard **17/17** (new checks include Unavailable then Fresh).
`tools/MigrationDryRun.java` on a disposable copy of the jirp database: **22/22**.

**Maven and the Wrapper.** Maven 3.9.9 is not installed system-wide and is not on PATH. The offline build used a copy in an
earlier session's temp folder (10.1 MB, created 2026-10-24 18:47), which the OS or a cleanup could remove; it cannot be
relied on. A Maven Wrapper is **not** available offline: the wrapper plugin is not in `~/.m2`, no Maven distribution zip is
cached anywhere, and `mvnw` itself downloads Maven from the network on first use. I therefore did not add one (it would
introduce a network step). Options needing your decision: (a) install Maven 3.9.9 properly or copy that folder somewhere
stable; (b) approve adding the wrapper (`mvn wrapper:wrapper`, which downloads the wrapper plugin and, on first run, Maven
itself, then pins the version so builds are reproducible); (c) rely on the CI workflow, which installs its own toolchain.

---

## 9. Phase 7 follow-up (2026-10-25)
* **Live-database working copy** prepared and verified, not activated: see `DATABASE_MIGRATION_PLAN.md` ("Status").
* **Approximate vs validated totals.** `CurrencyService.convertChecked` and `PortfolioService.currentValueChecked / totalInvestmentChecked
  / profitLossChecked / convertToBaseChecked` report whether a display figure used fixed fallback rates. The premium dashboard marks
  such figures with "≈" (stat cards, header P/L, both holdings tables), shows an amber "≈ APPROXIMATE RATES" banner while any
  approximation is in use, and exports, e-mail and AI context carry a note. Fixed a pre-existing inconsistency: the Value and
  Profit/Loss cells of both holdings tables were labelled "converted" but were not converted.
* **CI feasibility.** A clean copy of exactly the files Git would contain (no ignored/local files) builds with the workflow's
  command: `mvn -o clean verify spotbugs:check` gives 134 tests, SpotBugs 0, JAR built. The workflow YAML parses and uses Temurin 17
  against a `--release 17` compile. Not verifiable here: an actual GitHub Actions run, Linux path handling of the `file:///` lib-repo
  URL, and online dependency resolution.
* **Change review.** 28 modified files, 4 pre-existing deletions, 75 new files. Public-API retention check against both source
  versions: nothing from stock_project is missing; the jirp signatures that are gone are intentional (non-atomic wallet methods
  `processStockPurchase/Sale`, `PortfolioDAO.updatePortfolioItemQuantity`, jirp's old `StockValidator` which jirp had already deleted
  locally and stock_project's version replaces, and an anonymous listener that belonged to the removed random-walk timer);
  `getCurrentPrice/updateStockPrice` remain available as interface defaults.

---

## 10. Phase 8: audit, hardening and home screen (2026-10-25)
Full details: [`FULL_AUDIT_2026-10-25.md`](FULL_AUDIT_2026-10-25.md) (findings, fixes, tests, integrations), `SECRET_CONTAINMENT.md` (addendum on a
second credential-like literal that **is** in pushed history), `CLEANUP_MANIFEST.md`. Also new: the Java/Swing **home screen** (`HomeScreen`,
`Theme`, `PortfolioSummary`) as the default entry point.
* The previous report statements that "nothing containing a credential was ever pushed" hold for the 9 API-key literals only; see the addendum.
* Disposable database copies (18 files) and `target\` were deleted after verification; nothing else was.
