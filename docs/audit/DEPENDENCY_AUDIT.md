# Dependency, License and Static-Analysis Audit

## Versions and advisories
| Library | Original | Now (pom.xml) | OSV advisories (api.osv.dev, exact version) | License (from the artifact POM) |
|---|---|---|---|---|
| org.json:json | 20231013 | 20260814 | none reported | Public Domain |
| org.xerial:sqlite-jdbc | 3.45.1.0 | 3.53.4.0 | none reported | Apache-2.0 |
| org.jfree:jfreechart | 1.5.4 | 1.5.6 | none reported | LGPL (dynamic linking via a separate JAR; keep it a separate, replaceable JAR if you ever distribute) |
| org.slf4j:slf4j-api / slf4j-simple | 2.0.9 | 2.0.9 | none reported | **MIT** - verified from the artifact itself: `META-INF/LICENSE.txt` inside `slf4j-api-2.0.9.jar` is the MIT permission text (QOS.ch). The POM carries no license element |
| JUnit Jupiter (test only) | n/a | 5.10.2 | none reported | EPL-2.0 |

Upgrades were made only after build, tests and SpotBugs passed. No downgrades. OSV returning nothing is evidence, not a guarantee; only package coordinates were sent. sqlite-jdbc bundles native SQLite binaries (the license of the bundled SQLite is public domain; not separately verified).

## OWASP Dependency-Check
Command: `mvn org.owasp:dependency-check-maven:12.1.0:check` with no NVD API key (`NVD_API_KEY` is not set on this machine). **Not completed, so there is no clean result.** The first NVD download (413,899 records) reached 2% in several minutes and I stopped it; it was not retried in the final gate because no key exists.

To run it properly: request a free key at nvd.nist.gov (Developers > Request an API key), set it in your own shell (`$env:NVD_API_KEY = "..."`), then run the same command with the plugin's environment-variable option so the key never appears on the command line (parameter name `nvdApiKeyEnvironmentVariable=NVD_API_KEY`, **not verified against this plugin version**; check the plugin documentation). Allow time for the first download. Do not paste the key into files, reports or commits.

## Full dependency tree (`mvn dependency:tree`, 2026-10-24)
Compile/runtime: json 20260814, jfreechart 1.5.6, sqlite-jdbc 3.53.4.0, slf4j-api 2.0.9, slf4j-simple 2.0.9 (runtime) - **no transitive runtime dependencies**. Test only: junit-jupiter 5.10.2 -> junit-jupiter-api/params/engine 5.10.2, opentest4j 1.3.0, junit-platform-commons/engine 1.10.2, apiguardian-api 1.1.2 (EPL-2.0 / Apache-2.0; licenses of the transitive test artifacts were not individually checked, and none ship in the application).
Notes: jfreechart 1.5.6 JAR carries no license file (the POM states LGPL); sqlite-jdbc ships `LICENSE` (Apache-2.0) and `LICENSE.zentus`, and bundles native SQLite builds (public domain; not separately verified). OSV was re-queried only for the versions in the earlier table; the tree adds no further runtime artifacts to query.

## Tracked JAR review
| File | Purpose | Used by Maven build? | Same as pom? |
|---|---|---|---|
| lib/jfreechart-1.5.4.jar, json-20231013.jar, slf4j-api-2.0.9.jar, slf4j-simple-2.0.9.jar, sqlite-jdbc-3.45.1.0.jar | Classpath for the original manual `javac`/`java -cp "lib/*"` build | No | Older versions than the pom for jfreechart, json, sqlite-jdbc |
| test-lib/junit-platform-console-standalone-1.10.2.jar | Manual JUnit runner I added earlier (SHA-1 verified against Maven Central) | No | Superseded by Maven + JUnit 5.10.2 |

None were deleted. Consequences of keeping: ~8 MB of stale binaries in history either way (already committed; removal would not shrink history), risk that someone builds with outdated libs. Consequences of removing: the legacy manual build commands (in `docs/legacy/`) stop working; `mvn` is unaffected. **Recommendation:** remove `lib/` and `test-lib/` in a later commit once you confirm you no longer use the manual build.

## SpotBugs (spotbugs-maven-plugin 4.8.5.0, effort Max, threshold Medium)
Fixed: default-encoding `Scanner`, `\n` in format strings, unclosed `Statement`, unsynchronized loading in `PortfolioService`. Reviewed exclusions in `spotbugs-exclude.xml`: shared JDBC connection exposure, Swing windows holding the service, constructors that throw when the database is unavailable (`PortfolioDAO`, `PortfolioService`; no finalizer-sensitive state). Current run: 0 reported. One new finding during the final gate (SQL built by concatenation for the pre-migration backup path) was fixed by using a bound parameter, not excluded.

## Manual security review
- No hardcoded secrets in source, tests or any commit (regex scan across all commits, file names only). Old `"demo"` key removed.
- SQL: values via `PreparedStatement`; the only concatenated SQL is a private migration helper with constant names.
- HTTP: HTTPS, 10 s timeouts, status check, 1 MB streamed cap, URL-encoded parameters; key in the query string (provider design); URLs not logged.
- CSV export neutralises spreadsheet-formula prefixes. No servers, deserialization, command execution or reflection.
- Database failures now propagate; a failed load stops startup with a message.
