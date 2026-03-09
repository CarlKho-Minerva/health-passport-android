// Copyright 2024-2026 Nexa AI, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.nexa.demo

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.system.Os
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.SimpleAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin
import com.gyf.immersionbar.ktx.immersionBar
import com.hjq.toast.Toaster
import com.liulishuo.okdownload.DownloadContext
import com.liulishuo.okdownload.DownloadTask
import com.liulishuo.okdownload.OkDownload
import com.liulishuo.okdownload.core.cause.EndCause
import com.liulishuo.okdownload.core.connection.DownloadOkHttp3Connection
import com.liulishuo.okdownload.kotlin.listener.createDownloadContextListener
import com.liulishuo.okdownload.kotlin.listener.createListener1
import com.nexa.demo.bean.DownloadableFile
import com.nexa.demo.bean.DownloadableFileWithFallback
import com.nexa.demo.bean.DownloadState
import com.nexa.demo.bean.ModelData
import com.nexa.demo.bean.downloadableFiles
import com.nexa.demo.bean.downloadableFilesWithFallback
import com.nexa.demo.bean.getNexaManifest
import com.nexa.demo.bean.getNonExistModelFile
import com.nexa.demo.bean.getSupportPluginIds
import com.nexa.demo.bean.isNpuModel
import com.nexa.demo.bean.mmprojTokenFile
import com.nexa.demo.bean.modelDir
import com.nexa.demo.bean.modelFile
import com.nexa.demo.bean.tokenFile
import com.nexa.demo.bean.withFallbackUrls
import com.nexa.demo.utils.ModelFileListingUtil
import com.nexa.demo.databinding.ActivityMainBinding
import com.nexa.demo.databinding.DialogSelectPluginIdBinding
import com.nexa.demo.listeners.CustomDialogInterface
import com.nexa.demo.utils.ExecShell
import com.nexa.demo.utils.ImgUtil
import com.nexa.demo.utils.WavRecorder
import com.nexa.demo.utils.inflate
import com.nexa.sdk.AsrWrapper
import com.nexa.sdk.CvWrapper
import com.nexa.sdk.EmbedderWrapper
import com.nexa.sdk.LlmWrapper
import com.nexa.sdk.NexaSdk
import com.nexa.sdk.RerankerWrapper
import com.nexa.sdk.VlmWrapper
import com.nexa.sdk.bean.AsrCreateInput
import com.nexa.sdk.bean.AsrTranscribeInput
import com.nexa.sdk.bean.CVCapability
import com.nexa.sdk.bean.CVCreateInput
import com.nexa.sdk.bean.CVModelConfig
import com.nexa.sdk.bean.ChatMessage
import com.nexa.sdk.bean.EmbedResult
import com.nexa.sdk.bean.EmbedderCreateInput
import com.nexa.sdk.bean.EmbeddingConfig
import com.nexa.sdk.bean.LlmCreateInput
import com.nexa.sdk.bean.LlmStreamResult
import com.nexa.sdk.bean.ModelConfig
import com.nexa.sdk.bean.RerankConfig
import com.nexa.sdk.bean.RerankerCreateInput
import com.nexa.sdk.bean.VlmChatMessage
import com.nexa.sdk.bean.VlmContent
import com.nexa.sdk.bean.VlmCreateInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class MainActivity : FragmentActivity() {

    private val binding: ActivityMainBinding by inflate()
    private var downloadContext: DownloadContext? = null
    private var downloadState = DownloadState.IDLE
    private var downloadingModelData: ModelData? = null
    private var downloadWakeLock: android.os.PowerManager.WakeLock? = null
    private lateinit var spDownloaded: SharedPreferences
    private lateinit var llDownloading: LinearLayout
    private lateinit var tvDownloadProgress: TextView
    private lateinit var pbDownloading: ProgressBar
    private lateinit var spModelList: Spinner
    private lateinit var btnDownload: Button
    private lateinit var btnLoadModel: Button
    private lateinit var btnUnloadModel: Button
    private lateinit var btnStop: Button
    private lateinit var btnSelectModelFile: Button
    private lateinit var btnBrowseFiles: Button
    private lateinit var etInput: EditText
    private lateinit var btnAskDoctor: Button
    private lateinit var btnSaveVault: Button
    private lateinit var btnNewChat: ImageButton
    private lateinit var btnAddImage: Button
    private lateinit var btnAudioRecord: Button
    private var hkSystemPrompt: String = ""

    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvPrivacyBadge: TextView
    private lateinit var tvModelStatus: TextView
    private lateinit var llAdvancedControls: LinearLayout

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var bottomPanel: LinearLayout
    private lateinit var btnAudioDone: Button
    private lateinit var btnAudioCancel: Button

    private lateinit var scrollImages: HorizontalScrollView
    private lateinit var topScrollContainer: LinearLayout
    private lateinit var llLoading: LinearLayout
    private lateinit var vTip: View

    private lateinit var llmWrapper: LlmWrapper
    private lateinit var vlmWrapper: VlmWrapper
    var embedderWrapper: EmbedderWrapper? = null
    private lateinit var rerankerWrapper: RerankerWrapper
    private lateinit var cvWrapper: CvWrapper
    private lateinit var asrWrapper: AsrWrapper
    private val modelScope = CoroutineScope(Dispatchers.IO)

    private val chatList = arrayListOf<ChatMessage>()
    private lateinit var llmSystemPrompt: ChatMessage
    private val vlmChatList = arrayListOf<VlmChatMessage>()
    private lateinit var vlmSystemPrompty: VlmChatMessage
    private lateinit var modelList: List<ModelData>
    private var selectModelId = ""

    // ADD: Track which model type is loaded
    private var isLoadLlmModel = false
    private var isLoadVlmModel = false
    private var isLoadEmbedderModel = false
    private var isLoadRerankerModel = false
    private var isLoadCVModel = false
    private var isLoadAsrModel = false

    private var enableThinking = false

    // Think-tag stripping regex for VLM/LLM outputs
    private val thinkTagRegex = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)
    private val openThinkTagRegex = Regex("<think>.*", RegexOption.DOT_MATCHES_ALL)

    // Track last VLM extraction for cross-model pipeline
    private var lastVlmExtraction: String? = null

    // Throttle UI updates to prevent screen flickering during streaming
    private var lastUiUpdateMs = 0L
    private val UI_UPDATE_INTERVAL_MS = 80L  // ~12 fps, smooth enough without flicker

    // Garbage detection: stop stream if output is degenerate
    private var lastTokenForRepeatCheck = ""
    private var repeatTokenCount = 0
    private val MAX_REPEAT_TOKENS = 8  // Stop after 8 identical consecutive tokens
    private var streamStopRequested = false

    private var wavRecorder: WavRecorder? = null
    private var audioFile: File? = null

    private val savedImageFiles = mutableListOf<File>()
    private val messages = arrayListOf<Message>()

    // Manual model file selection
    private var manualModelFilePath: String? = null

    // Health Vault
    private lateinit var healthVaultDir: File
    private var mockScanInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        immersionBar {
            statusBarColorInt(Color.WHITE)
            statusBarDarkFont(true)
        }
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1002)
        okdownload()
        initData()
        initHealthVault()
        initView()
        setListeners()
        checkFirstLaunch()
    }

    private fun resetLoadState() {
        isLoadLlmModel = false
        isLoadVlmModel = false
        isLoadEmbedderModel = false
        isLoadRerankerModel = false
        isLoadCVModel = false
        isLoadAsrModel = false
    }

    private fun initView() {
        adapter = ChatAdapter(messages)
        binding.rvChat.adapter = adapter

        llDownloading = findViewById(R.id.ll_downloading)
        tvDownloadProgress = findViewById(R.id.tv_download_progress)
        pbDownloading = findViewById(R.id.pb_downloading)
        spModelList = findViewById(R.id.sp_model_list)
        spModelList.adapter = object : SimpleAdapter(this, modelList.map {
            val map = mutableMapOf<String, String>()
            map["displayName"] = it.displayName
            map
        }, R.layout.item_model, arrayOf("displayName"), intArrayOf(R.id.tv_model_id)) {

        }
        spModelList.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                selectModelId = modelList[position].id

                messages.clear()
                adapter.notifyDataSetChanged()
                binding.rvChat.scrollTo(0, 0)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectModelId = ""
            }
        }
        btnDownload = findViewById(R.id.btn_download)
        btnLoadModel = findViewById(R.id.btn_load_model)
        btnUnloadModel = findViewById(R.id.btn_unload_model)
        btnStop = findViewById(R.id.btn_stop)
        btnSelectModelFile = findViewById(R.id.btn_select_model_file)
        btnBrowseFiles = findViewById(R.id.btn_browse_files)
        etInput = findViewById(R.id.et_input)
        btnAddImage = findViewById(R.id.btn_add_image)
        btnAudioRecord = findViewById(R.id.btn_voice)

        tvHeaderTitle = findViewById(R.id.tv_header_title)
        tvPrivacyBadge = findViewById(R.id.tv_privacy_badge)
        tvModelStatus = findViewById(R.id.tv_model_status)
        llAdvancedControls = findViewById(R.id.ll_advanced_controls)

        // Long-press header to toggle advanced mode
        tvHeaderTitle.setOnLongClickListener {
            toggleAdvancedMode()
            true
        }
        // Tap privacy badge for settings, long-press for advanced mode
        tvPrivacyBadge.setOnClickListener {
            showSettingsDialog()
        }
        tvPrivacyBadge.setOnLongClickListener {
            toggleAdvancedMode()
            true
        }

        // Set initial status - demo-friendly
        tvModelStatus.text = "Qualcomm NPU · Health Vault Loaded"

        bottomPanel = findViewById(R.id.bottom_panel)
        btnAudioCancel = findViewById(R.id.btn_audio_cancel)
        btnAudioDone = findViewById(R.id.btn_audio_done)

        btnAskDoctor = findViewById(R.id.btn_ask_doctor)
        btnSaveVault = findViewById(R.id.btn_save_vault)
        btnNewChat = findViewById(R.id.btn_new_chat)
        scrollImages = findViewById(R.id.scroll_images)
        topScrollContainer = findViewById(R.id.ll_images_container)
        llLoading = findViewById(R.id.ll_loading)
        vTip = findViewById<View>(R.id.v_tip)

        btnAudioCancel.setOnClickListener {
            stopRecord(true)
        }

        btnAudioDone.setOnClickListener {
            stopRecord(false)
        }

        findViewById<Button>(R.id.btn_test).setOnClickListener {
            Thread {
                val exeFile = File(filesDir, "nexa_test_llm")
                val chmodProcess = Runtime.getRuntime().exec("chmod 755 " + exeFile.absolutePath);
                chmodProcess.waitFor()
                Log.d("nfl", "exeFile exe? ${exeFile.canExecute()}")
                Log.d("nfl", "Exe Thread:${Thread.currentThread().name}")
                ExecShell().executeCommand(
                    arrayOf(
                        //                        exeFile.absolutePath,
//                        "--test-suite=\"npu\"", "--success "
                        "cat",
                        "/sys/devices/soc0/sku"
//                        "/data/local/tmp/test_cat.txt"
                    )
                ).forEach {
                    Log.d("nfl", "cmd:$it")
                }
            }.start()
        }

        findViewById<View>(R.id.v_tip).setOnClickListener {
            Toast.makeText(this, "please unload model first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleAdvancedMode() {
        if (llAdvancedControls.visibility == View.GONE) {
            llAdvancedControls.visibility = View.VISIBLE
            Toast.makeText(this, "Advanced mode enabled", Toast.LENGTH_SHORT).show()
        } else {
            llAdvancedControls.visibility = View.GONE
            Toast.makeText(this, "Advanced mode disabled", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Auto-detect model files at common paths (for QDC SSH push or pre-placed models)
     * Following the tutorial pattern: check known paths, copy to app storage if needed
     */
    private fun autoDetectModels() {
        val searchPaths = listOf(
            // QDC AI Model upload / ADB push paths
            File("/data/local/tmp"),
            File("/sdcard/Download"),
            File("/sdcard/Models"),
            File("/sdcard/nexa_models"),
            // App-specific paths
            File(filesDir, "models"),
            File(filesDir, "manual_models"),
            // Assets-copied models path (tutorial pattern: assets → filesDir)
            File(filesDir, "nexa_models")
        )

        for (dir in searchPaths) {
            if (!dir.exists() || !dir.isDirectory) continue
            val ggufFiles = dir.walkTopDown().maxDepth(2).filter {
                it.isFile && it.name.endsWith(".gguf", ignoreCase = true)
            }.toList()

            if (ggufFiles.isNotEmpty()) {
                val model = ggufFiles.first()
                Log.i(TAG, "Auto-detected model: ${model.absolutePath} (${model.length() / 1024 / 1024}MB)")

                // Copy to app storage if not already there (tutorial ModelManager pattern)
                val appModelDir = File(filesDir, "manual_models")
                if (!appModelDir.exists()) appModelDir.mkdirs()

                val destFile = File(appModelDir, model.name)
                if (model.absolutePath.startsWith(filesDir.absolutePath)) {
                    // Already in app storage
                    manualModelFilePath = model.absolutePath
                } else if (destFile.exists() && destFile.length() == model.length()) {
                    // Already copied
                    manualModelFilePath = destFile.absolutePath
                } else {
                    // Copy to app storage (like tutorial's copyModelFromAssets)
                    Log.i(TAG, "Copying model to app storage: ${model.name}")
                    runOnUiThread {
                        tvModelStatus.text = "Found model: ${model.name}\nCopying to app storage..."
                    }
                    try {
                        model.copyTo(destFile, overwrite = true)
                        manualModelFilePath = destFile.absolutePath
                        Log.i(TAG, "Model copied successfully to: ${destFile.absolutePath}")
                    } catch (e: Exception) {
                        // If copy fails, try to use directly from source
                        Log.w(TAG, "Copy failed (${e.message}), using source path directly")
                        manualModelFilePath = model.absolutePath
                    }
                }

                runOnUiThread {
                    tvModelStatus.text = "Model found: ${model.name}\nTap Load to start (use CPU+GPU for GGUF)"
                    Toast.makeText(this, "Auto-detected: ${model.name}", Toast.LENGTH_LONG).show()
                }
                return
            }
        }
        Log.i(TAG, "No pre-placed models found at common paths")
    }

    /**
     * Copy bundled models from assets to filesDir (tutorial ModelManager pattern)
     * Called on first run to extract bundled models
     */
    private fun copyBundledModels() {
        try {
            val assetModels = assets.list("nexa_models") ?: emptyArray()
            if (assetModels.isEmpty()) {
                Log.i(TAG, "No bundled models in assets/nexa_models/")
                runOnUiThread {
                    Toast.makeText(this, "No bundled models found in assets/nexa_models/", Toast.LENGTH_SHORT).show()
                }
                return
            }

            val destDir = File(filesDir, "nexa_models")
            if (!destDir.exists()) destDir.mkdirs()

            for (modelFolder in assetModels) {
                val modelFiles = assets.list("nexa_models/$modelFolder") ?: continue
                val modelDestDir = File(destDir, modelFolder)
                if (!modelDestDir.exists()) modelDestDir.mkdirs()

                for (fileName in modelFiles) {
                    val destFile = File(modelDestDir, fileName)
                    if (destFile.exists()) continue  // Already copied

                    Log.i(TAG, "Copying bundled model: nexa_models/$modelFolder/$fileName")
                    assets.open("nexa_models/$modelFolder/$fileName").use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            // Auto-detect the copied models
            autoDetectModels()
        } catch (e: Exception) {
            Log.w(TAG, "No bundled models to copy: ${e.message}")
        }
    }

    /**
     * Initialize the Health Vault by copying bundled markdown files from assets/health_vault
     * to filesDir/health_vault. This gives the demo a pre-populated medical record structure.
     */
    private fun initHealthVault() {
        healthVaultDir = File(filesDir, "health_vault")
        if (healthVaultDir.exists() && File(healthVaultDir, "01_Body_Systems/01_Head_Eyes_ENT.md").exists()) {
            Log.i(TAG, "Health vault already initialized")
            return
        }
        try {
            copyAssetFolder("health_vault", healthVaultDir)
            Log.i(TAG, "Health vault initialized at: ${healthVaultDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init health vault: ${e.message}")
        }
    }

    /**
     * Recursively copy an asset folder to a destination directory
     */
    private fun copyAssetFolder(assetPath: String, destDir: File) {
        val entries = assets.list(assetPath) ?: return
        if (!destDir.exists()) destDir.mkdirs()

        for (entry in entries) {
            val assetEntryPath = "$assetPath/$entry"
            val destFile = File(destDir, entry)
            val subEntries = assets.list(assetEntryPath)

            if (subEntries != null && subEntries.isNotEmpty()) {
                // It's a directory
                copyAssetFolder(assetEntryPath, destFile)
            } else {
                // It's a file
                try {
                    assets.open(assetEntryPath).use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Skip: $assetEntryPath (${e.message})")
                }
            }
        }
    }

    /**
     * RAG: Load relevant health vault files as context for the LLM.
     * Uses keyword matching to find relevant files, reads their content,
     * and returns a context string to prepend to the user's query.
     */
    /**
     * Full vault ingest — reads ALL markdown files from the health vault
     * (like git-ingest) so the LLM has complete patient context.
     * Excludes system prompts and archives to stay within token budget.
     */
    private fun loadFullVaultContext(): String {
        if (!::healthVaultDir.isInitialized || !healthVaultDir.exists()) return ""

        val contextBuilder = StringBuilder()
        contextBuilder.append("--- HEALTH VAULT (FULL CONTEXT) ---\n")
        var totalChars = 0
        val maxChars = 12000

        // Read all vault .md files except system prompts and archives
        val vaultFiles = healthVaultDir.walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .filter { !it.path.contains("04_System_Prompt") }
            .filter { !it.path.contains("04_System_Meta") }
            .filter { !it.path.contains("99_Archives") }
            .sortedBy { it.path }
            .toList()

        for (file in vaultFiles) {
            if (totalChars >= maxChars) break
            try {
                val content = file.readText().trim()
                if (content.isBlank()) continue
                val relPath = file.relativeTo(healthVaultDir).path
                val remaining = maxChars - totalChars
                val truncated = if (content.length > remaining) content.substring(0, remaining) + "\n[...truncated]" else content
                contextBuilder.append("\n## FILE: $relPath\n")
                contextBuilder.append(truncated)
                contextBuilder.append("\n")
                totalChars += truncated.length + relPath.length + 12
            } catch (e: Exception) {
                Log.w(TAG, "Could not read vault file: ${file.name}: ${e.message}")
            }
        }
        contextBuilder.append("--- END HEALTH VAULT ---\n")

        Log.d(TAG, "Vault ingest: ${vaultFiles.size} files, $totalChars chars")
        return contextBuilder.toString()
    }

    /**
     * Build a RAG-augmented prompt: full vault context + user question.
     */
    private fun buildRagPrompt(userQuery: String): String {
        val vaultContext = loadFullVaultContext()
        if (vaultContext.isBlank()) return userQuery

        return """Based on the patient's complete health records below, answer their question accurately and concisely.

$vaultContext

Patient's question: $userQuery

Answer based on the health records above. If the records don't contain relevant information, say so."""
    }

    /**
     * Show intro modal on launch (unless user has dismissed it)
     */
    // Essential models for the core pipeline (LLM + OCR + ASR)
    private val essentialModelIds = listOf(
        "Qwen3-4B-Instruct-NPU",   // Brain — text Q&A
        "paddleocr-npu",            // Scanner — document OCR
        "parakeet-tdt-npu"          // Voice — speech-to-text
    )

    private fun checkFirstLaunch() {
        // Check which essential models are missing
        val missingModels = essentialModelIds.mapNotNull { id ->
            modelList.firstOrNull { it.id == id }?.let { model ->
                if (isModelDownloaded(model) != null) model else null
            }
        }

        if (missingModels.isNotEmpty()) {
            // Show setup modal and auto-download
            Handler(Looper.getMainLooper()).postDelayed({
                showSetupModal(missingModels)
            }, 600)
        } else {
            // All essential models present — auto-load the LLM
            Handler(Looper.getMainLooper()).postDelayed({
                autoLoadEssentialModels()
            }, 400)
        }
    }

    private fun autoLoadEssentialModels() {
        // Auto-load all essential models (LLM + OCR + ASR) in sequence
        val modelsToLoad = essentialModelIds.mapNotNull { id ->
            modelList.firstOrNull { it.id == id }?.let { model ->
                if (isModelDownloaded(model) == null) model else null
            }
        }

        // Load LLM first (most important)
        val llmModel = modelList.firstOrNull { it.id == "Qwen3-4B-Instruct-NPU" }
        if (llmModel != null && !isLoadLlmModel && isModelDownloaded(llmModel) == null) {
            selectModelId = llmModel.id
            val pos = modelList.indexOfFirst { it.id == selectModelId }
            if (pos >= 0) spModelList.setSelection(pos)
            btnLoadModel.performClick()
        }

        // Load OCR and ASR after a delay to let LLM finish
        Handler(Looper.getMainLooper()).postDelayed({
            val ocrModel = modelList.firstOrNull { it.id == "paddleocr-npu" }
            if (ocrModel != null && !isLoadCVModel && isModelDownloaded(ocrModel) == null) {
                selectModelId = ocrModel.id
                val pos = modelList.indexOfFirst { it.id == selectModelId }
                if (pos >= 0) spModelList.setSelection(pos)
                btnLoadModel.performClick()
            }
        }, 8000)

        Handler(Looper.getMainLooper()).postDelayed({
            val asrModel = modelList.firstOrNull { it.id == "parakeet-tdt-npu" }
            if (asrModel != null && !isLoadAsrModel && isModelDownloaded(asrModel) == null) {
                selectModelId = asrModel.id
                val pos = modelList.indexOfFirst { it.id == selectModelId }
                if (pos >= 0) spModelList.setSelection(pos)
                btnLoadModel.performClick()
            }
        }, 16000)
    }

    private fun showSetupModal(missingModels: List<ModelData>) {
        val sheet = BottomSheetDialog(this, R.style.DarkBottomSheetDialog)
        sheet.setCancelable(false)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        // Header
        val header = TextView(this).apply {
            text = "Welcome to Health Passport"
            setTextColor(Color.parseColor("#F2F2F2"))
            textSize = 20f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            letterSpacing = -0.02f
            setPadding(0, 0, 0, dp(8))
        }
        container.addView(header)

        val subtitle = TextView(this).apply {
            text = "Your medical records, privately on your phone.\nNo internet needed after setup."
            setTextColor(Color.parseColor("#808080"))
            textSize = 13f
            setLineSpacing(0f, 1.3f)
            setPadding(0, 0, 0, dp(20))
        }
        container.addView(subtitle)

        // Status text
        val statusText = TextView(this).apply {
            text = "Installing ${missingModels.size} AI component${if (missingModels.size > 1) "s" else ""}..."
            setTextColor(Color.parseColor("#10B981"))
            textSize = 14f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setPadding(0, 0, 0, dp(12))
        }
        container.addView(statusText)

        // Progress bar
        val progressBar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(6)
            ).also { it.bottomMargin = dp(8) }
            max = missingModels.size * 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#10B981"))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A1A1A"))
        }
        container.addView(progressBar)

        // Current item text
        val currentItem = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#4D4D4D"))
            textSize = 11f
            setPadding(0, 0, 0, dp(16))
        }
        container.addView(currentItem)

        // Privacy note
        val privacyNote = TextView(this).apply {
            text = "Everything runs on-device. Your data never leaves this phone."
            setTextColor(Color.parseColor("#4D4D4D"))
            textSize = 11f
            setPadding(0, dp(8), 0, 0)
        }
        container.addView(privacyNote)

        sheet.setContentView(container)
        sheet.window?.navigationBarColor = Color.parseColor("#0D0D0D")
        sheet.setOnShowListener {
            val bottomSheet = sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundColor(Color.parseColor("#0D0D0D"))
        }
        sheet.show()

        // Start sequential download of missing models
        modelScope.launch {
            var completedCount = 0
            for (model in missingModels) {
                withContext(Dispatchers.Main) {
                    val label = when (model.type) {
                        "chat", "llm" -> "Language model"
                        "paddleocr" -> "Document scanner"
                        "asr" -> "Voice recognition"
                        "multimodal", "vlm" -> "Vision model"
                        "embedder" -> "Memory model"
                        else -> model.displayName
                    }
                    currentItem.text = "Installing: $label"
                    statusText.text = "Installing ${completedCount + 1} of ${missingModels.size}..."
                }

                // Download this model and wait for completion
                val success = downloadModelSuspend(model) { percent ->
                    runOnUiThread {
                        progressBar.progress = completedCount * 100 + percent
                    }
                }

                completedCount++
                if (!success) {
                    withContext(Dispatchers.Main) {
                        currentItem.text = "Failed to install ${model.displayName}. You can retry from Models."
                        currentItem.setTextColor(Color.parseColor("#EF4444"))
                    }
                }
            }

            withContext(Dispatchers.Main) {
                statusText.text = "Ready"
                statusText.setTextColor(Color.parseColor("#10B981"))
                currentItem.text = "Setup complete. Tap to start."
                currentItem.setTextColor(Color.parseColor("#808080"))
                progressBar.progress = progressBar.max

                // Replace progress with a done button
                val btnStart = Button(this@MainActivity).apply {
                    text = "Start"
                    setTextColor(Color.parseColor("#FFFFFF"))
                    textSize = 14f
                    isAllCaps = false
                    setBackgroundResource(R.drawable.btn_send_accent)
                    setPadding(dp(20), dp(12), dp(20), dp(12))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(44)
                    ).also { it.topMargin = dp(12) }
                    setOnClickListener {
                        sheet.dismiss()
                        autoLoadEssentialModels()
                    }
                }
                container.addView(btnStart)
                sheet.setCancelable(true)
            }
        }
    }

    /**
     * Suspending download that waits for completion.
     * Used by the setup modal to download models sequentially.
     * Reuses the existing downloadModel() and polls for completion.
     */
    private suspend fun downloadModelSuspend(
        model: ModelData,
        onProgress: (Int) -> Unit
    ): Boolean {
        // Skip if already downloaded
        if (isModelDownloaded(model) == null) {
            onProgress(100)
            return true
        }

        val completionSignal = kotlinx.coroutines.CompletableDeferred<Boolean>()

        // Store callback for the setup flow
        setupDownloadCompletion = { success ->
            completionSignal.complete(success)
        }
        setupDownloadProgress = onProgress

        // Trigger the standard download on the main thread
        withContext(Dispatchers.Main) {
            downloadModel(model)
        }

        // Wait for completion
        return completionSignal.await()
    }

    // Callbacks for setup flow downloads
    private var setupDownloadCompletion: ((Boolean) -> Unit)? = null
    private var setupDownloadProgress: ((Int) -> Unit)? = null

    /**
     * Mock scan flow: simulates document processing with progress
     * Shows the vision certificate being "organized" into the vault
     */
    private fun runMockScanDemo() {
        if (mockScanInProgress) return
        mockScanInProgress = true

        runOnUiThread {
            llDownloading.visibility = View.VISIBLE
            tvDownloadProgress.text = "Analyzing document..."
            pbDownloading.isIndeterminate = true
        }

        Thread {
            Thread.sleep(1800)
            runOnUiThread { tvDownloadProgress.text = "Detected: Medical Certificate" }
            Thread.sleep(1400)
            runOnUiThread { tvDownloadProgress.text = "Extracting prescription data" }
            Thread.sleep(1600)
            runOnUiThread { tvDownloadProgress.text = "Category  ·  Head, Eyes & ENT" }
            Thread.sleep(1200)
            runOnUiThread { tvDownloadProgress.text = "Filing to 01_Body_Systems" }
            Thread.sleep(900)
            runOnUiThread { tvDownloadProgress.text = "Updating timeline" }
            Thread.sleep(700)
            runOnUiThread {
                llDownloading.visibility = View.GONE
                mockScanInProgress = false

                val scanResult = """**Document Type:** Medical Certificate (Vision Rx)
**Date:** February 16, 2026
**Facility:** Vision Center, Medical Plaza

___

| Test | Result | Reference | Status |
|------|--------|-----------|--------|
| OD SPH | -1.25 | — | Myopia |
| OD CYL | -0.50 | — | Astigmatism |
| OD AXIS | 90 | — | — |
| OS SPH | -1.00 | — | Myopia |
| OS CYL | -0.75 | — | Astigmatism |
| OS AXIS | 85 | — | — |

**Diagnosis:** Myopic Astigmatism (Confirmed)

**Medications:** Rx Corrective Glasses — Wear daily for distance

**Action Items:**
- Filed to `01_Body_Systems/01_Head_Eyes_ENT.md`
- Timeline updated (Feb 16 entry)
- Archived to `99_Archives/2026-02-16_Vision_Exam/`
- Follow-up: Annual check recommended

**Notes:** Doctor: Sarah Chen, O.D. (Lic. 0024891)

*Processed on-device in 4.8s · No cloud transmission*"""

                streamResponseToChat(scanResult)
            }
        }.start()
    }

    /**
     * Stream a response into chat character-by-character for a premium feel.
     */
    private fun streamResponseToChat(fullText: String) {
        messages.add(Message("", MessageType.ASSISTANT))
        reloadRecycleView()

        Thread {
            val sb = StringBuilder()
            var i = 0
            while (i < fullText.length) {
                // Grab chunks of 2-6 chars for natural feel
                val chunkSize = when {
                    fullText[i] == '\n' -> 1
                    fullText[i] == '|' -> 1
                    fullText[i] == '#' -> 1
                    else -> (2..5).random()
                }
                val end = minOf(i + chunkSize, fullText.length)
                sb.append(fullText.substring(i, end))
                i = end

                val current = sb.toString()
                runOnUiThread {
                    messages[messages.size - 1] = Message(current, MessageType.ASSISTANT)
                    adapter.notifyItemChanged(messages.size - 1)
                }

                // Variable delay: longer on newlines, shorter on regular chars
                val delay = when {
                    i < fullText.length && fullText[i - 1] == '\n' -> (30L..60L).random()
                    i < fullText.length && fullText[i - 1] == '|' -> (15L..25L).random()
                    else -> (8L..18L).random()
                }
                Thread.sleep(delay)
            }

            runOnUiThread {
                messages[messages.size - 1] = Message(fullText, MessageType.ASSISTANT)
                adapter.notifyItemChanged(messages.size - 1)
                binding.rvChat.scrollToPosition(messages.size - 1)
            }
        }.start()
    }

    /**
     * Generate a preloaded RAG response for health queries when model isn't loaded.
     * Reads actual health vault files and returns formatted answers.
     */
    private fun handlePreloadedQuery(query: String): Boolean {
        val q = query.lowercase()

        // Eye-related queries
        if (q.contains("eye") || q.contains("vision") || q.contains("glasses") ||
            q.contains("optical") || q.contains("myop") || q.contains("astigmat") ||
            q.contains("eo ") || q.contains("prescription") || q.contains("sight")) {

            val vaultFile = File(healthVaultDir, "01_Body_Systems/01_Head_Eyes_ENT.md")
            val vaultContent = try { vaultFile.readText() } catch (e: Exception) { "" }
            val response = if (vaultContent.isNotBlank()) {
                "**Eye Health — From Your Vault**\n\n$vaultContent\n\n___\n\n*Source: 01_Head_Eyes_ENT.md · on-device RAG*"
            } else {
                "No eye health records found in your vault. Scan an eye exam document to populate this."
            }
            streamResponseToChat(response)
            return true
        }

        // Medication queries
        if (q.contains("med") || q.contains("drug") || q.contains("pill") ||
            q.contains("prescription") || q.contains("taking")) {

            val vaultFile = File(healthVaultDir, "03_Protocols/Active_Medications.md")
            val vaultContent = try { vaultFile.readText() } catch (e: Exception) { "" }
            val response = if (vaultContent.isNotBlank()) {
                "**Active Medications — From Your Vault**\n\n$vaultContent\n\n___\n\n*Source: Active_Medications.md · on-device RAG*"
            } else {
                "No medication records found. Scan a prescription to populate this."
            }
            streamResponseToChat(response)
            return true
        }

        // Timeline / history queries
        if (q.contains("timeline") || q.contains("history") || q.contains("when") ||
            q.contains("visit") || q.contains("appointment")) {

            val vaultFile = File(healthVaultDir, "02_Timeline/Medical_Timeline.md")
            val vaultContent = try { vaultFile.readText() } catch (e: Exception) { "" }
            val response = if (vaultContent.isNotBlank()) {
                "**Medical Timeline — From Your Vault**\n\n$vaultContent\n\n___\n\n*Source: Medical_Timeline.md · on-device RAG*"
            } else {
                "No timeline data found. Scan documents to build your medical history."
            }
            streamResponseToChat(response)
            return true
        }

        // Lab results
        if (q.contains("lab") || q.contains("blood") || q.contains("test") ||
            q.contains("cbc") || q.contains("lipid") || q.contains("glucose") || q.contains("baseline")) {

            val vaultFile = File(healthVaultDir, "01_Body_Systems/00_Lab_Baselines.md")
            val vaultContent = try { vaultFile.readText() } catch (e: Exception) { "" }
            val response = if (vaultContent.isNotBlank()) {
                "**Lab Baselines — From Your Vault**\n\n$vaultContent\n\n___\n\n*Source: 00_Lab_Baselines.md · on-device RAG*"
            } else {
                "No lab results found. Scan a lab report to populate this."
            }
            streamResponseToChat(response)
            return true
        }

        // General health / system prompt
        if (q.contains("health") || q.contains("status") || q.contains("summary") ||
            q.contains("how am i") || q.contains("overall")) {

            // Gather overview from all vault files
            val overviewBuilder = StringBuilder("**Health Passport — Overview**\n\n")
            val vaultFiles = listOf(
                "01_Body_Systems/00_Lab_Baselines.md" to "Lab Baselines",
                "01_Body_Systems/01_Head_Eyes_ENT.md" to "Eyes & ENT",
                "03_Protocols/Active_Medications.md" to "Active Medications",
                "02_Timeline/Medical_Timeline.md" to "Timeline"
            )
            for ((path, label) in vaultFiles) {
                try {
                    val content = File(healthVaultDir, path).readText().trim()
                    if (content.isNotBlank()) {
                        overviewBuilder.append("### $label\n$content\n\n")
                    }
                } catch (_: Exception) {}
            }
            overviewBuilder.append("___\n\n*All data stored on-device · HIPAA-compliant*")
            streamResponseToChat(overviewBuilder.toString())
            return true
        }

        return false  // No preloaded answer found
    }

    private fun parseModelList() {
        try {
            val baseJson = assets.open("model_list.json").bufferedReader().use { it.readText() }
            modelList = Json.decodeFromString<List<ModelData>>(baseJson)
            // Spinner is hidden in layout — default to first model (OmniNeural-4B)
            // so Download button doesn't crash with NoSuchElementException on empty selectModelId
            if (selectModelId.isEmpty() && modelList.isNotEmpty()) {
                selectModelId = modelList[0].id
            }
        } catch (e: Exception) {
            Log.e("nfl", "parseModelList: $e")
        }
    }

    /**
     * Step 0. Preparing to download the model file.
     */
    private fun initData() {
        spDownloaded = getSharedPreferences(SP_DOWNLOADED, MODE_PRIVATE)
//        spDownloaded.edit().putBoolean("Qwen3-0.6B-Q8_0", false).commit()
//        spDownloaded.edit().putBoolean("Qwen3-0.6B-IQ4_NL", false).commit()
//        spDownloaded.edit().putBoolean("LFM2-1.2B-npu", false).commit()
//        spDownloaded.edit().putBoolean("embeddinggemma-300m-npu", false).commit()
//        spDownloaded.edit().putBoolean("jina-v2-rerank-npu", false).commit()
//        spDownloaded.edit().putBoolean("paddleocr-npu", false).commit()
//        spDownloaded.edit().putBoolean("parakeet-tdt-0.6b-v3-npu", false).commit()
//        spDownloaded.edit().putBoolean("OmniNeural-4B", false).commit()
//        spDownloaded.edit().putBoolean("Granite-4.0-h-350M-NPU", false).commit()
//        spDownloaded.edit().putBoolean("Granite-4-Micro-NPU", false).commit()
        parseModelList()
        //
        initNexaSdk()
        //
        val sysPrompt = """\
You are an experienced, evidence-based physician and personal health strategist built into the Health Passport app. You operate entirely on-device — no data leaves this phone.

You have direct access to the patient's health records stored locally, including:
- Body system files (eyes, cardiovascular, limbs, neuro/psych, lab baselines)
- Active medications and protocols
- Medical timeline and visit history
- Scanned documents (OCR extractions)

Core directives:
1. ALWAYS reference the patient's health records when answering. Cite specific data (dates, values, meds) from the records.
2. Be precise and evidence-based. Specify dosage, frequency, and mechanism when discussing medications.
3. If records contain relevant data, analyze and synthesize across systems (e.g., medication interactions, timeline patterns).
4. If records don't cover the topic, say "Your health vault doesn't have records about this yet — would you like to save this to the vault?"
5. When analyzing scanned documents: identify document type, key findings, medications, and action items.
6. Keep responses concise. Use bold for emphasis. No unnecessary fluff.
7. The patient travels internationally — factor in medication availability and regional healthcare context when relevant.
8. After answering, if there is relevant follow-up to explore, end with a brief one-line suggestion. Example: "Want me to cross-check this with your active medications?" or "Should I look at your timeline for related visits?"

You are a clinical tool, not a replacement for in-person care. Flag when something needs urgent professional attention.
"""
        // Use the full detailed prompt for both VLM and LLM
        addSystemPrompt(sysPrompt)

        // Load HK (housekeeping / medical record keeper) system prompt from vault
        loadHkSystemPrompt()

        // Tutorial pattern: Check for bundled models in assets, then auto-detect pre-placed
        copyBundledModels()
        // Auto-detect if no bundled models found
        if (manualModelFilePath == null) {
            Thread { autoDetectModels() }.start()
        }
    }

    /**
     * Step 1. initNexaSdk environment
     */
    private fun initNexaSdk() {
        // Initialize NexaSdk with context
        NexaSdk.getInstance().init(this, object : NexaSdk.InitCallback {
            override fun onSuccess() {
            }

            override fun onFailure(reason: String) {
                Log.e(TAG, "NexaSdk init failed: $reason")
            }
        })

        val testLocalPath = false
        if (testLocalPath) {
            // FIXME: Set directory according to terminal format
            val pluginNativeLibPath = filesDir.absolutePath
            val pluginAdspLibPath = File(filesDir, "npu/htp-files").absolutePath
            val pluginLdLibraryPath =
                "$pluginNativeLibPath:$pluginNativeLibPath/npu:$pluginAdspLibPath:\$LD_LIBRARY_PATH"
            // FIXME: Set directory with flattened .so files
            val NEXA_PLUGIN_PATH = pluginNativeLibPath
            val LD_LIBRARY_PATH = pluginLdLibraryPath
            val ADSP_LIBRARY_PATH = pluginAdspLibPath
            Log.d("nfl", "NEXA_PLUGIN_PATH:$NEXA_PLUGIN_PATH")
            Log.d("nfl", "LD_LIBRARY_PATH:$LD_LIBRARY_PATH")
            Log.d("nfl", "ADSP_LIBRARY_PATH:$ADSP_LIBRARY_PATH")

            Os.setenv("NEXA_PLUGIN_PATH", NEXA_PLUGIN_PATH, true)
            Os.setenv("LD_LIBRARY_PATH", LD_LIBRARY_PATH, true)
            Os.setenv("ADSP_LIBRARY_PATH", ADSP_LIBRARY_PATH, true)
        }
    }

    /**
     * Step 2. add system prompt, such as : output markdown style, contains emoji etc.(Options)
     */
    private fun addSystemPrompt(sysPrompt: String) {
        llmSystemPrompt = ChatMessage("system", sysPrompt)
        chatList.add(llmSystemPrompt)
        // VLM gets a short prompt so it doesn't echo the template on text-only queries
        val vlmPrompt = "You are a medical document scanner. When given an image, extract all text and medical data. Output structured markdown."
        vlmSystemPrompty =
            VlmChatMessage(
                "system",
                listOf(VlmContent("text", vlmPrompt))
            )
        vlmChatList.add(vlmSystemPrompty)
    }

    /**
     * Load the HK (housekeeping / medical record keeper) system prompt from the vault.
     * This prompt is used by the "Save to Vault" button to instruct the LLM
     * to organize and file medical data according to the vault structure.
     */
    private fun loadHkSystemPrompt() {
        try {
            val promptFile = File(healthVaultDir, "04_System_Prompt/hk_system_prompt.md")
            if (promptFile.exists()) {
                hkSystemPrompt = promptFile.readText().trim()
                Log.d(TAG, "HK system prompt loaded (${hkSystemPrompt.length} chars)")
            } else {
                hkSystemPrompt = "You are a medical record keeper. Organize the following medical data into structured markdown. Identify document type, key findings, medications, and action items. Output a well-structured health record entry with date, category, and details."
                Log.w(TAG, "HK prompt file not found, using fallback")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading HK prompt", e)
            hkSystemPrompt = "Organize the following medical data into a structured health record."
        }
    }

    /**
     * "Save to Vault" button action — sends input through LLM with the HK system prompt
     * instead of the Doctor prompt. The LLM acts as a medical record keeper,
     * organizing and filing data according to the vault's CRUD rules.
     */
    // ════════════════════════════════════════════════════════════════════════
    //  AGENTIC VAULT PIPELINE — Multi-stage Save to Vault
    //
    //  Stage 1: LLM classifies data + extracts structured medical entities
    //  Stage 2: App-side routing maps classification → target vault files
    //  Stage 3: LLM formats extracted data to match each target file's style
    //  Stage 4: App writes to files + auto-logs to Timeline
    // ════════════════════════════════════════════════════════════════════════

    /** Classification categories the LLM can assign */
    private val VAULT_ROUTES = mapOf(
        "medication" to listOf("03_Protocols/Active_Medications.md"),
        "lab" to listOf("01_Body_Systems/00_Lab_Baselines.md"),
        "eye" to listOf("01_Body_Systems/01_Head_Eyes_ENT.md"),
        "cardio" to listOf("01_Body_Systems/02_Cardiovascular_Heart.md"),
        "gi" to listOf("01_Body_Systems/02_Metabolic_GI.md"),
        "ortho" to listOf("01_Body_Systems/03_Limbs_Ortho.md"),
        "skin" to listOf("01_Body_Systems/04_Skin_Derma.md"),
        "immune" to listOf("01_Body_Systems/05_Systemic_Immune.md"),
        "neuro" to listOf("01_Body_Systems/06_Neuro_Psych.md"),
        "genetics" to listOf("01_Body_Systems/07_Genetics_PGx.md"),
        "diet" to listOf("03_Protocols/Diet_Plan.md"),
        "exercise" to listOf("03_Protocols/Physio_Routine.md"),
        "inventory" to listOf("03_Protocols/Medicine_Inventory.md"),
        "visit" to listOf("02_Timeline/Medical_Timeline.md"),
        "therapy" to listOf("05_Therapy/Therapy_Log.md"),
        "insurance" to listOf("03_Protocols/Insurance_Reimbursement.md")
    )

    /**
     * Run an LLM call that doesn't stream to the UI — returns the raw text.
     * Used for intermediate pipeline stages.
     */
    private suspend fun llmCallSilent(systemPrompt: String, userPrompt: String): String? {
        val tempChat = mutableListOf<ChatMessage>()
        tempChat.add(ChatMessage("system", systemPrompt))
        tempChat.add(ChatMessage("user", userPrompt))

        var result: String? = null
        llmWrapper.applyChatTemplate(tempChat.toTypedArray(), null, enableThinking)
            .onSuccess { templateOutput ->
                val sb = StringBuilder()
                lastTokenForRepeatCheck = ""
                repeatTokenCount = 0
                streamStopRequested = false
                llmWrapper.generateStreamFlow(
                    templateOutput.formattedText,
                    GenerationConfigSample().toGenerationConfig(null)
                ).collect { streamResult ->
                    when (streamResult) {
                        is LlmStreamResult.Token -> {
                            sb.append(streamResult.text)
                            // Garbage detection same as handleResult
                            val token = streamResult.text.trim()
                            if (token.isNotEmpty()) {
                                if (token == lastTokenForRepeatCheck) repeatTokenCount++
                                else { lastTokenForRepeatCheck = token; repeatTokenCount = 1 }
                            }
                            if (repeatTokenCount >= MAX_REPEAT_TOKENS && !streamStopRequested) {
                                streamStopRequested = true
                                try { llmWrapper.stopStream() } catch (_: Exception) {}
                            }
                        }
                        is LlmStreamResult.Completed -> {
                            lastTokenForRepeatCheck = ""
                            repeatTokenCount = 0
                            streamStopRequested = false
                        }
                        is LlmStreamResult.Error -> { Log.e(TAG, "Silent LLM error") }
                    }
                }
                result = sb.toString()
                    .replace(thinkTagRegex, "")
                    .replace(openThinkTagRegex, "")
                    .trimStart('\n', ' ')
            }.onFailure { e ->
                Log.e(TAG, "Silent LLM call failed: ${e.message}")
            }
        return result
    }

    /**
     * "Save to Vault" — multi-stage agentic pipeline:
     *
     * Stage 1: LLM classifies data type + extracts medical entities
     * Stage 2: App routes classification → target files, reads their current content
     * Stage 3: LLM formats entry to match target file style
     * Stage 4: App appends to files + logs to Timeline
     */
    private fun sendWithHkPrompt() {
        val inputString = etInput.text.trim().toString()
        val lastAssistant = messages.lastOrNull { it.type == MessageType.ASSISTANT }
        val contextToSave = if (inputString.isNotEmpty()) {
            inputString
        } else if (lastAssistant != null && lastAssistant.content.isNotBlank()) {
            lastAssistant.content
        } else {
            Toast.makeText(this, "Nothing to save — chat or scan first", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isLoadLlmModel) {
            saveHealthRecord(contextToSave)
            etInput.setText("")
            return
        }

        if (inputString.isNotEmpty()) {
            messages.add(Message(inputString, MessageType.USER))
            etInput.setText("")
        }
        messages.add(Message("*Stage 1/3 — Classifying medical data...*", MessageType.ASSISTANT))
        reloadRecycleView()
        etInput.clearFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etInput.windowToken, 0)

        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

        modelScope.launch {
            // ── STAGE 1: Classify + Extract ─────────────────────────────────
            val classifyPrompt = """You are a medical data classifier. Analyze the input and respond with EXACTLY this format:

CATEGORY: [one of: medication, lab, eye, cardio, gi, ortho, skin, immune, neuro, genetics, diet, exercise, inventory, visit, therapy, insurance, not_medical]
SUMMARY: [one-line summary of the medical data]
ENTITIES: [key medical entities — drug names, dosages, test values, diagnoses, etc.]

If the data contains MULTIPLE categories, list them comma-separated in CATEGORY.
If the data is NOT medically relevant at all, use: CATEGORY: not_medical

Examples:
Input: "Doctor prescribed Amoxicillin 500mg 3x daily for sinus infection"
CATEGORY: medication,visit
SUMMARY: New antibiotic prescription for sinus infection
ENTITIES: Amoxicillin 500mg, 3x daily, 7 days, sinus infection

Input: "My grocery list: eggs, milk, bread"
CATEGORY: not_medical
SUMMARY: Non-medical content
ENTITIES: none"""

            val stage1Result = llmCallSilent(classifyPrompt, contextToSave)

            if (stage1Result == null || stage1Result.isBlank()) {
                // LLM failed — fallback to inbox
                saveHealthRecord(contextToSave)
                updateStatus("Saved to inbox (classification unavailable)")
                return@launch
            }

            Log.d(TAG, "HK Stage 1 result: $stage1Result")

            // Parse Stage 1 output
            val categoryLine = stage1Result.lines().firstOrNull {
                it.trimStart().startsWith("CATEGORY:", ignoreCase = true)
            }?.substringAfter(":")?.trim()?.lowercase() ?: ""

            val summaryLine = stage1Result.lines().firstOrNull {
                it.trimStart().startsWith("SUMMARY:", ignoreCase = true)
            }?.substringAfter(":")?.trim() ?: ""

            val entitiesLine = stage1Result.lines().firstOrNull {
                it.trimStart().startsWith("ENTITIES:", ignoreCase = true)
            }?.substringAfter(":")?.trim() ?: ""

            // Check if not medical
            if (categoryLine.contains("not_medical")) {
                updateStatus("No medical data detected — not saved")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "No medical data to file", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // ── STAGE 2: App-side routing ───────────────────────────────────
            updateStatus("*Stage 2/3 — Routing to vault files...*")

            val categories = categoryLine.split(",").map { it.trim() }
            val targetFiles = mutableMapOf<String, File>() // relPath → File

            for (cat in categories) {
                val routes = VAULT_ROUTES[cat] ?: continue
                for (route in routes) {
                    val file = File(healthVaultDir, route)
                    targetFiles[route] = file
                }
            }

            // Always add Timeline for every save
            val timelineFile = findOrCreateTimelineFile(today)
            if (timelineFile != null) {
                val relPath = timelineFile.relativeTo(healthVaultDir).path
                targetFiles[relPath] = timelineFile
            }

            if (targetFiles.isEmpty()) {
                // No recognized category — save to inbox as fallback
                saveHealthRecord(contextToSave)
                updateStatus("Saved to inbox")
                return@launch
            }

            // ── STAGE 3: Format entries for each target file ────────────────
            updateStatus("*Stage 3/3 — Formatting and filing...*")

            var filesWritten = 0
            val filesSummary = mutableListOf<String>()

            for ((relPath, targetFile) in targetFiles) {
                // Read existing file content so LLM can match the style
                // Take header (structure) + tail (recent entries) for best context
                val existingContent = try {
                    if (targetFile.exists()) {
                        val text = targetFile.readText()
                        if (text.length <= 2000) text
                        else text.take(500) + "\n...\n" + text.takeLast(1500)
                    } else ""
                } catch (_: Exception) { "" }

                val isTimeline = relPath.contains("Timeline") || relPath.contains("02_Timeline")

                val formatPrompt = if (isTimeline) {
                    """You are updating a medical timeline. Add a brief dated entry.
Output ONLY the new bullet point to append (nothing else).
Format: - **$today**: [brief outcome]"""
                } else {
                    """You are a medical record keeper updating a health vault file.
The target file is: $relPath

EXISTING FILE CONTENT (tail):
$existingContent

RULES:
- Output ONLY the new content to APPEND to this file (not the whole file).
- Match the formatting style of the existing content.
- If updating a value that already exists, note the previous value with "(Previous: [old_value] on [date])".
- Include today's date: $today
- Be concise and structured. Use markdown."""
                }

                val formatted = llmCallSilent(
                    formatPrompt,
                    "Medical data to file:\nSummary: $summaryLine\nEntities: $entitiesLine\n\nRaw data:\n$contextToSave"
                )

                if (formatted != null && formatted.isNotBlank()) {
                    try {
                        targetFile.parentFile?.mkdirs()
                        if (targetFile.exists()) {
                            targetFile.appendText("\n\n$formatted")
                        } else {
                            targetFile.writeText("# ${targetFile.nameWithoutExtension.replace("_", " ")}\n\n$formatted")
                        }
                        filesWritten++
                        val shortName = targetFile.nameWithoutExtension
                        filesSummary.add(shortName)
                        Log.d(TAG, "HK: Wrote to $relPath")
                    } catch (e: Exception) {
                        Log.e(TAG, "HK: Failed to write $relPath: ${e.message}")
                    }
                }
            }

            // ── STAGE 4: Update UI with summary ────────────────────────────
            val resultMsg = if (filesWritten > 0) {
                "Filed to **$filesWritten** vault file${if (filesWritten != 1) "s" else ""}: ${filesSummary.joinToString(", ")}"
            } else {
                "Could not format entries — raw data saved to inbox"
            }

            if (filesWritten == 0) {
                saveHealthRecord(contextToSave)
            }

            updateStatus(resultMsg)
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    if (filesWritten > 0) "Filed to $filesWritten vault files" else "Saved to inbox",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /** Update the last assistant message in-place */
    private fun updateStatus(text: String) {
        runOnUiThread {
            if (messages.isNotEmpty() && messages.last().type == MessageType.ASSISTANT) {
                messages[messages.size - 1] = Message(text, MessageType.ASSISTANT)
                adapter.notifyItemChanged(messages.size - 1)
                binding.rvChat.scrollToPosition(messages.size - 1)
            }
        }
    }

    /** Find or create the current year's timeline file */
    private fun findOrCreateTimelineFile(today: String): File? {
        if (!::healthVaultDir.isInitialized) return null
        val year = today.substring(0, 4)
        val timelineDir = File(healthVaultDir, "02_Timeline")
        if (!timelineDir.exists()) timelineDir.mkdirs()

        // Look for existing file matching current year
        val existing = timelineDir.listFiles()?.firstOrNull {
            it.name.contains(year) && it.extension == "md"
        }
        if (existing != null) return existing

        // Also check for a generic timeline file
        val generic = File(timelineDir, "Medical_Timeline.md")
        if (generic.exists()) return generic

        // Create new year file
        val newFile = File(timelineDir, "${year}_Health_Timeline.md")
        newFile.writeText("# $year Health Timeline\n")
        return newFile
    }

    private fun getHfToken(model: ModelData, url: String): String? {
        // Replace with your own HuggingFace token if needed for private models
        return null
    }

    private fun onLoadModelSuccess(tip: String) {
        runOnUiThread {
            Toast.makeText(
                this@MainActivity, tip, Toast.LENGTH_SHORT
            ).show()

            // Build multi-model status showing ALL loaded models
            val loadedParts = mutableListOf<String>()
            if (isLoadLlmModel) {
                val llmName = modelList.firstOrNull { it.id == selectModelId && (it.type == "chat" || it.type == "llm") }?.displayName
                loadedParts.add("LLM: ${llmName ?: "loaded"}")
            }
            if (isLoadCVModel) loadedParts.add("OCR")
            if (isLoadVlmModel) loadedParts.add("VLM")
            if (isLoadEmbedderModel) loadedParts.add("EMB")
            if (isLoadAsrModel) loadedParts.add("ASR")
            if (isLoadRerankerModel) loadedParts.add("RNK")
            tvModelStatus.text = if (loadedParts.isNotEmpty()) {
                loadedParts.joinToString(" + ") + " · Ready"
            } else {
                val modelData = modelList.firstOrNull { it.id == selectModelId }
                "${modelData?.displayName ?: selectModelId} · Ready"
            }

            // Scan button always visible — routes to OCR/VLM if loaded, else guidance
            btnAddImage.visibility = View.VISIBLE
            btnAudioRecord.visibility = if (isLoadAsrModel) View.VISIBLE else View.GONE
            btnUnloadModel.visibility = View.VISIBLE
            llLoading.visibility = View.INVISIBLE
            // Stop button works for LLM/VLM streaming
            btnStop.visibility = if (isLoadLlmModel || isLoadVlmModel) View.VISIBLE else View.GONE
        }
    }

    private fun onLoadModelFailed(tip: String) {
        runOnUiThread {
            vTip.visibility = View.GONE

            // Update status
            tvModelStatus.text = "Load failed - Try manual model or check logs"

            // Only check model list if using list-based loading (not manual)
            if (selectModelId.isNotEmpty()) {
                val selectModelData = modelList.firstOrNull { it.id == selectModelId }
                if (selectModelData != null) {
                    val fileName = isModelDownloaded(selectModelData)
                    if (fileName != null) {
                        Toaster.showLong("The \"$fileName\" file is missing. Please download it first.")
                    } else {
                        Toast.makeText(this@MainActivity, "Load failed: $tip", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Model not found in list", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Manual model loading failed
                Toast.makeText(this@MainActivity, "Load failed: $tip", Toast.LENGTH_LONG).show()
            }

            // change UI — keep scan always visible
            btnAddImage.visibility = View.VISIBLE
            btnAudioRecord.visibility = View.GONE
            btnUnloadModel.visibility = View.GONE
            llLoading.visibility = View.INVISIBLE
        }
    }

    private fun hasLoadedModel(): Boolean {
        return isLoadLlmModel || isLoadVlmModel || isLoadEmbedderModel ||
                isLoadRerankerModel || isLoadCVModel || isLoadAsrModel
    }

    /**
     * Helper function to check if model files exist locally
     * @return null if all files exist locally. or file's name which is missing.
     */
    private fun isModelDownloaded(modelData: ModelData): String? {
        val modelDir = modelData.modelDir(this@MainActivity)
        val fileName = modelData.getNonExistModelFile(modelDir)
        val filesExist = fileName == null
        // Sync SharedPreferences with actual file existence
        if (filesExist && !spDownloaded.getBoolean(modelData.id, false)) {
            Log.d(TAG, "Model files found locally for ${modelData.id}, updating SharedPreferences")
            spDownloaded.edit().putBoolean(modelData.id, true).commit()
        }

        return fileName
    }

    /**
     * Detect if running on a Snapdragon/Qualcomm device.
     * Non-Snapdragon devices (Pixel Tensor, Exynos, MediaTek) will crash
     * when loading models with cpu_gpu or npu plugins.
     */
    private fun isSnapdragonDevice(): Boolean {
        val hardware = android.os.Build.HARDWARE.lowercase()
        val board = android.os.Build.BOARD.lowercase()
        val soc = try {
            File("/sys/devices/soc0/machine").readText().trim().lowercase()
        } catch (e: Exception) { "" }
        val socId = try {
            File("/sys/devices/soc0/soc_id").readText().trim()
        } catch (e: Exception) { "" }

        val isQcom = hardware.contains("qcom") ||
                board.contains("taro") || board.contains("kalama") ||
                board.contains("pineapple") || board.contains("sun") ||
                soc.contains("snapdragon") || soc.contains("qrd") ||
                hardware.contains("snapdragon")

        Log.i(TAG, "Device check: HW=$hardware, BOARD=$board, SOC=$soc, SOC_ID=$socId, isQcom=$isQcom")
        return isQcom
    }

    /**
     * Determine if a model file is a VLM (vision-language model) based on name.
     * VLMs need VlmWrapper; LLMs need LlmWrapper.
     */
    private fun isVisionModel(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.contains("vl") ||       // Qwen-VL, Qwen2-VL, Qwen3-VL
               lower.contains("vision") ||    // Llama-3.2-Vision
               lower.contains("omni") ||      // OmniNeural
               lower.contains("mmproj") ||    // mmproj files indicate VLM
               lower.endsWith(".nexa")        // Nexa NPU format (OmniNeural-4B)
    }

    private fun loadManualModel(modelPath: String, pluginId: String, nGpuLayers: Int) {
        modelScope.launch {
            resetLoadState()

            runOnUiThread {
                tvModelStatus.text = "Loading model..."
            }

            // Guard: warn if not Snapdragon and trying NPU/cpu_gpu plugins
            if (!isSnapdragonDevice() && (pluginId == "cpu_gpu" || pluginId == "npu")) {
                Log.w(TAG, "Non-Snapdragon device detected! Plugin '$pluginId' may crash.")
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Non-Snapdragon device. NPU/GPU plugins may fail. Use QDC or mock mode.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                // Continue anyway — let user try, but they've been warned
            }

            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                runOnUiThread {
                    tvModelStatus.text = "Model file not found"
                    Toast.makeText(
                        this@MainActivity,
                        "Model file not found: ${modelFile.name}",
                        Toast.LENGTH_LONG
                    ).show()
                    llLoading.visibility = View.INVISIBLE
                    vTip.visibility = View.GONE
                }
                return@launch
            }

            Log.d(TAG, "Loading manual model:")
            Log.d(TAG, "  - File: ${modelFile.name}")
            Log.d(TAG, "  - Path: ${modelFile.absolutePath}")
            Log.d(TAG, "  - Size: ${modelFile.length()} bytes")
            Log.d(TAG, "  - Plugin: $pluginId")
            Log.d(TAG, "  - GPU Layers: $nGpuLayers")
            Log.d(TAG, "  - Is VLM: ${isVisionModel(modelFile.name)}")

            // Check if this is a vision model → load as VLM
            if (isVisionModel(modelFile.name)) {
                loadManualVlmModel(modelFile, pluginId, nGpuLayers)
                return@launch
            }

            // Otherwise load as LLM (text-only)
            val conf = ModelConfig(
                nCtx = 2048,
                nGpuLayers = nGpuLayers,
                enable_thinking = false,
                npu_lib_folder_path = applicationInfo.nativeLibraryDir,
                npu_model_folder_path = filesDir.absolutePath
            )

            LlmWrapper.builder().llmCreateInput(
                LlmCreateInput(
                    model_name = "",  // Empty for GGUF
                    model_path = modelFile.absolutePath,
                    tokenizer_path = null,  // GGUF has embedded tokenizer
                    config = conf,
                    plugin_id = pluginId
                )
            ).build().onSuccess { wrapper ->
                isLoadLlmModel = true
                llmWrapper = wrapper
                onLoadModelSuccess("LLM loaded: ${modelFile.name}")
                Log.d(TAG, "Manual LLM model loaded successfully")
            }.onFailure { error ->
                Log.e(TAG, "Manual LLM load failed: ${error.message}")
                onLoadModelFailed(error.message.toString())
            }
        }
    }

    /**
     * Load a manual VLM model file. Handles both:
     * - GGUF VLMs (need mmproj file in same directory, plugin_id = "cpu_gpu")
     * - Nexa NPU VLMs (plugin_id = "npu", .nexa format)
     */
    private fun loadManualVlmModel(modelFile: File, pluginId: String, nGpuLayers: Int) {
        modelScope.launch {
            // For GGUF VLMs, look for mmproj file in same directory
            var mmprojPath: String? = null
            if (modelFile.name.endsWith(".gguf", ignoreCase = true)) {
                val parentDir = modelFile.parentFile
                val mmprojFile = parentDir?.listFiles()?.firstOrNull {
                    it.name.contains("mmproj", ignoreCase = true) && it.name.endsWith(".gguf", ignoreCase = true)
                }
                mmprojPath = mmprojFile?.absolutePath
                Log.d(TAG, "VLM mmproj file: ${mmprojPath ?: "NOT FOUND (will attempt without)"}")
            }

            val isNpu = pluginId == "npu"
            val config = if (isNpu) {
                ModelConfig(
                    nCtx = 2048,
                    nThreads = 8,
                    enable_thinking = false,
                    npu_lib_folder_path = applicationInfo.nativeLibraryDir,
                    npu_model_folder_path = modelFile.parentFile?.absolutePath ?: filesDir.absolutePath
                )
            } else {
                ModelConfig(
                    nCtx = 2048,
                    nThreads = 4,
                    nBatch = 1,
                    nUBatch = 1,
                    nGpuLayers = nGpuLayers,
                    enable_thinking = false,
                    npu_lib_folder_path = applicationInfo.nativeLibraryDir,
                    npu_model_folder_path = filesDir.absolutePath
                )
            }

            val vlmCreateInput = VlmCreateInput(
                model_name = if (isNpu) modelFile.nameWithoutExtension else "",
                model_path = modelFile.absolutePath,
                mmproj_path = mmprojPath,
                config = config,
                plugin_id = pluginId
            )

            VlmWrapper.builder()
                .vlmCreateInput(vlmCreateInput)
                .build().onSuccess {
                    isLoadVlmModel = true
                    vlmWrapper = it
                    onLoadModelSuccess("VLM loaded: ${modelFile.name}")
                    Log.d(TAG, "Manual VLM model loaded successfully")
                }.onFailure { error ->
                    Log.e(TAG, "Manual VLM load failed: ${error.message}")
                    // If VLM fails, try falling back to LLM
                    Log.i(TAG, "Attempting LLM fallback...")
                    val llmConf = ModelConfig(
                        nCtx = 2048,
                        nGpuLayers = nGpuLayers,
                        enable_thinking = false,
                        npu_lib_folder_path = applicationInfo.nativeLibraryDir,
                        npu_model_folder_path = filesDir.absolutePath
                    )
                    LlmWrapper.builder().llmCreateInput(
                        LlmCreateInput(
                            model_name = "",
                            model_path = modelFile.absolutePath,
                            tokenizer_path = null,
                            config = llmConf,
                            plugin_id = pluginId
                        )
                    ).build().onSuccess { wrapper ->
                        isLoadLlmModel = true
                        llmWrapper = wrapper
                        onLoadModelSuccess("Loaded as LLM (VLM failed): ${modelFile.name}")
                    }.onFailure { llmError ->
                        Log.e(TAG, "Both VLM and LLM load failed: ${llmError.message}")
                        onLoadModelFailed("VLM: ${error.message}\nLLM fallback: ${llmError.message}")
                    }
                }
        }
    }

    private fun loadModel(selectModelData: ModelData, modelDataPluginId: String, nGpuLayers: Int) {
        modelScope.launch {
            // Multi-model: only unload the specific type being replaced, not all models
            val modelType = selectModelData.getNexaManifest(this@MainActivity)?.ModelType ?: selectModelData.type
            when (modelType) {
                "chat", "llm" -> {
                    if (isLoadLlmModel) { try { llmWrapper.stopStream(); llmWrapper.destroy(); chatList.clear() } catch (_: Exception) {} }
                    isLoadLlmModel = false
                }
                "multimodal", "vlm" -> {
                    if (isLoadVlmModel) { try { vlmWrapper.stopStream(); vlmWrapper.destroy(); vlmChatList.clear() } catch (_: Exception) {} }
                    isLoadVlmModel = false
                }
                "paddleocr", "cv" -> {
                    if (isLoadCVModel) { try { cvWrapper.destroy() } catch (_: Exception) {} }
                    isLoadCVModel = false
                }
                "embedder" -> {
                    if (isLoadEmbedderModel) { try { embedderWrapper?.destroy() } catch (_: Exception) {} }
                    isLoadEmbedderModel = false
                }
                "asr" -> {
                    if (isLoadAsrModel) { try { asrWrapper.destroy() } catch (_: Exception) {} }
                    isLoadAsrModel = false
                }
                "reranker" -> {
                    if (isLoadRerankerModel) { try { rerankerWrapper.destroy() } catch (_: Exception) {} }
                    isLoadRerankerModel = false
                }
            }
            val nexaManifestBean = selectModelData.getNexaManifest(this@MainActivity)
            val pluginId = nexaManifestBean?.PluginId ?: modelDataPluginId

            // Safe model path: for NPU directory-based models, modelFile() is null
            // (no modelUrl), so fall back to model directory path
            val safeModelPath = selectModelData.modelFile(this@MainActivity)?.absolutePath
                ?: selectModelData.modelDir(this@MainActivity).absolutePath

            when (nexaManifestBean?.ModelType ?: selectModelData.type) {
                "chat", "llm" -> {

                    val conf = ModelConfig(
                        nCtx = 1024,
                        nGpuLayers = nGpuLayers,
                        enable_thinking = enableThinking,
                        npu_lib_folder_path = applicationInfo.nativeLibraryDir,
                        npu_model_folder_path = selectModelData.modelDir(this@MainActivity).absolutePath
                    )
                    // Build and initialize LlmWrapper for chat model
                    LlmWrapper.builder().llmCreateInput(
                        LlmCreateInput(
                            model_name = nexaManifestBean?.ModelName ?: "",
                            model_path = safeModelPath,
                            tokenizer_path = selectModelData.tokenFile(this@MainActivity)?.absolutePath,
                            config = conf,
                            plugin_id = pluginId
                        )
                    ).build().onSuccess { wrapper ->
                        isLoadLlmModel = true
                        llmWrapper = wrapper
                        onLoadModelSuccess("llm model loaded")
                    }.onFailure { error ->
                        onLoadModelFailed(error.message.toString())
                    }

                }

                "embedder" -> {
                    // Handle embedder model loading with NPU paths using EmbedderCreateInput
                    // embed-gemma
                    val embedderCreateInput = EmbedderCreateInput(
                        model_name = nexaManifestBean?.ModelName
                            ?: "",  // Model name for NPU plugin
                        model_path = safeModelPath,
                        tokenizer_path = selectModelData.tokenFile(this@MainActivity)?.absolutePath,
                        config = ModelConfig(
                            npu_lib_folder_path = applicationInfo.nativeLibraryDir,
                            npu_model_folder_path = selectModelData.modelDir(this@MainActivity).absolutePath,
                            nGpuLayers = nGpuLayers
                        ),
                        plugin_id = pluginId,
                        device_id = null
                    )

                    EmbedderWrapper.builder()
                        .embedderCreateInput(embedderCreateInput)
                        .build().onSuccess { wrapper ->
                            isLoadEmbedderModel = true
                            embedderWrapper = wrapper
                            onLoadModelSuccess("embedder model loaded")
                        }.onFailure { error ->
                            onLoadModelFailed(error.message.toString())
                        }

                }

                "reranker" -> {
                    // Handle reranker model loading with NPU paths using RerankerCreateInput
                    // jina-v2-rerank-npu
                    val rerankerCreateInput = RerankerCreateInput(
                        model_name = nexaManifestBean?.ModelName
                            ?: "",  // Model name for NPU plugin
                        model_path = safeModelPath,
                        tokenizer_path = selectModelData.tokenFile(this@MainActivity)?.absolutePath,
                        config = ModelConfig(
                            npu_lib_folder_path = applicationInfo.nativeLibraryDir,
                            npu_model_folder_path = selectModelData.modelDir(this@MainActivity).absolutePath,
                            nGpuLayers = nGpuLayers
                        ),
                        plugin_id = pluginId,
                        device_id = null
                    )

                    RerankerWrapper.builder()
                        .rerankerCreateInput(rerankerCreateInput)
                        .build().onSuccess { wrapper ->
                            isLoadRerankerModel = true
                            rerankerWrapper = wrapper
                            onLoadModelSuccess("reranker model loaded")
                        }.onFailure { error ->
                            onLoadModelFailed(error.message.toString())
                        }

                }

                "paddleocr", "cv" -> {
                    // paddleocr-npu
                    val cvCreateInput = CVCreateInput(
                        model_name = nexaManifestBean?.ModelName ?: "",
                        config = CVModelConfig(
                            capabilities = CVCapability.OCR,
                            det_model_path = selectModelData.modelDir(this@MainActivity).absolutePath,
                            rec_model_path = safeModelPath,
                            char_dict_path = selectModelData.modelDir(this@MainActivity).absolutePath,
                            npu_model_folder_path = selectModelData.modelDir(this@MainActivity).absolutePath,
                            npu_lib_folder_path = applicationInfo.nativeLibraryDir
                        ),
                        plugin_id = pluginId
                    )
                    CvWrapper.builder()
                        .createInput(cvCreateInput)
                        .build().onSuccess {
                            isLoadCVModel = true
                            cvWrapper = it
                            onLoadModelSuccess("paddleocr model loaded")
                        }.onFailure { error ->
                            onLoadModelFailed(error.message.toString())
                        }
                }

                "asr" -> {
                    // ADD: Handle ASR model loading
                    // parakeet-tdt-0.6b-v3-npu
                    val asrCreateInput = AsrCreateInput(
                        model_name = nexaManifestBean?.ModelName ?: "",
                        model_path = safeModelPath,
                        config = ModelConfig(
                            npu_lib_folder_path = applicationInfo.nativeLibraryDir,
                            npu_model_folder_path = selectModelData.modelDir(this@MainActivity).absolutePath,
                            nGpuLayers = nGpuLayers
                        ),
                        plugin_id = pluginId
                    )

                    AsrWrapper.builder()
                        .asrCreateInput(asrCreateInput)
                        .build().onSuccess { wrapper ->
                            isLoadAsrModel = true
                            asrWrapper = wrapper
                            onLoadModelSuccess("ASR model loaded")
                        }.onFailure { error ->
                            onLoadModelFailed(error.message.toString())
                        }
                }

                "multimodal", "vlm" -> {
                    // VLM model
                    val isNpuVlm = nexaManifestBean?.PluginId == "npu"
                    val config = if (isNpuVlm) {
                        ModelConfig(
                            nCtx = 2048,
                            nThreads = 8,
                            enable_thinking = false,  // NPU VLM: disable thinking to avoid empty <think> loops
                            npu_lib_folder_path = applicationInfo.nativeLibraryDir,
                            npu_model_folder_path = selectModelData.modelDir(this@MainActivity).absolutePath
                        )
                    } else {
                        ModelConfig(
                            nCtx = 1024,
                            nThreads = 4,
                            nBatch = 1,
                            nUBatch = 1,
                            nGpuLayers = nGpuLayers,
                            enable_thinking = false  // VLM: disable thinking mode
                        )
                    }

                    val vlmCreateInput = VlmCreateInput(
                        model_name = nexaManifestBean?.ModelName ?: "",
                        model_path = safeModelPath,
                        mmproj_path = selectModelData.mmprojTokenFile(this@MainActivity)?.absolutePath,
                        config = config,
                        plugin_id = pluginId
                    )

                    VlmWrapper.builder()
                        .vlmCreateInput(vlmCreateInput)
                        .build().onSuccess {
                            isLoadVlmModel = true
                            vlmWrapper = it
                            onLoadModelSuccess("vlm model loaded")
                        }.onFailure { error ->
                            onLoadModelFailed(error.message.toString())
                        }
                }

                else -> {
                    onLoadModelFailed("model type error")
                }
            }
        }
    }

    private fun downloadModel(selectModelData: ModelData) {
        // Check local files first before SharedPreferences
        val fileName = isModelDownloaded(selectModelData)
        if (fileName == null) {
            Toast.makeText(this@MainActivity, "Model already downloaded", Toast.LENGTH_SHORT)
                .show()
            return
        }
        if (downloadState == DownloadState.DOWNLOADING) {
            Toast.makeText(this@MainActivity, "A download is already in progress", Toast.LENGTH_SHORT)
                .show()
            return
        }
        // Sync selectModelId so post-download auto-load uses the right model
        selectModelId = selectModelData.id
        val pos = modelList.indexOfFirst { it.id == selectModelId }
        if (pos >= 0) spModelList.setSelection(pos)

        downloadState = DownloadState.DOWNLOADING
        downloadingModelData = selectModelData
        llDownloading.visibility = View.VISIBLE
        tvDownloadProgress.text = "0%"
        // Keep screen/CPU alive during download so sleep doesn't interrupt it
        downloadWakeLock = (getSystemService(POWER_SERVICE) as android.os.PowerManager)
            .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "HealthPassport:Download")
            .also { it.acquire(30 * 60 * 1000L /*30min*/) }
        modelScope.launch {
            // Use the passed model data directly (don't re-lookup from selectModelId)
            val unsafeClient = getUnsafeOkHttpClient().build()

            // Track URL mapping for fallback: primary URL -> fallback URL
            val fallbackUrlMap = mutableMapOf<String, String>()
            // Track failed downloads for fallback retry
            val failedDownloads = mutableListOf<DownloadableFileWithFallback>()

            // For NPU models without explicit files list, fetch file list with fallback support
            val filesToDownloadWithFallback: List<DownloadableFileWithFallback> = if (selectModelData.isNpuModel() &&
                selectModelData.files.isNullOrEmpty() &&
                !selectModelData.baseUrl.isNullOrEmpty()) {

                Log.d(TAG, "NPU model detected, fetching file list: ${selectModelData.baseUrl}")

                // Fetch file list with fallback support
                val result = ModelFileListingUtil.listFilesWithFallback(selectModelData.baseUrl!!, unsafeClient)

                if (result.files.isEmpty()) {
                    Log.e(TAG, "Failed to fetch file list for ${selectModelData.id}")
                    runOnUiThread {
                        downloadState = DownloadState.IDLE
                        llDownloading.visibility = View.GONE
                        downloadWakeLock?.let { if (it.isHeld) it.release() }
                        downloadWakeLock = null
                        Toaster.show("Failed to fetch file list.")
                        setupDownloadCompletion?.invoke(false)
                        setupDownloadCompletion = null
                        setupDownloadProgress = null
                    }
                    return@launch
                }

                val useHfUrls = result.source == ModelFileListingUtil.FileListResult.Source.HUGGINGFACE
                Log.d(TAG, "Found ${result.files.size} files from ${result.source}: ${result.files}")

                selectModelData.downloadableFilesWithFallback(
                    selectModelData.modelDir(this@MainActivity),
                    result.files,
                    useHfUrls
                )
            } else {
                // For non-NPU models or models with explicit files, use the original method with fallback
                selectModelData.downloadableFiles(selectModelData.modelDir(this@MainActivity)).withFallbackUrls()
            }

            // Build fallback URL map
            filesToDownloadWithFallback.forEach {
                fallbackUrlMap[it.primaryUrl] = it.fallbackUrl
            }

            // Convert to simple DownloadableFile for initial download attempt
            val filesToDownload = filesToDownloadWithFallback.map {
                DownloadableFile(it.file, it.primaryUrl)
            }

            Log.d(TAG, "filesToDownload: $filesToDownload")
            if (filesToDownload.isEmpty()) throw IllegalArgumentException("No download URL")

            fun getUrlFileSize(client: OkHttpClient, url: String): Long {
                val hostname = try {
                    url.substringAfter("://").substringBefore("/")
                } catch (e: Exception) {
                    "unknown"
                }

                Log.d(TAG, "Requesting file size: $hostname")

                val builder = Request.Builder().url(url).head()
                getHfToken(selectModelData, url)?.let {
                    builder.addHeader("Authorization", "Bearer $it")
                }
                val request = builder.build()
                try {
                    client.newCall(request).execute().use { resp ->
                        val size = resp.header("Content-Length")?.toLongOrNull() ?: 0L
                        Log.d(TAG, "Response: code=${resp.code}, size=$size")
                        return size
                    }
                } catch (e: java.net.UnknownHostException) {
                    Log.e(TAG, "DNS resolution failed for $hostname - Check DNS/network")
                    return 0L
                } catch (e: java.net.SocketTimeoutException) {
                    Log.e(TAG, "Connection timeout to $hostname - Possible firewall/proxy issue")
                    return 0L
                } catch (e: java.net.ConnectException) {
                    Log.e(TAG, "Connection refused by $hostname - Server unreachable")
                    return 0L
                } catch (e: javax.net.ssl.SSLException) {
                    Log.e(TAG, "SSL/TLS error to $hostname - ${e.message}")
                    return 0L
                } catch (e: Exception) {
                    Log.e(TAG, "Network error: ${e.javaClass.simpleName} - ${e.message}")
                    return 0L
                }
            }

            // Try to get file sizes, with fallback to HF if S3 fails
            val fileSizeMap = mutableMapOf<String, Long>()
            filesToDownloadWithFallback.forEach { fileWithFallback ->
                var size = getUrlFileSize(unsafeClient, fileWithFallback.primaryUrl)
                if (size == 0L && fileWithFallback.fallbackUrl != fileWithFallback.primaryUrl) {
                    Log.w(TAG, "Primary URL failed, trying fallback for size: ${fileWithFallback.file.name}")
                    size = getUrlFileSize(unsafeClient, fileWithFallback.fallbackUrl)
                }
                fileSizeMap[fileWithFallback.primaryUrl] = size
            }

            val totalSizes = filesToDownload.map { fileSizeMap[it.url] ?: 0L }
            if (totalSizes.any { it == 0L }) {
                runOnUiThread {
                    downloadState = DownloadState.IDLE
                    llDownloading.visibility = View.GONE
                    downloadWakeLock?.let { if (it.isHeld) it.release() }
                    downloadWakeLock = null
                    Toaster.show("Download failed - could not get file sizes.")
                    setupDownloadCompletion?.invoke(false)
                    setupDownloadCompletion = null
                    setupDownloadProgress = null
                }
                return@launch
            }

            val alreadyDownloaded = mutableMapOf<String, Long>()
            val totalBytes = totalSizes.sum()
            Log.d(TAG, "all model size: $totalBytes")

            val startTime = System.currentTimeMillis()
            var lastProgressTime = 0L
            val progressInterval = 500L

            fun onProgress(
                modelId: String,
                percent: Int,
                downloaded: Long,
                totalBytes: Long,
                etaSec: Long,
                speedStr: String
            ) {
                runOnUiThread {
                    if (100 == percent) {
                        llDownloading.visibility = View.GONE
                        spDownloaded.edit().putBoolean(selectModelId, true).commit()
                        // Write completion marker so getNonExistModelFile knows download is complete
                        try {
                            File(selectModelData.modelDir(this@MainActivity), ".complete").writeText("done")
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not write .complete marker: ${e.message}")
                        }
                        downloadWakeLock?.let { if (it.isHeld) it.release() }
                        downloadWakeLock = null
                        Toaster.show("${downloadingModelData?.displayName} downloaded")
                        downloadState = DownloadState.IDLE
                        // Signal setup flow if active
                        setupDownloadCompletion?.invoke(true)
                        setupDownloadCompletion = null
                        setupDownloadProgress = null
                    } else {
                        tvDownloadProgress.text = "$percent%"
                        // Forward progress to setup flow if active
                        setupDownloadProgress?.invoke(percent)
                    }
                }
            }

            fun reportProgress(force: Boolean = false) {
                val now = System.currentTimeMillis()
                if (force || now - lastProgressTime > progressInterval) {
                    val elapsedMs = now - startTime
                    val downloaded = alreadyDownloaded.values.sum()
                    val percent =
                        if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt() else 0
                    val speedAvg =
                        if (elapsedMs > 0) downloaded / (elapsedMs / 1000.0) else 0.0
                    val etaSec =
                        if (speedAvg > 0) ((totalBytes - downloaded) / speedAvg).toLong() else -1L
                    val speedStr = if (speedAvg > 1024 * 1024) {
                        String.format("%.2f MB/s", speedAvg / (1024 * 1024))
                    } else {
                        String.format("%.1f KB/s", speedAvg / 1024)
                    }
                    onProgress(selectModelId, percent, downloaded, totalBytes, etaSec, speedStr)
                    lastProgressTime = now
                }
            }

            // Function to start download for a list of files
            fun startDownload(
                downloadFiles: List<DownloadableFile>,
                isFallbackAttempt: Boolean = false
            ) {
                if (downloadFiles.isEmpty()) {
                    if (failedDownloads.isEmpty()) {
                        // All downloads complete
                        downloadState = DownloadState.IDLE
                        reportProgress(force = true)
                        onProgress(selectModelId, 100, totalBytes, totalBytes, 0, "0 KB/s")
                    } else {
                        runOnUiThread {
                            downloadState = DownloadState.IDLE
                            llDownloading.visibility = View.GONE
                            downloadWakeLock?.let { if (it.isHeld) it.release() }
                            downloadWakeLock = null
                            Toaster.show("Download failed for some files.")
                            setupDownloadCompletion?.invoke(false)
                            setupDownloadCompletion = null
                            setupDownloadProgress = null
                        }
                    }
                    return
                }

                val queueSet = DownloadContext.QueueSet()
                    .setParentPathFile(downloadFiles[0].file.parentFile)
                    .setMinIntervalMillisCallbackProcess(300)
                val builder = queueSet.commit()

                downloadFiles.forEach { item ->
                    val taskBuilder = DownloadTask.Builder(item.url, item.file)
                    getHfToken(selectModelData, item.url)?.let {
                        taskBuilder.addHeader("Authorization", "Bearer $it")
                    }
                    val task = taskBuilder.build()
                    task.info?.let {
                        alreadyDownloaded[it.url] = it.totalOffset
                    }
                    builder.bindSetTask(task)
                }

                val totalCount = filesToDownload.size
                var currentCount = filesToDownload.size - downloadFiles.size
                val pendingFallbacks = mutableListOf<DownloadableFile>()

                downloadContext = builder.setListener(createDownloadContextListener {}).build()
                downloadContext?.start(
                    createListener1(taskStart = { task, _ ->
                        Log.d(TAG, "download task ${task.id} Start${if (isFallbackAttempt) " (fallback)" else ""}")
                    }, retry = { task, _ ->
                        Log.d(TAG, "download task ${task.id} retry")
                    }, connected = { task, _, _, _ ->
                        Log.d(TAG, "download task ${task.id} connected")
                    }, progress = { task, currentOffset, totalLength ->
                        Log.d(TAG, "download task ${task.id} progress $currentOffset $totalLength")
                        alreadyDownloaded[task.url] = currentOffset
                        reportProgress(true)
                    }) { task, cause, exception, _ ->
                        when(cause) {
                            EndCause.CANCELED -> {
                                // do nothing
                            }

                            EndCause.COMPLETED -> {
                                Log.d(TAG, "download task ${task.id} end")
                                currentCount += 1
                                Log.d(TAG, "download task process currentCount:$currentCount, totalCount:$totalCount")

                                if (currentCount >= totalCount) {
                                    downloadState = DownloadState.IDLE
                                    reportProgress(force = true)
                                    onProgress(selectModelId, 100, totalBytes, totalBytes, 0, "0 KB/s")
                                }
                            }

                            else -> {
                                Log.e(TAG, "download task ${task.id} error: $cause, ${exception?.message}")

                                // Try fallback URL if available and not already a fallback attempt
                                if (!isFallbackAttempt) {
                                    val fallbackUrl = fallbackUrlMap[task.url]
                                    if (fallbackUrl != null && fallbackUrl != task.url && task.file != null) {
                                        Log.w(TAG, "Primary download failed, queuing fallback: ${task.file?.name}")
                                        pendingFallbacks.add(DownloadableFile(task.file!!, fallbackUrl))
                                    } else {
                                        val failedFile = filesToDownloadWithFallback.find { it.primaryUrl == task.url }
                                        if (failedFile != null) {
                                            failedDownloads.add(failedFile)
                                        }
                                    }
                                } else {
                                    val failedFile = filesToDownloadWithFallback.find {
                                        it.primaryUrl == task.url || it.fallbackUrl == task.url
                                    }
                                    if (failedFile != null) {
                                        failedDownloads.add(failedFile)
                                    }
                                }

                                currentCount += 1
                                if (currentCount >= totalCount && pendingFallbacks.isEmpty()) {
                                    if (failedDownloads.isEmpty()) {
                                        downloadState = DownloadState.IDLE
                                        reportProgress(force = true)
                                        onProgress(selectModelId, 100, totalBytes, totalBytes, 0, "0 KB/s")
                                    } else {
                                        runOnUiThread {
                                            downloadState = DownloadState.IDLE
                                            llDownloading.visibility = View.GONE
                                            downloadWakeLock?.let { if (it.isHeld) it.release() }
                                            downloadWakeLock = null
                                            Toaster.show("Download failed for ${failedDownloads.size} file(s).")
                                        }
                                    }
                                } else if (pendingFallbacks.isNotEmpty()) {
                                    Log.d(TAG, "Starting ${pendingFallbacks.size} fallback downloads")
                                    modelScope.launch {
                                        startDownload(pendingFallbacks.toList(), isFallbackAttempt = true)
                                    }
                                    pendingFallbacks.clear()
                                }
                            }
                        }
                    }, true
                )
            }

            // Start initial download with primary URLs
            startDownload(filesToDownload)
        }
    }

    /**
     * Show a model picker bottom sheet with all available models.
     * Shows download status, model type, and quick actions for each model.
     */
    private fun showModelPicker() {
        val sheet = BottomSheetDialog(this, R.style.DarkBottomSheetDialog)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(dp(20), dp(16), dp(20), dp(20))
        }

        // Header
        val header = TextView(this).apply {
            text = "Select Model"
            setTextColor(Color.parseColor("#F2F2F2"))
            textSize = 18f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        container.addView(header)

        val subtitle = TextView(this).apply {
            text = "Multi-model: load LLM + OCR + ASR together for full pipeline"
            setTextColor(Color.parseColor("#808080"))
            textSize = 12f
            setPadding(0, dp(4), 0, dp(12))
        }
        container.addView(subtitle)

        // Group models by type
        val typeOrder = listOf("chat", "llm", "multimodal", "vlm", "paddleocr", "asr", "embedder", "reranker")
        val typeGroups = mapOf(
            "LLM" to modelList.filter { it.type == "chat" || it.type == "llm" },
            "Vision (VLM)" to modelList.filter { it.type == "multimodal" || it.type == "vlm" },
            "OCR" to modelList.filter { it.type == "paddleocr" },
            "Speech (ASR)" to modelList.filter { it.type == "asr" },
            "Embedder" to modelList.filter { it.type == "embedder" },
            "Reranker" to modelList.filter { it.type == "reranker" }
        )

        for ((groupName, models) in typeGroups) {
            if (models.isEmpty()) continue

            // Section header
            val sectionHeader = TextView(this).apply {
                text = groupName.uppercase()
                setTextColor(Color.parseColor("#4D4D4D"))
                textSize = 10f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                letterSpacing = 0.1f
                setPadding(dp(4), dp(12), 0, dp(4))
            }
            container.addView(sectionHeader)

        // Model list within group
        for (model in models) {
            val isDownloaded = isModelDownloaded(model) == null
            // Multi-model: check if THIS specific model type is loaded
            val isLoaded = when (model.type) {
                "chat", "llm" -> isLoadLlmModel && model.id == selectModelId
                "multimodal", "vlm" -> isLoadVlmModel
                "paddleocr" -> isLoadCVModel
                "embedder" -> isLoadEmbedderModel
                "asr" -> isLoadAsrModel
                "reranker" -> isLoadRerankerModel
                else -> false
            }

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(if (isLoaded) Color.parseColor("#0A2E1F") else Color.parseColor("#111111"))

                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = dp(6)
                layoutParams = lp
            }

            // Color-coded type badge
            val typeColor = when (model.type) {
                "multimodal", "vlm" -> "#A855F7"  // purple
                "chat", "llm" -> "#10B981"        // green
                "paddleocr" -> "#3B82F6"           // blue
                "asr" -> "#F59E0B"                 // amber
                "embedder" -> "#22D3EE"            // cyan
                "reranker" -> "#EF4444"            // red
                else -> "#808080"                   // gray
            }
            val typeLabel = when (model.type) {
                "multimodal", "vlm" -> "VLM"
                "chat", "llm" -> "LLM"
                "paddleocr" -> "OCR"
                "asr" -> "ASR"
                "embedder" -> "EMB"
                "reranker" -> "RNK"
                else -> "AI"
            }

            val badgeView = TextView(this).apply {
                text = typeLabel
                textSize = 9f
                setTextColor(Color.parseColor(typeColor))
                gravity = android.view.Gravity.CENTER
                setPadding(dp(6), dp(2), dp(6), dp(2))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#1A${typeColor.removePrefix("#")}"))
                    cornerRadius = dp(4).toFloat()
                }
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, dp(10), 0)
                layoutParams = lp
            }
            card.addView(badgeView)

            // Model info column
            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameView = TextView(this).apply {
                text = model.displayName
                setTextColor(Color.parseColor("#F2F2F2"))
                textSize = 13f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }
            infoCol.addView(nameView)

            val statusText = when {
                isLoaded -> "● Loaded"
                isDownloaded -> "Downloaded"
                else -> "Not downloaded"
            }
            val statusColor = when {
                isLoaded -> "#10B981"
                isDownloaded -> "#808080"
                else -> "#4D4D4D"
            }
            val statusView = TextView(this).apply {
                text = statusText
                setTextColor(Color.parseColor(statusColor))
                textSize = 11f
            }
            infoCol.addView(statusView)
            card.addView(infoCol)

            // Action button
            val actionBtn = Button(this).apply {
                val btnLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(30))
                layoutParams = btnLp
                setPadding(dp(12), 0, dp(12), 0)
                textSize = 11f
                isAllCaps = false
                minimumWidth = 0
                minWidth = 0
                minimumHeight = 0
                minHeight = 0

                when {
                    isLoaded -> {
                        text = "Loaded"
                        setTextColor(Color.parseColor("#10B981"))
                        setBackgroundColor(Color.TRANSPARENT)
                        isEnabled = false
                    }
                    isDownloaded -> {
                        text = "Load"
                        setTextColor(Color.parseColor("#F2F2F2"))
                        setBackgroundResource(R.drawable.btn_rounded_border)
                    }
                    else -> {
                        text = "Download"
                        setTextColor(Color.parseColor("#22D3EE"))
                        setBackgroundResource(R.drawable.btn_rounded_border)
                    }
                }
            }

            actionBtn.setOnClickListener {
                sheet.dismiss()
                selectModelId = model.id
                // Programmatically select in hidden spinner to keep state in sync
                val pos = modelList.indexOfFirst { it.id == model.id }
                if (pos >= 0) spModelList.setSelection(pos)

                if (isDownloaded) {
                    // Multi-model: load directly (replaces same-type model if already loaded)
                    btnLoadModel.performClick()
                } else {
                    // Download the model directly (don't use btnDownload.performClick which reopens picker)
                    downloadModel(model)
                }
            }

            card.addView(actionBtn)
            container.addView(card)
        }
        } // end typeGroups loop

        // Close button
        val closeBtn = Button(this).apply {
            text = "Close"
            setTextColor(Color.parseColor("#808080"))
            setBackgroundResource(R.drawable.btn_rounded_border)
            isAllCaps = false
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40))
            lp.topMargin = dp(12)
            layoutParams = lp
        }
        closeBtn.setOnClickListener { sheet.dismiss() }
        container.addView(closeBtn)

        sheet.setContentView(container)
        sheet.show()
    }

    private fun setListeners() {

        btnAddImage.setOnClickListener {
            showPopupMenu(it)
        }

        btnAudioRecord.setOnClickListener {
            startRecord()
        }

        // Save to Vault — routes input through LLM with HK (housekeeping) system prompt
        btnSaveVault.setOnClickListener {
            sendWithHkPrompt()
        }

        // New Chat button — clear with confirmation
        btnNewChat.setOnClickListener {
            if (messages.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(this, R.style.DarkBottomSheetDialog)
                .setTitle("New Chat")
                .setMessage("Clear this conversation?")
                .setPositiveButton("Clear") { _, _ ->
                    clearHistory()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnSelectModelFile.setOnClickListener {
            openModelFilePicker()
        }

        btnBrowseFiles.setOnClickListener {
            browseHealthFiles()
        }

        // Quick-action buttons
        findViewById<Button>(R.id.btn_vault_quick).setOnClickListener {
            browseHealthFiles()
        }
        findViewById<Button>(R.id.btn_voice_quick).setOnClickListener {
            if (isLoadAsrModel) {
                startRecord()
            } else {
                Toast.makeText(this, "Voice model loading... please wait", Toast.LENGTH_SHORT).show()
            }
        }

        /**
         * Step 3. download model
         */
        binding.btnCancelDownload.setOnClickListener {
            downloadContext?.stop()
            tvDownloadProgress.text = "0%"
            downloadingModelData?.downloadableFiles(downloadingModelData!!.modelDir(this))
                ?.forEach {
                    it.file.delete()
                }
            binding.btnDismissDownload.performClick()
        }
        binding.btnRetryDownload.setOnClickListener {
            downloadContext?.stop()
            downloadState = DownloadState.IDLE
            downloadModel(downloadingModelData!!)
        }
        binding.btnDismissDownload.setOnClickListener {
            binding.llDownloading.visibility = View.GONE
        }
        btnDownload.setOnClickListener {
            // Open model picker for easy model switching
            showModelPicker()
        }
        // Long-press Download to force-download current model
        btnDownload.setOnLongClickListener {
            // Guard: if spinner was never shown, default to first model
            if (selectModelId.isEmpty() && modelList.isNotEmpty()) {
                selectModelId = modelList[0].id
            }
            if (downloadState == DownloadState.DOWNLOADING) {
                if (downloadingModelData?.id == selectModelId) {
                    binding.llDownloading.visibility = View.VISIBLE
                } else {
                    Toaster.show("${downloadingModelData?.displayName} is currently downloading.")
                }
                return@setOnLongClickListener true
            }
            val selectModelData = modelList.first { it.id == selectModelId }
            downloadModel(selectModelData)
            true
        }
        /**
         * Step 4. load model
         */
        btnLoadModel.setOnClickListener {
            // Check if manual model file is selected
            if (manualModelFilePath != null) {
                // Manual model loading can replace an existing model
                vTip.visibility = View.VISIBLE
                llLoading.visibility = View.VISIBLE

                // Show plugin selection dialog for manual model
                val dialogBinding = DialogSelectPluginIdBinding.inflate(layoutInflater)
                dialogBinding.rbCpu.visibility = View.VISIBLE
                dialogBinding.rbCpu.text = "CPU"
                dialogBinding.rbGpu.visibility = View.VISIBLE
                dialogBinding.rbNpu.visibility = View.VISIBLE
                // Default: NPU on Snapdragon, CPU otherwise
                var selectedPluginId = if (isSnapdragonDevice()) "npu" else "cpu"
                if (isSnapdragonDevice()) dialogBinding.rbNpu.isChecked = true
                else dialogBinding.rbCpu.isChecked = true
                var nGpuLayers = 0
                dialogBinding.llGpuLayers.visibility = View.GONE
                dialogBinding.etGpuLayers.setText("35")

                dialogBinding.rgSelectPluginId.setOnCheckedChangeListener { group, checkedId ->
                    selectedPluginId = when (checkedId) {
                        R.id.rb_cpu -> "cpu"
                        R.id.rb_gpu -> "gpu"
                        R.id.rb_npu -> "npu"
                        else -> "cpu"
                    }
                    dialogBinding.llGpuLayers.visibility =
                        if (checkedId == R.id.rb_gpu) View.VISIBLE else View.GONE
                }

                val dialogOnClickListener = object : CustomDialogInterface.OnClickListener() {
                    override fun onClick(dialog: DialogInterface?, which: Int) {
                        if (dialogBinding.llGpuLayers.visibility == View.VISIBLE) {
                            val layers = (dialogBinding.etGpuLayers.text.toString().toIntOrNull() ?: 0)
                                .coerceIn(1, 99)
                            if (layers == 0) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "nGpuLayers min value is 1",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return
                            }
                            nGpuLayers = layers
                        }

                        when (which) {
                            DialogInterface.BUTTON_POSITIVE -> {
                                dialog?.dismiss()
                                loadManualModel(manualModelFilePath!!, selectedPluginId, nGpuLayers)
                            }
                            DialogInterface.BUTTON_NEGATIVE -> {
                                llLoading.visibility = View.INVISIBLE
                                vTip.visibility = View.GONE
                            }
                        }
                    }
                }

                val alertDialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
                    .setView(dialogBinding.root)
                    .setNegativeButton("Cancel", dialogOnClickListener)
                    .setPositiveButton("Load", dialogOnClickListener)
                    .setCancelable(false)
                    .create()
                alertDialog.show()
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#10b981"))
                alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.parseColor("#808080"))
                dialogOnClickListener.resetPositiveButton(alertDialog)
                return@setOnClickListener
            }

            // Normal flow - load from model list
            if (selectModelId.isEmpty()) {
                Toast.makeText(this@MainActivity, "Please select a model from list or use 'Select Model File'", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectModelData = modelList.firstOrNull { it.id == selectModelId }
            if (selectModelData == null) {
                Toast.makeText(this@MainActivity, "Model not found in list", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Log.d(TAG, "current select model data:$selectModelData")
            // Multi-model: allow loading additional models without unloading first

            // Check if model files exist locally before attempting to load
            val fileName = isModelDownloaded(selectModelData)
            if (fileName != null) {
                Toaster.showLong("The \"$fileName\" file is missing. Please download it first.")
                return@setOnClickListener
            }

            vTip.visibility = View.VISIBLE
            llLoading.visibility = View.VISIBLE

            val supportPluginIds = selectModelData.getSupportPluginIds()
            Log.d(TAG, "support plugin_id:$supportPluginIds")
            var modelDataPluginId = "cpu"
            var nGpuLayers = 0

            // ── Smart default: Snapdragon → NPU, else CPU ──
            val isSnapdragon = isSnapdragonDevice()
            if (isSnapdragon && supportPluginIds.contains("npu")) {
                // Snapdragon with NPU support — skip dialog, load directly with NPU
                modelDataPluginId = "npu"
                Log.d(TAG, "Snapdragon detected, auto-selecting NPU for ${selectModelData.id}")
                Toast.makeText(this@MainActivity, "Loading with NPU (Snapdragon detected)", Toast.LENGTH_SHORT).show()
                loadModel(selectModelData, modelDataPluginId, 0)
            } else if (supportPluginIds.size > 1) {
                val dialogBinding = DialogSelectPluginIdBinding.inflate(layoutInflater)
                supportPluginIds.forEach {
                    when (it) {
                        "cpu" -> {
                            dialogBinding.rbCpu.visibility = View.VISIBLE
                            dialogBinding.rbCpu.text = "CPU"
                            dialogBinding.rbCpu.isChecked = true
                        }

                        "gpu" -> {
                            dialogBinding.rbGpu.visibility = View.VISIBLE
                            dialogBinding.rbGpu.text = "GPU"
                        }

                        "npu" -> {
                            dialogBinding.rbNpu.visibility = View.VISIBLE
                            dialogBinding.rbNpu.text = "NPU"
                            dialogBinding.rbNpu.isChecked = true
                        }
                    }
                }
                // Hide GPU layers by default — only show when GPU selected
                dialogBinding.llGpuLayers.visibility = View.GONE
                dialogBinding.etGpuLayers.setText("35")  // Safe default instead of 999
                dialogBinding.rgSelectPluginId.setOnCheckedChangeListener { group, checkedId ->
                    dialogBinding.llGpuLayers.visibility =
                        if (checkedId == R.id.rb_gpu) View.VISIBLE else View.GONE
                }

                val dialogOnClickListener = object : CustomDialogInterface.OnClickListener() {
                    override fun onClick(
                        dialog: DialogInterface?,
                        which: Int
                    ) {
                        // Capture selected plugin ID from radio buttons
                        modelDataPluginId = when (dialogBinding.rgSelectPluginId.checkedRadioButtonId) {
                            R.id.rb_cpu -> "cpu"
                            R.id.rb_gpu -> "gpu"
                            R.id.rb_npu -> "npu"
                            else -> "cpu"
                        }

                        nGpuLayers = 0
                        if (dialogBinding.llGpuLayers.visibility == View.VISIBLE) {
                            nGpuLayers = (dialogBinding.etGpuLayers.text.toString().toIntOrNull() ?: 0)
                                .coerceIn(1, 99)  // Clamp to safe range
                            if (nGpuLayers == 0) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "nGpuLayers min value is 1",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return
                            }
                        }
                        when (which) {
                            DialogInterface.BUTTON_POSITIVE -> {
                                dialog?.dismiss()
                                loadModel(selectModelData, modelDataPluginId, nGpuLayers)
                            }

                            DialogInterface.BUTTON_NEGATIVE -> {
                                llLoading.visibility = View.INVISIBLE
                                vTip.visibility = View.GONE
                            }
                        }
                    }

                }
                val alertDialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
                    .setView(dialogBinding.root)
                    .setNegativeButton("Cancel", dialogOnClickListener)
                    .setPositiveButton("Load", dialogOnClickListener)
                    .setCancelable(false)
                    .create()
                alertDialog.show()
                // Style dialog buttons to match app theme
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#10b981"))
                alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.parseColor("#808080"))
                alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                dialogOnClickListener.resetPositiveButton(alertDialog)
            } else {
                // Single plugin available - use it directly
                modelDataPluginId = supportPluginIds.firstOrNull() ?: "cpu"
                loadModel(selectModelData, modelDataPluginId, nGpuLayers)
            }
        }

        /**
         * Step 5. send message — Smart Pipeline Routing
         *
         * Architecture:
         * - Qwen3-4B / Llama (LLM) = center stage: text Q&A with RAG from health vault
         * - PaddleOCR = document scanner: image → extracted text → saved to vault
         * - OmniNeural VLM = vision fallback: for images OCR can't handle (X-rays, photos)
         * - EmbedGemma = memory indexer: embeds vault docs for semantic search
         * - Parakeet ASR = speech input: audio → text
         *
         * Smart routing:
         * - Image + OCR loaded → OCR extract → if LLM also loaded, auto-analyze
         * - Image + VLM loaded (no OCR) → VLM process directly
         * - Text + LLM loaded → RAG query with vault context
         * - Audio + ASR loaded → transcribe → if LLM also loaded, auto-analyze
         * - Multiple models loaded → pick the best pipeline automatically
         */
        btnAskDoctor.setOnClickListener {
            // No model loaded at all → fallback modes
            if (!hasLoadedModel()) {
                if (savedImageFiles.isNotEmpty()) {
                    // Mock scan demo
                    messages.add(Message("", MessageType.IMAGES, savedImageFiles.map { it }))
                    reloadRecycleView()
                    clearImages()
                    runMockScanDemo()
                    etInput.setText("")
                } else {
                    // Preloaded RAG mode
                    val inputString = etInput.text.trim().toString()
                    if (inputString.isNotEmpty()) {
                        messages.add(Message(inputString, MessageType.USER))
                        reloadRecycleView()
                        etInput.setText("")
                        etInput.clearFocus()
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(etInput.windowToken, 0)
                        if (!handlePreloadedQuery(inputString)) {
                            streamResponseToChat("I can look up your **eyes**, **medications**, **timeline**, or give a **health summary**.\n\n*Load a model for free-form responses.*")
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Ask about your health records", Toast.LENGTH_SHORT).show()
                    }
                }
                return@setOnClickListener
            }

            // Show images in chat if present
            if (savedImageFiles.isNotEmpty()) {
                messages.add(Message("", MessageType.IMAGES, savedImageFiles.map { it }))
                reloadRecycleView()
            }

            var inputString = etInput.text.trim().toString()
            etInput.setText("")
            etInput.clearFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(etInput.windowToken, 0)

            val hasImages = savedImageFiles.isNotEmpty()
            val hasAudio = audioFile != null

            // Auto-prompt for image-only sends
            if (inputString.isEmpty() && hasImages) {
                inputString = "Extract all text and medical data from this image."
            }
            if (inputString.isEmpty() && !hasImages && !hasAudio) {
                Toast.makeText(this@MainActivity, "Type a message or attach a photo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (inputString.isNotEmpty()) {
                messages.add(Message(inputString, MessageType.USER))
                reloadRecycleView()
            }

            // ── SMART ROUTING ──
            modelScope.launch {
                lastTokenForRepeatCheck = ""
                repeatTokenCount = 0
                streamStopRequested = false
                lastUiUpdateMs = 0L
                val sb = StringBuilder()

                // ── AUDIO PATH: ASR → transcribe → optionally feed to LLM ──
                if (hasAudio && isLoadAsrModel) {
                    val audioFilePath = audioFile!!.absolutePath
                    audioFile = null
                    asrWrapper.transcribe(AsrTranscribeInput(audioFilePath, "en", null))
                        .onSuccess { transcription ->
                            val transcript = transcription.result.transcript ?: ""
                            runOnUiThread {
                                messages.add(Message("**Transcription (ASR)**\n\n$transcript\n\n*on-device speech recognition*", MessageType.ASSISTANT))
                                reloadRecycleView()
                            }
                            // If LLM also loaded, auto-analyze the transcript
                            if (isLoadLlmModel && transcript.isNotBlank()) {
                                // Auto-save raw transcript before analysis
                                val record = "## Audio Transcription\n**Date:** ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date())}\n\n$transcript"
                                saveHealthRecord(record)

                                val asrQuery = "Analyze this transcribed note: $transcript"
                                val ragPrompt = buildRagPrompt(asrQuery)
                                chatList.add(ChatMessage(role = "user", asrQuery))
                                val ragChatList = chatList.dropLast(1).toMutableList()
                                ragChatList.add(ChatMessage("user", ragPrompt))
                                llmWrapper.applyChatTemplate(ragChatList.toTypedArray(), null, enableThinking)
                                    .onSuccess { templateOutput ->
                                        val asrSb = StringBuilder()
                                        llmWrapper.generateStreamFlow(
                                            templateOutput.formattedText,
                                            GenerationConfigSample().toGenerationConfig(null)
                                        ).collect { handleResult(asrSb, it) }
                                    }
                            } else if (transcript.isNotBlank()) {
                                // ASR only, no LLM — auto-save and hint user
                                val record = "## Audio Transcription\n**Date:** ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date())}\n\n$transcript"
                                saveHealthRecord(record)
                                runOnUiThread {
                                    Toast.makeText(this@MainActivity, "Transcript saved. Tap Save to Vault to organize it.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }.onFailure { error ->
                            runOnUiThread {
                                messages.add(Message("ASR Error: ${error.message}", MessageType.PROFILE))
                                reloadRecycleView()
                            }
                        }
                    clearImages()
                    return@launch
                }

                // ── IMAGE PATH: OCR or VLM ──
                if (hasImages) {
                    // Priority 1: PaddleOCR (best for documents)
                    if (isLoadCVModel) {
                        val imagePath = savedImageFiles.last().absolutePath
                        val imagesCopy = savedImageFiles.toList()
                        clearImages()
                        cvWrapper.infer(imagePath).onSuccess { results ->
                            val ocrLines = results.map { it.text }.toList()
                            val fullText = ocrLines.joinToString(separator = "\n")
                            runOnUiThread {
                                val content = "**OCR Extraction (PaddleOCR)**\n\n```\n$fullText\n```\n\n*${ocrLines.size} text regions detected*"
                                messages.add(Message(content, MessageType.ASSISTANT))
                                reloadRecycleView()
                            }
                            // Auto-save to health vault
                            if (fullText.isNotBlank()) {
                                val record = "## OCR Extraction\n**Date:** ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date())}\n\n$fullText"
                                saveHealthRecord(record)
                            }
                            // ── OCR → LLM pipeline: if LLM is also loaded, auto-analyze ──
                            if (isLoadLlmModel && fullText.isNotBlank()) {
                                runOnUiThread {
                                    messages.add(Message("*Analyzing with LLM...*", MessageType.ASSISTANT))
                                    reloadRecycleView()
                                }
                                val analysisPrompt = "Analyze this medical document extracted via OCR. Identify document type, key findings, medications, and action items:\n\n$fullText"
                                val ragPrompt = buildRagPrompt(analysisPrompt)
                                chatList.add(ChatMessage(role = "user", analysisPrompt))
                                val ragChatList = chatList.dropLast(1).toMutableList()
                                ragChatList.add(ChatMessage("user", ragPrompt))
                                llmWrapper.applyChatTemplate(ragChatList.toTypedArray(), null, enableThinking)
                                    .onSuccess { templateOutput ->
                                        val ocrSb = StringBuilder()
                                        llmWrapper.generateStreamFlow(
                                            templateOutput.formattedText,
                                            GenerationConfigSample().toGenerationConfig(null)
                                        ).collect { handleResult(ocrSb, it) }
                                    }.onFailure { error ->
                                        runOnUiThread {
                                            messages.add(Message("LLM analysis error: ${error.message}", MessageType.PROFILE))
                                            reloadRecycleView()
                                        }
                                    }
                            } else if (fullText.isNotBlank()) {
                                // OCR only, no LLM — already auto-saved raw text
                            }
                        }.onFailure { error ->
                            runOnUiThread {
                                messages.add(Message("OCR Error: $error", MessageType.PROFILE))
                                reloadRecycleView()
                            }
                        }
                        return@launch
                    }

                    // Priority 2: VLM (for non-document images, X-rays, photos)
                    if (isLoadVlmModel) {
                        val contents = savedImageFiles.map { VlmContent("image", it.absolutePath) }.toMutableList()
                        audioFile?.let { contents.add(VlmContent("audio", it.absolutePath)) }
                        contents.add(VlmContent("text", inputString))
                        audioFile = null
                        clearImages()
                        val sendMsg = VlmChatMessage(role = "user", contents = contents)
                        vlmChatList.add(sendMsg)
                        vlmWrapper.applyChatTemplate(vlmChatList.toTypedArray(), null, enableThinking)
                            .onSuccess { result ->
                                val baseConfig = GenerationConfigSample().toGenerationConfig(null)
                                val configWithMedia = vlmWrapper.injectMediaPathsToConfig(vlmChatList.toTypedArray(), baseConfig)
                                vlmWrapper.generateStreamFlow(inputString, configWithMedia)
                                    .collect { handleResult(sb, it) }
                            }.onFailure { error ->
                                runOnUiThread {
                                    messages.add(Message("VLM Error: ${error.message}", MessageType.PROFILE))
                                    reloadRecycleView()
                                }
                            }
                        return@launch
                    }

                    // No image-capable model loaded → try auto-loading VLM
                    val vlmModel = modelList.firstOrNull { it.id == "OmniNeural-4B" }
                    if (vlmModel != null && isModelDownloaded(vlmModel) == null) {
                        clearImages()
                        runOnUiThread {
                            streamResponseToChat(
                                "**Loading Vision model (OmniNeural-4B)...**\n\n" +
                                "Please re-attach your image after loading completes."
                            )
                            selectModelId = vlmModel.id
                            val pos = modelList.indexOfFirst { it.id == selectModelId }
                            if (pos >= 0) spModelList.setSelection(pos)
                            btnLoadModel.performClick()
                        }
                    } else {
                        clearImages()
                        runOnUiThread {
                            streamResponseToChat(
                                "**No image model available.**\n\n" +
                                "Download **PaddleOCR** (documents) or **OmniNeural VLM** (photos/X-rays) from the model picker."
                            )
                        }
                    }
                    return@launch
                }

                // ── TEXT-ONLY PATH ──
                // Priority 1: LLM with RAG (the "brain")
                if (isLoadLlmModel) {
                    val ragPrompt = buildRagPrompt(inputString)
                    Log.d(TAG, "RAG prompt (${ragPrompt.length} chars): ${ragPrompt.take(200)}...")
                    // Store bare query in history — vault context stays out of chatList
                    // so it's re-fetched fresh each call and doesn't bloat context window
                    chatList.add(ChatMessage(role = "user", inputString))
                    val ragChatList = chatList.dropLast(1).toMutableList()
                    ragChatList.add(ChatMessage("user", ragPrompt))
                    llmWrapper.applyChatTemplate(ragChatList.toTypedArray(), null, enableThinking)
                        .onSuccess { templateOutput ->
                            llmWrapper.generateStreamFlow(
                                templateOutput.formattedText,
                                GenerationConfigSample().toGenerationConfig(null)
                            ).collect { handleResult(sb, it) }
                        }.onFailure { error ->
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, error.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    clearImages()
                    return@launch
                }

                // Priority 2: Embedder (show embedding stats — utility mode)
                if (isLoadEmbedderModel) {
                    val texts = inputString.split("|").map { it.trim() }.toTypedArray()
                    embedderWrapper!!.embed(texts, EmbeddingConfig()).onSuccess { embedResult ->
                        runOnUiThread {
                            val result = StringBuilder()
                            val allEmbeddings = embedResult.embeddings
                            val embeddingDim = allEmbeddings.size / texts.size
                            texts.forEachIndexed { idx, text ->
                                val start = idx * embeddingDim
                                val end = start + embeddingDim
                                val embedding = allEmbeddings.slice(start until end)
                                val mean = embedding.average()
                                result.append("\"$text\" → ${embeddingDim}d, mean=${"%.4f".format(mean)}\n")
                            }
                            messages.add(Message(result.toString(), MessageType.ASSISTANT))
                            reloadRecycleView()
                        }
                    }.onFailure { error ->
                        runOnUiThread {
                            messages.add(Message("Embedder Error: ${error.message}", MessageType.PROFILE))
                            reloadRecycleView()
                        }
                    }
                    clearImages()
                    return@launch
                }

                // Priority 3: Reranker (utility mode)
                if (isLoadRerankerModel) {
                    val query = inputString.split("\n")[0]
                    val documents = inputString.split("\n").drop(1).toTypedArray()
                    rerankerWrapper.rerank(query, documents, RerankConfig())
                        .onSuccess { rerankerResult ->
                            runOnUiThread {
                                val result = StringBuilder("Rerank Results:\n")
                                rerankerResult.scores?.withIndex()?.sortedByDescending { it.value }
                                    ?.forEach { (idx, score) ->
                                        result.append("${idx + 1}. ${"%.4f".format(score)}: ${documents[idx]}\n")
                                    }
                                messages.add(Message(result.toString(), MessageType.ASSISTANT))
                                reloadRecycleView()
                            }
                        }.onFailure { error ->
                            runOnUiThread {
                                messages.add(Message("Error: ${error.message}", MessageType.PROFILE))
                                reloadRecycleView()
                            }
                        }
                    clearImages()
                    return@launch
                }

                // VLM without image → redirect
                if (isLoadVlmModel) {
                    runOnUiThread {
                        messages.add(Message("VLM is a vision model — attach an image first.\nFor text Q&A, also load an LLM (Qwen3-4B).", MessageType.ASSISTANT))
                        reloadRecycleView()
                    }
                    clearImages()
                    return@launch
                }

                // OCR without image → redirect
                if (isLoadCVModel) {
                    runOnUiThread {
                        messages.add(Message("OCR processes images — attach a document photo first.\nFor text Q&A, also load an LLM (Qwen3-4B).", MessageType.ASSISTANT))
                        reloadRecycleView()
                    }
                    clearImages()
                    return@launch
                }

                clearImages()
            }
        }

        /**
         * Step 6. others
         */
        btnUnloadModel.setOnClickListener {
            if (!hasLoadedModel()) {
                Toast.makeText(this@MainActivity, "model not loaded", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Unload ALL loaded models
            val handleUnloadResult = fun(result: Int) {
                resetLoadState()
                runOnUiThread {
                    vTip.visibility = View.GONE
                    btnUnloadModel.visibility = View.GONE
                    btnStop.visibility = View.GONE
                    btnAddImage.visibility = View.VISIBLE
                    btnAudioRecord.visibility = View.GONE

                    // Update status
                    tvModelStatus.text = "No model loaded - Select model file in advanced mode"

                    Toast.makeText(
                        this@MainActivity, if (result == 0) {
                            "unload success"
                        } else {
                            "unload failed and error code: $result"
                        }, Toast.LENGTH_SHORT
                    ).show()
                }
            }
            modelScope.launch {
                // Multi-model: unload ALL loaded models
                try {
                    if (isLoadVlmModel) { vlmWrapper.stopStream(); vlmWrapper.destroy(); vlmChatList.clear() }
                    if (isLoadLlmModel) { llmWrapper.stopStream(); llmWrapper.destroy(); chatList.clear() }
                    if (isLoadCVModel) { cvWrapper.destroy() }
                    if (isLoadEmbedderModel) { embedderWrapper?.destroy() }
                    if (isLoadAsrModel) { asrWrapper.destroy() }
                    if (isLoadRerankerModel) { rerankerWrapper.destroy() }
                } catch (e: Exception) {
                    Log.w(TAG, "Error during unload: ${e.message}")
                }
                handleUnloadResult(0)
            }
        }
        btnStop.setOnClickListener {
            if (!hasLoadedModel()) {
                Toast.makeText(
                    this@MainActivity,
                    "model not loaded",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            // MODIFY: Stop button only works for LLM/VLM (not embedder/reranker)
            if (isLoadEmbedderModel || isLoadRerankerModel || isLoadAsrModel || isLoadCVModel) {
                Toast.makeText(
                    this@MainActivity,
                    "Stop not applicable for embedder/reranker/asr/cv",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            // Stop streaming
            modelScope.launch {
                if (isLoadVlmModel) {
                    vlmWrapper.stopStream()
                } else if (isLoadLlmModel) {
                    llmWrapper.stopStream()
                }
            }
        }
    }

    fun handleResult(sb: StringBuilder, streamResult: LlmStreamResult) {
        when (streamResult) {
            is LlmStreamResult.Token -> {
                sb.append(streamResult.text)

                // --- Garbage detection: stop degenerate outputs ("Reactive Reactive..." loops) ---
                val token = streamResult.text.trim()
                if (token.isNotEmpty()) {
                    if (token == lastTokenForRepeatCheck) {
                        repeatTokenCount++
                    } else {
                        lastTokenForRepeatCheck = token
                        repeatTokenCount = 1
                    }
                }
                if (repeatTokenCount >= MAX_REPEAT_TOKENS && !streamStopRequested) {
                    streamStopRequested = true
                    Log.w(TAG, "Garbage output detected: '$token' repeated $repeatTokenCount times. Stopping stream.")
                    modelScope.launch {
                        try {
                            if (isLoadVlmModel) vlmWrapper.stopStream()
                            else if (isLoadLlmModel) llmWrapper.stopStream()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error stopping stream: ${e.message}")
                        }
                    }
                    runOnUiThread {
                        val errorMsg = "Model produced repetitive output and was stopped.\n\n" +
                            "**Try:**\n" +
                            "- Clear chat and retry\n" +
                            "- Use a different model (Qwen3-4B LLM recommended)\n" +
                            "- For document text: use PaddleOCR instead"
                        val size = messages.size
                        messages[size - 1] = Message(errorMsg, MessageType.ASSISTANT)
                        adapter.notifyItemChanged(size - 1)
                    }
                    return
                }

                // --- Throttled UI update to prevent screen flickering ---
                val now = System.currentTimeMillis()
                if (now - lastUiUpdateMs >= UI_UPDATE_INTERVAL_MS) {
                    lastUiUpdateMs = now
                    runOnUiThread {
                        // Strip <think>...</think> tags from display
                        val displayText = sb.toString()
                            .replace(thinkTagRegex, "")
                            .replace(openThinkTagRegex, "")
                            .trimStart('\n', ' ')
                        Message(displayText, MessageType.ASSISTANT).let { lastMsg ->
                            val size = messages.size
                            messages[size - 1].let { msg ->
                                if (msg.type != MessageType.ASSISTANT) {
                                    messages.add(lastMsg)
                                } else {
                                    messages[size - 1] = lastMsg
                                }
                            }
                        }
                        adapter.notifyItemChanged(messages.size - 1)
                    }
                }
                Log.d(TAG, "Token: ${streamResult.text}")
            }

            is LlmStreamResult.Completed -> {
                // Reset garbage detection state
                lastTokenForRepeatCheck = ""
                repeatTokenCount = 0
                streamStopRequested = false
                lastUiUpdateMs = 0L

                // Strip think tags from final content
                val cleanContent = sb.toString()
                    .replace(thinkTagRegex, "")
                    .replace(openThinkTagRegex, "")
                    .trimStart('\n', ' ')

                if (isLoadVlmModel) {
                    vlmChatList.add(
                        VlmChatMessage(
                            "assistant",
                            listOf(VlmContent("text", cleanContent))
                        )
                    )
                    // Store VLM extraction for cross-model pipeline
                    lastVlmExtraction = cleanContent
                } else {
                    chatList.add(ChatMessage("assistant", cleanContent))
                }

                runOnUiThread {
                    val content = cleanContent
                    val size = messages.size
                    messages[size - 1] = Message(content, MessageType.ASSISTANT)

                    val ttft = String.format(null, "%.2f", streamResult.profile.ttftMs)
                    val promptTokens = streamResult.profile.promptTokens
                    val prefillSpeed =
                        String.format(null, "%.2f", streamResult.profile.prefillSpeed)

                    val generatedTokens = streamResult.profile.generatedTokens
                    val decodingSpeed =
                        String.format(null, "%.2f", streamResult.profile.decodingSpeed)

                    // Attach perf data to the assistant message — revealed on long-press
                    val profileData =
                        "TTFT: $ttft ms | Prompt: $promptTokens tok\nPrefill: $prefillSpeed tok/s | Decode: $decodingSpeed tok/s\nGenerated: $generatedTokens tok"
                    if (messages.isNotEmpty() && messages.last().type == MessageType.ASSISTANT) {
                        messages.last().perfData = profileData
                    }
                    reloadRecycleView()
                }
                Log.d(TAG, "Completed: ${streamResult.profile}")
            }

            is LlmStreamResult.Error -> {
                runOnUiThread {
                    val content =
                        "your conversation is out of model’s context length, please start a new conversation or click clear button"
                    messages.add(Message(content, MessageType.PROFILE))
                    reloadRecycleView()
                }
                Log.d(TAG, "Error: $streamResult")
            }
        }
    }

    private fun okdownload() {
        val okDownloadBuilder = OkDownload.Builder(this)
        val factory = DownloadOkHttp3Connection.Factory()
        factory.setBuilder(getUnsafeOkHttpClient())
        okDownloadBuilder.connectionFactory(factory)
        try {
            OkDownload.setSingletonInstance(okDownloadBuilder.build())
        } catch (e: java.lang.Exception) {
            Log.e("download", "download init failed")
        }
    }

    private fun getUnsafeOkHttpClient(): OkHttpClient.Builder {
        try {
            val x509m: X509TrustManager = object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate?>? {
                    //Note: Cannot return null here, otherwise it will throw an error
                    val x509Certificates = arrayOfNulls<X509Certificate>(0)
                    return x509Certificates
                }

                @Throws(CertificateException::class)
                override fun checkServerTrusted(
                    chain: Array<X509Certificate?>?, authType: String?
                ) {
// Do not throw exception to trust all server certificates
                }

                @Throws(CertificateException::class)
                override fun checkClientTrusted(
                    chain: Array<X509Certificate?>?, authType: String?
                ) {
// Default trust mechanism
                }
            }
            // Create a TrustManager that trusts all certificates
            val trustAllCerts = arrayOf<TrustManager>(x509m)

            // Initialize SSLContext
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())

            // Create SSLSocketFactory
            val sslSocketFactory: SSLSocketFactory = sslContext.getSocketFactory()

            // Build OkHttpClient
            return OkHttpClient.Builder().sslSocketFactory(
                sslSocketFactory, (trustAllCerts[0] as X509TrustManager?)!!
            ).hostnameVerifier { hostname: String?, session: SSLSession? -> true }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun openGallery() {
        // Use ACTION_GET_CONTENT for broad file access (Gallery + Downloads + Files)
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        // Also trigger MediaStore scan for adb-pushed files
        try {
            val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            if (downloadDir.exists()) {
                android.media.MediaScannerConnection.scanFile(
                    this, arrayOf(downloadDir.absolutePath), null, null
                )
            }
        } catch (e: Exception) { /* ignore scan errors */ }
        startActivityForResult(Intent.createChooser(intent, "Select Image"), 1)
    }

    private fun openModelFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        // Try to suggest common download directories
        try {
            startActivityForResult(
                Intent.createChooser(intent, "Select GGUF Model File"),
                REQUEST_CODE_MODEL_FILE
            )
        } catch (ex: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "Please install a file manager.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun browseHealthFiles() {
        browseVaultFolder(healthVaultDir, "Health Vault")
    }

    private fun browseVaultFolder(folder: File, title: String) {
        if (!folder.exists()) {
            Toast.makeText(this, "Vault not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        val entries = folder.listFiles()?.filter { !it.name.startsWith(".") }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()

        if (entries.isEmpty()) {
            Toast.makeText(this, "Empty", Toast.LENGTH_SHORT).show()
            return
        }

        val sheet = BottomSheetDialog(this, R.style.DarkBottomSheetDialog)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(0, dp(16), 0, dp(24))
        }

        // Header
        val header = TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#4D4D4D"))
            textSize = 10f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            letterSpacing = 0.08f
            isAllCaps = true
            setPadding(dp(20), dp(4), dp(20), dp(12))
        }
        container.addView(header)

        // Entries
        for (entry in entries) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(14), dp(20), dp(14))
                isClickable = true
                isFocusable = true
                // Ripple-like touch feedback
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener {
                    sheet.dismiss()
                    if (entry.isDirectory) {
                        browseVaultFolder(entry, entry.name)
                    } else if (entry.name.endsWith(".md", true) || entry.name.endsWith(".txt", true)) {
                        try { showFileContent(entry) }
                        catch (e: Exception) { Toast.makeText(this@MainActivity, "Error", Toast.LENGTH_SHORT).show() }
                    }
                }
            }

            val icon = TextView(this).apply {
                text = if (entry.isDirectory) "/" else "."
                setTextColor(Color.parseColor("#10B981"))
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(0, 0, dp(12), 0)
            }

            val name = TextView(this).apply {
                text = entry.name
                setTextColor(if (entry.isDirectory) Color.parseColor("#F2F2F2") else Color.parseColor("#B0B0B0"))
                textSize = 14f
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                letterSpacing = -0.01f
            }

            val chevron = TextView(this).apply {
                text = if (entry.isDirectory) ">" else ""
                setTextColor(Color.parseColor("#4D4D4D"))
                textSize = 12f
                setPadding(dp(8), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                gravity = android.view.Gravity.END
            }

            row.addView(icon)
            row.addView(name)
            row.addView(chevron)
            container.addView(row)

            // Separator
            val sep = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).also { it.setMargins(dp(20), 0, dp(20), 0) }
                setBackgroundColor(Color.parseColor("#0F0F0F"))
            }
            container.addView(sep)
        }

        val scrollView = android.widget.ScrollView(this).apply {
            addView(container)
            setBackgroundColor(Color.parseColor("#0D0D0D"))
        }
        sheet.setContentView(scrollView)
        sheet.window?.navigationBarColor = Color.parseColor("#0D0D0D")
        // Style the bottom sheet background
        sheet.setOnShowListener {
            val bottomSheet = sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundColor(Color.parseColor("#0D0D0D"))
        }
        sheet.show()
    }

    private fun showFileContent(file: File) {
        val fileName = file.name
        val content = try { file.readText() } catch (e: Exception) { "Error reading file" }

        val sheet = BottomSheetDialog(this, R.style.DarkBottomSheetDialog)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#070707"))
            setPadding(dp(20), dp(16), dp(20), dp(32))
        }

        // Header with file name and edit button
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(16))
        }

        val header = TextView(this).apply {
            text = fileName.removeSuffix(".md").removeSuffix(".txt")
            setTextColor(Color.parseColor("#4D4D4D"))
            textSize = 10f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            letterSpacing = 0.08f
            isAllCaps = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(header)

        val btnEdit = Button(this).apply {
            text = "Edit"
            setTextColor(Color.parseColor("#10B981"))
            textSize = 11f
            isAllCaps = false
            setBackgroundResource(R.drawable.btn_rounded_border)
            setPadding(dp(12), dp(4), dp(12), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(28)
            )
            setOnClickListener {
                sheet.dismiss()
                editFileContent(file)
            }
        }
        headerRow.addView(btnEdit)
        container.addView(headerRow)

        // Markdown rendered content
        val markwon = Markwon.builder(this)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .usePlugin(LinkifyPlugin.create())
            .build()

        val contentView = TextView(this).apply {
            setTextColor(Color.parseColor("#D0D0D0"))
            textSize = 13f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            setLineSpacing(0f, 1.4f)
            letterSpacing = -0.01f
            setTextIsSelectable(true)
        }
        markwon.setMarkdown(contentView, content)
        container.addView(contentView)

        val scrollView = android.widget.ScrollView(this).apply {
            addView(container)
            setBackgroundColor(Color.parseColor("#070707"))
        }
        sheet.setContentView(scrollView)
        sheet.window?.navigationBarColor = Color.parseColor("#070707")
        sheet.setOnShowListener {
            val bottomSheet = sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundColor(Color.parseColor("#070707"))
        }
        sheet.show()
    }

    private fun editFileContent(file: File) {
        val content = try { file.readText() } catch (e: Exception) { "" }

        val sheet = BottomSheetDialog(this, R.style.DarkBottomSheetDialog)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#070707"))
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }

        // Header
        val header = TextView(this).apply {
            text = "Edit ${file.name}"
            setTextColor(Color.parseColor("#4D4D4D"))
            textSize = 10f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            letterSpacing = 0.08f
            isAllCaps = true
            setPadding(0, dp(4), 0, dp(12))
        }
        container.addView(header)

        // Editable text area
        val editText = EditText(this).apply {
            setText(content)
            setTextColor(Color.parseColor("#D0D0D0"))
            setHintTextColor(Color.parseColor("#4D4D4D"))
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            gravity = android.view.Gravity.START or android.view.Gravity.TOP
            minLines = 15
            maxLines = 30
            setHorizontallyScrolling(false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val scrollView = android.widget.ScrollView(this).apply {
            addView(editText)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        container.addView(scrollView)

        // Save button
        val btnSave = Button(this).apply {
            text = "Save Changes"
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 13f
            isAllCaps = false
            setBackgroundResource(R.drawable.btn_send_accent)
            setPadding(dp(20), dp(10), dp(20), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            ).also { it.topMargin = dp(16) }
            setOnClickListener {
                try {
                    file.writeText(editText.text.toString())
                    Toast.makeText(this@MainActivity, "Saved", Toast.LENGTH_SHORT).show()
                    sheet.dismiss()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        container.addView(btnSave)

        sheet.setContentView(container)
        sheet.window?.navigationBarColor = Color.parseColor("#070707")
        sheet.setOnShowListener {
            val bottomSheet = sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundColor(Color.parseColor("#070707"))
        }
        sheet.show()
    }

    private fun showSettingsDialog() {
        val systemPrompt = try {
            val promptFile = File(healthVaultDir, "04_System_Prompt/hk_system_prompt.md")
            if (promptFile.exists()) promptFile.readText() else "No system prompt loaded"
        } catch (e: Exception) { "Error loading prompt" }

        val vaultStats = try {
            var fileCount = 0
            var totalSize = 0L
            healthVaultDir.walkTopDown().forEach {
                if (it.isFile) { fileCount++; totalSize += it.length() }
            }
            "$fileCount files  ·  ${totalSize / 1024} KB"
        } catch (e: Exception) { "—" }

        val modelInfo = if (hasLoadedModel()) {
            "Active" + (if (manualModelFilePath != null) "  ·  ${File(manualModelFilePath!!).name}" else "")
        } else {
            "RAG demo mode"
        }

        val sheet = BottomSheetDialog(this, R.style.DarkBottomSheetDialog)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }

        fun addSection(label: String, value: String) {
            val sectionLabel = TextView(this).apply {
                text = label
                setTextColor(Color.parseColor("#808080"))
                textSize = 10f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                letterSpacing = 0.1f
                isAllCaps = true
                setPadding(0, dp(16), 0, dp(4))
            }
            container.addView(sectionLabel)

            val sectionValue = TextView(this).apply {
                text = value
                setTextColor(Color.parseColor("#D0D0D0"))
                textSize = 13f
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                setLineSpacing(0f, 1.3f)
                letterSpacing = -0.01f
                setTextIsSelectable(true)
            }
            container.addView(sectionValue)
        }

        addSection("Model", modelInfo)
        addSection("Health Vault", vaultStats)
        addSection("System Prompt", systemPrompt)
        addSection("Privacy", "All processing happens on-device.\nNo data is transmitted to any server.")

        // Advanced mode button
        val advBtn = Button(this).apply {
            text = "Advanced Mode"
            setTextColor(Color.parseColor("#808080"))
            textSize = 12f
            isAllCaps = false
            setBackgroundResource(R.drawable.btn_rounded_border)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(36)
            )
            lp.topMargin = dp(20)
            layoutParams = lp
            setOnClickListener {
                sheet.dismiss()
                toggleAdvancedMode()
            }
        }
        container.addView(advBtn)

        val scrollView = android.widget.ScrollView(this).apply {
            addView(container)
            setBackgroundColor(Color.parseColor("#0D0D0D"))
        }
        sheet.setContentView(scrollView)
        sheet.window?.navigationBarColor = Color.parseColor("#0D0D0D")
        sheet.setOnShowListener {
            val bottomSheet = sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundColor(Color.parseColor("#0D0D0D"))
        }
        sheet.show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()



    private fun saveHealthRecord(content: String) {
        try {
            // Save to health_vault/00_Inbox/ so RAG can find it
            val inboxDir = File(healthVaultDir, "00_Inbox")
            if (!inboxDir.exists()) {
                inboxDir.mkdirs()
            }

            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US)
                .format(java.util.Date())
            val fileName = "health_record_$timestamp.md"
            val file = File(inboxDir, fileName)

            file.writeText(content)

            Log.d(TAG, "Health record saved: ${file.absolutePath}")
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Saved to vault",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving health record", e)
        }
    }

    private fun handleModelFileSelection(uri: android.net.Uri) {
        try {
            // Get the file path from URI
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val fileName = if (displayNameIndex >= 0) it.getString(displayNameIndex) else "model.gguf"

                    // Check if it's a GGUF file
                    if (!fileName.endsWith(".gguf", ignoreCase = true)) {
                        Toast.makeText(this, "Please select a .gguf model file", Toast.LENGTH_LONG).show()
                        return
                    }

                    // Copy file to app's internal storage
                    val destFile = File(filesDir, "manual_models/$fileName")
                    destFile.parentFile?.mkdirs()

                    contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    manualModelFilePath = destFile.absolutePath

                    Toast.makeText(
                        this,
                        "Model loaded: $fileName\nTap 'Load' to initialize",
                        Toast.LENGTH_LONG
                    ).show()

                    Log.d(TAG, "Manual model file saved to: ${destFile.absolutePath}, size: ${destFile.length()} bytes")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling model file selection", e)
            Toast.makeText(this, "Error loading file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 0) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery()
            } else {
                Toast.makeText(this, "Not allow", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == 2001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(this, "Camera not allow", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Handle model file selection
        if (requestCode == REQUEST_CODE_MODEL_FILE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                data.data?.let { uri ->
                    handleModelFileSelection(uri)
                }
            }
            return
        }

        var bitmap: Bitmap? = null
        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val inputStream = contentResolver.openInputStream(data.data!!)
                bitmap = BitmapFactory.decodeStream(inputStream)
            }
        } else if (requestCode == 1001 && resultCode == Activity.RESULT_OK) {
            photoFile?.let {
                bitmap = BitmapFactory.decodeFile(it.absolutePath)
            }
        }

        bitmap?.let {
            try {
                val file = File(filesDir, "chat_${System.currentTimeMillis()}.jpg")
                val success = saveBitmapToFile(it, file)
                if (success) {
                    Log.d(TAG, "Save success：${file.absolutePath}")
                    savedImageFiles.add(file)
                    refreshTopScrollContainer()
                } else {
                    Toast.makeText(this, "Save Image failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap, file: File): Boolean {
        return try {
            val tempDir = File(this.filesDir, "tmp").apply { if (!exists()) mkdirs() }

            val tempFile = File(
                tempDir,
                "tmp_${System.currentTimeMillis()}.jpg"
            )
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }

            val outFile = File(
                tempDir,
                "out_${System.currentTimeMillis()}.jpg"
            )
            ImgUtil.squareCrop(
                ImgUtil.downscaleAndSave(
                    imageFile = tempFile,
                    outFile = outFile,
                    maxSize = 448,
                    format = Bitmap.CompressFormat.JPEG,
                    quality = 90
                ), file, 448
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun stopRecord(cancel: Boolean) {
        wavRecorder?.stopRecording()
        wavRecorder = null
        bottomPanel.visibility = View.GONE
        if (cancel) {
            audioFile = null
        }
        refreshTopScrollContainer()
    }

    private fun startRecord() {
        bottomPanel.visibility = View.VISIBLE

        val file = File(filesDir, "audio")
        if (!file.exists()) {
            file.mkdirs()
        }
        audioFile =
            File(file, "audio_${System.currentTimeMillis()}.wav")
        Log.d(TAG, "audioFile: ${audioFile!!.absolutePath}")
        wavRecorder = WavRecorder()

        wavRecorder?.startRecording(audioFile!!)
    }

    private fun clearHistory() {
        if (isLoadLlmModel) {
            chatList.clear()
            chatList.add(llmSystemPrompt) // Re-add doctor persona
            modelScope.launch {
                llmWrapper.reset()
            }
        }
        if (isLoadVlmModel) {
            vlmChatList.clear()
            vlmChatList.add(vlmSystemPrompty) // Re-add VLM persona
            modelScope.launch {
                vlmWrapper.reset()
            }
        }
        messages.clear()
        audioFile = null
        clearImages()
        reloadRecycleView()
    }

    private var popupWindow: PopupWindow? = null
    private fun showPopupMenu(anchorView: View) {
        val sheet = BottomSheetDialog(this, R.style.DarkBottomSheetDialog)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }

        // Header
        val header = TextView(this).apply {
            text = "Add Document"
            setTextColor(Color.parseColor("#4D4D4D"))
            textSize = 10f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            letterSpacing = 0.08f
            isAllCaps = true
            setPadding(0, 0, 0, dp(16))
        }
        container.addView(header)

        // Camera button
        val btnCamera = TextView(this).apply {
            text = "Take Photo"
            setTextColor(Color.parseColor("#F2F2F2"))
            textSize = 15f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            setPadding(0, dp(14), 0, dp(14))
            gravity = android.view.Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                sheet.dismiss()
                checkAndOpenCamera()
            }
        }
        container.addView(btnCamera)

        // Separator
        val sep1 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.parseColor("#0F0F0F"))
        }
        container.addView(sep1)

        // Gallery button
        val btnGallery = TextView(this).apply {
            text = "Choose from Photos"
            setTextColor(Color.parseColor("#F2F2F2"))
            textSize = 15f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            setPadding(0, dp(14), 0, dp(14))
            gravity = android.view.Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                sheet.dismiss()
                openGallery()
            }
        }
        container.addView(btnGallery)

        sheet.setContentView(container)
        sheet.window?.navigationBarColor = Color.parseColor("#0D0D0D")
        sheet.setOnShowListener {
            val bottomSheet = sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundColor(Color.parseColor("#0D0D0D"))
        }
        sheet.show()
    }

    private var photoUri: Uri? = null
    private var photoFile: File? = null

    private fun checkAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                2001
            )
        } else {
            openCamera()
        }
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        photoFile = File(
            getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "photo_${System.currentTimeMillis()}.jpg"
        )
        photoUri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            photoFile!!
        )

        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        startActivityForResult(intent, 1001)
    }

    private fun clearImages() {
        savedImageFiles.clear()
        refreshTopScrollContainer()
    }

    private fun refreshTopScrollContainer() {
        runOnUiThread {
            topScrollContainer.removeAllViews()
            if (savedImageFiles.isEmpty() && audioFile == null) {
                scrollImages.visibility = View.GONE
                return@runOnUiThread
            }

            scrollImages.visibility = View.VISIBLE

            for (file in savedImageFiles) {
                val itemView = LayoutInflater.from(this)
                    .inflate(R.layout.item_image_scroll, topScrollContainer, false)
                val ivImage = itemView.findViewById<ImageView>(R.id.iv_image)
                val btnRemove = itemView.findViewById<ImageButton>(R.id.btn_remove)

                ivImage.setImageURI(Uri.fromFile(file))

                btnRemove.setOnClickListener {
                    savedImageFiles.remove(file)
                    refreshTopScrollContainer()
                }
                topScrollContainer.addView(itemView)
            }

            if (audioFile != null) {
                val audioView = LayoutInflater.from(this)
                    .inflate(R.layout.item_audio_scroll, topScrollContainer, false)
                val audioName = audioView.findViewById<TextView>(R.id.tv_audio_name)
                val audioType = audioView.findViewById<TextView>(R.id.tv_audio_type)
                val btnRemove = audioView.findViewById<ImageButton>(R.id.btn_audio_remove)
                audioName.text = audioFile!!.name
                // TODO: hard code
                audioType.text = "wav"

                btnRemove.setOnClickListener {
                    audioFile = null
                    refreshTopScrollContainer()
                }
                topScrollContainer.addView(audioView)
            }
        }
    }

    private fun reloadRecycleView() {
        adapter.notifyDataSetChanged()
        binding.rvChat.scrollToPosition(messages.size - 1)
    }

    companion object {
        private const val SP_DOWNLOADED = "sp_downloaded"
        private const val TAG = "HealthPassport"
        private const val REQUEST_CODE_MODEL_FILE = 2000

        // Model types for the model_list.json "type" field
        const val MODEL_TYPE_LLM = "chat"
        const val MODEL_TYPE_VLM = "multimodal"
        const val MODEL_TYPE_OCR = "paddleocr"
        const val MODEL_TYPE_ASR = "asr"
        const val MODEL_TYPE_EMBEDDING = "embedder"
        const val MODEL_TYPE_RERANKER = "reranker"
    }
}
