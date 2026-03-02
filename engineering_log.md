# Health Passport: Engineering Log

## Day 2 Session 2 - 2026-03-02
**Objective:** Build APKs, fix all compilation errors, set up Play Store infrastructure.

### 🏗️ Technical Progress
- [x] **Created `gradle.properties`** — `android.useAndroidX=true`, `android.enableJetifier=true`, keystore passwords
- [x] **Created `transform/` module** — minimal Android library stub (was referenced in settings.gradle.kts but never committed)
- [x] **Fixed `copyBundledModels()`** — added missing `assetModels` variable: `assets.list("nexa_models") ?: emptyArray()`
- [x] **Fixed embedder inference** — `embed()` returns `EmbedResult`, not `FloatArray`; added `EmbedResult` import, access `embedResult.embeddings`
- [x] **Implemented RAG** — `loadVaultContext()` (keyword-based file retrieval from health vault), `buildRagPrompt()` (wraps vault context + user query)
- [x] **Uncommented embedder inference code** (~L2219-2258)
- [x] **Fixed ASR audio path** — was hardcoded `/sdcard/Download/assets/OSR_us_000_0010_16k.wav`, now uses `audioFile!!.absolutePath`
- [x] **Generated real signing keystore** — `healthpassport-release.jks` (RSA 2048, alias `healthpassport`, DN: Carl Kho/Health Passport/CarlKho/San Francisco/CA/US)
- [x] **Built debug APK** — 299 MB at `app/build/outputs/apk/debug/app-debug.apk`
- [x] **Built signed release APK** — 273 MB at `app/build/outputs/apk/release/app-release.apk`
- [x] **Signing verified** — apksigner confirms CN=Carl Kho certificate

### 📄 Documentation Created
- `docs/2026-03-02_day2-code-changes.md` — history of Day 2 session 1 code changes
- `docs/2026-03-02_phase2-game-plan.md` — comprehensive 9-section Phase 2 guide
- `docs/2026-03-02_play-store-listing.md` — full Play Store listing text + step-by-step upload guide
- `docs/2026-03-02_qdc-testing-guide.md` — QDC session walkthrough + testing checklist
- `docs/privacy-policy.html` — privacy policy for Play Store (12 sections, styled HTML)
- `docs/support.html` — support/FAQ page for Play Store
- `docs/index.html` — GitHub Pages landing page

### 🔧 Build Issues Resolved
1. **Missing `:transform` module** → Created minimal Android library stub
2. **`android.useAndroidX` not set** → Created `gradle.properties`
3. **No space left on device** (713 MB free) → Cleared pip/Homebrew/Gradle caches (+3.8 GB)
4. **Corrupted Gradle state** → Full Gradle daemon stop + cache reset
5. **Keystore password not found** → Moved passwords from `local.properties` to `gradle.properties` (findProperty reads gradle.properties)
6. **`assetModels` undefined** → Added inline `assets.list()` call
7. **EmbedResult type mismatch** → Fixed to access `.embeddings` property from `EmbedResult`

### 🚧 Next Steps
- [ ] Push to GitHub, enable GitHub Pages (Settings → Pages → main → /docs)
- [ ] Create Google Play Developer account ($25)
- [ ] Upload release APK to Play Console
- [ ] Test on QDC Snapdragon 8 Elite (follow `docs/2026-03-02_qdc-testing-guide.md`)
- [ ] Download models on QDC via HuggingFace
- [ ] Record demo video with real on-device inference
- [ ] Submit to Play Store review

---

## Day 2 - 2026-03-02
**Objective:** Phase 2 prep — fix model loading, add VLM support, prepare for Play Store.

### 🏗️ Technical Progress
- [x] **Pinned SDK to 0.0.24** (was `+` which is unstable). Matches docs.nexa.ai quickstart.
- [x] **Added Snapdragon device detection** (`isSnapdragonDevice()`) — warns users on non-Qualcomm hardware instead of silent crash.
- [x] **Added VLM detection** (`isVisionModel()`) — auto-routes vision models to `VlmWrapper` instead of `LlmWrapper`.
- [x] **Rewrote `loadManualModel()`** — now detects VLM files and dispatches to `loadManualVlmModel()`.
- [x] **Added `loadManualVlmModel()`** — handles GGUF VLMs (with mmproj lookup), Nexa NPU VLMs, and LLM fallback if VLM loading fails.
- [x] **Updated `model_list.json`** with all Nexa NPU Mobile models:
  - OmniNeural-4B (VLM — primary for medical document scanning)
  - Qwen3-4B-Instruct NPU (LLM for RAG chat)
  - Llama-3.2-3B NPU Turbo (LLM alternative)
  - PaddleOCR NPU (OCR fallback)
  - Parakeet-TDT NPU (ASR for clinic transcription)
  - EmbedGemma NPU (Embeddings for RAG)
  - + GGUF fallbacks for CPU-only devices

### 🚧 Next Steps (Phase 2)
- [ ] Build APK and test on QDC Snapdragon 8 Elite
- [ ] Download OmniNeural-4B on QDC, test real VLM medical scan
- [ ] Record updated demo video with real inference
- [ ] Google Play Store: create developer account ($25), create listing, upload signed APK
- [ ] Add Play Asset Delivery for model files (>150MB APK limit)

---

## Day 1 - 2026-03-01
**Objective:** Resolve model bundling issues and establish project structure.

### 🏗️ Technical Progress
- [x] Located project source code in `Downloads/CODELocalProjects/`.
- [x] Identified missing `assets/nexa_models` directory required for bundled LLMs.
- [x] Created `engineering_log.md` for tracking.
- [x] Setup `nexa_models` directory structure.

### 🚧 Blockers & Issues
- **Issue:** Bundled models are not detected by `MainActivity.copyBundledModels()`.
- **Status:** Investigating/Fixing.
- **Root Cause:** Directory `assets/nexa_models` is missing from the source tree.

### 🧪 Results & Validation
- **Device:** Local Filesystem
- **Status:** Project structure updated.

### 📅 Next Steps
- [x] Create `app/src/main/assets/nexa_models/` folder.
- [x] Verify build with `./gradlew assembleDebug`.
