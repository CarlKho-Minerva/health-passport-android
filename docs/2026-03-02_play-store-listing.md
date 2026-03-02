# Health Passport — Play Store Listing

```
Package      com.carlkho.healthpassport
Category     Medical
Language     English (United States)
Price        Free
```

---

## Short Description  `80 chars`

```
On-device AI medical scanner & health vault. Zero cloud. Snapdragon NPU.
```

---

## Full Description  `4000 chars`

```
HEALTH PASSPORT

On-device AI that scans, organizes, and answers questions about your health
documents. Zero cloud dependency. Your data never leaves your device.


SCAN MEDICAL DOCUMENTS

Point your camera at any medical document — prescriptions, lab results,
insurance cards, clinical notes. OmniNeural-4B Vision Language Model reads
and extracts structured data entirely on your Snapdragon NPU.


HEALTH VAULT

Records organized by body system, timeline, medications, and protocols.
Browse, search, and edit offline — no account, no sync, no server.


ASK YOUR DATA

"What are my current medications?"
"Summarize my cardiac history."
"When was my last eye exam?"

Answered instantly by on-device AI querying your personal vault.
Zero server round-trips. Zero latency. Zero exposure.


CLINIC TRANSCRIPTION

Record clinic visits. Parakeet-TDT ASR transcribes speech on-device and
auto-files the transcript to your vault.


DOCUMENT OCR

PaddleOCR extracts text from any medical document — prescriptions, lab
PDFs, insurance cards. Fully offline.


ARCHITECTURE

· Qualcomm Snapdragon NPU acceleration
· Nexa AI On-Device SDK v0.0.24
· OmniNeural-4B — Vision Language Model
· Parakeet-TDT — Speech Recognition
· PaddleOCR — Document Extraction
· EmbedGemma — Semantic Search (RAG)
· GGUF CPU fallbacks for non-Snapdragon devices


PRIVACY

No account. No cloud. No analytics. No data collection. All AI processing
is local. Android file-based encryption at rest. Uninstall to delete all.


REQUIREMENTS

· Android 8.1+ (API 27)
· Snapdragon 8 Gen 2 or newer for full NPU acceleration
· 4 GB RAM minimum recommended
· ~2 GB storage for AI models (downloaded on first use)


Built for the Qualcomm × Nexa AI On-Device Builder Bounty.
Source: github.com/CarlKho-Minerva/health-passport-android
```

---

## URLs

```
Privacy Policy   https://carlkho-minerva.github.io/health-passport-android/privacy-policy.html
Support          https://carlkho-minerva.github.io/health-passport-android/support.html
Website          https://carlkho-minerva.github.io/health-passport-android/
```

---

## Release Notes  `v1.0`

```
v1.0 — Initial Release

· On-device medical document scanning — OmniNeural-4B VLM
· Health records vault organized by body system
· AI chat over vault — 100% on-device RAG
· Clinic transcription — Parakeet-TDT ASR
· Document OCR — PaddleOCR
· Semantic search — EmbedGemma
· Zero cloud — all data stays on-device
· Snapdragon 8 Gen 2+ NPU acceleration
· GGUF CPU fallbacks for non-Snapdragon devices
```

---

## Content Rating

```
Violence                  No
Sexuality                 No
Language                  No
Controlled substances     No
User-generated content    No
Personal information      Yes — health data, stored locally only
Location                  No
Ads                       No
Expected rating           Everyone / Low Maturity
```

## Data Safety

```
Collects or shares user data    No
All processing on-device        Yes
Encryption                      Android file-based encryption at rest
Deletion                        Settings → Apps → Clear Data, or uninstall
```

---

## Assets Checklist

```
[ ] App icon         512×512 PNG, 32-bit, no alpha
[ ] Feature graphic  1024×500 PNG/JPEG
[ ] Screenshots      2+ phone, 9:16, 320–3840px  — capture from QDC device
[ ] Video            optional — https://www.youtube.com/watch?v=2JNhoXNvsCo
```

---

## Play Console Upload — Step by Step

### Step 1 — Create app
```
https://play.google.com/console → Create app

  App name      Health Passport
  Language      English (US)
  Type          App
  Price         Free
  Declarations  Check all
```

### Step 2 — Complete setup dashboard
```
App access          All functionality available without special access
Ads                 No ads
Content rating      Fill questionnaire → answers above
Target audience     18+
News app            No
COVID-19 tracing    No
Data safety         Fill → answers above
Government app      No
```

### Step 3 — Store listing
```
Upload app icon
Upload feature graphic
Upload 2+ screenshots
Paste short description
Paste full description
Set Privacy Policy URL
```

### Step 4 — Production release
```
Production → Create new release
Upload app-release.apk
Add release notes
Review → Start rollout to production
```

### Alternative — Internal testing first (recommended)
```
Testing → Internal testing → Create release
Add your email as tester
Upload APK → publish
Verify install via private link
Promote to Production when ready
```
