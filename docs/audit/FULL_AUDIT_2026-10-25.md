# Full audit, 2026-10-25

Scope: the whole consolidated application, not only recent changes. Method: code reading of every area listed below, targeted
searches (process execution, TLS, deserialization, logging, temp files, UI-thread network calls, shutdown), regression tests
written first where a bug could be reproduced (they failed, then the fix made them pass), then the full build, static analysis
and scripted GUI runs. Nothing here was tested against live providers or with real credentials.

Severity: **High** = data/money loss, credential exposure or code execution; **Medium** = wrong figures, UI freeze, weakened
protection; **Low** = hygiene.

## Confirmed findings and fixes
| # | Sev | Finding (evidence) | Fix | Regression test |
|---|---|---|---|---|
| 1 | High | **A password-like literal was hard-coded in tracked source** (`EmailService.java`, `PremiumStockDashboard.java` help text) and is present in 3 commits: `e3fb5ba` and `72d4c6f` (both 2026-03-05) **are on `origin/main`, i.e. were pushed to GitHub**; `fcf3669` (2026-03-07, a clean-up that removed it from docs) is local only. Whether it is a live password is unknown. | Removed from all source and docs. **History not rewritten (not permitted); the password must be changed at Google.** | repo-wide value search in the verification step |
| 2 | High | **Command injection in text-to-speech**: the Windows command interpolated AI/news text into a PowerShell string (`Speak('%s')`), so a single quote ran arbitrary commands; macOS/Linux passed text as an argument (option injection). | Text now travels in an environment variable (Windows) or on stdin (mac/Linux), is sanitised and capped; `-NoProfile -NonInteractive`; process timeout. | `SecurityRegressionTest` (6 payloads) |
| 3 | High | **Portfolio e-mailed to a hard-coded third-party address** by the "Send Email Now" button, regardless of settings. | Recipient is the configured account only; refuses when e-mail is not configured; asks for confirmation naming the recipient. | manual/GUI path; logic covered by `isConfigured` test |
| 4 | High | **"Pay Tax from Wallet" moved money from an approximate, wrong-currency display estimate** with no confirmation, and paid no tax. | Now "Set Tax Aside": refused when the estimate is approximate or in another currency than the wallet; explicit confirmation stating it pays no tax; honest wallet-history description. | `AuditRegressionTest` (description, cents) |
| 5 | High | **AI assistant executed trades (including "sell all") without showing what it parsed**; reply interpreted by substring (`contains("buy")`). | Plan / confirm / perform split; strict JSON parsing with ticker, quantity and price validation; confirmation dialog describing the exact action; old `executeAction` deprecated. | `SecurityRegressionTest` (12 malformed replies, substring case, sell-all) |
| 6 | Medium | **Wallet precision**: ten deposits of 0.10 gave 0.9999999999999999; a withdrawal of exactly the balance could be refused; sub-cent amounts accepted. | All wallet SQL rounds to cents; amounts validated/rounded to whole cents; NaN/infinite/huge refused. | `AuditRegressionTest` (red before, green after) |
| 7 | Medium | **Failed exchange-rate lookups were retried on every conversion** (50 conversions = 50 attempts), which can freeze a table-building UI for minutes when the network is slow. | Failures are remembered 30 s; an explicit "Refresh prices" clears the memory. | `AuditRegressionTest` (1 attempt, then retry after expiry) |
| 8 | Medium | **SMTP certificate validation disabled** (`mail.smtp.ssl.trust=<host>`), enabling a man-in-the-middle to read the app password; SMTP debug output on; no timeouts. | TLS required, server identity checked, TLS 1.2/1.3 only, debug off, 10/20 s timeouts. | `SecurityRegressionTest` |
| 9 | Medium | `EmailService.isConfigured()` was true with an **empty password**, so the app believed e-mail was set up. | Requires non-blank address and password. | `SecurityRegressionTest` |
| 10 | Medium | "Test Email" button sent mail **on the Swing event thread** (UI freeze up to the SMTP timeout). | Runs in a `SwingWorker`. | GUI path |
| 11 | Medium | **Database path depended on the working directory** (`./portfolio.db`); a packaged executable would create or look for the file in an arbitrary folder. | `AppPaths`: property, `STOCKVAULT_DB`, an existing `./portfolio.db` (existing installs keep working), else the per-user data folder; API-key file resolved the same way. | `AppPathsTest`; JAR and exe launched from an unrelated directory |
| 12 | Medium | Finnhub market cap was labelled in rupees although the profile is in the company's currency. | Labelled with the profile's currency code. | `ProviderHttpTest` |
| 13 | Medium | Wallet messages hard-coded "₹" and showed the typed amount rather than the applied cents. | Uses the wallet's currency and the applied amount. | GUI path |
| 14 | Low | `PortfolioSummary`-style value objects exposed mutable lists (SpotBugs). | Immutable snapshots. | SpotBugs |
| 15 | Low | The database was not closed on exit. | Shutdown hook closes it. | n/a |

