package com.gamehub.lite.patches

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gamehub.lite.R
import com.gamehub.lite.extension.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*

class MainActivityPatch : AppCompatActivity(), HybridConnection.ConnectionListener {

    private lateinit var hybridConnection: HybridConnection
    private lateinit var taskRouter: TaskRouter
    private lateinit var memorySnapshotter: MemorySnapshotter
    private lateinit var cloudFSProvider: CloudFSProvider
    private lateinit var cacheManager: CacheManager

    // UI elements from hybrid_hud.xml
    private lateinit var hudOverlay: View
    private lateinit var connectionIndicator: View
    private lateinit var statusText: TextView
    private lateinit var btnAiTranslate: Button
    private lateinit var btnCloudSave: Button
    private lateinit var btnCloudLoad: Button
    private lateinit var translationOverlayCanvas: FrameLayout

    private val deviceId = UUID.randomUUID().toString()
    private val currentGameId = "gta_5" // Example active game ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Initialize Hybrid Computing Components
        val winePrefixDir = File(filesDir, "wine_prefix")
        val cacheDir = File(cacheDir, "game_chunks")
        
        hybridConnection = HybridConnection(deviceId)
        memorySnapshotter = MemorySnapshotter(winePrefixDir, hybridConnection)
        taskRouter = TaskRouter(hybridConnection, memorySnapshotter)
        cloudFSProvider = CloudFSProvider(cacheDir)
        cacheManager = CacheManager(cacheDir)

        hybridConnection.setListener(this)
        hybridConnection.connect()

        // 2. Inflate and Add Hybrid HUD Overlay to current window
        val inflater = LayoutInflater.from(this)
        hudOverlay = inflater.inflate(R.layout.hybrid_hud, null)
        val layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        addContentView(hudOverlay, layoutParams)

        // 3. Bind UI Controls
        connectionIndicator = hudOverlay.findViewById(R.id.connectionIndicator)
        statusText = hudOverlay.findViewById(R.id.statusText)
        btnAiTranslate = hudOverlay.findViewById(R.id.btnAiTranslate)
        btnCloudSave = hudOverlay.findViewById(R.id.btnCloudSave)
        btnCloudLoad = hudOverlay.findViewById(R.id.btnCloudLoad)
        translationOverlayCanvas = hudOverlay.findViewById(R.id.translationOverlayCanvas)

        btnAiTranslate.setOnClickListener {
            captureScreenAndTranslate()
        }

        btnCloudSave.setOnClickListener {
            memorySnapshotter.freezeAndSave(currentGameId)
        }

        btnCloudLoad.setOnClickListener {
            memorySnapshotter.restoreAndResume(currentGameId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hybridConnection.disconnect()
    }

    // 4. WebSocket Listener implementations
    override fun onConnectionStatusChanged(connected: Boolean) {
        if (connected) {
            connectionIndicator.setBackgroundColor(resources.getColor(android.R.color.holo_green_light, null))
            statusText.text = "Nexus: Connected"
        } else {
            connectionIndicator.setBackgroundColor(resources.getColor(android.R.color.holo_red_dark, null))
            statusText.text = "Nexus: Reconnecting..."
        }
    }

    override fun onMessageReceived(type: String, data: JSONObject) {
        when (type) {
            "prefetch_directive" -> {
                val fileUrl = data.getString("file_url")
                val byteRange = data.getString("byte_range")
                // Instruct CloudFS to load chunk ahead of time
                cloudFSProvider.prefetchRange(currentGameId, fileUrl, byteRange)
            }
            "ocr_translate_response" -> {
                val translations = data.getJSONArray("translations")
                renderTranslationOverlays(translations)
            }
            "task_result" -> {
                val task = data.getString("task")
                val status = data.getString("status")
                val result = data.getJSONObject("result")
                taskRouter.handleTaskResult(task, status, result)
            }
        }
    }

    // 5. Screen Capture and Translation Overlay Logic
    private fun captureScreenAndTranslate() {
        // Take screenshot of the emulator drawing surface
        val rootView = window.decorView.rootView
        val bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        rootView.draw(canvas)

        // Compress and encode to Base64
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val imageBytes = outputStream.toByteArray()
        val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)

        // Dispatch translation request to Space B
        val payload = JSONObject().apply {
            put("image_data", base64Image)
        }
        taskRouter.routeTask("ocr_translate", payload)
        
        statusText.text = "Nexus: Translating..."
    }

    private fun renderTranslationOverlays(translations: org.json.JSONArray) {
        translationOverlayCanvas.removeAllViews()
        translationOverlayCanvas.visibility = View.VISIBLE

        for (i in 0 until translations.length()) {
            val block = translations.getJSONObject(i)
            val translatedText = block.getString("translated_text")
            val x = block.getInt("x")
            val y = block.getInt("y")
            val width = block.getInt("width")
            val height = block.getInt("height")

            // Create a textview for each translated phrase bounding box
            val textView = TextView(this).apply {
                text = translatedText
                setTextColor(resources.getColor(android.R.color.black, null))
                setBackgroundColor(resources.getColor(android.R.color.holo_orange_light, null))
                textSize = 12f
                setPadding(4, 2, 4, 2)
            }

            val params = FrameLayout.LayoutParams(width, height).apply {
                leftMargin = x
                topMargin = y
            }
            
            translationOverlayCanvas.addView(textView, params)
        }

        statusText.text = "Nexus: Translated"
        
        // Automatically clear translations after 8 seconds
        translationOverlayCanvas.postDelayed({
            translationOverlayCanvas.visibility = View.GONE
            translationOverlayCanvas.removeAllViews()
        }, 8000)
    }
}
