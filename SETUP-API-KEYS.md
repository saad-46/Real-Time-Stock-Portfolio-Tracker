# 🔑 API Keys Setup

## Required API Keys

This application requires two API keys for the AI voice assistant to work:

### 1. AssemblyAI (Voice Recognition)
- **Purpose:** Converts speech to text with 95% accuracy
- **Free Tier:** 5 hours/month
- **Get your key:** https://www.assemblyai.com/

### 2. Groq AI (Conversational AI)
- **Purpose:** Powers the conversational AI assistant
- **Free Tier:** Unlimited (currently)
- **Get your key:** https://console.groq.com/

---

## Setup Methods

### Method 1: Environment Variables (Recommended)

**Windows (PowerShell):**
```powershell
$env:ASSEMBLYAI_API_KEY="your_assemblyai_key_here"
$env:GROQ_API_KEY="your_groq_key_here"
```

**Windows (Command Prompt):**
```cmd
set ASSEMBLYAI_API_KEY=your_assemblyai_key_here
set GROQ_API_KEY=your_groq_key_here
```

**Linux/Mac:**
```bash
export ASSEMBLYAI_API_KEY="your_assemblyai_key_here"
export GROQ_API_KEY="your_groq_key_here"
```

### Method 2: Edit Source Code Directly

If you don't want to use environment variables, you can edit the source files:

**File: `src/com/portfolio/service/AssemblyAIVoiceService.java`**
```java
private static final String API_KEY = "your_assemblyai_key_here";
```

**File: `src/com/portfolio/service/GroqAIService.java`**
```java
private static final String API_KEY = "your_groq_key_here";
```

---

## Running the Application

After setting up API keys:

```bash
# Compile
javac -encoding UTF-8 -cp "lib/*;." -d . src/com/portfolio/**/*.java

# Run
java -cp "lib/*;." com.portfolio.Main
```

---

## Verification

When you run the application:
1. Login with any username/password
2. Go to "My Portfolio" page
3. Click "🎤 Voice Assistant"
4. Click "🎤 Speak" and say something
5. If API keys are correct, you'll see transcription and AI response

---

## Troubleshooting

### "API error" or "Upload failed"
- Check your internet connection
- Verify API keys are correct
- Check API quota (AssemblyAI: 5 hours/month)

### "YOUR_ASSEMBLYAI_API_KEY_HERE" error
- You haven't set the API keys
- Follow Method 1 or Method 2 above

---

## Security Note

⚠️ **Never commit API keys to Git!**

The source code uses environment variables by default to keep your keys secure.
