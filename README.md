# Health Passport

> **Grand Champion — Qualcomm × Nexa AI On-Device Bounty ($6,500)** · Apr 2026  
> **Gemma 4 Good Hackathon (Google DeepMind)** · May 2026

Privacy-first medical records on your phone. Talk, scan, or type — AI models run entirely on-device. No internet. No cloud. No HIPAA liability.

**v1.4 — Gemma 4 E2B (Google AI Edge LiteRT-LM)** is now the primary LLM, replacing Nexa SDK's Qwen3-4B for health Q&A and reasoning.

[![Health Passport — GitHub Pages](docs/preview.png)](https://carlkho-minerva.github.io/health-passport-android/)

---

## The Problem

I rotate across seven global cities every semester as a Minerva student. Manila, Mumbai, Taipei, San Francisco. Each new country means re-explaining my complete medical history — prescriptions buried in camera roll photos, lab reports in three languages, no structured record I can hand a doctor.

This affects 50M+ international travelers. Medical continuity breaks at every border. Existing health apps require cloud upload, creating compliance nightmares across jurisdictions.

## The Solution

On-device AI pipeline, auto-downloaded on first launch:

| Model | Role | SDK | Size |
|-------|------|-----|------|
| **Gemma 4 E2B** | Primary LLM — health Q&A + grounded reasoning | Google AI Edge LiteRT-LM | ~2.6 GB |
| **PaddleOCR** | Document scanner | Nexa SDK | ~100 MB |
| **Parakeet ASR** | Speech-to-text | Nexa SDK | ~300 MB |
| **EmbedGemma** | Memory / embeddings | Nexa SDK | ~300 MB |
| **OmniNeural-4B** | Vision analysis (optional) | Nexa SDK | ~4 GB |
| **Qwen3-4B NPU** | LLM fallback (NPU devices) | Nexa SDK | ~4 GB |

First launch auto-downloads Gemma 4 E2B + PaddleOCR + Parakeet ASR. The smart router picks the right model automatically.

---

## Architecture

```mermaid
graph TB
    subgraph UI["User Interface"]
        TEXT[Text Box]
        CAM[Camera]
        MIC[Audio]
        VAULT[Vault Browser]
    end

    subgraph ROUTER["Smart Pipeline Router"]
        R1["text → RAG Lookup → Gemma 4"]
        R2["image → OCR → Save → Gemma 4"]
        R3["audio → ASR → RAG → Gemma 4"]
    end

    subgraph INFERENCE["On-Device Inference"]
        G4["Gemma 4 E2B\nLiteRT-LM · GPU/CPU"]
        OCR["PaddleOCR\nNexa SDK · NPU"]
        ASR["Parakeet ASR\nNexa SDK · NPU"]
        EMB["EmbedGemma\nNexa SDK · NPU"]
    end

    subgraph STORAGE["Health Vault · On-Device"]
        HV["health_vault/\neyes.md, meds.md, ..."]
        HR["health_records/\nscan_2026-04-07.md, ..."]
    end

    TEXT --> R1
    CAM --> R2
    MIC --> R3
    R1 --> G4
    R2 --> OCR
    R2 --> G4
    R3 --> ASR
    R3 --> G4
    G4 --> STORAGE
    OCR --> STORAGE
    STORAGE --> G4
    EMB --> STORAGE
```

---

## What You Can Do

**Conversational queries** — "What's my current eye prescription?", "List my active medications", "Show my February health timeline." Gemma 4 E2B answers from your local Health Vault using RAG.

**Document scanning** — Point the camera at a prescription or lab report. PaddleOCR extracts the text, saves it to the vault, then Gemma 4 analyzes it. One tap.

**Voice input** — Speak naturally. Parakeet ASR transcribes, the app searches your vault, and Gemma 4 answers. Same chat interface as text.

**Vault browsing** — Browse by body system, timeline, or protocol. Every file is markdown. Tap to view, edit, save.

**Grounded answers** — Responses are built from your actual health records. Gemma 4 does not guess at medical facts outside your vault.

---

## Technical Stack

| Component | Details |
|-----------|---------|
| Primary LLM | Gemma 4 E2B via `com.google.ai.edge.litertlm:litertlm-android:latest.release` |
| LLM Fallback | Nexa SDK `ai.nexa:core:0.0.24` (Qwen3-4B NPU, Llama-3.2-3B) |
| Language | Kotlin 1.9.22 |
| Target | API 34 (min API 27) |
| UI | Material Design 3, custom dark theme |
| Markdown | Markwon v4.6.2 (tables, strikethrough, linkify) |
| Architecture | Single-activity, coroutine-based model management |

### Color Palette

```
#070707  background     #0D0D0D  surface
#111111  card           #F2F2F2  text
#808080  secondary      #4D4D4D  tertiary
#10B981  accent green   #1A1A1A  border
```

### Health Vault Structure

```
assets/health_vault/
  01_Body_Systems/   — eyes, cardiovascular, allergies
  02_Timeline/       — chronological medical events
  03_Protocols/      — active medications, routines
  04_System_Prompt/  — RAG system prompt
```

---

## Building

```bash
# Prerequisites: OpenJDK 17+, Android SDK API 27-34, ARM64 device

./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Demo

[carlkho-minerva.github.io/health-passport-android](https://carlkho-minerva.github.io/health-passport-android/)

---

## Project Structure

```
app/src/main/
  java/com/nexa/demo/
    MainActivity.kt (~3850 lines)
      checkFirstLaunch()      — Auto-download setup modal
      showSetupModal()        — Welcome + sequential model install
      downloadModelSuspend()  — Coroutine-bridged download
      loadModel()             — Type-specific model loading
      streamResponseToChat()  — Real-time token streaming
      handlePreloadedQuery()  — RAG response handlers
      browseVaultFolder()     — Recursive file browser
      showFileContent()       — Markdown viewer + editor
    ChatAdapter.kt
  res/
    layout/activity_main.xml
    values/colors.xml, themes.xml
    drawable/btn_*.xml
  assets/health_vault/
```

---

## Performance (Snapdragon 8 Elite)

| Metric | Value |
|--------|-------|
| Time to First Token | 446 ms |
| Prefill Throughput | 1,049 tok/s |
| Decode Throughput | 19 tok/s |
| Average Response | ~220 tokens |
| OCR Scan Time | < 1 second |
| APK Size | 299 MB |
| Concurrent NPU Models | Up to 6 |

---

## Development Context

**Carl Vincent Kho** · Minerva University '26 · kho@uni.minerva.edu

The vault structure comes from my [md-health](../../../md-health/) system — a markdown-based health knowledge base I've maintained across seven countries. This app makes that system conversationally queryable, entirely on-device.

---

## License

Apache 2.0

## Links

- [NexaSDK](https://nexa.ai)
- [Qualcomm AI](https://www.qualcomm.com/developer)
- [Architecture & Plan](docs/PLAN.md)

---

NexaSDK v0.0.24 · Kotlin 1.9.22 · Snapdragon 8 Elite · Material Design 3 · Markwon 4.6.2
