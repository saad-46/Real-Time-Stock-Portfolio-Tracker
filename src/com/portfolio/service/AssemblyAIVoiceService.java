package com.portfolio.service;

import javax.sound.sampled.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import org.json.*;

/**
 * AssemblyAI Voice Recognition Service
 * 95% accuracy, FREE 5 hours/month
 * Pure Java implementation
 */
public class AssemblyAIVoiceService {
    
    private static final String API_KEY = System.getenv("ASSEMBLYAI_API_KEY") != null ? 
        System.getenv("ASSEMBLYAI_API_KEY") : "YOUR_ASSEMBLYAI_API_KEY_HERE";  // Set via environment variable
    private static final String UPLOAD_URL = "https://api.assemblyai.com/v2/upload";
    private static final String TRANSCRIPT_URL = "https://api.assemblyai.com/v2/transcript";
    
    private final HttpClient httpClient;
    private boolean isRecording = false;
    private TargetDataLine microphone;
    
    public AssemblyAIVoiceService() {
        this.httpClient = HttpClient.newHttpClient();
    }
    
    /**
     * Record audio from microphone with Voice Activity Detection (VAD)
     * Automatically stops when user stops speaking
     * @return Transcribed text with 95% accuracy
     */
    public String recordAndTranscribeWithVAD() throws Exception {
        System.out.println("🎤 Starting recording with Voice Activity Detection...");
        System.out.println("💡 Speak now! Recording will stop when you finish speaking.");
        
        // Step 1: Record audio with VAD
        File audioFile = recordAudioWithVAD();
        System.out.println("✅ Recording complete: " + audioFile.getName());
        
        // Step 2: Upload audio to AssemblyAI
        System.out.println("📤 Uploading audio to AssemblyAI...");
        String uploadUrl = uploadAudio(audioFile);
        System.out.println("✅ Upload complete");
        
        // Step 3: Request transcription
        System.out.println("🔄 Transcribing audio...");
        String transcriptId = requestTranscription(uploadUrl);
        
        // Step 4: Poll for result
        String transcription = pollForTranscription(transcriptId);
        System.out.println("✅ Transcription: " + transcription);
        
        // Cleanup
        audioFile.delete();
        
        return transcription;
    }
    
    /**
     * Record audio from microphone and transcribe using AssemblyAI
     * @param durationSeconds How long to record (default: 5 seconds)
     * @return Transcribed text with 95% accuracy
     */
    public String recordAndTranscribe(int durationSeconds) throws Exception {
        System.out.println("🎤 Starting recording for " + durationSeconds + " seconds...");
        
        // Step 1: Record audio
        File audioFile = recordAudio(durationSeconds);
        System.out.println("✅ Recording complete: " + audioFile.getName());
        
        // Step 2: Upload audio to AssemblyAI
        System.out.println("📤 Uploading audio to AssemblyAI...");
        String uploadUrl = uploadAudio(audioFile);
        System.out.println("✅ Upload complete");
        
        // Step 3: Request transcription
        System.out.println("🔄 Transcribing audio...");
        String transcriptId = requestTranscription(uploadUrl);
        
        // Step 4: Poll for result
        String transcription = pollForTranscription(transcriptId);
        System.out.println("✅ Transcription: " + transcription);
        
        // Cleanup
        audioFile.delete();
        
        return transcription;
    }
    
    /**
     * Record audio from microphone
     */
    private File recordAudio(int durationSeconds) throws Exception {
        // Try multiple audio formats until one works
        AudioFormat[] formats = {
            new AudioFormat(16000, 16, 1, true, false),  // 16kHz mono
            new AudioFormat(44100, 16, 1, true, false),  // 44.1kHz mono (CD quality)
            new AudioFormat(8000, 16, 1, true, false),   // 8kHz mono (phone quality)
            new AudioFormat(22050, 16, 1, true, false),  // 22kHz mono
        };
        
        AudioFormat selectedFormat = null;
        DataLine.Info info = null;
        
        // Find a supported format
        for (AudioFormat format : formats) {
            info = new DataLine.Info(TargetDataLine.class, format);
            if (AudioSystem.isLineSupported(info)) {
                selectedFormat = format;
                System.out.println("✅ Using audio format: " + format);
                break;
            }
        }
        
        if (selectedFormat == null) {
            throw new Exception("No supported microphone format found!\n" +
                "Please check:\n" +
                "1. Microphone is connected\n" +
                "2. Microphone is not being used by another app\n" +
                "3. Audio drivers are installed");
        }
        
        microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(selectedFormat);
        microphone.start();
        isRecording = true;
        
        // Create temp file
        File audioFile = File.createTempFile("voice_", ".wav");
        
        // Record in separate thread
        Thread recordThread = new Thread(() -> {
            try {
                AudioInputStream audioStream = new AudioInputStream(microphone);
                AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, audioFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        recordThread.start();
        
        // Record for specified duration
        Thread.sleep(durationSeconds * 1000);
        
        // Stop recording
        isRecording = false;
        microphone.stop();
        microphone.close();
        recordThread.join();
        
        return audioFile;
    }
    
    /**
     * Upload audio file to AssemblyAI
     */
    private String uploadAudio(File audioFile) throws Exception {
        byte[] audioData = Files.readAllBytes(audioFile.toPath());
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(UPLOAD_URL))
            .header("authorization", API_KEY)
            .header("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofByteArray(audioData))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new Exception("Upload failed: " + response.body());
        }
        
        JSONObject json = new JSONObject(response.body());
        return json.getString("upload_url");
    }
    
    /**
     * Request transcription from AssemblyAI
     */
    private String requestTranscription(String audioUrl) throws Exception {
        JSONObject requestBody = new JSONObject();
        requestBody.put("audio_url", audioUrl);
        requestBody.put("language_code", "en");  // English
        
        // Add speech model (required by AssemblyAI)
        JSONArray speechModels = new JSONArray();
        speechModels.put("universal-2");  // Use universal-2 model
        requestBody.put("speech_models", speechModels);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(TRANSCRIPT_URL))
            .header("authorization", API_KEY)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new Exception("Transcription request failed: " + response.body());
        }
        
        JSONObject json = new JSONObject(response.body());
        return json.getString("id");
    }
    
