# StockVault

A Java 17+ / Swing desktop portfolio tracker: live quotes from several providers, buy/sell with a wallet, weighted-average
cost and realized profit/loss, a transaction ledger with CSV export, watchlist and price alerts, news, currency
conversion, and optional AI / voice / email features. Data is stored in a local SQLite file.

This project is the consolidation of two earlier copies (`jirp` = the feature-rich app, `stock_project` = the hardened,
tested core). Details of what came from where, what was verified and what was not:
[`docs/audit/INTEGRATION_REPORT.md`](docs/audit/INTEGRATION_REPORT.md) and [`docs/INTEGRATION_DESIGN.md`](docs/INTEGRATION_DESIGN.md).

## Requirements
* JDK 17 or newer (built and tested with Temurin 21)
* Apache Maven 3.9 (to build), with Java 21. On Windows: unpack the official binary zip (verify its published SHA-512) to a stable folder
  such as `C:\Tools\Apachepache-maven-3.9.9`, add its `bin` folder to your *user* PATH, and open a new PowerShell. Check with `mvn -version`
  (it must report the Java 21 install). There is no Maven Wrapper: it would download Maven and a plugin on first use, which breaks offline builds.
  Offline build: `mvn -o clean verify spotbugs:check` (needs the dependencies already in `%USERPROFILE%\.m2`; if one is missing,
  Maven names the exact artifact). A running StockVault holds `target\*.jar` open, so close it before `clean`.
  Demo mode: `.
un-stockvault.ps1 -Mode Demo`.
* Optional API keys (see Configuration). Without keys the app runs, but live prices, news, AI, voice and email are
  unavailable and say so; it never substitutes made-up numbers.

## Run
```
mvn clean package
java -jar target/stock-portfolio-tracker-1.0.0-SNAPSHOT.jar              # landing page, then sign-in, then the home screen
java -jar target/stock-portfolio-tracker-1.0.0-SNAPSHOT.jar --home       # straight to the home screen (verified summary), no sign-in
java -jar target/stock-portfolio-tracker-1.0.0-SNAPSHOT.jar --dashboard  # straight to the full dashboard
java -jar target/stock-portfolio-tracker-1.0.0-SNAPSHOT.jar --classic    # simpler window with ledger + CSV export
java -cp "target/stock-portfolio-tracker-1.0.0-SNAPSHOT.jar;target/lib/*" com.portfolio.Main   # console version
```
**Landing page and accounts.** The start page (Java Swing/Java2D) shows the hero over a photograph of the BSE building, eight market
cards (NIFTY 50, SENSEX, Reliance Industries, HDFC Bank, ICICI Bank, Tata Consultancy Services, Infosys, State Bank of India), the
selected instrument's chart and a Recent Market News feed. Cards, chart and news show only what your providers return (loading,
provider and fetch time, stale, or unavailable with the reason); a card's daily history is fetched when you select it. The sun/moon
button switches light and dark mode and remembers the choice in `%APPDATA%\StockVault\ui.properties` (the large dashboard window
keeps its own dark palette). Sign In uses your email and password; Create Account asks for name, email and password only. Both use the
local `users` table (PBKDF2 hashes) and open the application directly. Limitations: no password recovery; index data (NIFTY 50,
SENSEX) is not offered by the free Alpha Vantage plan, and Twelve Data / Finnhub need keys they accept. `--home`, `--dashboard` and
`--classic` skip the landing page.

`target/lib/` must stay next to the JAR. A self-contained app-image can be made with `jpackage` (see
`docs/audit/INTEGRATION_REPORT.md`, "Packaging").

## Configuration (API keys)
Keys are looked up in this order; the first non-empty value wins:
1. **Operating-system environment variables.**
2. **`stockvault.local.properties`** (the file named by `STOCKVAULT_CONFIG`; else one in the folder you start from; else one in the
   per-user data folder described under Database).
