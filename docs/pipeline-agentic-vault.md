# Agentic Vault Pipeline — Health Passport

**Committed:** `262a178` → new multistage pipeline (committed below as next)
**Date designed:** March 9, 2026
**Inspired by:** `example-plan-nexareference.md` — the IDE-driven CRUD plan for pharmacogenomics data

---

## The Problem with the Old Approach

The old `sendWithHkPrompt()` asked Qwen3-4B to produce structured `---WRITE_TO: path---` blocks in a single shot. A 4B model frequently:
- Missed the format delimiters
- Hallucinated file paths that don't exist
- Wrote a wall of prose instead of structured output
- Saved everything to `00_Inbox/` instead of routing it correctly

The root issue: **one prompt can't do classification, routing, reading, formatting, and writing all at once.**

---

## The Multi-Stage Pipeline

Inspired by how an IDE agent like Cursor/Claude:
1. First *reads* the existing file structure
2. *Classifies* what kind of data it received
3. *Decides* which file to write to
4. *Formats* the entry to match existing style
5. *Applies* the diff

We split this into **stages** — each doing one job, with the **app doing the hard routing logic** and the **LLM doing extraction and formatting**.

---

## Stage Breakdown

### Stage 1 — Classify + Extract (LLM, silent)

**Prompt type:** Structured output extraction
**System prompt asks for:**
```
CATEGORY: [medication | lab | eye | cardio | gi | ortho | skin | immune | neuro | genetics | diet | exercise | inventory | visit | therapy | insurance | not_medical]
SUMMARY: [one-line description]
ENTITIES: [drug names, dosages, diagnoses, test values, etc.]
```

**Why LLM:** Natural language → structured categories. The model is good at reading medical freetext and tagging it.
**Why silent (not streamed to UI):** This is a pipeline step, not a response to the user.
**Fallback:** If LLM fails or returns blank → skip to inbox save.

---

### Stage 2 — App-side Routing (Kotlin, no LLM)

**Logic:** CATEGORY strings map to vault file paths via a static dictionary:

```kotlin
private val VAULT_ROUTES = mapOf(
    "medication"  to listOf("03_Protocols/Active_Medications.md"),
    "lab"         to listOf("01_Body_Systems/00_Lab_Baselines.md"),
    "eye"         to listOf("01_Body_Systems/01_Head_Eyes_ENT.md"),
    "cardio"      to listOf("01_Body_Systems/02_Cardiovascular_Heart.md"),
    "gi"          to listOf("01_Body_Systems/02_Metabolic_GI.md"),
    "ortho"       to listOf("01_Body_Systems/03_Limbs_Ortho.md"),
    "skin"        to listOf("01_Body_Systems/04_Skin_Derma.md"),
    "immune"      to listOf("01_Body_Systems/05_Systemic_Immune.md"),
    "neuro"       to listOf("01_Body_Systems/06_Neuro_Psych.md"),
    "genetics"    to listOf("01_Body_Systems/07_Genetics_PGx.md"),
    "diet"        to listOf("03_Protocols/Diet_Plan.md"),
    "exercise"    to listOf("03_Protocols/Physio_Routine.md"),
    "inventory"   to listOf("03_Protocols/Medicine_Inventory.md"),
    "visit"       to listOf("02_Timeline/Medical_Timeline.md"),
    "therapy"     to listOf("05_Therapy/Therapy_Log.md"),
    "insurance"   to listOf("03_Protocols/Insurance_Reimbursement.md")
)
```

**Key decisions made app-side (not by LLM):**
- Multi-category: e.g. `medication,visit` → routes to BOTH `Active_Medications.md` AND timeline
- Timeline is **always** appended for every save — mandatory audit log
- `not_medical` → bail out, show toast, do NOT save
- Unknown category → fallback to `00_Inbox/`
- Creates parent directories if they don't exist
- Uses `findOrCreateTimelineFile(today)` to find the current year's log or create one

**Why app-side:** Routing is deterministic. No reason to burn LLM tokens on a lookup table.

---

### Stage 3 — Format Entry (LLM, silent, per file)

**One LLM call per target file.** Reads the **last 2000 chars** of the target file so the model can match its formatting style.

**Timeline prompt:**
```
Output ONLY the new bullet point to append.
Format: - **YYYY-MM-DD**: [brief outcome]
```

**Body system / protocol prompt:**
```
EXISTING FILE CONTENT (tail): [last 2000 chars of file]
RULES:
- Output ONLY the new content to APPEND (not the whole file)
- Match the formatting style of existing content
- If updating a value that already exists, note: "(Previous: [old_value] on [date])"
- Include today's date
- Be concise, structured, use markdown
```

**Why this works:** The model sees the actual file style and mirrors it. If `Active_Medications.md` uses an `| Drug | Dose | Freq | Purpose |` table, the model will produce a table row.

**Preservation rule:** Explicitly instructed to note previous values, not overwrite — matching the `hk_system_prompt.md` directive 2.

---

### Stage 4 — Write + UI Update (Kotlin)

