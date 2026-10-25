# Secret containment (no values appear in this document)

Scope: the key-bearing `ApiKeyManager.java` from the original jirp folder (9 key-like literals, identified by SHA-256 prefix
only, never printed). Found by value-search of every file (including inside JARs) and of Git history.

## Where the literals exist
| Location | Contains literals? | Tracked by Git? |
|---|---|---|
| `D:\Projects\jirp\...\src\com\portfolio\service\ApiKeyManager.java` | yes (the source) | no (git-ignored) |
| `D:\Projects\jirp\...\src\com\portfolio\service\ApiKeyManager.class` | yes, all 9 | no (ignored) |
| `D:\Projects\jirp\...\bin\com\portfolio\service\ApiKeyManager.class` | yes, all 9 | no (ignored) |
| `D:\Projects\_recovery_stockvault_...\jirp\...` (full copy of the three files above) and `conflicts\jirp\` | yes | not a repo |
| `D:\Projects\_recovery_stockvault_...\jirp.bundle`, `...\jirp\...\.git` | **no**: never committed | n/a |
| `D:\Projects\StockVault\src\com\portfolio\service\ApiKeyManager.class` | yes, all 9 | **no** (ignored) |
| `D:\Projects\StockVault\bin\com\portfolio\service\ApiKeyManager.class` | yes, all 9 | **no** (ignored) |
| `D:\Projects\StockVault\src\...\ApiKeyManager.java`, `target\classes\...`, `target\*.jar`, `target\lib\*`, `lib\`, `lib-repo\`, `docs\`, `test\`, `tools\` | **no** | n/a |
| stock_project folder, its `.git`, `portfolio-db-backups`, `gui-acceptance-run` | **no** | n/a |

## Git history
`git log --all -S<literal>` over the StockVault repository (all 46 commits, both lines) and over each original repository:
**0 commits** contain any literal. Nothing containing the key was ever pushed (no commit reachable from `origin/*` has it).
The new `ApiKeyManager.java` is tracked and contains no key; keys come from environment variables or the git-ignored
`stockvault.local.properties`. A scan of every tracked or trackable file for key-like literals found nothing.

## Exposure assessment
The key exists only on this machine (the original jirp folder, the recovery copy, and two stray compiled files in
StockVault's ignored paths). It was not committed or pushed. Anything able to read these folders (backup/sync tools, other
local users, archives made of the folders) could have copied it, and the recovery directory now holds several more
copies of it on disk. It has been sitting in plain text in an ignored source file since 2026-03-07, which is reason enough
to rotate it. Treat it as exposed and rotate it.

## Recommended provider-side steps (you do these; I cannot)
For each provider whose key was in the file (stock-price, news, AI, speech, e-mail and exchange-rate services, per the
getters in the old class: Finnhub, Twelve Data, Alpha Vantage, GNews, NewsData, Groq, AssemblyAI, ElevenLabs, ExchangeRate-API,
and the Gmail app password):
1. Sign in to the provider's dashboard and **revoke / delete** the old key (for Gmail: revoke the app password at
   myaccount.google.com > Security > App passwords).
2. **Create a new key**, and set it as an environment variable or in `stockvault.local.properties` (never in source).
3. Check each provider's usage page for calls you do not recognise since the key was created; if any, say so and consider the
   account compromised.
4. Tell me when each key is rotated; only then run the removal plan below.

## Removal plan (after rotation is confirmed; needs your explicit approval; nothing done yet)
1. In StockVault only: delete the two stray compiled files `src\com\portfolio\service\ApiKeyManager.class` and
   `bin\com\portfolio\service\ApiKeyManager.class` (they are part of the cleanup manifest), then rebuild
   (`mvn -o clean verify spotbugs:check`) and re-run the value-search to confirm zero occurrences.
2. The original folders and the recovery directory are protected copies; they keep the old key file until you decide what to
   do with them. Once the key is revoked at the provider, the old literals are harmless, and you may then delete or archive
   those folders on your own timetable. Do not rewrite Git history: there is nothing in history to remove.

---

## Addendum 2026-10-25: a second credential-like literal (found by the full audit)
A password-like string was hard-coded in the **help text** of `EmailService.java` and `PremiumStockDashboard.java` (it told users which
placeholder to replace). Unlike the 9 key literals above, it **is in Git history**: commits `e3fb5ba` and `72d4c6f` (2026-03-05) are
ancestors of `origin/main`, so they were pushed to `github.com/saad-46/Real-Time-Stock-Portfolio-Tracker` (visibility not checked).
`fcf3669` (local only) also touches it. Why my earlier scans missed it: they matched key-shaped assignments, and this one sat in a message string.

* Current source, docs and tests no longer contain it (verified by value search over every file Git would contain).
* It also remains inside stale compiled classes: `bin/` and `src/` copies of `EmailService.class` and `PremiumStockDashboard.class`
  (added to the cleanup manifest; delete them together with the `ApiKeyManager.class` files after you confirm rotation).
* **Action for you:** if that string is, or was ever, the password of the Gmail account `stockvault123@gmail.com` (or any account),
  change it now at Google and revoke app passwords; also check whether the GitHub repository is public. History is not rewritten:
  rotation is the remedy, and a history rewrite would not undo a clone someone already made.
