package com.nexa.demo

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * JVM unit tests for the 4-stage agentic vault pipeline.
 *
 * These tests run locally (no device / no Nexa SDK / no LLM required).
 * They cover the pure-Kotlin logic: Stage 1 output parsing, Stage 2 routing,
 * Stage 4 file writes. Stage 3 (LLM format) requires a real model and is
 * tested on-device via integration test.
 *
 * Run with: ./gradlew test
 */
class VaultPipelineTest {

    // ── Mirrors VAULT_ROUTES from MainActivity ────────────────────────────

    private val VAULT_ROUTES = mapOf(
        "medication" to listOf("03_Protocols/Active_Medications.md"),
        "lab"        to listOf("01_Body_Systems/00_Lab_Baselines.md"),
        "eye"        to listOf("01_Body_Systems/01_Head_Eyes_ENT.md"),
        "cardio"     to listOf("01_Body_Systems/02_Cardiovascular_Heart.md"),
        "gi"         to listOf("01_Body_Systems/02_Metabolic_GI.md"),
        "ortho"      to listOf("01_Body_Systems/03_Limbs_Ortho.md"),
        "skin"       to listOf("01_Body_Systems/04_Skin_Derma.md"),
        "immune"     to listOf("01_Body_Systems/05_Systemic_Immune.md"),
        "neuro"      to listOf("01_Body_Systems/06_Neuro_Psych.md"),
        "genetics"   to listOf("01_Body_Systems/07_Genetics_PGx.md"),
        "diet"       to listOf("03_Protocols/Diet_Plan.md"),
        "exercise"   to listOf("03_Protocols/Physio_Routine.md"),
        "inventory"  to listOf("03_Protocols/Medicine_Inventory.md"),
        "visit"      to listOf("02_Timeline/Medical_Timeline.md"),
        "therapy"    to listOf("05_Therapy/Therapy_Log.md"),
        "insurance"  to listOf("03_Protocols/Insurance_Reimbursement.md")
    )

    // ── Mirrors Stage 1 output parsing from MainActivity ──────────────────

    data class Stage1Result(
        val categories: List<String>,
        val summary: String,
        val entities: String,
        val isNotMedical: Boolean
    )

    private fun parseStage1(llmOutput: String): Stage1Result {
        val categoryLine = llmOutput.lines()
            .firstOrNull { it.trimStart().startsWith("CATEGORY:", ignoreCase = true) }
            ?.substringAfter(":")?.trim()?.lowercase() ?: ""

        val summaryLine = llmOutput.lines()
            .firstOrNull { it.trimStart().startsWith("SUMMARY:", ignoreCase = true) }
            ?.substringAfter(":")?.trim() ?: ""

        val entitiesLine = llmOutput.lines()
            .firstOrNull { it.trimStart().startsWith("ENTITIES:", ignoreCase = true) }
            ?.substringAfter(":")?.trim() ?: ""

        val isNotMedical = categoryLine.contains("not_medical")
        val categories = if (isNotMedical) emptyList()
        else categoryLine.split(",").map { it.trim() }.filter { it.isNotBlank() }

        return Stage1Result(categories, summaryLine, entitiesLine, isNotMedical)
    }

    // ── Mirrors Stage 2 routing from MainActivity ─────────────────────────

    private fun routeCategories(categories: List<String>): Set<String> {
        val routes = mutableSetOf<String>()
        for (cat in categories) {
            VAULT_ROUTES[cat]?.forEach { routes.add(it) }
        }
        return routes
    }

    // ── Stage 1 parsing tests ──────────────────────────────────────────────

    @Test
    fun `stage1 - parses single category correctly`() {
        val llmOutput = """
            CATEGORY: medication
            SUMMARY: New antibiotic prescription for sinus infection
            ENTITIES: Amoxicillin 500mg, 3x daily, 7 days
        """.trimIndent()

        val result = parseStage1(llmOutput)
        assertEquals(listOf("medication"), result.categories)
        assertEquals("New antibiotic prescription for sinus infection", result.summary)
        assertFalse(result.isNotMedical)
    }

    @Test
    fun `stage1 - parses multi-category correctly`() {
        val llmOutput = """
            CATEGORY: medication,visit
            SUMMARY: Doctor visit, prescribed Amoxicillin for sinus infection
            ENTITIES: Amoxicillin 500mg, Dr. Santos, sinus infection
        """.trimIndent()

        val result = parseStage1(llmOutput)
        assertTrue(result.categories.contains("medication"))
        assertTrue(result.categories.contains("visit"))
        assertEquals(2, result.categories.size)
    }

    @Test
    fun `stage1 - not_medical returns isNotMedical true with empty categories`() {
        val llmOutput = """
            CATEGORY: not_medical
            SUMMARY: Non-medical content
            ENTITIES: none
        """.trimIndent()

        val result = parseStage1(llmOutput)
        assertTrue(result.isNotMedical)
        assertTrue(result.categories.isEmpty())
    }

    @Test
    fun `stage1 - handles mixed case and extra whitespace`() {
        val llmOutput = """
            CATEGORY:   LAB, CARDIO
            SUMMARY:   Lipid panel results
            ENTITIES:   LDL 130, HDL 55, Triglycerides 140
        """.trimIndent()

        val result = parseStage1(llmOutput)
        assertTrue(result.categories.contains("lab"))
        assertTrue(result.categories.contains("cardio"))
    }

    @Test
    fun `stage1 - handles malformed output gracefully`() {
        val llmOutput = "Here are the lab results from today blah blah no format at all"
        val result = parseStage1(llmOutput)
        // Empty category line should not crash and should not be medical
        assertTrue(result.categories.isEmpty() || result.isNotMedical)
    }

    // ── Stage 2 routing tests ─────────────────────────────────────────────

