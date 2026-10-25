# Currency and quote policy

The rules the trading service enforces (`PortfolioService`, `QuotePolicy`, `CurrencyService`). The UI only displays them.

## Currencies
| Thing | Currency |
|---|---|
| **Wallet** | One currency, stored in `wallet.currency` (`WalletService.getCurrency()`). Default **INR**; wallets that existed before this setting are rupee wallets. It can be changed only while the wallet is empty and has no history. |
| **Holding** | The currency its purchase price was entered/quoted in (`portfolio_items.original_currency`). One holding = one currency; a purchase in another currency is refused, never averaged in. |
| **Ledger / realized P/L** | The holding's currency. The wallet row for each trade records the converted wallet amount, the rate and its source in its description. |
| **Quote** | Stated by the provider, or unknown. Twelve Data states it. Finnhub's free `/quote` and Alpha Vantage `GLOBAL_QUOTE` do not: a plain US ticker (no `.`) is taken as **USD**, a symbol with an exchange suffix is **unknown**. |
| **Display totals** | In the chosen display currency (settings). May use fixed approximate rates if no live rate exists (flagged by `CurrencyService.usedFallbackRates()`); never used to move money. |

Supported quote currencies for trading: any currency the provider states, plus USD for bare US tickers. An unknown quote
currency blocks trading on that quote.

## Rules
1. **No silent mixing.** A number in one currency is never debited from, or credited to, a wallet in another currency.
   Same currency: no conversion. Different: a validated exchange rate or the trade is **blocked** before anything is written.
2. **Valid exchange rate** (`CurrencyService.requireRate`): finite and positive, for exactly the requested pair, with a named
   source and a timestamp no older than **1 hour** (and not in the future). Cached for that hour, then refetched. If the
   source fails or answers badly there is **no fallback**: `TradeBlockedException(RATE_UNAVAILABLE)`.
   Live source: exchangerate-api.com (needs `EXCHANGERATE_API_KEY`).
3. **Quote used for a trade** (`sellStock(symbol, qty, Quote)`, `buyStock(symbol, qty, Quote)`): price finite and positive,
   currency known, symbol matches, fetched no more than **60 s** ago (`QuotePolicy.TRADE_MAX_AGE`), and equal in currency to
   an existing holding. Violations throw `TradeBlockedException` (`STALE_QUOTE`, `QUOTE_CURRENCY_UNKNOWN`,
   `CURRENCY_MISMATCH`) or `PriceUnavailableException` (no/invalid price). The service checks all of this itself at execution,
   so a confirmation dialog left open past 60 s fails safely.
4. **Quote status shown to the user** (`QuotePolicy.displayStatus`): **Fresh** = last successful refresh within 15 min;
   **Stale** = older (or a future timestamp); **Unavailable** = never refreshed (the price shown is only what was paid).
   Shown on the premium dashboard's holdings tables and in tooltips.
5. **Refresh** stores a price only in the holding's currency: a quote in another currency is converted at a validated rate;
   an unknown quote currency or an unavailable rate leaves the old price and reports why.
6. **Atomic.** Wallet, holding and ledger change in one database transaction (`PortfolioDAO.recordTrade`); every check above
   happens before it starts, and any failure in it rolls everything back.
7. **No fake prices.** There is no fallback to the last known, purchase or sample price anywhere.

## Entry points
| Path | Price source | Checks |
|---|---|---|
| Premium dashboard sell/buy-at-quote (`TradeFlow`) | live quote, shown and confirmed with wallet effect | rules 1-3 |
| Classic window sell (`TradeFlow`) | same | rules 1-3 |
| Console sell (`Main`) | same | rules 1-3 |
| `sellStock(symbol, qty)` (AI assistant, compat) | fetches its own quote | rules 1-3 |
| Typed-price buys (dashboard forms, console, classic "Add Stock") | the price the user types, in the stated currency | rule 1 (conversion or block), validation |
| `sellStock(symbol, qty, double)` | caller-asserted price (tests, explicit user price) | rule 1 only |