3. **`.env` files** (fallback): the project-root `.env`, and the per-user `%APPDATA%\StockVault\.env` (for a packaged app outside the project tree). The project-root `.env` is the one next to `pom.xml`; it is found from where the program's code lives
   (the folder above `target/`), not from the folder you launch in. A packaged app that is not inside the project tree has no
   project root and reads no `.env`; use environment variables or the properties file there.

`.env` syntax: UTF-8, one `NAME=value` per line; blank lines and lines starting with `#` are ignored; spaces around names and values
are trimmed; a value in matching single or double quotes loses the quotes; a `#` after a value is part of the value; an empty value
means "not set". `.env.example` is only a template and is never read.

To configure locally: edit `.env` in Notepad, type each value after its `=`, save, and restart. Files and variables are read once at
startup, so **a restart is required after changing any key**. `.env` and `stockvault.local.properties` are git-ignored and must stay
untracked, because they hold live credentials; `ApiKeyManager.java` contains no keys, and `.env.example` holds only empty names.
The app never changes your environment or writes these files.

The automated tests use synthetic values and local fake servers only. They show the lookup works; they do **not** show that any
provider accepts your key. That needs a live check with your own keys (for example Refresh prices), which has not been done.

| Setting | Used for |
|---|---|
| `FINNHUB_API_KEY` (comma-separated list allowed) | primary quotes |
| `TWELVEDATA_API_KEY` | second-choice quotes, price history |
| `ALPHAVANTAGE_API_KEY` | last-resort quotes, price history |
| `GNEWS_API_KEY`, `NEWSDATA_API_KEY` | market news |
| `GROQ_API_KEY` | AI assistant |
| `ASSEMBLYAI_API_KEY`, `ELEVENLABS_API_KEY` | voice input / speech |
| `EXCHANGERATE_API_KEY` | currency conversion |
| `GMAIL_ADDRESS`, `GMAIL_APP_PASSWORD` | daily e-mail report (the address defaults to stockvault104@gmail.com if unset; the old stockvault123 account is never used; GMAIL_APP_PASSWORD takes precedence over the Credential Manager entry; the password can also be saved under Settings, where it goes to Windows Credential Manager, never the database) |

Example `stockvault.local.properties`:
```
FINNHUB_API_KEY=your-key
TWELVEDATA_API_KEY=your-key
```

**Provider setup for the landing page.** Market index cards need at least one of `TWELVEDATA_API_KEY`, `FINNHUB_API_KEY`,
`ALPHAVANTAGE_API_KEY`; headlines need `GNEWS_API_KEY` or `NEWSDATA_API_KEY`; AI insights need `GROQ_API_KEY`. The landing page
has a "Set up API keys" button that shows these names and the exact file locations (never a key). Free plans often exclude index
data: an index your provider does not serve shows "Not supported", an unusable key shows "Invalid API key", and a provider that hit its
limit shows "Rate limit reached" and is paused for 90 seconds. Nothing is displayed as live unless a provider returned it just now;
older values are labelled stale. Automated tests use local fake servers only; whether a real key serves real index data is verified
only by pressing "Refresh market data" with your own key.

## Database
* Location, in order: `-Dportfolio.db.path=...`; the `STOCKVAULT_DB` environment variable; an existing `portfolio.db` in the folder
  you start the program from (so existing setups keep working); otherwise `portfolio.db` in your per-user data folder
  (`%APPDATA%\StockVault` on Windows, `~/Library/Application Support/StockVault` on macOS, `~/.local/share/stockvault` on Linux).
  The folder the program starts in no longer matters for a new install, so a packaged executable behaves the same wherever it is launched.
  The home screen shows the file in use and, if it cannot be opened, the reason and the path.
* It holds your holdings, trades, **wallet balance and history**, watchlist, e-mail settings and any local accounts.
  **Back it up** (copy the file while the app is closed). It is git-ignored.
