# Final Audit Report (final verification gate, 2026-10-24)

Evidence: `TEST_RESULTS.md`. Details: `REMEDIATION_LOG.md`, `INTEGRATION_STATUS.md`, `DEPENDENCY_AUDIT.md`, `KNOWN_LIMITATIONS.md`, `DEPLOYMENT_ASSESSMENT.md`, `FEATURE_PLAN.md`, `TRADE_MODEL.md`, `GUI_ACCEPTANCE_CHECKLIST.md`, `../BACKUP_AND_RESTORE.md`.

## Defects found and fixed (cumulative)
| Severity | Finding | Status |
|---|---|---|
| Critical | Random $100-$200 prices shown as live on any API failure | Fixed |
| High | Java source never committed (`.gitignore` `com/`) | Fixed |
| High | GUI button toolbar never displayed (two panels in one `BorderLayout.NORTH` slot) | Fixed (found by running the GUI; harness now asserts all 5 buttons) |
| High | Holdings shown when the DB write failed; trades not atomic; DB read errors looked like an empty portfolio | Fixed |
| High | Hardcoded `"demo"` key, no URL-encoding, HTTP-200 rate-limit replies undetected, no size cap | Fixed |
| High | An invalid API key (`Error Message`) was reported as "symbol not found" | Fixed (final gate) |
| Medium | Ledger "newest first" order wrong when timestamps tie (probable cause of the intermittent test failure) | Fixed, deterministic regression test (final gate) |
| Medium | Refresh hammered the provider after key/quota errors; failures not explained | Fixed: stops early, shows first problem; retries only network/5xx, max 3 attempts |
| Medium | Schema upgrade had no backup and was not all-or-nothing | Fixed: automatic backup, one transaction, rollback proven by mutation test |
| Medium | Others: validation, `double` money, no sell/cost basis, overlapping refresh, UTC/local timestamp mismatch, schema-init errors swallowed, EDT blocking, false doc claims, CSV timestamp precision | Fixed |

## Verification status
| Area | Status |
|---|---|
| Build and tests | `mvn clean verify spotbugs:check`: 74 tests pass, SpotBugs 0 reported; 8/8 repeat runs pass (4 under CPU load) |
| Intermittent failure | **Not identified from evidence** (report lost). A probable cause was found, reproduced deterministically and fixed; not proven to be the original cause |
| Alpha Vantage | **Locally tested only** (17 fake-server tests); **not live-verified**; no key on this machine; message-wording heuristics unverified |
| Dependencies | OSV: no advisories for current versions. **OWASP Dependency-Check incomplete** (no NVD key). Licenses verified (SLF4J = MIT from the JAR's own LICENSE file); transitive tree reviewed (no runtime transitives) |
| Database migration | Tested on synthetic legacy databases (values preserved, backup, idempotent restart, rollback, restore). **Real `portfolio.db` never touched or migrated**; user must back it up first |
| GUI | 31/31 scripted in-process interaction checks passed. **No human acceptance**; remaining manual items listed in the checklist |
| Packaging | JAR, `target/lib`, and `jpackage` app-image build; exe not launched |
| CI | Workflow written, never run (nothing pushed) |
| Deployment | Vercel docs list no first-party Java runtime; keep desktop distribution |

## Readiness
Suitable for **single-user desktop trial use** after you (1) back up your database, (2) run the live Alpha Vantage check, (3) do the manual GUI items. **Not** declared production-ready: material gaps remain (live provider unverified, dependency scan incomplete, no human GUI acceptance, real-database upgrade unverified, no hosting story).

Java-only: all application code is Java (the GUI harness in `tools/` is Java too).