- `appendText("\n\n$formatted")` to each target file
- Creates file with header if it doesn't exist yet
- Counts successfully written files
- Fallback: if ALL writes fail → saves raw content to `00_Inbox/`
- UI: updates the last assistant message in-place (no new bubble — same "Filing..." turns into "Filed to 3 vault files: Active_Medications, 2026_Health_Timeline, 06_Neuro_Psych")

---

## Full Flow Diagram

```
User taps "Save to Vault"
        │
        ▼
[Get contextToSave]
  → typed input, OR last assistant message
        │
        ▼
[No LLM loaded?] ──yes──▶ raw save to 00_Inbox/
        │ no
        ▼
┌─────────────────────────────────┐
│  STAGE 1: LLM Classify (silent) │
│  Output: CATEGORY, SUMMARY,     │
│          ENTITIES               │
└─────────────────────────────────┘
        │
        ▼
[category == not_medical?] ──yes──▶ toast "No medical data" + return
        │ no
        ▼
┌─────────────────────────────────┐
│  STAGE 2: App Routes Categories │
│  VAULT_ROUTES lookup             │
│  + always add timeline          │
└─────────────────────────────────┘
        │
        ▼
[no recognized routes?] ──yes──▶ fallback to 00_Inbox/
        │ no
        ▼
┌──────────────────────────────────────────┐
│  STAGE 3: For each target file:          │
│    LLM Format (silent)                   │
│    Input: summary + entities + raw data  │
│           + existing file tail (2000ch)  │
│    Output: only the new append content   │
└──────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────┐
│  STAGE 4: App Writes            │
│  appendText() to each file      │
│  create file if missing         │
│  fallback if all fail           │
└─────────────────────────────────┘
        │
        ▼
UI: "Filed to N vault files: [names]"
```

---

## What This Maps to in the Example Plan

The `example-plan-nexareference.md` shows how an IDE agent handled pharmacogenomics data. Mapping to our stages:

| IDE Agent Step | Our Pipeline Stage |
|---|---|
| Reads existing 06_Neuro_Psych.md, sees Atomoxetine entry | Stage 3: reads last 2000 chars of target file |
| Classifies input as genetics + neuro | Stage 1: CATEGORY: genetics,neuro |
| Routes to 07_Genetics_PGx.md AND 06_Neuro_Psych.md | Stage 2: VAULT_ROUTES["genetics"] + VAULT_ROUTES["neuro"] |
| Formats "Previous: Suspected → Note: WES report shows Normal Metabolizer" | Stage 3: prompted to note previous values |
| Logs to 2026_India_Logs.md timeline | Stage 4: always writes to timeline |
| Archives raw files to 99_Archives/ | NOT yet implemented (see Future Roadmap) |

---

## Limitations vs. the IDE Agent

The IDE agent (Claude/GPT-4) can:
- Read arbitrary file trees dynamically
- Diff-preview changes before applying
- Create new files based on reasoning
- Handle binary files (BAM, FASTQ, VCF)

Our 4B on-device model tradeoffs:
- No file-tree reasoning → we do routing in Kotlin
- No diff preview → appending is safer than rewriting
- Can't handle binary/genomics formats → out of scope for v1
- 2 LLM calls per save adds ~5-10s latency on NPU

---

## Future Roadmap

### Near-term (v1.1)
- **Stage 1.5 — Deduplication check**: Before Stage 3, grep target file for the ENTITIES to detect if this data was already saved. If duplicate, skip.
- **Stage 3 enhancement**: Include more of the existing file (not just 2000 chars tail) using the full vault ingest from `loadFullVaultContext()`.
- **`99_Archives/` routing**: If input contains an image or audio reference, route to `99_Archives/` with timestamped filename.

### Medium-term (v1.2)
- **VLM upgrade**: Current OCR is PaddleOCR (CV model, text extraction only). When Snapdragon 8 Elite device is available, swap in a proper Vision Language Model (e.g. Qwen2-VL-2B-NPU or OmniNeural) that can visually analyze handwriting, charts, and non-text elements. The pipeline stages are the same — only Stage 1 input changes.
- **Diff preview**: Show a "these changes will be made" card before the user confirms Stage 4 write.

### Long-term (v2.0)
- **Multi-file reads in Stage 2**: For `medication` category, also read `Paused_Medications.md` to detect resumed/conflicting drugs.
- **Cross-system linking**: When routing to `06_Neuro_Psych.md`, also check `03_Protocols/Active_Medications.md` for interaction conflicts.
- **Timeline intelligence**: Detect if a similar event was logged < 7 days ago and ask "update existing entry instead?"

---

## OCR Stack Note

Current: **PaddleOCR-NPU** — text extraction from images, no visual understanding.

It's the right choice now because:
1. It runs on the Snapdragon NPU (fast)
2. It's what Nexa provides and tested on QDC hardware
3. For the bounty use-case (printed documents, prescriptions, lab reports) printed text extraction is sufficient

Future: **Qwen2-VL or OmniNeural** when you have the Snapdragon 8 Elite device. These can read handwriting, understand charts, and capture non-text context. The pipeline design already accommodates this — OCR output goes through the same Stage 1 classify call.
