# Deployment Assessment

Research date: **2026-10-24**. **Nothing was deployed, provisioned, configured, or paid for.**

## Evidence on Vercel (official docs, via the Vercel documentation search tool)
Sources: `vercel.com/docs/functions/runtimes` (Node.js, Edge), `.../runtimes/python`, `.../runtimes/rust`, `vercel.com/docs/functions/configuring-functions/runtime`, `vercel.com/docs/project-configuration/vercel-json`, `vercel.com/docs/sandbox/*`.
- Documented Function runtimes: Node.js (and Bun), Edge, Python, Ruby, Rust (Go is also listed in the platform). Other languages are possible only through community runtimes configured per file (e.g. `vercel-php`).
- **No first-party Java runtime appeared in the documentation retrieved.** The search index is not exhaustive and I did not test a deployment; absence in these excerpts is evidence, not proof.
- Vercel Sandbox is an ephemeral/snapshot-able VM for running code (default timeouts of minutes, optional persistence); it is not a hosting platform for a Swing GUI or a long-running public service. A "container image" option was mentioned for some service types; its Java suitability, cost, and limits were not researched.
- Functions have no durable local disk; a SQLite file there would not persist.

## Options
| Option | Description | Feasibility |
|---|---|---|
| **A. Desktop app (recommended now)** | `mvn clean verify`, then `jpackage` app-image (verified on Windows, JDK 21) or run the JAR | Works today. Installers (`msi`/`exe`) need WiX; macOS/Linux packages must be built on those OSes (untested) |
| B. Browser UI + Java backend | New Java HTTP server (e.g. embedded server or Spring) with server-rendered HTML; Swing UI would be rewritten | Large rewrite. Stays Java-only only if no JavaScript application logic is written (plain HTML forms/CSS). Needs a decision from you |
| C. Vercel frontend + Java backend elsewhere | Vercel hosts static/Node-based frontend, Java runs on another host | Frontend would be **non-Java application code** (JS/TS) and violates the stated Java-only rule unless you explicitly relax it. Not started |

## If B or C is ever pursued: required decisions
- **Persistence:** replace single-file SQLite with a managed database (e.g. PostgreSQL) or a host with a persistent volume + backups; schema per user.
- **Auth and isolation:** today there are no users; a public service needs authentication, per-user data isolation, CSRF/session protection, and HTTPS.
- **Provider quota:** the free Alpha Vantage quota is per key; a multi-user service needs a shared cache and quota accounting (or a paid plan).
- **Secrets:** keys in the host's secret store, never in the repo; logging without keys; monitoring/alerts; backups and tested restore; rollback by redeploying the previous artifact.
- **Cost:** not researched. A Java host with persistent storage is typically a paid or small-free-tier service; verify current pricing before choosing.

## Recommendation
Keep distributing option A. Do not attempt to host the current Swing application on Vercel. Revisit B (Java-only, server-rendered) only if multi-device access is a real requirement, and decide the language policy first.

## Step-by-step future plan (not executed)
1. Decide scope (single user vs. multi-user) and language policy.
2. Extract the service layer behind an HTTP API (the existing `PortfolioService` is UI-independent).
3. Replace SQLite with a hosted database; add migrations.
4. Add auth, HTTPS, quota-aware price cache.
5. Choose a Java-capable host, verify pricing/limits, deploy a staging copy, test, then production with a documented rollback.
