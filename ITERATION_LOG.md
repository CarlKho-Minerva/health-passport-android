# Health Passport — Iteration Log & Demo Plan

> Tracking doc for all user feedback, technical issues, and implementation changes.
> Device: Snapdragon 8 Elite QRD8750 (Qualcomm Device Cloud)

---

## 1. User Insights & Requests (Collected)

| # | Insight / Request | Category | Status |
|---|---|---|---|
| 1 | Model downloaded but "Load" crashes with NPE — `modelFile()` returns null for `baseUrl` NPU models | Bug | ✅ Fixed (commit `012bd30`) |
| 2 | Incomplete download (phone slept) falsely shows "already downloaded" — no integrity check | Bug | ✅ Fixed (`.complete` marker, commit `d0e7ea5`) |
| 3 | GPU 999 layers crashes native SDK | Bug | ✅ Fixed (clamped 1-99) |
| 4 | "Model Already Downloaded" on fresh install | Bug | ✅ Fixed |
| 5 | Download button requires "Advanced Mode" — unintuitive | UX | ✅ Fixed (Models picker) |
| 6 | Need a **model sidebar/slide-out** for model management instead of hidden Advanced Mode | UX | ✅ Fixed (bottom sheet picker) |
| 7 | VLM only outputs `<think><think></think>` — no actual response | Bug | ✅ Fixed (think stripping) |
| 8 | thumbnail.jpg pushed to `/sdcard/Download/` but not accessible in app's photo picker | UX | 🔴 Fix |
| 9 | No guided **welcome flow** — user doesn't know what to do on first launch | UX | ✅ Done (intro modal) |
| 10 | Core function not built: **Photo → Extract → Categorize → View** pipeline | Feature | 🟡 Partial |
| 11 | Dialog buttons said "sure" instead of "Load" — not styled green | UX | ✅ Fixed |
| 12 | No NPU option in load dialog on Snapdragon | Bug | ✅ Fixed (auto-NPU) |
| 13 | Phone sleep interrupts download mid-way | Bug | ✅ Fixed (WakeLock) |
| 14 | Should install **all needed models** on first start for demo readiness | UX | 🟡 Planned |
| 15 | Need to think ahead for demo — clear user flow for judges | Strategy | 🟡 This doc |
| 16 | VLM outputs garbage: "Reactive Reactive Reactive..." gibberish on NPU | Bug | ✅ Fixed (garbage detection + auto-stop) |
| 17 | Screen flickers violently during model streaming response | Bug | ✅ Fixed (throttled UI ~12fps) |
| 18 | VLM unreliable → fall back to LLM (Qwen3) + PaddleOCR as primary | Architecture | ✅ Done |
| 19 | Image sent to text-only LLM → no guidance message | UX | ✅ Fixed (fallback guidance) |

---

## 2. Root Cause: VLM Only Outputs `<think>` Tags

**Diagnosis:**
- OmniNeural-4B loads on NPU successfully (TTFT: 845ms, 28.48 tok/s decoding — excellent!)
- But output is only `<think><think></think>` (9 tokens)
- `enableThinking` is `false` by default (line 201), but the NPU VLM config at line 1495 passes `enable_thinking = enableThinking`
- Line 2382 has `if (isNpu || true) inputString else result.formattedText` — the `|| true` means it ALWAYS sends raw user text instead of the formatted chat template
- For NPU models, the SDK handles templating internally — but the raw text "Hey you guys" with no image context produces meaningless think tokens
- **The model works but has nothing meaningful to process** — it needs an image + a specific extraction prompt

