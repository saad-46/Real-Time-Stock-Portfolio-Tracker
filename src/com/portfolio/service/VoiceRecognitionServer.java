package com.portfolio.service;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * HTTP Server for receiving voice commands from Web Speech API
 * Provides 95%+ accuracy using Google's speech recognition
 */
public class VoiceRecognitionServer {
    
    private HttpServer server;
    private Consumer<String> commandHandler;
    private boolean isRunning = false;
    
    public VoiceRecognitionServer(Consumer<String> commandHandler) {
        this.commandHandler = commandHandler;
    }
    
    public void start() throws IOException {
        if (isRunning) return;
        
        server = HttpServer.create(new InetSocketAddress(8765), 0);
        
        // Handle voice commands
        server.createContext("/voice-command", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                // Enable CORS
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                
                // Read request body
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                
                // Parse JSON (simple parsing)
                String command = extractCommand(body);
                
                System.out.println("🎤 Voice command received: " + command);
                
                // Process command
                if (commandHandler != null && !command.isEmpty()) {
                    commandHandler.accept(command);
                }
                
                // Send response
                String response = "{\"status\":\"success\",\"message\":\"Command received\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else if ("OPTIONS".equals(exchange.getRequestMethod())) {
                // Handle CORS preflight
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
            }
        });
        
        server.setExecutor(null);
        server.start();
        isRunning = true;
        
        System.out.println("✅ Voice Recognition Server started on port 8765");
    }
    
    public void stop() {
        if (server != null && isRunning) {
            server.stop(0);
            isRunning = false;
            System.out.println("🛑 Voice Recognition Server stopped");
        }
    }
    
    private String extractCommand(String json) {
        try {
            // Simple JSON parsing for {"command":"text"}
            int start = json.indexOf("\"command\"");
            if (start == -1) return "";
            
            start = json.indexOf(":", start) + 1;
            start = json.indexOf("\"", start) + 1;
            int end = json.indexOf("\"", start);
            
            if (end > start) {
                return json.substring(start, end).trim();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
}
