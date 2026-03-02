# 2026-03-02 — QDC Testing Session Guide

## What is QDC?
Qualcomm Device Cloud — remote access to real Snapdragon hardware.
- **URL:** https://qdc.qualcomm.com
- **Your device:** Snapdragon 8 Elite QRD8750
- **Free minutes:** 1000
- **SSH key:** `/Users/cvk/Downloads/carl/qdc_id_2026-2-15_1941.pem`


---

## Step 1: Start a QDC Session

1. Open https://qdc.qualcomm.com in your browser
2. Log in with your Qualcomm account
3. Navigate to **Devices** → find **Snapdragon 8 Elite QRD8750**
4. Click **Launch Session**
5. Select mode: **Screen Mirroring + SSH** (you need both)
6. Wait ~2 minutes for the device to boot
7. You'll see the device screen in the browser + an SSH command

---

## Step 2: Connect via SSH

The QDC dashboard will give you an SSH command like:
```bash
ssh -i /Users/cvk/Downloads/CODELocalProjects/health-passport-android/qdc_id_2026-2-15_1941.pem -L 5037:sa484732.sa.svc.cluster.local:5037 -N sshtunnel@ssh.qdc.qualcomm.com
```

Run it in your terminal. Once connected, you have `adb` access to the device.

Test adb:
```bash
adb devices
# Should show the QRD8750 device
```

---

## Step 3: Install the APK

### Option A: Via ADB (SSH)
```bash
# From your local machine, use the SSH-forwarded adb
adb install /Users/cvk/Downloads/CODELocalProjects/health-passport-android/app/build/outputs/apk/debug/app-debug.apk
```

### Option B: Via Screen Mirroring
1. Upload APK through the QDC file manager
2. The device will prompt to install

### Option C: Push APK and install on device
```bash
adb push app-debug.apk /sdcard/Download/
adb shell pm install /sdcard/Download/app-debug.apk
```

---

## Step 3b: Reinstall APK with Crash Fix

> **The original APK had a bug:** the model selector spinner is hidden in the UI,
> so `selectModelId` stayed empty (`""`) and tapping **Download** crashed the app
> with `NoSuchElementException`. The fix initializes `selectModelId` to OmniNeural-4B
> on startup. Reinstall before testing.

```bash
# Uninstall old build first (avoids signature conflicts)
adb uninstall com.nexa.demo

# Install the fixed APK
adb install /Users/cvk/Downloads/CODELocalProjects/health-passport-android/app/build/outputs/apk/debug/app-debug.apk
```

---

## Step 4: Download Models on Device

### Option A: In-App Download (Recommended)
1. Open Health Passport app on the QDC device
2. Tap **Download** — there is NO visible model spinner; the app defaults to **OmniNeural-4B** automatically (fixed in latest build)
3. Wait for download (~2 GB, QDC has fast internet)
4. Once downloaded, tap **Load** → dialog appears with plugin options
5. Select **NPU** → tap **Sure**
6. Wait for "VLM loaded" toast

> Note: the model spinner (`sp_model_list`) exists in code but is hidden in the layout. The UI shows four buttons: Download / Load / Select Model File / Health Vault. Download defaults to OmniNeural-4B.

### Option B: Pre-push Models via ADB
```bash
# On your Mac, download from HuggingFace first
pip install huggingface-hub
huggingface-cli download NexaAI/OmniNeural-4B-mobile --local-dir ./OmniNeural-4B-mobile

# Push to device
adb push ./OmniNeural-4B-mobile /sdcard/Download/nexa_models/OmniNeural-4B-mobile/

# The app's autoDetectModels() will find it, or place in the app's model dir:
adb shell mkdir -p /data/data/com.carlkho.healthpassport/files/models/omni-neural/
adb push ./OmniNeural-4B-mobile/* /data/data/com.carlkho.healthpassport/files/models/omni-neural/
```

### Option C: Push a Test Image for Scanning

You don't need a real medical document to test VLM scanning — push the included thumbnail:

```bash
# Push the test image to the device's Downloads folder
adb push /Users/cvk/Downloads/CODELocalProjects/health-passport-android/docs/thumbnail.jpg /sdcard/Download/thumbnail.jpg
```

