package com.portfolio;

import com.portfolio.database.WalletDAO;

import java.sql.SQLException;

/**
 * Test fixture: trades move money through the wallet (see docs/INTEGRATION_DESIGN.md), so tests that trade need a
 * funded wallet. Call after pointing {@code portfolio.db.path} at a temporary database. Never touches a real database.
 *
 * The wallet has its own currency (product default INR). The inherited tests price everything in USD, so by default the
 * temporary wallet is made a USD wallet; tests about currency conversion pass another currency explicitly.
 */
public final class TestWallet {
    public static final double DEFAULT_FUNDS = 1_000_000;
    public static final String DEFAULT_CURRENCY = "USD";

    private TestWallet() {
    }

    public static void fund() {
        fund(DEFAULT_FUNDS, DEFAULT_CURRENCY);
    }

    public static void fund(double amount) {
        fund(amount, DEFAULT_CURRENCY);
    }

    public static void fund(double amount, String currency) {
        try {
            WalletDAO dao = new WalletDAO();
            if (!dao.getCurrency().equalsIgnoreCase(currency) && !dao.setCurrencyIfPristine(currency)) {
                throw new IllegalStateException("could not set the test wallet currency");
            }
            if (!dao.applyCashMovement(true, amount, "test funding")) {
                throw new IllegalStateException("could not fund the test wallet");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not fund the test wallet", e);
        }
    }
}
