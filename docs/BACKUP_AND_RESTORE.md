# Backing Up and Restoring Your Portfolio Database

Your data lives in one file: `portfolio.db`, in the folder you start the app from (or the path given with `-Dportfolio.db.path=...`).

## Before you run a new version for the first time
This version upgrades older databases automatically (it adds two columns). The upgrade is tested only on synthetic databases, so make your own copy first:

1. Close the application.
2. Copy `portfolio.db` to a safe place, e.g. `portfolio-backup-2026-10-24.db` (File Explorer copy, or `copy portfolio.db portfolio-backup.db`).
3. Start the app.

## What the app does for you
If it finds an older-format database it first writes a consistent copy next to it, named
`portfolio.db.pre-migration-YYYYMMDD-HHMMSS-mmm.bak`, then upgrades inside a single database transaction. If the backup cannot be written, or the upgrade fails, nothing is changed and the app shows "The application could not start" with the reason. Only databases that actually need the upgrade get a `.bak`; later starts do not create more. `*.bak` files are git-ignored. Delete them yourself when you are satisfied.

## Restore
1. Close the application.
2. Keep the current file in case you need it: rename `portfolio.db` to `portfolio.db.broken`.
3. Copy your backup (or a `.pre-migration-....bak`) to `portfolio.db`.
4. Start the app. A restored old-format file is upgraded again (and gets a fresh `.bak`).

Anything you recorded after the backup was taken is not in it.

## Notes
- Copy only while the app is closed.
- Transaction times are stored in local time; rows written by the very first version used UTC and display shifted by your UTC offset.
- The app never deletes or overwrites a database to fix a schema problem.