## Areas reviewed with no defect found
* **SQL injection**: every user-influenced value is bound; the only concatenated identifiers (`PRAGMA table_info(<table>)`, `ALTER TABLE`) use constant names; the backup path is a bound parameter.
* **Deserialization / reflection / dynamic code**: none (`ObjectInputStream`, `Class.forName` for the JDBC driver only).
* **Atomicity**: a trade is one transaction covering wallet, holding and ledger; injected failures on buy and sell, converted or not, roll everything back (`WalletTradeIntegrationTest`, `TradePolicyTest`). Success messages are shown only after the service call returns.
* **Interrupted/invalid databases**: a file that is not a database is refused and left byte-identical; a failed migration rolls back; repeated startups are idempotent; a brief lock held by another connection is waited for.
* **Network on the UI thread**: news, AI, voice and price calls run in threads/workers; the home-screen harness proves no provider call runs on the event thread.
* **Secrets in logs**: provider errors report status classes, never URLs or keys (asserted for Finnhub, Twelve Data, exchange rates).

## Not fixed (reported)
| Sev | Item | Why / recommendation |
|---|---|---|
| Medium | The Gmail app password, when entered in Settings, is stored **in clear text** in the database (`email_settings`). | Needs an OS keystore or user-supplied master key (design decision). Prefer `GMAIL_APP_PASSWORD` in the environment; the settings screen still offers storage. |
| Medium | Local accounts (`DatabaseManager.registerUser/loginUser`) use **unsalted SHA-256**; the welcome/login screen is cosmetic (any input proceeds) and is no longer on the normal start path. | Remove or replace with PBKDF2/Argon2 before anything depends on it. |
| Medium | Dependency vulnerabilities **not scanned**: no network and no NVD key (OWASP Dependency-Check was not run). Versions: sqlite-jdbc 3.53.4.0, json 20260814, jfreechart 1.5.6, flatlaf 3.4, javax.mail 1.6.2 (old line), slf4j 2.0.9. | Run Dependency-Check or Dependabot in CI with network. |
| Low | Time zones: new ledger rows use local time, legacy rows UTC; DST ambiguity possible. | Store UTC instants in a future schema change. |
| Low | `PortfolioService.removeStock` deletes a holding without ledger/wallet effect; unused. | Remove when convenient. |
| Low | Cross-platform: only Windows was exercised. Paths use `java.nio`; TTS, tray and file choosers are untested on Linux/macOS. | Test on those platforms. |

## Integrations: tested, mocked, unverified
| Integration | Status |
|---|---|
| Alpha Vantage | Mocked HTTP (local server): auth, quota, HTTP errors, malformed bodies, history. **Not verified live.** |
| Finnhub, Twelve Data | Mocked HTTP: auth, quota, 5xx, error bodies, malformed/empty/zero/negative prices, currency, key encoding and non-leakage. **Not verified live.** |
| Exchange rates | Mocked HTTP and fake source: valid/invalid/stale/wrong-pair rates, caching, failure memory, no key. **Not verified live.** |
| News (GNews/NewsData), Groq AI, AssemblyAI voice, ElevenLabs/TTS audio, SMTP sending | Code reviewed; AI action parsing and TTS command construction and SMTP settings are unit-tested; network behaviour **unverified**. |
| Tray notifications | Headless-safe; not observed on a desktop. |