Then in the app:
1. After loading OmniNeural-4B, tap the **image icon** (bottom of chat)
2. Navigate to **Downloads/** and select `thumbnail.jpg`
3. Type "What does this document say?" and tap **Send**

---

## Step 5: Test Each Feature

### 5a. VLM Document Scanning (Primary Feature)
1. Make sure OmniNeural-4B is loaded (VLM 👁️)
2. Tap the **camera icon** (📷) in the bottom bar
3. Point at a medical document (or use a pre-saved image)
   - If no real document available, take a photo of text on screen
4. Type: "What does this document say?"
5. Tap **Send**
6. Watch for real VLM inference output (not the mock hardcoded response)
7. Check the TTFT and decoding speed in the profile message

Expected output: Structured markdown with document type, date, findings table, medications, action items.

### 5b. LLM RAG Chat
1. Unload VLM (tap Unload)
2. Load **Qwen3-4B (LLM 💬)** with NPU
3. Type: "What are my current medications?"
4. The app will:
   - Read relevant vault files (03_Protocols/Active_Medications.md)
   - Prepend them as context
   - Send augmented prompt to LLM
5. LLM should respond based on the vault data

Other test queries:
- "Tell me about my eye health"
- "What was my last appointment?"
- "Give me a health summary"

### 5c. OCR
1. Load **PaddleOCR (OCR 📄)**
2. Take a photo of text
3. Should return OCR text with confidence scores

### 5d. ASR Transcription
1. Load **Parakeet ASR (🎙️ Speech)**
2. Tap the microphone button
3. Record some audio
4. Tap done
5. Should transcribe speech to text

### 5e. Mock Mode (No Model)
1. Unload any model
2. Take a photo → sends → triggers `runMockScanDemo()` (fake scan with hardcoded result)
3. Type health questions → triggers `handlePreloadedQuery()` (keyword RAG without model)

---

## Step 6: Test Sequence Checklist

```
[ ] App launches without crash
[ ] Intro modal appears on first launch
[ ] Model list shows all 8 models
[ ] OmniNeural-4B downloads successfully
[ ] OmniNeural-4B loads with NPU plugin
[ ] VLM inference produces real output from image
[ ] VLM inference TTFT < 5s on Snapdragon 8 Elite
[ ] Qwen3-4B downloads and loads
[ ] LLM RAG chat returns vault-augmented responses
[ ] PaddleOCR loads and produces OCR text
[ ] Parakeet ASR loads and transcribes audio
[ ] Health vault browser shows files
[ ] Files are editable
[ ] Settings dialog shows model info
[ ] Mock scan works when no model loaded
[ ] Preloaded RAG works when no model loaded
[ ] App doesn't crash on unload/reload cycle
```

---

## Step 7: Capture Screenshots & Demo

### Screenshots for Play Store
Take screenshots of:
1. Main chat screen (with the "Health Passport" header and privacy badge)
2. Loading a model (progress indicator)
3. VLM scanning result (structured markdown output from real document)
4. Health vault browser (showing the file tree)
5. RAG chat response (LLM answering from vault data)

### Capture via ADB
```bash
# Take screenshot
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png ./screenshots/

# Screen record (max 3 minutes)
adb shell screenrecord /sdcard/demo.mp4
# Press Ctrl+C to stop
adb pull /sdcard/demo.mp4 ./
```

### QDC Screen Recording
The QDC screen mirroring interface usually has a built-in record button. Use that for the demo video.

---

## Troubleshooting

### Model won't load
- Check logcat: `adb logcat -s HealthPassport:* | tail -50`
- Ensure NPU is selected (not CPU) for NPU models
- Check device has enough free RAM: `adb shell cat /proc/meminfo | head -5`

### App crashes
- Check logcat for stack trace: `adb logcat | grep -A 20 "FATAL\|HealthPassport"`
- Common cause: Trying to load GGUF model with NPU plugin or vice versa
- OOM: 4B models need ~4GB RAM

### Download fails
- QDC devices should have fast internet
- Check: `adb shell ping -c 3 huggingface.co`
- If blocked, try pushing models via ADB instead

### No image button visible
- Image button only shows when VLM or CV model is loaded
- If using LLM, you'll only have text input

---

## Time Budget
- Session setup: ~5 min
- APK install: ~2 min
- OmniNeural-4B download: ~5-10 min (2GB on fast connection)
- Model loading: ~30 sec
- Testing all features: ~20 min
- Screenshots + recording: ~10 min
- **Total:** ~45-60 min of your 1000 free minutes

*Remember: End the QDC session when done to save minutes!*
