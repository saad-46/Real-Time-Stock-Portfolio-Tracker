# JavaFX decision

**Recommendation: do not migrate now.** Stay on Swing until JavaFX can be installed reproducibly and there is a concrete UI need Swing cannot meet.

## Facts (checked 2026-10-26)
- The UI is Swing/Java2D/FlatLaf: 27 UI classes, `PremiumStockDashboard` alone about 7,500 lines, plus custom Java2D charts (`TimeSeriesChart`, `Sparklines`).
- JavaFX is not part of JDK 21 (Temurin) and no `org.openjfx` artifact exists in the offline Maven repository or `lib-repo/`. A migration therefore cannot be built or verified offline today.
- Nothing has been downloaded or migrated; the application is entirely Swing.

## Costs and risks
- It is a rewrite, not a port: every screen, the dialogs, the table renderers, the chart components and about 40 UI tests (which drive Swing components directly) would be redone.
- JavaFX needs per-platform native modules, module-path launcher flags and a different packaging story; the current JAR + `target/lib` launcher would change.
- Benefit (CSS theming, built-in charts, animation) is real but mostly cosmetic here: the current Java2D components already give hover, tooltips and a consistent theme.

## If it is ever approved
1. Install JavaFX 21 into the offline repository (checksummed) and prove an empty JavaFX window builds and launches via `run-stockvault.ps1`.
2. Keep services, DAOs and `HistoryService`/`PortfolioHistory` untouched; they have no Swing dependency.
3. Move one self-contained screen first (Wallet), behind a launcher flag, with its tests, before touching the dashboard shell.
4. Remove Swing only when every screen has moved; never ship a mixed state without a written plan.
