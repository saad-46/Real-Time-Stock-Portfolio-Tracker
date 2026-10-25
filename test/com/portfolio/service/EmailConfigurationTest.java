package com.portfolio.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Sender selection and credential precedence. Synthetic values only; nothing is sent and no connection is made. */
class EmailConfigurationTest {
    private static final String OK_PASSWORD = "abcdabcdabcdabcd";

    @Test
    void theSenderIsTheNewAccountAndTheRevokedOneIsNeverUsed() {
        assertEquals("stockvault104@gmail.com", EmailService.DEFAULT_SENDER);
        assertEquals(EmailService.DEFAULT_SENDER, EmailService.resolveSender(null));
        assertEquals(EmailService.DEFAULT_SENDER, EmailService.resolveSender("  "));
        assertEquals(EmailService.DEFAULT_SENDER, EmailService.resolveSender("StockVault123@gmail.com"));
        assertEquals("someone@gmail.com", EmailService.resolveSender(" someone@gmail.com "));

        EmailService mail = new EmailService();
        mail.configure("stockvault123@gmail.com", OK_PASSWORD);
        assertEquals(EmailService.DEFAULT_SENDER, mail.getEmail());
    }

    @Test
    void diagnosticsAreUsefulAndNeverContainThePassword() {
        EmailService mail = new EmailService();
        mail.configure("stockvault104@gmai.com", OK_PASSWORD);
        assertTrue(mail.configurationProblem().contains("GMAIL_ADDRESS"));

        mail.configure(EmailService.DEFAULT_SENDER, null);
        assertTrue(mail.configurationProblem().contains("No Gmail App Password"));

        mail.configure(EmailService.DEFAULT_SENDER, "short1234");
        String shortProblem = mail.configurationProblem();
        assertTrue(shortProblem.contains("16 characters") && shortProblem.contains("has 9"), shortProblem);
        assertFalse(shortProblem.contains("short1234"));

        mail.configure(EmailService.DEFAULT_SENDER, "abcd abcd abcd abcd");
        assertNull(mail.configurationProblem(), "Google's grouped display form is accepted");
    }

    @Test
    void aBadConfigurationSendsNothing() {
        EmailService mail = new EmailService();
        mail.configure(EmailService.DEFAULT_SENDER, "short1234");
        assertFalse(mail.sendTestEmail(), "refused before any connection is attempted");
    }

    /** Records configure() calls; reports whether a password is already configured. */
    private static final class Recorder extends EmailService {
        final boolean configured;
        final List<String> calls = new ArrayList<>();

        Recorder(boolean configured) {
            this.configured = configured;
        }

        @Override public boolean isConfigured() {
            return configured;
        }

        @Override public void configure(String email, String password) {
            calls.add(email + "|" + (password == null ? "-" : "pw"));
        }
    }

    @Test
    void anExplicitlyConfiguredPasswordTakesPrecedenceOverTheStoredOne() throws Exception {
        GmailCredentialServiceTest.FakeStore store = new GmailCredentialServiceTest.FakeStore();
        store.data.put(GmailCredentialService.TARGET, OK_PASSWORD);
        GmailCredentialService svc = new GmailCredentialService(store);

        Recorder explicit = new Recorder(true);
        assertFalse(svc.applyStoredPassword(explicit, "x@gmail.com"));
        assertTrue(explicit.calls.isEmpty(), "the environment/.env password is left alone");

        Recorder none = new Recorder(false);
        assertTrue(svc.applyStoredPassword(none, "stockvault123@gmail.com"));
        assertEquals(List.of("stockvault104@gmail.com|pw"), none.calls, "stored password used, and never with the revoked sender");

        assertFalse(new GmailCredentialService(new GmailCredentialServiceTest.FakeStore()).applyStoredPassword(new Recorder(false), "x@gmail.com"));
    }
}
