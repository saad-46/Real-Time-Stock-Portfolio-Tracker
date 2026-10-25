# GUI Acceptance

## Automated interaction (done, 2026-10-24)
`tools/GuiAccept.java` builds the real `ModernPortfolioUI` and drives its actual Swing components in-process (`doClick`, `setText`, real modal dialogs, the real `JFileChooser`) against a **temporary database**. It does not move the mouse, take screenshots of your desktop, or touch other windows. Result: **31/31 checks passed, no uncaught exceptions.**

Run it yourself (after `mvn clean verify`, from the repo root, Windows classpath separator):
```
javac -cp "target/classes;target/lib/*" -d %TEMP%\guiaccept tools/GuiAccept.java
java -Dportfolio.db.path=%TEMP%\gui-accept.db -cp "%TEMP%\guiaccept;target/classes;target/lib/*" GuiAccept %TEMP%\accept.csv
```
It needs a desktop session (not headless) and is not part of `mvn verify`/CI.

| # | Verified by the harness |
|---|---|
| 1 | All five toolbar buttons are present (this caught the earlier "toolbar never displayed" bug); empty table at start |
| 2 | Add with quantity 0 shows "Quantity must be positive"; nothing added |
| 3-4 | Two buys of one symbol merge into one row; qty 20, average cost $110.00; price labelled "Not refreshed" |
| 5-7 | Sell with no selection explains itself; selling 25 of 20 is rejected with "only 20 held", holding unchanged; selling 5 @ 130 reports realized P/L $100.00, qty 15, Realized P/L card $100.00 |
| 8 | Refresh with no key: warning "0 of 1 ... First problem: No API key configured", button re-enabled, price still "Not refreshed" |
| 10 | Ledger opens; 3 transactions, SELL first; symbol filter with no match = 0 rows; type SELL = 1 row with $100.00; invalid date reported in the status bar |
| 11 | CSV export through the real file chooser writes header + 3 rows (SELL first, realized 100.00); exporting to an existing file asks to confirm overwrite |
| 12 | Charts window opens |
| 13 | Selling everything removes the row |
| 14 | After a restart the holding, the "3 d ago (STALE)" label and the realized P/L ($100.00) are shown |
| 15 | No uncaught exception on any thread |

## Still unverified (needs a human and/or a live key)
Automated component driving is **not** human acceptance: it does not judge layout, readability, scaling, colours, focus order, or how it feels to use. Remaining manual steps (use a throwaway database: `-Dportfolio.db.path=%TEMP%\gui-check.db`):

| # | Step | Expected |
|---|---|---|
| A | Look at the main window at your normal display scaling (100/125/150 %) | Toolbar, five summary cards and table fully visible, text not clipped |
| B | Resize and maximise the window; open Ledger and Charts | Content stays usable |
| C | Validate button with a bogus symbol, then with no key | "Stock not found" vs. "live price unavailable" are different messages (with a real key only for the first) |
| D | Refresh with a **valid key** | "Price Updated" shows "0 min ago", current price changes, no error |
| E | Click Refresh twice quickly | Second click does nothing while the first runs |
| F | Start with a deliberately unreadable database copy | "The application could not start" dialog, no empty portfolio |
| G | Charts: compare the numbers with the table | They agree |
| H | Close the main window while a refresh runs | Application exits without hanging (not tested) |