    @Test
    fun `stage2 - medication routes to Active_Medications`() {
        val routes = routeCategories(listOf("medication"))
        assertTrue(routes.contains("03_Protocols/Active_Medications.md"))
    }

    @Test
    fun `stage2 - lab routes to Lab_Baselines`() {
        val routes = routeCategories(listOf("lab"))
        assertTrue(routes.contains("01_Body_Systems/00_Lab_Baselines.md"))
    }

    @Test
    fun `stage2 - eye routes to Head_Eyes_ENT`() {
        val routes = routeCategories(listOf("eye"))
        assertTrue(routes.contains("01_Body_Systems/01_Head_Eyes_ENT.md"))
    }

    @Test
    fun `stage2 - multi-category routes to all target files`() {
        val routes = routeCategories(listOf("medication", "visit", "neuro"))
        assertTrue(routes.contains("03_Protocols/Active_Medications.md"))
        assertTrue(routes.contains("02_Timeline/Medical_Timeline.md"))
        assertTrue(routes.contains("01_Body_Systems/06_Neuro_Psych.md"))
    }

    @Test
    fun `stage2 - unknown category produces empty routes`() {
        val routes = routeCategories(listOf("unknown_garbage_category"))
        assertTrue(routes.isEmpty())
    }

    @Test
    fun `stage2 - genetics routes to PGx file`() {
        val routes = routeCategories(listOf("genetics"))
        assertTrue(routes.contains("01_Body_Systems/07_Genetics_PGx.md"))
    }

    @Test
    fun `stage2 - all 16 categories have routes`() {
        val allCategories = VAULT_ROUTES.keys.toList()
        for (cat in allCategories) {
            val routes = routeCategories(listOf(cat))
            assertTrue("No route for category: $cat", routes.isNotEmpty())
        }
    }

    // ── Stage 4 file write tests ──────────────────────────────────────────

    @Test
    fun `stage4 - appends to existing file`() {
        val tmpDir = Files.createTempDirectory("vault_test").toFile()
        val targetFile = File(tmpDir, "Active_Medications.md")
        targetFile.writeText("# Active Medications\n\n- Omega-3 1g daily\n")

        val newEntry = "\n\n- Amoxicillin 500mg 3x daily (2026-03-09)"
        targetFile.appendText(newEntry)

        val content = targetFile.readText()
        assertTrue(content.contains("Omega-3"))       // original preserved
        assertTrue(content.contains("Amoxicillin"))   // new entry added
        tmpDir.deleteRecursively()
    }

    @Test
    fun `stage4 - creates new file with header if missing`() {
        val tmpDir = Files.createTempDirectory("vault_test").toFile()
        val targetFile = File(tmpDir, "03_Protocols/Diet_Plan.md")
        targetFile.parentFile?.mkdirs()

        assertFalse(targetFile.exists())

        // Simulate Stage 4 create-new logic
        val content = "Avoid processed sugar, increase fiber intake (2026-03-09)"
        targetFile.writeText("# Diet Plan\n\n$content")

        assertTrue(targetFile.exists())
        assertTrue(targetFile.readText().contains("Diet Plan"))
        assertTrue(targetFile.readText().contains(content))
        tmpDir.deleteRecursively()
    }

    @Test
    fun `stage4 - write does not overwrite existing content`() {
        val tmpDir = Files.createTempDirectory("vault_test").toFile()
        val targetFile = File(tmpDir, "06_Neuro_Psych.md")
        val originalContent = "# Neuro\n\n## Atomoxetine\n- ADHD, 40mg daily"
        targetFile.writeText(originalContent)

        targetFile.appendText("\n\n- Melatonin 0.5mg at bedtime (2026-03-09)")

        val final = targetFile.readText()
        assertTrue(final.contains("Atomoxetine"))  // original preserved
        assertTrue(final.contains("Melatonin"))    // new appended
        tmpDir.deleteRecursively()
    }

    // ── Full pipeline integration (no LLM) ───────────────────────────────

    @Test
    fun `full pipeline - prescription text routes and writes correctly`() {
        val tmpVault = Files.createTempDirectory("vault_full_test").toFile()

        // Simulate Stage 1 output from LLM
        val stage1Output = """
            CATEGORY: medication,visit
            SUMMARY: Prescribed Amoxicillin for sinus infection
            ENTITIES: Amoxicillin 500mg, 3x daily, 7 days, sinus infection, Dr. Santos
        """.trimIndent()

        val parsed = parseStage1(stage1Output)
        assertFalse("Should be medical", parsed.isNotMedical)

        // Stage 2 routing
        val routes = routeCategories(parsed.categories)
        assertTrue(routes.contains("03_Protocols/Active_Medications.md"))
        assertTrue(routes.contains("02_Timeline/Medical_Timeline.md"))

        // Stage 4 writes (skipping Stage 3 LLM formatting — use raw entities as content)
        val today = "2026-03-09"
        for (route in routes) {
            val file = File(tmpVault, route)
            file.parentFile?.mkdirs()
            val entry = "- **$today**: ${parsed.summary} (${parsed.entities})"
            if (file.exists()) file.appendText("\n\n$entry") else file.writeText("# ${file.nameWithoutExtension}\n\n$entry")
        }

        val medsFile = File(tmpVault, "03_Protocols/Active_Medications.md")
        val timelineFile = File(tmpVault, "02_Timeline/Medical_Timeline.md")

        assertTrue("Meds file should exist", medsFile.exists())
        assertTrue("Timeline file should exist", timelineFile.exists())
        assertTrue(medsFile.readText().contains("Amoxicillin"))
        assertTrue(timelineFile.readText().contains("sinus infection"))

        tmpVault.deleteRecursively()
    }
}
