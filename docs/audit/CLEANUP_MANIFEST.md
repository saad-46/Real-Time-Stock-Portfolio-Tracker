# Cleanup manifest (PROPOSAL; nothing has been deleted)

Only regenerable build artifacts inside `D:\Projects\StockVault`. Each path was inspected: none is tracked by Git
(`git ls-files` returns nothing under them), and every `.class` file corresponds to an existing `.java` source (no orphans).
Excluded on purpose: both original folders, the recovery directory, every database and backup, all sources, `lib\`, `lib-repo\`,
`test-lib\`, `src\resources\`, docs and reports.

| # | Path | Files | Bytes | What it is | Regenerable by |
|---|---|---|---|---|---|
| 1 | `D:\Projects\StockVault\bin\` (whole directory) | 98 | 445,894 (0.43 MB) | Stale `.class` files from an old manual compile in jirp (all 98 match current sources; compiled from OLDER versions of them). Includes `bin\com\portfolio\service\ApiKeyManager.class`, **one of the two files that contain the old key literals**. | `mvn package` (nothing reads `bin\`) |
| 2 | The 92 `*.class` files under `D:\Projects\StockVault\src\` (listed by `Get-ChildItem src -Recurse -Filter *.class`) | 92 | 410,883 (0.39 MB) | Stale compiled classes sitting beside the sources, same origin as #1. Includes `src\com\portfolio\service\ApiKeyManager.class`, **the other key-bearing file**. | `mvn package` (Maven compiles only `.java`; the only non-java file under `src\` that must stay is `src\resources\images\default_market_bg.png`, which is not matched) |
| 3 | `D:\Projects\StockVault\target\` (whole directory) | 187 | 17,933,376 (17.1 MB) | Maven output: `classes`, `test-classes`, `lib` (8 runtime JARs), the packaged JAR, `surefire-reports`, `spotbugsXml.xml`, `maven-status`, `maven-archiver`. Contains the CLEAN build of `ApiKeyManager` (no key literals). | `mvn -o clean verify spotbugs:check` |

Total: **377 files, 18,790,153 bytes (about 17.9 MB)**.

Notes
* #1 and #2 hold the only key literals inside StockVault. Per `SECRET_CONTAINMENT.md`, delete them only after you confirm the
  provider-side rotation. #3 can go any time (it also holds the JAR the GUI tools use; rebuild to recreate).
* Suggested exact command once approved (not run): remove the three paths above by name; do not use wildcards beyond
  `src\**\*.class`, and list the files first so the count (92) can be checked.
* Not in the manifest: two stray items outside StockVault that I only report: the original jirp folder's identical copies
  (protected), and `C:\Users\Admin\AppData\Local\Temp\...\scratchpad` build/test scratch (Maven distribution, disposable
  database copies, GUI-run folders, a 168 MB jpackage image). The scratchpad is outside the project, so it is not mine to
  clean without being asked. (The Maven distribution the offline build uses is in an EARLIER session's temp folder,
  `...\Temp\claude\D--Projects-stock-project-Real-Time-Stock-Portfolio-Tracker\...\mvn-dl\`, not in this one.)
* **Sensitive leftovers outside the project (reported, not touched):** my read-only database inspection and the migration dry
  run made disposable copies of your real database files inside that scratchpad (`...\scratchpad\dbcopies2\*.db`,
  `...\scratchpad\dry\work\` including its `.pre-migration-*.bak` and `restored-check.db`). They contain your data. They are
  outside StockVault, so I have not deleted them; tell me if you want them removed.

## Addendum 2026-10-25
The stale classes under `bin\` and `src\` also embed the password-like literal described in `SECRET_CONTAINMENT.md`
(`EmailService.class` and `PremiumStockDashboard.class`, one copy in each of the two folders). They are already inside manifest items 1 and
2, so the manifest paths are unchanged; confirmation of **both** the API-key rotation and that password change is now the condition
for deleting them. `target\` was deleted and rebuilt on 2026-10-25 and contains no literal.
