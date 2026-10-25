package com.portfolio.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** Regression tests for security findings of the audit: no text is ever executed, TLS is validated, model replies are parsed strictly. */
class SecurityRegressionTest {

    // ---------------------------------------------------------------- speech: text is data, never a command

    private static final String[] PAYLOADS = {
            "'); Remove-Item -Recurse C:\\Users; ('",
            "\" ; calc.exe ; \"",
            "`$(whoami)`",
            "$(rm -rf /)",
            "-v Alex --help",
            "line1\r\nline2\u0000\u0007"
    };

    @Test
    void theWindowsSpeechCommandNeverContainsTheText() {
        for (String payload : PAYLOADS) {
            ProcessBuilder pb = TextToSpeechService.windowsCommand(payload);
            List<String> cmd = pb.command();
            assertEquals("powershell", cmd.get(0));
            for (String part : cmd) {
                String core = payload.replaceAll("[\\p{Cntrl}]", "").trim();
                assertFalse(!core.isEmpty() && part.contains(core), "payload must not be in the command line: " + payload);
            }
            String script = cmd.get(cmd.size() - 1);
            assertTrue(script.contains("$env:STOCKVAULT_TTS_TEXT"), "the script reads the text as data from the environment");
            assertTrue(cmd.contains("-NoProfile") && cmd.contains("-NonInteractive"));
            String passed = pb.environment().get("STOCKVAULT_TTS_TEXT");
            assertNotNull(passed);
            assertFalse(passed.chars().anyMatch(Character::isISOControl), "control characters are removed");
        }
    }

    @Test
    void macAndLinuxReceiveTheTextOnStdinSoItCanNeverBeAnOption() {
        assertEquals(List.of("say", "-f", "-"), TextToSpeechService.macCommand().command());
        assertEquals(List.of("espeak", "--stdin"), TextToSpeechService.linuxCommand().command());
    }

    @Test
    void sanitizingCapsLengthAndHandlesNull() {
        assertEquals("", TextToSpeechService.sanitize(null));
        assertEquals(2000, TextToSpeechService.sanitize("x".repeat(5000)).length());
        assertEquals("a b c", TextToSpeechService.sanitize("a\u0001b\nc"));
    }

    // ---------------------------------------------------------------- e-mail transport

    @Test
    void smtpRequiresValidatedTlsAndHasNoDebugOrTrustBypass() {
        Properties p = EmailService.mailProperties("smtp.example.com", "587");
        assertEquals("true", p.get("mail.smtp.starttls.required"));
        assertEquals("true", p.get("mail.smtp.starttls.enable"));
        assertEquals("true", p.get("mail.smtp.ssl.checkserveridentity"));
        assertNull(p.get("mail.smtp.ssl.trust"), "naming a trusted host would skip certificate validation");
        assertNull(p.get("mail.smtp.ssl.socketFactory.class"));
        assertEquals("false", p.get("mail.debug"), "debug mode prints the SMTP conversation");
        assertTrue(p.getProperty("mail.smtp.ssl.protocols").contains("TLSv1.2"));
        assertFalse(p.getProperty("mail.smtp.ssl.protocols").contains("SSL"), "no legacy protocols");
        for (String t : new String[] { "mail.smtp.connectiontimeout", "mail.smtp.timeout", "mail.smtp.writetimeout" }) {
            assertTrue(Integer.parseInt(p.getProperty(t)) > 0, t + " must be bounded");
        }
    }

    @Test
    void anEmptyPasswordIsNotAConfiguration() {
        EmailService e = new EmailService(); // no GMAIL_APP_PASSWORD is configured in the test environment
        e.configure("someone@example.com", "");
        assertFalse(e.isConfigured());
        e.configure("someone@example.com", "app-password-value");
        assertTrue(e.isConfigured());
        e.configure("", "app-password-value");
        assertFalse(e.isConfigured());
    }

    // ---------------------------------------------------------------- assistant: strict interpretation, nothing runs unconfirmed

    @Test
    void onlyExplicitWellFormedActionsAreAccepted() {
        GroqAIService.AIAction buy = GroqAIService.interpret("Sure! {\"action\":\"buy\",\"symbol\":\"aapl\",\"quantity\":10,\"price\":150}");
        assertEquals(GroqAIService.AIAction.Kind.BUY, buy.kind);
        assertEquals("AAPL", buy.symbol);
        assertEquals(10, buy.quantity);
        assertEquals(150.0, buy.price, 0.0);
        assertTrue(buy.requiresConfirmation());

        GroqAIService.AIAction sell = GroqAIService.interpret("{\"action\":\"sell\",\"symbol\":\"GOOGL\",\"quantity\":5}");
        assertEquals(GroqAIService.AIAction.Kind.SELL, sell.kind);
        assertTrue(sell.requiresConfirmation());

        GroqAIService.AIAction show = GroqAIService.interpret("{\"action\":\"show_portfolio\"}");
        assertEquals(GroqAIService.AIAction.Kind.SHOW_PORTFOLIO, show.kind);
        assertFalse(show.requiresConfirmation());
    }

    @Test
    void ambiguousOrDangerousRepliesBecomeUnclearNeverATrade() {
        String[] bad = {
                "I would buy some shares if I were you",                           // prose containing the word
                "{\"action\":\"buy\",\"symbol\":\"AAPL\",\"quantity\":10}",           // no price
                "{\"action\":\"buy\",\"symbol\":\"AAPL\",\"quantity\":10,\"price\":-5}",
                "{\"action\":\"buy\",\"symbol\":\"AAPL\",\"quantity\":0,\"price\":5}",
                "{\"action\":\"sell\",\"symbol\":\"AAPL\",\"quantity\":-3}",
                "{\"action\":\"sell\",\"symbol\":\"AAPL\",\"quantity\":99999999}",   // absurd quantity
                "{\"action\":\"sell\",\"symbol\":\"AAPL; DROP TABLE\",\"quantity\":1}", // not a ticker
                "{\"action\":\"sell\",\"symbol\":\"\",\"quantity\":1}",
                "{\"action\":\"transfer\",\"symbol\":\"AAPL\",\"quantity\":1}",
                "{not json", "", null
        };
        for (String reply : bad) {
            GroqAIService.AIAction a = GroqAIService.interpret(reply);
            assertEquals(GroqAIService.AIAction.Kind.UNCLEAR, a.kind, "reply: " + reply);
            assertFalse(a.requiresConfirmation());
        }
    }

    @Test
    void aSubstringThatLooksLikeBuyDoesNotMakeATrade() {
        GroqAIService.AIAction a = GroqAIService.interpret("{\"action\":\"show_portfolio\",\"symbol\":\"BUYX\"}");
        assertEquals(GroqAIService.AIAction.Kind.SHOW_PORTFOLIO, a.kind, "only the explicit action field matters");
        assertFalse(a.requiresConfirmation());
    }

    @Test
    void sellAllIntentIsRecognisedAndAlwaysNeedsConfirmation() {
        assertTrue(GroqAIService.isSellAllIntent("Please SELL ALL my stocks"));
        assertTrue(GroqAIService.isSellAllIntent("clear portfolio"));
        assertFalse(GroqAIService.isSellAllIntent("sell 5 AAPL"));
        assertFalse(GroqAIService.isSellAllIntent(null));
        GroqAIService.AIAction all = new GroqAIService.AIAction(GroqAIService.AIAction.Kind.SELL_ALL, null, 0, 0, null);
        assertTrue(all.requiresConfirmation());
    }
}
