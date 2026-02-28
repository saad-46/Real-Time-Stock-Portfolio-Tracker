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
    private static final String ELEVENLABS_API_KEY = ""; // Optional: Add ElevenLabs key for better quality
    private static final String ELEVENLABS_URL = "https://api.elevenlabs.io/v1/text-to-speech/";
    
    // Voice IDs (ElevenLabs)
    private static final String VOICE_ID = "21m00Tcm4TlvDq8ikWAM"; // Rachel voice
    
    public TextToSpeechService() {
        this.httpClient = HttpClient.newHttpClient();
    }
    
    /**
     * Speak text using Java's built-in TTS (cross-platform, no API needed)
     * @param text Text to speak
     */
    public void speak(String text) {
        new Thread(() -> {
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
    
    /**
     * Windows TTS using PowerShell
     */
    private void speakWindows(String text) throws Exception {
        // Escape quotes in text
        text = text.replace("\"", "'");
        
        String command = String.format(
            "powershell -Command \"Add-Type -AssemblyName System.Speech; " +
            "$speak = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
            "$speak.Rate = 1; " +
            "$speak.Volume = 100; " +
            "$speak.Speak('%s')\"",
            text
        );
        
        Process process = Runtime.getRuntime().exec(command);
        process.waitFor();
    }
    
    /**
     * macOS TTS using 'say' command
     */
    private void speakMac(String text) throws Exception {
        String[] command = {"say", text};
        Process process = Runtime.getRuntime().exec(command);
        process.waitFor();
    }
    
    /**
     * Linux TTS using espeak
     */
    private void speakLinux(String text) throws Exception {
        String[] command = {"espeak", text};
        Process process = Runtime.getRuntime().exec(command);
        process.waitFor();
    }
    
    /**
     * Speak using ElevenLabs API (better quality, requires API key)
     * @param text Text to speak
     */
    public void speakWithElevenLabs(String text) {
        if (ELEVENLABS_API_KEY.isEmpty()) {
            System.out.println("⚠️ ElevenLabs API key not set, using built-in TTS");
            speak(text);
            return;
        }
        
        new Thread(() -> {
            try {
                // Build request
                String requestBody = String.format(
                    "{\"text\":\"%s\",\"model_id\":\"eleven_monolingual_v1\",\"voice_settings\":{\"stability\":0.5,\"similarity_boost\":0.5}}",
                    text.replace("\"", "\\\"")
                );
                
                HttpRequest request = HttpRequest.newBuilder()
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
