# Health Passport — Architecture & Plan

> **Qualcomm × Nexa AI On-Device Bounty** · Snapdragon 8 Elite · Feb 2026
> Carl Vincent Kho · Minerva University '26

---

## How It Works (Simple Version)

```
┌─────────────────────────────────────────────┐
│              Health Passport                 │
│                                              │
│   You talk, scan, or type.                   │
│   Your phone understands — no internet.      │
│   Your medical data never leaves the device. │
└─────────────────────────────────────────────┘
```

**Three ways to interact:**

| You do this... | The phone does this... | You get... |
|---|---|---|
| 📝 Type a question | Searches your health vault → asks the AI | A personalized medical answer |
| 📷 Scan a document | Reads every word with OCR → saves it → explains it | Your record digitized + analyzed |
| 🎤 Send a voice note | Transcribes speech → searches vault → asks the AI | Hands-free medical Q&A |

**Everything runs on the chip. No cloud. No API keys. No waiting.**

---

## The Pipeline (Abstracted)

```
  Input                    Brain                      Output
─────────     ──────────────────────────     ─────────────────
  Text   ───→  Health Vault (RAG) + LLM  ──→  Answer
  Photo  ───→  OCR → Vault Save → LLM   ──→  Record + Analysis
  Voice  ───→  ASR → RAG + LLM          ──→  Answer
  Image  ───→  Vision Model (VLM)        ──→  Description
```

The user never picks which model to use. The app routes automatically based on what you send.

---

## Insights Table

| # | Insight | What It Means | Impact |
|---|---------|---------------|--------|
| 1 | **NPU beats CPU 50×** | Qwen3-4B on NPU: 19 tok/s decode, 1049 tok/s prefill. CPU models crawl at <1 tok/s on 4B params. | Real-time conversation possible only on NPU |
| 2 | **Multi-model coexistence** | 6 NPU models can be loaded without unloading each other (type-specific memory management) | Seamless pipeline — OCR, LLM, ASR all hot simultaneously |
| 3 | **RAG > fine-tuning for medical** | Keyword-mapped health vault with ~4000 char context window gives accurate, source-grounded answers without any training | Zero training cost, works immediately with any user's records |
| 4 | **OCR auto-save closes the loop** | Scanning a prescription auto-saves structured text to the vault → future queries can reference it | The vault grows smarter with every scan |
| 5 | **Think-tag stripping = UX** | Qwen3 emits `<think>...</think>` reasoning blocks. Stripping them from UI output makes AI feel instant and polished | Customer-facing quality without losing model capability |
| 6 | **446ms TTFT** | Time to first token under 500ms on Snapdragon 8 Elite. Feels like the AI is already thinking before you finish reading | Perceived latency ≈ 0 for the user |
| 7 | **HF URL detection prevents download failures** | Model URLs can be S3 or HuggingFace — auto-detecting the format prevents misparse and failed downloads | Reliable model delivery over any CDN |
| 8 | **Garbage output detection** | Regex-based detection of repeated/template artifacts prevents broken responses from reaching the user | Graceful failure instead of gibberish |
| 9 | **WakeLock for downloads** | 30-min partial wake lock prevents OS sleep from killing multi-GB model downloads | Users can lock their phone during download |
| 10 | **On-device = HIPAA-adjacent** | No data leaves the device → no BAA needed, no breach surface, no cloud costs | Regulatory simplicity for health data |

---

## Implementations Table

| # | Feature | Models Used | Code Location | Status |
|---|---------|------------|---------------|--------|
| 1 | **Smart Text Q&A** | Qwen3-4B (LLM) + Health Vault | `btnSend` → `loadVaultContext()` → `buildRagPrompt()` → LLM inference | ✅ Working |
| 2 | **Document OCR Scan** | PaddleOCR (CV) | `btnSend` → OCR extract → `saveHealthRecord()` → auto-LLM analysis | ✅ Working |
| 3 | **Voice-to-Answer** | Parakeet ASR + Qwen3-4B | `btnSend` → ASR transcribe → RAG + LLM | ✅ Working |
| 4 | **Vision Analysis** | OmniNeural-4B (VLM) | `btnSend` → VLM with image → analysis | ✅ Working |
| 5 | **Multi-Model Loading** | All NPU models | `loadModel()` — type-specific cleanup, no global reset | ✅ Working |
| 6 | **Model Download + Fallback** | N/A | `ModelFileListingUtil` — S3 primary, HF fallback, HF URL auto-detect | ✅ Fixed |
| 7 | **Health Vault Browser** | None | `browseVaultFolder()` → `showFileContent()` → `editFileContent()` | ✅ Working |
| 8 | **Auto-Save Medical Records** | LLM output detection | `handleResult()` — keyword trigger → `saveHealthRecord()` | ✅ Working |
| 9 | **Think-Tag Stripping** | Qwen3-4B | Regex strip in `handleResult()` streaming + final path | ✅ Working |
| 10 | **Mock Scan Demo** | None (pre-scripted) | `runMockScanDemo()` — 6-stage animated scan | ✅ Working |
| 11 | **Color-Coded Model Badges** | All | `showModelPicker()` — LLM=green, OCR=blue, VLM=purple, ASR=amber, EMB=cyan | ✅ Working |
| 12 | **Embedding Utility** | EmbedGemma | `btnSend` → embedder path → vector stats display | ✅ Working |

---

## Model Registry