    /**
     * Poll for transcription result
     */
    private String pollForTranscription(String transcriptId) throws Exception {
        String url = TRANSCRIPT_URL + "/" + transcriptId;
        
        // Poll every 1 second, max 30 seconds
        for (int i = 0; i < 30; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("authorization", API_KEY)
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new Exception("Polling failed: " + response.body());
            }
            
            JSONObject json = new JSONObject(response.body());
            String status = json.getString("status");
            
            if (status.equals("completed")) {
                String text = json.getString("text");
                double confidence = json.optDouble("confidence", 0.0);
                System.out.println("📊 Confidence: " + Math.round(confidence * 100) + "%");
                return text;
            } else if (status.equals("error")) {
                throw new Exception("Transcription error: " + json.optString("error"));
            }
            
            // Wait 1 second before next poll
            Thread.sleep(1000);
        }
        
        throw new Exception("Transcription timeout");
    }
    
    /**
     * Record audio with Voice Activity Detection (VAD)
     * Stops automatically when user stops speaking
     */
    private File recordAudioWithVAD() throws Exception {
        // Try multiple audio formats until one works
        AudioFormat[] formats = {
            new AudioFormat(16000, 16, 1, true, false),  // 16kHz mono
            new AudioFormat(44100, 16, 1, true, false),  // 44.1kHz mono (CD quality)
            new AudioFormat(8000, 16, 1, true, false),   // 8kHz mono (phone quality)
            new AudioFormat(22050, 16, 1, true, false),  // 22kHz mono
        };
        
        AudioFormat selectedFormat = null;
        DataLine.Info info = null;
        
        // Find a supported format
        for (AudioFormat format : formats) {
            info = new DataLine.Info(TargetDataLine.class, format);
            if (AudioSystem.isLineSupported(info)) {
                selectedFormat = format;
                System.out.println("✅ Using audio format: " + format);
                break;
            }
        }
        
        if (selectedFormat == null) {
            throw new Exception("No supported microphone format found!");
        }
        
        final AudioFormat finalFormat = selectedFormat;  // Make final for lambda
        
        microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(finalFormat);
        microphone.start();
        isRecording = true;
        
        // Create temp file
        File audioFile = File.createTempFile("voice_", ".wav");
        
        // VAD parameters
        final int SILENCE_THRESHOLD = 500;  // Amplitude threshold for silence
        final int SILENCE_DURATION_MS = 1500;  // 1.5 seconds of silence = done
        final int MAX_RECORDING_MS = 30000;  // Max 30 seconds
        final int MIN_RECORDING_MS = 500;  // Min 0.5 seconds
        
        // Record with VAD in separate thread
        Thread recordThread = new Thread(() -> {
            try {
                ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                
                long startTime = System.currentTimeMillis();
                long lastSoundTime = startTime;
                boolean hasDetectedSound = false;
                
                while (isRecording) {
                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        audioBuffer.write(buffer, 0, bytesRead);
                        
                        // Calculate amplitude (simple VAD)
                        int amplitude = 0;
                        for (int i = 0; i < bytesRead; i += 2) {
                            if (i + 1 < bytesRead) {
                                int sample = (buffer[i + 1] << 8) | (buffer[i] & 0xFF);
                                amplitude = Math.max(amplitude, Math.abs(sample));
                            }
                        }
                        
                        // Check if sound detected
                        if (amplitude > SILENCE_THRESHOLD) {
                            lastSoundTime = System.currentTimeMillis();
                            hasDetectedSound = true;
                        }
                        
                        long currentTime = System.currentTimeMillis();
                        long elapsedTime = currentTime - startTime;
                        long silenceDuration = currentTime - lastSoundTime;
                        
                        // Stop conditions:
                        // 1. Detected sound, then silence for SILENCE_DURATION_MS
                        // 2. Max recording time reached
                        if (hasDetectedSound && silenceDuration > SILENCE_DURATION_MS && elapsedTime > MIN_RECORDING_MS) {
                            System.out.println("🔇 Silence detected - stopping recording");
                            isRecording = false;
                            break;
                        }
                        
                        if (elapsedTime > MAX_RECORDING_MS) {
                            System.out.println("⏱️ Max recording time reached");
                            isRecording = false;
                            break;
                        }
                    }
                }
                
                // Write to WAV file
                byte[] audioData = audioBuffer.toByteArray();
                ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
                AudioInputStream audioStream = new AudioInputStream(bais, finalFormat, audioData.length / finalFormat.getFrameSize());
                AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, audioFile);
                
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        recordThread.start();
        recordThread.join();
        
        // Stop recording
        microphone.stop();
        microphone.close();
        
        return audioFile;
    }
    
    /**
     * Stop recording if in progress
     */
    public void stopRecording() {
        if (isRecording && microphone != null) {
            isRecording = false;
            microphone.stop();
            microphone.close();
        }
    }
}