* Opening an older database upgrades it in place, all-or-nothing. Before changing an existing database the app writes
  `portfolio.db.pre-migration-<timestamp>.bak` next to it. To undo an upgrade, close the app and copy that file back over
  `portfolio.db`. A new, empty database gets no backup (nothing to protect). See `docs/BACKUP_AND_RESTORE.md`.

## How money moves
Buying debits the wallet and selling credits it; the wallet change, the holding and the ledger row are written in one
database transaction, so a failure (insufficient funds, bad input, disk error) changes nothing. Interactive sales (premium
dashboard, classic window, console) use a live quote that you see and confirm, with its price, currency, age and exact wallet
effect; the service refuses a quote older than 60 s, one whose currency is unknown or differs from the holding's, and any
trade that would need a currency conversion without a validated, fresh exchange rate. Prices are never invented. The wallet
has one currency (default INR). Full rules: [`docs/CURRENCY_AND_QUOTES.md`](docs/CURRENCY_AND_QUOTES.md).
The holdings tables show each price as Fresh / Stale / Unavailable. Totals shown in a display currency use a validated live
rate when one exists; if not, they use fixed approximate rates and are marked with "≈" and an "APPROXIMATE RATES" banner (money is
never moved with such rates).

## Symbols and exchanges
A stored symbol may carry its exchange: `RELIANCE.NS` / `RELIANCE.NSE` (NSE), `RELIANCE.BSE` / `RELIANCE.BO` (BSE); a bare ticker such as
`AAPL` is sent to every provider exactly as typed (no suffix is ever guessed). Each provider gets its own documented spelling
(Alpha Vantage `.BSE`, Twelve Data `TICKER:EXCHANGE`, Finnhub `.NS` / `.BO`). Alpha Vantage documents no NSE listings, so an NSE
symbol is not sent to it and may be unavailable on free plans: use the `.BSE` form there. Free plans of Twelve Data and Finnhub do not
cover Indian listings. Whether a given key serves a given symbol is only known by trying it with your own key.

**Price history** is requested for about 400 calendar days (Twelve Data `outputsize`; Alpha Vantage `outputsize=full`, falling back
to the compact ~100-day series if the plan does not include it). A chart range (1W, 1M, 6M, 1Y) is enabled only when the loaded data
really covers it. History is stored in the database (`price_history`) and reused for 12 hours.

## Build, test, static analysis
```
mvn -o clean verify spotbugs:check
```
(Drop `-o` to let Maven download plugins on a machine without a populated `%USERPROFILE%\.m2`.)
Runs the unit/integration tests (temporary databases and fake providers only; no network, no real database) and SpotBugs.
`tools/GuiAccept.java` and `tools/DashboardSmoke.java` are scripted Swing smoke tests (see the integration report).
`lib-repo/` holds three JARs (FlatLaf, JavaMail, Activation) as a tiny local Maven repository so the build works offline;
delete the `<repositories>` block in `pom.xml` to use Maven Central instead.
`lib/` keeps the original JARs, including ones no current code imports (see the dependency map in the report).

SpotBugs runs inside Maven's own JVM (`<fork>false</fork>`). On the Windows development machine, starting a second `java.exe` from
Maven intermittently failed with "CreateProcess error=5, Access is denied", and on some runs even `mvn.cmd` itself was refused
before building anything. That is the operating system or security software refusing a process launch, not a project fault; if you
see `Access is denied` with no test output, simply run the command again.

There is no Maven Wrapper: it downloads Maven on first use, which defeats the offline build. Install Maven 3.9 (see above).

## Layout
```
src/com/portfolio/        application (database, model, service, ui, data)
src/resources/            bundled resources (classpath root)
test/                     JUnit 5 tests
tools/                    scripted GUI smoke tests
docs/                     design, backup/restore, audit reports
lib/  lib-repo/           libraries (see the integration report)
pom.xml  spotbugs-exclude.xml  .github/workflows/ci.yml
```