**Fix Plan:**
1. Disable `enable_thinking` for NPU VLM (it's a vision model, not a reasoning model)
2. Keep the `|| true` for NPU since SDK handles templating
3. Ensure images are properly injected into the VLM pipeline
4. Add a default "Describe this image" prompt when user sends image without text

---

## 3. Available Models & Their Roles

| Model | Type | Size | Role in Health Passport | Priority |
|---|---|---|---|---|
| **OmniNeural-4B** | VLM 👁️ | ~4GB | Scan documents, extract text from photos | ⚠️ Experimental (garbage output on NPU) |
| **Qwen3-4B-Instruct** | LLM 💬 | ~4GB | Reasoning, categorization, Q&A about records | ⭐ Primary |
| **PaddleOCR** | OCR 📄 | ~100MB | Fast text extraction from clean documents | ⭐ Primary (document scanning) |
| **Llama-3.2-3B Turbo** | LLM 💬 | ~3GB | Lighter alternative for chat | Optional |
| **Parakeet ASR** | Speech 🎙️ | ~600MB | Voice notes about health records | Nice-to-have |
| **EmbedGemma** | Embedder 🔗 | ~300MB | RAG search over health vault | Future |
| **Llama-3.2-1B GGUF** | LLM (CPU) | ~1GB | Fallback if NPU fails | Backup |
| **Qwen3-4B GGUF** | LLM (CPU) | ~4GB | CPU fallback for reasoning | Backup |

**For Demo: Download Qwen3-4B-Instruct (LLM) + PaddleOCR**
- LLM: health Q&A with RAG from vault, categorize data
- OCR: extract text from document photos (reliable, fast)
- VLM (OmniNeural): experimental — may produce garbage on NPU, use with caution

---

## 4. Demo Flow (Target UX)

```
┌─────────────────────────────────────────┐
│          HEALTH PASSPORT                │
│     "Your AI Medical Vault"             │
├─────────────────────────────────────────│
│                                         │
│  ┌─────────┐  ┌─────────┐              │
│  │ 📸 Scan │  │ 💬 Chat │              │
│  │Document │  │ Records │              │
│  └────┬────┘  └────┬────┘              │
│       │             │                   │
│       ▼             ▼                   │
│  [Camera/Photo] [Ask about your health] │
│       │             │                   │
│       ▼             │                   │
│  VLM extracts       │                   │
│  structured data    │                   │
│       │             │                   │
│       ▼             │                   │
│  Auto-categorize    │                   │
│  (Labs/Rx/Notes)    │                   │
│       │             │                   │
│       ▼             ▼                   │
│  ┌──────────────────────────┐           │
│  │    HEALTH VAULT          │           │
│  │  📋 Lab Results          │           │
│  │  💊 Medications          │           │
│  │  📝 Doctor Notes         │           │
│  │  🏥 Visit Summaries      │           │
│  └──────────────────────────┘           │
└─────────────────────────────────────────┘
```

### Demo Script (2 min):
1. **Open app** → Qwen3-4B auto-selected as default
2. **Ask vault question** → "What are my current medications?" → LLM answers from vault (RAG)
3. **Tap "Scan Document"** → Camera opens → Take photo of medical document
4. **Switch to PaddleOCR** → Tap "Models" → Load PaddleOCR → Send photo → Text extracted
5. **Switch to LLM** → Tap "Models" → Load Qwen3-4B → Ask "Summarize the scan" → LLM reasons about extracted text
6. **Show vault** → Tap "Health Vault" → Browse organized medical records
7. **Show privacy** → "All on-device, zero cloud, Qualcomm NPU, HIPAA compliant"

---

## 5. Implementation Changes Log

| # | What | Why | How | Rationale |
|---|---|---|---|---|
| 1 | `safeModelPath` fallback in `loadModel()` | `modelFile()` returns null for NPU `baseUrl` models → NPE crash | Added `val safeModelPath = modelFile()?.absolutePath ?: modelDir().absolutePath` and replaced all 6 crash sites | NPU models use directory-based loading, not single-file. `modelDir()` always valid. |
| 2 | `.complete` marker file | Phone sleep = partial download = false "already downloaded" | Write `.complete` to model dir after all files finish; require it in `getNonExistModelFile()` | Atomic completion check instead of "any files exist" heuristic |
| 3 | `PARTIAL_WAKE_LOCK` during download | Device sleep interrupts multi-GB download | Acquire 30min WakeLock on download start, release on all exit paths | Keeps CPU alive; PARTIAL = screen can still turn off but download continues |
| 4 | `WAKE_LOCK` permission | Android requires manifest permission for WakeLock | Added `<uses-permission android:name="android.permission.WAKE_LOCK" />` | Required by Android framework |
| 5 | Disable `enable_thinking` for NPU VLM | OmniNeural-4B outputs only `<think>` tags — thinking mode produces empty reasoning loops | Set `enable_thinking = false` in VLM NPU config | NPU VLM is for vision extraction, not chain-of-thought reasoning |
| 6 | Auto-prompt for image-only sends | User uploads photo but types nothing — VLM gets empty string | If user sends image without text, default to "Extract all text and medical data from this image" | VLM needs a task-specific prompt to produce useful output |
| 7 | Remove hidden model spinner | Download required "Advanced Mode" to access model spinner | Move model selector to always-visible sidebar or top section | Users shouldn't need to discover hidden UI to use core functionality |
| 8 | Fix screen flickering during streaming | `notifyDataSetChanged()` on every token → full RecyclerView redraw | Throttle UI to ~12fps (80ms interval) + use `notifyItemChanged(pos)` instead | Eliminates visual jitter during generation |
| 9 | Garbage output detection & auto-stop | VLM outputs "Reactive Reactive..." infinite loops | Count consecutive identical tokens; stop stream after 8 repeats; show friendly error | Prevents scary gibberish filling the screen |
| 10 | Reorder models: LLM + OCR first | VLM unreliable on NPU → shouldn't be default | Moved Qwen3-4B to #1, PaddleOCR to #2, OmniNeural to #4 with ⚠️ label | LLM + OCR = reliable pipeline for demo |
| 11 | Image-to-text-model fallback | Sending image to LLM (text-only) shows nothing | Show guidance: "Current model is text-only. Use PaddleOCR or VLM for images." | Clear user guidance instead of silent failure |
| 12 | Model picker bottom sheet | Need easy model switching without hidden spinner | "Models" button → bottom sheet showing all models with download/load status | One-tap model management |
| 13 | Better system prompt | `sysPrompt2` was too short/generic | Use the full detailed `sysPrompt` with structured output format | LLM needs clear instructions for medical extraction |
| 14 | RAG reads actual vault files | `handlePreloadedQuery` used hardcoded responses | Now reads real vault `.md` files and returns actual content | Responses are always current with vault state |
| 15 | Think-tag stripping in stream | VLM/LLM may emit `<think>...</think>` blocks | Regex-strip complete and partial think tags from both live stream and final output | Clean output for user |
| 16 | Better OCR output formatting | Raw `[confidence] text` output was hard to read | Format as markdown code block with line count | Professional-looking extraction results |

---

## 6. Git History

| Commit | Description |
|---|---|
| `6713e00` | feat: auto-NPU, GPU clamp, download overlay, button styling |
| `012bd30` | fix: resolve NPE on model Load for NPU directory models |
| `d0e7ea5` | fix: WakeLock during download + .complete marker for integrity |
| `c5e2471` | fix: VLM think-only output + auto-prompt + visible controls |
| (next) | fix: garbage detection, screen flicker, LLM+OCR primary pipeline |

---

## 7. How to Interact with Models (Quick Reference)

### OmniNeural-4B (VLM) — For Document Scanning
1. Load model (tap Load after download)
2. Tap 📷 camera icon → take photo of document
3. Type: "Extract all medical data from this image" → Send
4. VLM returns structured markdown

### Qwen3-4B (LLM) — For Chat & Reasoning
1. Load model
2. Type questions about health: "Summarize my medications" → Send
3. LLM answers using RAG from health vault

### PaddleOCR — For Fast Text Extraction
1. Load model
2. Tap 📷 → take photo → Send
3. Returns bounding-box text (faster but less structured than VLM)
