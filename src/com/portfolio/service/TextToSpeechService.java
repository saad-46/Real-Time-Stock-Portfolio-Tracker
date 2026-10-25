package com.portfolio.service;

import javax.sound.sampled.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;

/**
 * Text-to-Speech Service using ElevenLabs API (FREE tier available)
 * Or fallback to Java's built-in speech synthesis
 */
public class TextToSpeechService {

    private final HttpClient httpClient;
    private static final String ELEVENLABS_API_KEY = ApiKeyManager.getElevenLabsKey();
    private static final String ELEVENLABS_URL = "https://api.elevenlabs.io/v1/text-to-speech/";

    // Voice IDs (ElevenLabs)
    private static final String VOICE_ID = "21m00Tcm4TlvDq8ikWAM"; // Rachel voice

    public TextToSpeechService() {
        this.httpClient = Net.client();
    }

    /**
     * Speak text using Java's built-in TTS (cross-platform, no API needed)
     * 
     * @param text Text to speak
     */
    public void speak(String text) {
        com.portfolio.AppThreads.daemon(() -> {
            try {
                // Use Java's built-in TTS via command line
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    // Windows: Use PowerShell
                    speakWindows(text);
                } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                    // macOS: Use 'say' command
                    speakMac(text);
                } else {
                    // Linux: Use espeak if available
                    speakLinux(text);
                }
            } catch (Exception e) {
                System.err.println("TTS Error: " + e.getMessage());
            }
        }).start();
    }

    /** Text to speak, made safe to hand to a speech engine: control characters become spaces, length capped. */
    static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").replace('\n', ' ').trim();
        return t.length() > 2000 ? t.substring(0, 2000) : t;
    }

    /**
     * Windows speech via PowerShell. The text is passed in an ENVIRONMENT VARIABLE and read as data by the script;
     * it is never part of the script or the command line, so quotes or ';' in AI/news text cannot run anything.
     */
    static ProcessBuilder windowsCommand(String text) {
        ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
                "Add-Type -AssemblyName System.Speech; $s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                        + "$s.Rate = 1; $s.Volume = 100; $s.Speak($env:STOCKVAULT_TTS_TEXT)");
        pb.environment().put("STOCKVAULT_TTS_TEXT", sanitize(text));
        return pb;
    }

    /** macOS: text goes in on stdin ("say -f -"), so it can never be parsed as an option. */
    static ProcessBuilder macCommand() {
        return new ProcessBuilder("say", "-f", "-");
    }

    /** Linux: text goes in on stdin, never on the command line. */
    static ProcessBuilder linuxCommand() {
        return new ProcessBuilder("espeak", "--stdin");
    }

    private void run(ProcessBuilder pb, String stdinText) throws Exception {
        Process process = pb.start();
        try (OutputStream out = process.getOutputStream()) {
            if (stdinText != null) {
                out.write(sanitize(stdinText).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        if (!process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
        }
    }

    private void speakWindows(String text) throws Exception {
        run(windowsCommand(text), null);
    }

    private void speakMac(String text) throws Exception {
        run(macCommand(), text);
    }

    private void speakLinux(String text) throws Exception {
        run(linuxCommand(), text);
    }

    /**
     * Speak using ElevenLabs API (better quality, requires API key)
     * 
     * @param text Text to speak
     */
    public void speakWithElevenLabs(String text) {
        if (ELEVENLABS_API_KEY.isEmpty()) {
            System.out.println("⚠️ ElevenLabs API key not set, using built-in TTS");
            speak(text);
            return;
        }

        com.portfolio.AppThreads.daemon(() -> {
            try {
                // Build request
                String requestBody = String.format(
                        "{\"text\":\"%s\",\"model_id\":\"eleven_monolingual_v1\",\"voice_settings\":{\"stability\":0.5,\"similarity_boost\":0.5}}",
                        text.replace("\"", "\\\""));

                HttpRequest request = HttpRequest.newBuilder().timeout(Net.REQUEST_TIMEOUT)
                        .uri(URI.create(ELEVENLABS_URL + VOICE_ID))
                        .header("xi-api-key", ELEVENLABS_API_KEY)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

                if (response.statusCode() == 200) {
                    // Play audio
                    playAudio(response.body());
                } else {
                    System.err.println("ElevenLabs API error: " + response.statusCode());
                    speak(text); // Fallback
                }
            } catch (Exception e) {
                System.err.println("ElevenLabs TTS Error: " + e.getMessage());
                speak(text); // Fallback
            }
        }).start();
    }

    /**
     * Play audio bytes
     */
    private void playAudio(byte[] audioData) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
        AudioInputStream audioStream = AudioSystem.getAudioInputStream(bais);

        AudioFormat format = audioStream.getFormat();
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);

        line.open(format);
        line.start();

        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = audioStream.read(buffer)) != -1) {
            line.write(buffer, 0, bytesRead);
        }

        line.drain();
        line.close();
        audioStream.close();
    }

    /**
     * Stop any ongoing speech
     */
    public void stop() {
        // For built-in TTS, we can't easily stop it
        // For ElevenLabs, we'd need to track the audio line
        System.out.println("🔇 Stopping speech...");
    }
}
