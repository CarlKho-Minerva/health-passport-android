# Play Store Listing — Health Passport

## App Details
- **Package name:** `com.carlkho.healthpassport`
- **App name:** Health Passport
- **Category:** Medical
- **Default language:** English (United States)
- **Free or Paid:** Free

---

## Short Description (80 chars max)
```
AI-powered medical document scanner & health records vault — runs 100% on-device
```

## Full Description (4000 chars max)
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

---

## URLs
- **Privacy Policy:** https://carlkho-minerva.github.io/health-passport-android/privacy-policy.html
- **Support URL:** https://carlkho-minerva.github.io/health-passport-android/support.html
- **Website:** https://carlkho-minerva.github.io/health-passport-android/

---

## Release Notes (v1.0)
```
v1.0 — Initial Release
• On-device medical document scanning with OmniNeural-4B VLM
• Health records vault organized by body system
• AI chat for health questions (100% on-device, RAG over vault)
• Clinic visit transcription with Parakeet ASR
• OCR document extraction with PaddleOCR
• Zero cloud dependency — all data stays on your phone
• Supports Snapdragon 8 Gen 2+ NPU acceleration
• CPU fallback models for non-Snapdragon devices
```

---

## Content Rating Questionnaire
- Violence: **No**
- Sexuality: **No**
- Language: **No**
- Controlled substances: **No**
- User-generated content: **No**
- Personal information: **Yes** (health data stored locally only)
- Location: **No**
- Ads: **No**
- Expected rating: **Everyone** / **Low Maturity**

## Data Safety
- **Does your app collect or share any user data?** → No
- **Is all data processed on-device?** → Yes
- **Encryption:** Data at rest on device storage (Android file-based encryption)
- **Data deletion:** User can delete via Settings → Apps → Clear Data, or uninstall

---

## Required Assets Checklist
- [ ] App icon (512×512 PNG, 32-bit, no alpha) → Can export from Android Studio or create manually
- [ ] Feature graphic (1024×500 PNG/JPEG) → Create in Canva/Figma
- [ ] Phone screenshots (min 2, 16:9 or 9:16, 320-3840px) → Capture from QDC
- [ ] Tablet screenshots (optional)
- [ ] Video (optional but recommended) → YouTube: https://www.youtube.com/watch?v=2JNhoXNvsCo

---

## Step-by-step Play Console Upload

### 1. Go to https://play.google.com/console
### 2. Click "Create app"
### 3. Fill:
- App name: Health Passport
- Language: English (US)
- App or game: App
- Free or paid: Free
- Check all declarations
### 4. Complete "Set up your app" dashboard:
- App access → All functionality available without special access
- Ads → No ads
- Content rating → Fill questionnaire (answers above)
- Target audience → 18+
- News app → No
- COVID-19 contact tracing/status → No
- Data safety → Fill (see above)
- Government apps → No
### 5. Store listing:
- Upload app icon
- Upload feature graphic
- Upload 2+ phone screenshots
- Paste short description
- Paste full description
- Set Privacy Policy URL
### 6. Go to Production → Create new release
- Upload app-release.apk (or .aab)
- Add release notes
- Review → Start rollout

### Alternative: Internal Testing First
- Go to Testing → Internal testing
- Create a release
- Add your email as tester
- Upload APK → publish
- Test the private link
- Then promote to production