| Model | Role | Backend | Size | Speed |
|-------|------|---------|------|-------|
| **Qwen3-4B Instruct** | Brain (LLM) | NPU | ~3.3 GB | 19 tok/s decode, 1049 tok/s prefill |
| **PaddleOCR** | Document Scanner | NPU | ~8 MB | < 1s per page |
| **Parakeet ASR** | Speech-to-Text | NPU | ~600 MB | Real-time |
| **OmniNeural-4B** | Vision (VLM) | NPU | ~3 GB | ~15 tok/s |
| **EmbedGemma** | Memory/Embeddings | NPU | ~300 MB | Instant |
| **Llama-3.2-3B Turbo** | Alt Brain (NPU) | NPU | ~3.2 GB | ~15 tok/s |
| **Llama-3.2-1B** | Lightweight (CPU) | CPU | ~700 MB | ~5 tok/s |
| **Qwen3-4B (CPU)** | CPU Fallback | CPU | ~2.5 GB | ~1 tok/s |

---

## Architecture Diagram

```
┌──────────────────────────────────────────────────────────┐
│                      User Interface                       │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐  ┌─────────┐ │
│  │  Text Box │  │  Camera  │  │   Audio   │  │  Vault  │ │
│  └─────┬────┘  └────┬─────┘  └─────┬─────┘  │ Browser │ │
│        │             │              │         └─────────┘ │
├────────┼─────────────┼──────────────┼────────────────────┤
│        ▼             ▼              ▼                     │
│   ┌─────────── Smart Pipeline Router ──────────────┐     │
│   │                                                 │     │
│   │  text?  ──→ RAG Lookup → LLM                   │     │
│   │  image? ──→ OCR → Save → LLM  or  VLM          │     │
│   │  audio? ──→ ASR → RAG → LLM                    │     │
│   │                                                 │     │
│   └─────────────────────────────────────────────────┘     │
│                          │                                │
├──────────────────────────┼────────────────────────────────┤
│          NPU Inference Layer (Snapdragon 8 Elite)         │
│                                                           │
│  ┌──────────┐ ┌────────┐ ┌─────┐ ┌─────┐ ┌───────────┐  │
│  │ Qwen3-4B │ │PaddleOC│ │ ASR │ │ VLM │ │ EmbedGemma│  │
│  │  (LLM)   │ │  (OCR) │ │     │ │     │ │  (Embed)  │  │
│  └──────────┘ └────────┘ └─────┘ └─────┘ └───────────┘  │
│                                                           │
├───────────────────────────────────────────────────────────┤
│           Health Vault (On-Device File System)            │
│                                                           │
│  health_vault/          health_records/                   │
│  ├── eyes.md            ├── scan_2026-02-15_1423.md       │
│  ├── medications.md     ├── scan_2026-02-16_0901.md       │
│  ├── allergies.md       └── ...                           │
│  ├── conditions.md                                        │
│  └── ...                                                  │
└───────────────────────────────────────────────────────────┘
```

---

## Bug Fix Log

| # | Bug | Root Cause | Fix | Commit |
|---|-----|-----------|-----|--------|
| 1 | Download crash | `downloadModel()` called on null thread | Wrapped in `modelScope.launch` | `966349e` |
| 2 | NPE on load | Null model path access | Added null checks before `loadModel()` | `966349e` |
| 3 | Incomplete download | OS sleep kills download midway | Added `PARTIAL_WAKE_LOCK` (30 min) | `966349e` |
| 4 | VLM think-only output | VLM returned only `<think>` block, no actual answer | Short system prompt + think-tag stripping | `966349e` |
| 5 | Garbage output | Repeated template artifacts from LLM | Regex-based garbage detection → retry prompt | `966349e` |
| 6 | Screen flicker | Rapid UI updates during streaming | Debounced `notifyDataSetChanged()` | `966349e` |
| 7 | Download infinite loop | Model picker called `btnDownload.performClick()` → `showModelPicker()` loop | Changed picker to call `downloadModel(model)` directly | `3a77757` |
| 8 | Download blocked | `downloadModel()` returned early if any model loaded | Removed `hasLoadedModel()` guard | `3a77757` |
| 9 | VLM template echo | Text-only VLM queries returned system prompt | Redirect text-only to LLM + guidance message | `3a77757` |
| 10 | Llama download "Failed to fetch file list" | `extractRepoNameFromS3Url()` misparses HF URLs — version dots cause filename detection | Added `extractHfRepoId()` for HF URL auto-detection | `8d461bd` |

---

## What Makes This Different

| Traditional Health App | Health Passport |
|---|---|
| Cloud API calls | 100% on-device |
| One model, one task | 6 models, auto-routed |
| Manual data entry | OCR scan → auto-save → queryable |
| Text only | Text + Photo + Voice |
| Needs internet | Works offline, forever |
| HIPAA compliance = expensive | No data leaves device = no breach |
| Generic answers | RAG from YOUR medical records |

---

## Performance Benchmarks (Snapdragon 8 Elite)

| Metric | Value |
|--------|-------|
| Time to First Token (TTFT) | **446 ms** |
| Prefill Throughput | **1,049 tok/s** |
| Decode Throughput | **19 tok/s** |
| Tokens Generated (avg response) | **~220 tokens** |
| OCR Scan Time | **< 1 second** |
| APK Size | **299 MB** |
| Models on NPU simultaneously | **Up to 6** |

---

*Built in 48 hours. Runs entirely on your phone. Your health data stays yours.*
