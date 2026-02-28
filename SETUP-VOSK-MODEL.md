# 🎤 Vosk Model Setup

## Current Status:
- ✅ vosk-0.3.45.jar → Moved to lib/
- ✅ jna-5.13.0.jar → Already in lib/
- ❌ Language model → MISSING (need to download)

---

## Download Language Model

### Option 1: Direct Download (Recommended)

**Click this link:**
```
https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip
```

**Size:** 40 MB

**Save to:** `C:\Users\boltr\Desktop\New folder\models\`

**Then extract it!**

---

### Option 2: PowerShell Command

Open PowerShell in your project folder and run:

```powershell
# Download model
Invoke-WebRequest -Uri "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip" -OutFile "models\vosk-model-small-en-in-0.4.zip"

# Extract model
Expand-Archive -Path "models\vosk-model-small-en-in-0.4.zip" -DestinationPath "models\" -Force

# Clean up zip file
Remove-Item "models\vosk-model-small-en-in-0.4.zip"
```

---

### Option 3: Run Batch Script

I'll create a batch script for you:

