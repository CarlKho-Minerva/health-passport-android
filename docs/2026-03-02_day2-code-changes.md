# 2026-03-02 — Day 2: Code Changes Summary

## Context
Phase 2 prep for Qualcomm × Nexa AI On-Device Bounty. Won Phase 1 (Top 3, $500). Phase 2 requires Play Store submission + real on-device inference.

## Changes Made

### 1. Pinned Nexa SDK Version
**File:** `app/build.gradle.kts`
- Changed `ai.nexa:core:+` → `ai.nexa:core:0.0.24`
- Prevents breaking changes from unstable `+` version
- Matches docs.nexa.ai quickstart recommendation

### 2. Added Snapdragon Device Detection
**File:** `app/src/main/java/com/nexa/demo/MainActivity.kt` ~L1063
```kotlin
private fun isSnapdragonDevice(): Boolean
```
- Reads `Build.HARDWARE`, `Build.BOARD`, `/sys/devices/soc0/machine`, `/sys/devices/soc0/soc_id`
- Returns `true` for Qualcomm/Snapdragon devices
- **Why:** Pixel 6 Pro (Google Tensor) crashes on `cpu_gpu`/`npu` plugins because they need Qualcomm-specific native libs (libQnnCpu.so, libQnnHtp.so)

### 3. Added VLM Detection
**File:** `MainActivity.kt` ~L1089
```kotlin
private fun isVisionModel(fileName: String): Boolean
```
- Detects VLM files by name: contains "vl", "vision", "omni", "mmproj", or ends with ".nexa"
- Used to route manual model loads to `VlmWrapper` instead of `LlmWrapper`

### 4. Rewrote `loadManualModel()`
**File:** `MainActivity.kt` ~L1098-1160
- Added Snapdragon guard: warns (but doesn't block) non-Qualcomm devices
- Added VLM detection: routes vision models to `loadManualVlmModel()`
- LLM path unchanged for text-only models

### 5. Added `loadManualVlmModel()`
**File:** `MainActivity.kt` ~L1166-1243
- Handles GGUF VLMs: auto-discovers `mmproj` file in same directory
- Handles NPU VLMs: uses `.nexa` format with NPU config
- Falls back to LLM loading if VLM loading fails

### 6. Updated `model_list.json`
**File:** `app/src/main/assets/model_list.json`
- Expanded from 3 text-only models to 8 models:

| Model | Type | Plugin | Use Case |
|-------|------|--------|----------|
| OmniNeural-4B | VLM 👁️ | NPU | Medical document scanning |
| Qwen3-4B-Instruct | LLM 💬 | NPU | RAG chat over health vault |
| Llama-3.2-3B Turbo | LLM 💬 | NPU | Alternative LLM |
| PaddleOCR | OCR 📄 | NPU | OCR fallback for documents |
| Parakeet-TDT | ASR 🎙️ | NPU | Clinic transcription |
| EmbedGemma | Embed 🔗 | NPU | Embeddings for RAG |
| Llama-3.2-1B GGUF | LLM | CPU | CPU-only fallback |
| Qwen3-4B GGUF | LLM | CPU | CPU-only fallback |

### 7. Updated Companion Object TAG
- Changed `TAG = "MainActivity"` → `TAG = "HealthPassport"`
- Better log filtering

## Files Modified
- `app/build.gradle.kts` (1 change)
- `app/src/main/java/com/nexa/demo/MainActivity.kt` (4 changes, ~200 lines added)
- `app/src/main/assets/model_list.json` (complete rewrite)
- `engineering_log.md` (Day 2 entry added)

## Verification
- ✅ `model_list.json` validated with `python3 -m json.tool`
- ✅ grep confirmed all new functions at expected line numbers
- ⏳ Build not yet attempted (`./gradlew assembleDebug`)
