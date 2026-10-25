# Market data, credential security and manual actions

Facts observed 2026-10-25 with the project owner's own free-tier keys. Outcomes only; no key values are recorded here.

## 1. Provider support matrix (free plans)

| Instrument | Twelve Data | Alpha Vantage | Finnhub | Result in the app |
|---|---|---|---|---|
| NIFTY 50 | symbol unknown (404) | no index symbol (ETF only) | `^NSEI` needs paid CFD-index subscription | "Unavailable" with reason |
| SENSEX | symbol unknown (404) | no index symbol (ETF `SENSEXBEES.BSE` only) | `^BSESN` needs paid subscription | "Unavailable" with reason |
| Reliance | plan-gated (Grow/Venture) | `RELIANCE.BSE` quote + daily history | no access (403) | Alpha Vantage |
| HDFC Bank | plan-gated | `HDFCBANK.BSE` | no access | Alpha Vantage |
| ICICI Bank | plan-gated | `ICICIBANK.BSE` | no access | Alpha Vantage |
| TCS | plan-gated | `TCS.BSE` | no access | Alpha Vantage |
| Infosys | plan-gated | `INFY.BSE` | no access | Alpha Vantage |
| SBI | plan-gated | `SBIN.BSE` | no access | Alpha Vantage |

NIFTY 50 and SENSEX are never replaced by ETFs or invented values. Options (your decision): Twelve Data Grow/Venture plan (index symbols must be re-verified), Finnhub paid index data, a licensed NSE/BSE feed, or explicitly labelled ETF proxies (NIFTYBEES / SENSEXBEES).

## 2. Root causes found

- Earlier HTTP 401: the keys then in `.env` were rejected by Twelve Data and Finnhub. Replacing them fixed it (both now answer 200 for a US test symbol).
- Finnhub 403 "no access to this resource" was mislabelled "invalid key"; now classified as unsupported.
- Alpha Vantage free tier allows a small number of requests per day. Bursts and repeated tests exhausted it, which looked like missing history. Now: requests paced (>= 1.2 s), history fetched lazily for the selected instrument only, a daily-quota answer pauses that provider for 6 h (persisted across restarts), quotes and history are cached on disk with timestamps and shown as stale after 15 min, history is reused for 12 h.
- Twelve Data HTTP 404 is now "not in plan" or "unknown symbol", never "invalid key".

## 3. Cache and settings files (not tracked by Git)

`%APPDATA%\StockVault\market-cache.properties` (quotes, history, quota pause), `%APPDATA%\StockVault\ui.properties` (light/dark). Deleting them is safe.

## 4. Credential audit

- `.env`, `stockvault.local.properties`, `sources.txt`, databases and caches are ignored; no sensitive file is tracked.
- The current `.env` values appear in no working-tree file and in none of the 46 commits.
- Keys are never logged; provider errors show status class only.
- `GMAIL_APP_PASSWORD` in `.env` was 9 characters; a Google app password is 16, so it is probably not valid.
- **Gmail App Password: implemented.** The Settings screen now saves it in Windows Credential Manager (generic credential `StockVault/GmailAppPassword`, per Windows user). `email_settings.app_password` holds only the non-secret reference `credman:StockVault/GmailAppPassword`. The field shows a mask, never the value; "Remove Saved Password" deletes it; typing a new one replaces it.
  - Integration: `WindowsCredentialStore` calls advapi32 (`CredWrite/CredRead/CredDelete`) from a short-lived PowerShell process; the secret travels only on its stdin/stdout. JNA was not used: it is not in the offline repository and Java 21's native-access API is still preview. No dependency was added.
  - Save: stored, then read back and compared; on any failure nothing is written to the settings row and the user is told (no plaintext fallback). Unavailable store (non-Windows, no PowerShell, 20 s timeout) is reported as such. Credential Manager shows no prompt, so there is no cancel path.
  - Migration (runs when Settings opens, off the UI thread): a legacy plaintext value is stored, read back, compared, and only then replaced by the reference. If the store fails the plaintext stays untouched and Settings shows what to do: re-enter the password once the store works (or set `GMAIL_APP_PASSWORD`) and revoke the old one.
  - `GMAIL_APP_PASSWORD` (environment / local properties) still works and takes precedence; a stored password is used only when none is configured.
  - Tests use an in-memory `CredentialStore` (`GmailCredentialServiceTest`); none touches real credentials.
  - Existing plaintext copies in older database backups or files are not touched: treat any password that was ever stored in plaintext as exposed and rotate it.

### Remediation for the exposed old Gmail password (commits `e3fb5ba`, `72d4c6f`)

History was not rewritten and nothing was pushed. Manual steps: revoke that app password at https://myaccount.google.com/apppasswords (and change the account password if it was reused); create a new 16-character app password; put it only in `.env`. If the repository is public or shared, rewriting history is optional once the secret is revoked.

## 5. Manual actions still required

1. Wait for the Alpha Vantage daily quota to reset before pressing Refresh; then check that the six stocks show quotes and daily history.
2. Decide how NIFTY 50 / SENSEX should be sourced (section 1).
3. Revoke the old Gmail app password and create a valid one.
4. Open Settings once so any old plaintext password migrates (or re-enter the new App Password there).

## 6. Testing normal mode safely

`.\run-stockvault.ps1 -Diagnostic` uses disposable data and no credentials. `.\run-stockvault.ps1` uses the real `.env` keys (free-tier live calls) and the real database in `%APPDATA%\StockVault`: use a throw-away account name, and do not press Refresh repeatedly.
