# 🎤 FREE Voice Assistant Setup Guide

## Current Status

Your voice assistant works with **text commands** (type and send). To add **real voice recognition**, here are the FREE options:

---

## ✅ Option 1: Vosk (RECOMMENDED - 100% FREE)

### Why Vosk?
- ✅ **100% FREE** - No API keys, no limits
- ✅ **Works OFFLINE** - No internet needed
- ✅ **Pure Java** - Easy integration
- ✅ **Decent accuracy** - 80-85% for English
- ✅ **Small model** - ~50MB download
- ✅ **No signup** - Just download and use

### Setup Steps:

#### 1. Download Vosk Library
```
https://github.com/alphacep/vosk-api/releases
```
Download: `vosk-0.3.45.jar` (or latest version)

#### 2. Download Language Model
```
https://alphacephei.com/vosk/models
```
Download: `vosk-model-small-en-in-0.4.zip` (Indian English, 40MB)
Or: `vosk-model-small-en-us-0.15.zip` (US English, 40MB)

#### 3. Extract Model
```
Extract to: models/vosk-model-small-en-in-0.4/
```

#### 4. Add to Your Project
```
Copy vosk-0.3.45.jar to lib/ folder
```

#### 5. Add Code to PremiumStockDashboard.java

Add this import:
```java
import org.vosk.*;
import javax.sound.sampled.*;
```

Add this method:
```java
private void startVoskRecognition() {
    try {
        // Load model
        Model model = new Model("models/vosk-model-small-en-in-0.4");
        
        // Setup audio
        AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(format);
        microphone.start();
        
        // Create recognizer
        Recognizer recognizer = new Recognizer(model, 16000);
        
        // Listen
        byte[] buffer = new byte[4096];
        while (true) {
            int bytesRead = microphone.read(buffer, 0, buffer.length);
            if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                String result = recognizer.getResult();
                // Parse JSON result and extract text
                String command = extractTextFromJson(result);
                processVoiceCommand(command, null);
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
}

private String extractTextFromJson(String json) {
    // Simple JSON parsing
    int start = json.indexOf("\"text\" : \"") + 10;
    int end = json.indexOf("\"", start);
    return json.substring(start, end);
}
```

#### 6. Update Voice Button
```java
JButton micBtn = createStyledButton("🎤 Speak", new Color(220, 38, 127));
micBtn.addActionListener(e -> {
    new Thread(() -> startVoskRecognition()).start();
});
```

#### 7. Compile and Run
```cmd
javac -encoding UTF-8 -cp ".;lib/*" -d . src/com/portfolio/ui/PremiumStockDashboard.java
java --enable-native-access=ALL-UNNAMED -cp ".;lib/*" com.portfolio.ui.PremiumStockDashboard
```

---

## ✅ Option 2: Web Speech API (100% FREE, Easiest)

