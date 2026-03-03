# 2026-03-02 — Phase 2 Complete Game Plan

> **Status:** Active — follow steps in order
> **Deadline:** TBD (Lynn said "prepare for Phase 2")
> **Prize:** Phase 1 = $500 (paid). Phase 2 = TBD (likely larger).
> **Requirement from Lynn:** "We are asking all finalists to submit their app to the Google Play Store"

---

## Table of Contents
1. [Build the APK](#1-build-the-apk)
2. [Create Signed Release APK (Real Keystore)](#2-create-signed-release-apk)
3. [Google Play Store Developer Account](#3-google-play-store-account)
4. [GitHub Pages for Privacy Policy](#4-github-pages-privacy-policy)
5. [Create Play Store Listing](#5-play-store-listing)
6. [Download Models from HuggingFace](#6-huggingface-models)
7. [QDC Testing Session](#7-qdc-session)
8. [Record Demo Video](#8-demo-video)
9. [Submit to Play Store](#9-submit-to-play-store)

---

## 1. Build the APK

### Debug Build (for testing)
```bash
cd /Users/cvk/Downloads/CODELocalProjects/health-passport-android
./gradlew clean assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### If build fails
Common issues:
- **Missing SDK:** Open in Android Studio → SDK Manager → install SDK 36
- **Gradle version:** Check `gradle/wrapper/gradle-wrapper.properties`
- **Java version:** Needs JDK 11+. Check with `java -version`

---

## 2. Create Signed Release APK

### Step 2a: Generate a Real Keystore
```bash
keytool -genkey -v \
  -keystore /Users/cvk/Downloads/CODELocalProjects/health-passport-android/healthpassport-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias healthpassport \
  -storepass <YOUR_PASSWORD> \
  -keypass <YOUR_PASSWORD> \
  -dname "CN=Carl Kho, OU=Health Passport, O=CarlKho, L=San Francisco, ST=CA, C=US"
```

**IMPORTANT:**
- Replace `<YOUR_PASSWORD>` with a strong password you'll remember
- **NEVER lose this keystore** — you need the SAME key for all future updates
- Back it up somewhere safe (NOT in git)

### Step 2b: Create `local.properties` (if not exists)
```properties
# /Users/cvk/Downloads/CODELocalProjects/health-passport-android/local.properties
sdk.dir=/Users/cvk/Library/Android/sdk
KEYSTORE_PASSWORD=<YOUR_PASSWORD>
KEY_PASSWORD=<YOUR_PASSWORD>
```

### Step 2c: Update `build.gradle.kts` Signing Config
The signing config already reads from properties. Update it to use the real keystore:

Change in `app/build.gradle.kts`:
```kotlin
signingConfigs {
    create("release") {
        storeFile = file("healthpassport-release.jks")
        storePassword = project.findProperty("KEYSTORE_PASSWORD")?.toString() ?: ""
        keyAlias = "healthpassport"
        keyPassword = project.findProperty("KEY_PASSWORD")?.toString() ?: ""
    }
}
```

### Step 2d: Build the Release APK
```bash
./gradlew clean assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

### Step 2e: Verify the APK is Signed
```bash
# Check signing info
jarsigner -verify -verbose -certs app/build/outputs/apk/release/app-release.apk

# Or use Android build tools
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

---

## 3. Google Play Store Account

### Step 3a: Create Google Play Developer Account
1. Go to https://play.google.com/console/signup
2. Sign in with your Google account
3. Accept the Developer Distribution Agreement
4. Pay the **one-time $25 registration fee**
5. Complete identity verification:
   - Full legal name
   - Address
   - Phone number
   - Email
   - ``` I've shipped three Android apps to date, none yet published through Play Console.

Health Passport (Feb 2026): On-device medical document scanner built for the Qualcomm × Nexa AI Bounty. Users photograph prescriptions and lab reports; a Vision-Language Model (Qwen VL) runs inference directly on the Qualcomm Hexagon NPU via the Nexa SDK — no cloud upload. Placed Top 3 of 100+ teams. APK was distributed manually via Google Drive. GitHub: github.com/CarlKho-Minerva/health-passport-android

Nailbite Tracker (2024): Wear OS app for the Pixel Watch that detects hand-to-mouth gestures using accelerometer data. A 14-day N=1 experiment showed 80% reduction in nail-biting episodes. Distributed as a sideloaded APK.

Distracted Driving Detector (2019): Android app that reads accelerometer data to detect vehicle motion above 20 m/s² and restricts phone usage while driving. Built in 3 days for a Grade 10 Innovation Expo. No hardware required.

I'm creating this account to publish Health Passport Phase 2 — a full Play Store release integrating Parakeet speech-to-text, live camera OCR, and longitudinal health record management.

Stack: Kotlin, Jetpack Compose, CameraX, Nexa SDK, PaddleOCR, on-device VLM inference.```
6. **Wait for verification** — can take 2-7 days for individual accounts
   - Google may ask for ID verification

### Step 3b: Create the App in Play Console
1. Go to https://play.google.com/console
2. Click **"Create app"**
3. Fill in:
   - **App name:** Health Passport
   - **Default language:** English (United States)
   - **App or game:** App
   - **Free or paid:** Free
4. Check all declaration boxes
5. Click **Create app**

---

## 4. GitHub Pages Privacy Policy

### Step 4a: Create Privacy Policy Page
You need a privacy policy URL for Play Store. Easiest: host on GitHub Pages.

1. Go to https://github.com/CarlKho-Minerva/health-passport-android
2. Create file: `docs/privacy-policy.html` (or use a dedicated repo)

Content is created for you at: `docs/privacy-policy.html` (see below in this plan)

### Step 4b: Enable GitHub Pages
1. Go to your repo → **Settings** → **Pages**
2. Source: **Deploy from a branch**
3. Branch: `main` → `/docs` folder
4. Click **Save**
5. Wait ~2 min, your URL will be:
   `https://carlkho-minerva.github.io/health-passport-android/privacy-policy.html`

### Step 4c: Also Create Support URL
Play Store needs a support URL too. Options:
- Use the same GitHub Pages site with a `support.html`
- Use your GitHub repo URL: `https://github.com/CarlKho-Minerva/health-passport-android/issues`
- Use your email: `carlkho.cvk@gmail.com` (or whatever your contact email is)

---

## 5. Play Store Listing

### Required Assets

| Asset | Spec | Status |
|-------|------|--------|
| App icon | 512×512 PNG, 32-bit, no alpha | Need to create |
| Feature graphic | 1024×500 PNG or JPEG | Need to create |
| Screenshots (phone) | Min 2, 320-3840px, 16:9 or 9:16 | Need to capture |
| Screenshots (tablet) | Optional but recommended | Can skip |
| Short description | Max 80 chars | See below |
| Full description | Max 4000 chars | See below |
| Privacy policy URL | Must be publicly accessible | Step 4 |
| App category | Medical | — |
| Content rating | IARC questionnaire | Fill in console |
| Target audience | 18+ (medical app) | — |

### Short Description (80 chars max)
```
AI-powered medical document scanner & health records vault — runs 100% on-device
```

### Full Description (4000 chars max)
```
Health Passport — Your AI Health Records, 100% On-Device

Health Passport uses on-device AI to scan, categorize, and store your medical documents — with zero cloud dependency. Your health data never leaves your phone.

🔍 SCAN MEDICAL DOCUMENTS
Point your camera at prescriptions, lab results, insurance cards, or any medical document. Our on-device Vision Language Model (OmniNeural-4B) reads and extracts structured data instantly.

📋 ORGANIZED HEALTH VAULT
All your health records organized by body system (Head/Eyes/ENT, Cardiovascular, etc.), timeline, medications, and protocols. Browse, search, and edit your records anytime.

💬 ASK QUESTIONS ABOUT YOUR HEALTH
Chat with your health vault using on-device AI. Ask "What are my current medications?" or "Show my eye prescription history" and get instant answers from YOUR data.

🔒 100% PRIVATE — NO CLOUD
All AI processing happens on your Snapdragon-powered device using Qualcomm NPU acceleration. Your medical records, prescriptions, and health data NEVER leave your phone. No account required. No data collection.

🎙️ CLINIC TRANSCRIPTION
Record and transcribe clinic visits with on-device speech recognition. Auto-files transcripts to your health vault.

⚡ POWERED BY
• Qualcomm Snapdragon NPU (Neural Processing Unit)
• Nexa AI On-Device SDK
• OmniNeural-4B Vision Language Model
• Parakeet ASR for speech recognition
• PaddleOCR for document text extraction

📱 REQUIREMENTS
• Android 8.1+ (API 27)
• Snapdragon 8 Gen 2 or newer recommended for NPU features
• 4GB+ RAM recommended
• ~2GB storage for AI models (downloaded on first use)

Built for the Qualcomm × Nexa AI On-Device Builder Bounty Program.
Open source: https://github.com/CarlKho-Minerva/health-passport-android
```

### Content Rating Questionnaire Answers
When filling out the IARC questionnaire:
- Violence: No
- Sexuality: No
- Language: No
- Controlled substances: No (it tracks medications but doesn't sell/promote them)
- User-generated content: No (all data stays on device)
- Personal information: Yes — collects health data (stored locally only)
- Location: No
- Ads: No

This should give you an **"Everyone"** or **"Low Maturity"** rating.

### Data Safety Section
When filling out Data Safety:
- **Does your app collect or share any user data?** No (all processing is on-device)
- **Data collected:** None shared. Health records stored locally.
- **Encryption:** Data at rest on device storage
- **Data deletion:** User can delete health vault at any time

---

## 6. HuggingFace Models

### How the App Downloads Models
The app has a built-in model downloader (`downloadModel()` in MainActivity.kt). When you tap a model in the list:
1. It fetches the file list from the HuggingFace repo URL in `model_list.json`
2. Downloads all files to `filesDir/models/<modelName>/`
3. Shows progress bar

### NPU Models (Snapdragon only — use on QDC)
These are the models in `model_list.json`. They download automatically in-app:

| Model | HuggingFace Repo | Size | Purpose |
|-------|------------------|------|---------|
| OmniNeural-4B | [NexaAI/OmniNeural-4B-mobile](https://huggingface.co/NexaAI/OmniNeural-4B-mobile) | ~2GB | Document scanning VLM |
| Qwen3-4B-Instruct | [NexaAI/Qwen3-4B-Instruct-2507-npu-mobile](https://huggingface.co/NexaAI/Qwen3-4B-Instruct-2507-npu-mobile) | ~2GB | Chat/RAG LLM |
| PaddleOCR | [NexaAI/paddleocr-npu-mobile](https://huggingface.co/NexaAI/paddleocr-npu-mobile) | ~50MB | OCR fallback |
| Parakeet-TDT | [NexaAI/parakeet-tdt-0.6b-v3-npu-mobile](https://huggingface.co/NexaAI/parakeet-tdt-0.6b-v3-npu-mobile) | ~300MB | Speech-to-text |
| EmbedGemma | [NexaAI/embeddinggemma-300m-npu-mobile](https://huggingface.co/NexaAI/embeddinggemma-300m-npu-mobile) | ~300MB | Embeddings for RAG |

### GGUF Models (CPU fallback — works on any Android)
| Model | Direct Download | Size |
|-------|----------------|------|
| Llama-3.2-1B | [bartowski/Llama-3.2-1B-Instruct-GGUF](https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_0.gguf) | ~700MB |
| Qwen3-4B | [unsloth/Qwen3-4B-GGUF](https://huggingface.co/unsloth/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_0.gguf) | ~2.5GB |

### Manual Download (for QDC or testing)
If you want to pre-download models:

```bash
# Install huggingface CLI
pip install huggingface-hub

# Download OmniNeural-4B (the VLM — most important model)
huggingface-cli download NexaAI/OmniNeural-4B-mobile --local-dir ./OmniNeural-4B-mobile

# Download Qwen3-4B LLM
huggingface-cli download NexaAI/Qwen3-4B-Instruct-2507-npu-mobile --local-dir ./Qwen3-4B-npu

# Download PaddleOCR
huggingface-cli download NexaAI/paddleocr-npu-mobile --local-dir ./paddleocr-npu

# Download GGUF fallback (single file)
huggingface-cli download bartowski/Llama-3.2-1B-Instruct-GGUF Llama-3.2-1B-Instruct-Q4_0.gguf --local-dir ./

# Or use wget for single files
wget https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_0.gguf
```

### Push Models to QDC Device via ADB
```bash
# Push to device storage where the app can find them
adb push ./OmniNeural-4B-mobile /sdcard/Download/nexa_models/
adb push ./Qwen3-4B-npu /sdcard/Download/nexa_models/

# Or push a GGUF directly
adb push Llama-3.2-1B-Instruct-Q4_0.gguf /data/local/tmp/
```

---

## 7. QDC Testing Session

### What is QDC?
Qualcomm Device Cloud — remote access to real Snapdragon devices. You have 1000 free minutes.
- URL: https://qdc.qualcomm.com
- Device: Snapdragon 8 Elite QRD8750
- SSH Key: `/Users/cvk/Downloads/carl/qdc_id_2026-2-15_1941.pem`

### Step 7a: Start QDC Session
1. Go to https://qdc.qualcomm.com
2. Log in
3. Select **Snapdragon 8 Elite QRD8750**
4. Choose **Screen mirroring + SSH** mode
5. Wait for device to boot (~2 min)

### Step 7b: Install APK on QDC
Once connected:
```bash
# SSH into QDC (use the SSH command shown in QDC dashboard)
ssh -i /Users/cvk/Downloads/carl/qdc_id_2026-2-15_1941.pem <user>@<host>

# Install APK via adb
adb install app/build/outputs/apk/debug/app-debug.apk

# Or if using screen mirroring, drag-drop the APK file
```

### Step 7c: Test Models on QDC
1. **Open Health Passport app**
2. **Download OmniNeural-4B** — tap the model in the list, wait for download
3. **Load the model** — select it, hit Load. Should show "VLM loaded: ..."
4. **Test VLM scanning:**
   - Tap camera icon → take photo of a medical document (or use a saved image)
   - Type "What does this document say?" and send
   - Should get real VLM inference output
5. **Test LLM chat:**
   - Download Qwen3-4B-Instruct
   - Load it
   - Ask health questions — should respond from on-device LLM
6. **Test mock mode (no model):**
   - Without loading a model, take a photo and send
   - Should trigger `runMockScanDemo()` with hardcoded response

### Step 7d: Capture Screenshots for Play Store
While on QDC:
1. Screenshots of the app in different states:
   - Main chat screen
   - Model loading
   - VLM scanning a document
   - Health vault browser
   - Settings dialog
2. Use QDC screen capture button or `adb shell screencap -p /sdcard/screenshot.png && adb pull /sdcard/screenshot.png`

---

## 8. Demo Video

### What to Record
Record a QDC session showing:
1. App launch → intro modal
2. Download OmniNeural-4B model (show progress)
3. Load model → "VLM loaded" message
4. Take photo of medical document
5. Send → real VLM inference output (structured medical data)
6. Browse health vault → show categorized records
7. Chat with LLM about health question
8. Show settings → model info, vault stats

### Tools
- QDC screen mirroring has built-in recording
- Or use QuickTime on Mac: File → New Screen Recording
- Upload to YouTube (update existing video or new one)
- Current YouTube: https://www.youtube.com/watch?v=2JNhoXNvsCo

---

## 9. Submit to Play Store

### Checklist Before Submission
- [ ] Signed release APK built with real keystore
- [ ] Privacy policy hosted on GitHub Pages
- [ ] App icon (512×512)
- [ ] Feature graphic (1024×500)
- [ ] At least 2 phone screenshots
- [ ] Short description (80 chars)
- [ ] Full description
- [ ] Content rating completed
- [ ] Data safety section filled
- [ ] Target API level 36 (already set)

### Upload to Play Console
1. Go to Play Console → your app → **Production** (or **Internal testing** first)
2. Click **Create new release**
3. Upload `app-release.apk`
4. Add release notes:
   ```
   v1.0 — Initial Release
   • On-device medical document scanning with OmniNeural-4B VLM
   • Health records vault organized by body system
   • AI chat for health questions (100% on-device)
   • Clinic visit transcription with Parakeet ASR
   • Zero cloud dependency — all data stays on your phone
   ```
5. Review and **Start rollout**

### Internal Testing (Recommended First)
Instead of going straight to production:
1. Go to **Testing** → **Internal testing**
2. Create an internal testing release
3. Add your email as a tester
4. Upload APK → review → publish to internal track
5. You'll get a private link to install the app
6. Test everything works, then promote to production

---

## Quick Reference

### Key Files
| File | Purpose |
|------|---------|
| `app/build.gradle.kts` | Build config, signing, dependencies |
| `app/src/main/java/com/nexa/demo/MainActivity.kt` | Main app logic (3270 lines) |
| `app/src/main/assets/model_list.json` | Model catalog (8 models) |
| `app/src/main/assets/health_vault/` | Pre-populated health records |
| `engineering_log.md` | Development tracking |
| `docs/` | Privacy policy, changelogs |

### Key URLs
| Resource | URL |
|----------|-----|
| GitHub Repo | https://github.com/CarlKho-Minerva/health-passport-android |
| YouTube Demo | https://www.youtube.com/watch?v=2JNhoXNvsCo |
| Medium Blog | https://medium.com/@carlkho-cvk/health-passport-270f183db3c6 |
| Nexa SDK Docs | https://docs.nexa.ai/en/nexa-sdk-android/quickstart |
| QDC | https://qdc.qualcomm.com |
| Play Console | https://play.google.com/console |

### Device Info
| Device | Chip | NPU Support |
|--------|------|-------------|
| Pixel 6 Pro (yours) | Google Tensor | ❌ No Qualcomm NPU |
| QDC QRD8750 | Snapdragon 8 Elite | ✅ Full NPU support |

---

*Last updated: 2026-03-02*
