# Integration Status

Derived from the source (`getenv`/`getProperty`/URLs), not from old documentation. Updated 2026-10-24 (final gate).

| Integration | In code | Config | Status |
|---|---|---|---|
| Alpha Vantage `GLOBAL_QUOTE` (`https://www.alphavantage.co/query`) | `AlphaVantageService`; reachable via Refresh, Validate (GUI), Refresh/Add (console) | env var `ALPHAVANTAGE_API_KEY` | **Implemented and locally tested against a fake server (17 provider tests); NOT live-verified** |
| Local SQLite file | `DatabaseManager` | system property `portfolio.db.path` | Implemented, tested on temporary and synthetic legacy databases |
| Email, AI, news, voice, other providers | No | none | Not implemented |

Environment variables read: only `ALPHAVANTAGE_API_KEY`. No key is set on this machine, and no live request was made.

## Alpha Vantage behavior (all verified against a local fake server unless stated)

| Provider response | App result (`Reason`) | Retried? |
|---|---|---|
| No / blank key | `NO_KEY`, no request sent | no |
| 200 + `Error Message` mentioning apikey | `INVALID_KEY` (no longer shown as "symbol not found") | no |
| 200 + other `Error Message` | symbol not found (`null`) | no |
| 200 + `Note` | `RATE_LIMIT` | no |
| 200 + `Information` containing rate limit / requests per / call frequency / premium | `RATE_LIMIT` | no |
| 200 + `Information` mentioning API key (e.g. demo key) | `INVALID_KEY` | no |
| 200 + any other `Information` | `PROVIDER_MESSAGE` | no |
| 401 / 403 | `INVALID_KEY` | no |
| 429 | `RATE_LIMIT` | no |
| other 4xx | `HTTP_ERROR` | no |
| 5xx | `HTTP_ERROR` | yes, up to 3 attempts total, 500 ms then 1 s backoff |
| connection failure / timeout (10 s) | `NETWORK` | yes, same bound |
| malformed JSON, missing `Global Quote`, bad/zero/negative/NaN price, body > 1 MB | `BAD_RESPONSE` | no |
| empty `Global Quote` | symbol not found (`null`) | no |

- **Batch behavior:** a refresh stops after the first `NO_KEY`, `INVALID_KEY` or `RATE_LIMIT` (the remaining holdings are counted as failed, not requested). Not-found and transient errors do not stop the batch. Failed holdings keep their last price and are never stamped as refreshed; the dialog shows the first problem.
- **Limits of this classification:** the provider's message wording was taken from its documented/commonly observed text and **has not been checked against the live service**. If Alpha Vantage changes wording, a quota message may surface as `PROVIDER_MESSAGE` (still reported as unavailable, never as a price).
- No quota counter and no daily-limit tracking exists; the free tier's limits are enforced only by the provider's replies.
- The key is URL-encoded into the query string (provider design); it is never logged and not present in error messages (tested).

## Verify the live service yourself (safe, small)
1. Get a free key from Alpha Vantage. Set it only in your shell: `$env:ALPHAVANTAGE_API_KEY = "..."`.
2. Start the console app with a throwaway database:
   `java -Dportfolio.db.path=$env:TEMP\check.db -cp "target/stock-portfolio-tracker-1.0.0-SNAPSHOT.jar;target/lib/*" com.portfolio.Main`
3. Add one holding (`AAPL`, 1, 1.00), then choose **3 Refresh prices** once.
   - "Prices updated." and a changed price in **2 View portfolio**: connectivity, key, quota and symbol all worked.
   - Otherwise the message names the cause: "No API key configured", "API key is invalid, missing or demo-only", "quota or rate limit was reached", "Network error", etc. Please report the exact message if it looks wrong, since the wording heuristics are unverified.
4. Do not loop the refresh; free tiers allow very few calls. Delete the throwaway database afterwards.

Until step 3 is done, treat Alpha Vantage as **mock-tested only**.