### Why Web Speech API?
- ✅ **100% FREE** - Built into Chrome/Edge
- ✅ **Best accuracy** - 95%+ (Google's engine)
- ✅ **No downloads** - Already in browser
- ✅ **No API key** - Just works
- ✅ **Easy integration** - Just HTML + JavaScript

### Setup Steps:

#### 1. Create HTML File
Create `voice-recognition.html`:
```html
<!DOCTYPE html>
<html>
<head>
    <title>Voice Recognition</title>
</head>
<body>
    <button id="startBtn">🎤 Start Listening</button>
    <div id="result"></div>
    
    <script>
        const recognition = new (window.SpeechRecognition || window.webkitSpeechRecognition)();
        recognition.lang = 'en-IN';  // Indian English
        recognition.continuous = false;
        recognition.interimResults = false;
        
        document.getElementById('startBtn').onclick = () => {
            recognition.start();
        };
        
        recognition.onresult = (event) => {
            const command = event.results[0][0].transcript;
            document.getElementById('result').innerText = command;
            
            // Send to Java via HTTP
            fetch('http://localhost:8080/voice-command', {
                method: 'POST',
                body: JSON.stringify({ command: command }),
                headers: { 'Content-Type': 'application/json' }
            });
        };
    </script>
</body>
</html>
```

#### 2. Add HTTP Server to Java
```java
import com.sun.net.httpserver.*;
import java.net.InetSocketAddress;

private void startVoiceServer() {
    try {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/voice-command", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes());
                // Parse JSON and extract command
                String command = extractCommand(body);
                processVoiceCommand(command, null);
                
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
            }
        });
        server.start();
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```

#### 3. Open HTML in Browser
```java
Desktop.getDesktop().browse(new URI("file:///path/to/voice-recognition.html"));
```

---

## ✅ Option 3: Google Cloud Speech (FREE Tier)

### Why Google Cloud?
- ✅ **FREE Tier** - 60 minutes/month
- ✅ **Best accuracy** - 95%+
- ✅ **Indian English** - Excellent support
- ✅ **Easy API** - Well documented

### Setup Steps:

#### 1. Create Google Cloud Account
```
https://console.cloud.google.com/
```

#### 2. Enable Speech-to-Text API
- Go to APIs & Services
- Enable "Cloud Speech-to-Text API"

#### 3. Create API Key
- Go to Credentials
- Create API Key
- Copy the key

#### 4. Download Library
```
https://github.com/googleapis/java-speech/releases
```
Download: `google-cloud-speech-4.0.0.jar` and dependencies

#### 5. Add Code
```java
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;

private void startGoogleRecognition(String apiKey) {
    try {
        SpeechSettings settings = SpeechSettings.newBuilder()
            .setCredentialsProvider(() -> new ApiKeyCredentials(apiKey))
            .build();
        
        SpeechClient speechClient = SpeechClient.create(settings);
        
        RecognitionConfig config = RecognitionConfig.newBuilder()
            .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
            .setSampleRateHertz(16000)
            .setLanguageCode("en-IN")  // Indian English
            .build();
        
        // Record audio
        byte[] audioBytes = recordAudio();
        ByteString audioData = ByteString.copyFrom(audioBytes);
        
        RecognitionAudio audio = RecognitionAudio.newBuilder()
            .setContent(audioData)
            .build();
        
        RecognizeResponse response = speechClient.recognize(config, audio);
        
        for (SpeechRecognitionResult result : response.getResultsList()) {
            String command = result.getAlternatives(0).getTranscript();
            processVoiceCommand(command, null);
        }
        
        speechClient.close();
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```

---

## 📊 Comparison

| Feature | Vosk | Web Speech API | Google Cloud |
|---------|------|----------------|--------------|
| Cost | FREE | FREE | FREE (60 min/month) |
| Accuracy | 80-85% | 95%+ | 95%+ |
| Offline | ✅ Yes | ❌ No | ❌ No |
| Setup Time | 15 min | 5 min | 30 min |
| API Key | ❌ No | ❌ No | ✅ Yes |
| Indian English | ✅ Good | ✅ Excellent | ✅ Excellent |
| File Size | 50MB | 0MB | 20MB |
| Internet | ❌ No | ✅ Yes | ✅ Yes |

---

## 🎯 My Recommendation

### For Your Project: **Vosk**

**Why?**
1. ✅ 100% FREE - No limits, no API keys
2. ✅ Works offline - No internet dependency
3. ✅ Easy to demo - Just run the app
4. ✅ Pure Java - No web components
5. ✅ Good enough accuracy for demo

**Setup Time:** 15 minutes
**Cost:** $0
**Limitations:** None

---

## 🚀 Quick Start with Vosk

### Step-by-Step:

1. **Download Vosk JAR**
   ```
   https://github.com/alphacep/vosk-api/releases/download/v0.3.45/vosk-0.3.45.jar
   ```
   Save to: `lib/vosk-0.3.45.jar`

2. **Download Model**
   ```
   https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip
   ```
   Extract to: `models/vosk-model-small-en-in-0.4/`

3. **I'll add the code for you** - Just provide the files!

4. **Run**
   ```cmd
   java --enable-native-access=ALL-UNNAMED -cp ".;lib/*" com.portfolio.ui.PremiumStockDashboard
   ```

---

## 💡 What You Need to Provide

### For Vosk Integration:
1. Download `vosk-0.3.45.jar` → Put in `lib/` folder
2. Download `vosk-model-small-en-in-0.4.zip` → Extract to `models/` folder
3. Tell me when done → I'll add the code!

### For Google Cloud (if you prefer):
1. Create Google Cloud account
2. Enable Speech-to-Text API
3. Get API key
4. Give me the API key → I'll integrate it!

---

## 🎉 Summary

**Current:** Text commands working ✅  
**Next:** Add real voice recognition  
**Best Option:** Vosk (FREE, offline, easy)  
**Alternative:** Google Cloud (FREE tier, best accuracy)  

**Your Choice:**
- Want offline + free → Use Vosk
- Want best accuracy + free tier → Use Google Cloud
- Want easiest + free → Use Web Speech API

**Let me know which option you want, and I'll help you set it up!** 🚀
