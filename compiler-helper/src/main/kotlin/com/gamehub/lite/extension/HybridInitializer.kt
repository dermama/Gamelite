package com.gamehub.lite.extension

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

object HybridInitializer : HybridConnection.ConnectionListener {
    private val TAG = "GameHubNexus-Initializer"
    private lateinit var activity: Activity
    private lateinit var hybridConnection: HybridConnection
    private lateinit var taskRouter: TaskRouter
    private lateinit var memorySnapshotter: MemorySnapshotter
    private lateinit var cloudFSProvider: CloudFSProvider
    private lateinit var cacheManager: CacheManager

    private lateinit var hudOverlay: View
    private lateinit var connectionIndicator: View
    private lateinit var statusText: TextView
    private lateinit var btnAiTranslate: Button
    private lateinit var btnCloudSave: Button
    private lateinit var btnCloudLoad: Button
    private lateinit var translationOverlayCanvas: FrameLayout

    private val deviceId = UUID.randomUUID().toString()
    private val currentGameId = "gta_5"

    @JvmStatic
    fun init(activity: Activity) {
        this.activity = activity
        Log.i(TAG, "Initializing GameHub Nexus Hybrid extension...")

        val winePrefixDir = File(activity.filesDir, "wine_prefix")
        val cacheDir = File(activity.cacheDir, "game_chunks")

        hybridConnection = HybridConnection(deviceId)
        memorySnapshotter = MemorySnapshotter(winePrefixDir, hybridConnection)
        taskRouter = TaskRouter(hybridConnection, memorySnapshotter)
        cloudFSProvider = CloudFSProvider(cacheDir)
        cacheManager = CacheManager(cacheDir)

        hybridConnection.setListener(this)
        hybridConnection.connect()

        // Inflate HUD Overlay dynamically
        val resId = activity.resources.getIdentifier("hybrid_hud", "layout", activity.packageName)
        if (resId == 0) {
            Log.e(TAG, "Failed to find layout resource 'hybrid_hud'")
            return
        }

        val inflater = LayoutInflater.from(activity)
        hudOverlay = inflater.inflate(resId, null)
        
        val layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        activity.addContentView(hudOverlay, layoutParams)

        // Bind views dynamically
        val connIndId = activity.resources.getIdentifier("connectionIndicator", "id", activity.packageName)
        val statusTextId = activity.resources.getIdentifier("statusText", "id", activity.packageName)
        val btnAiId = activity.resources.getIdentifier("btnAiTranslate", "id", activity.packageName)
        val btnSaveId = activity.resources.getIdentifier("btnCloudSave", "id", activity.packageName)
        val btnLoadId = activity.resources.getIdentifier("btnCloudLoad", "id", activity.packageName)
        val canvasId = activity.resources.getIdentifier("translationOverlayCanvas", "id", activity.packageName)

        connectionIndicator = hudOverlay.findViewById(connIndId)
        statusText = hudOverlay.findViewById(statusTextId)
        btnAiTranslate = hudOverlay.findViewById(btnAiId)
        btnCloudSave = hudOverlay.findViewById(btnSaveId)
        btnCloudLoad = hudOverlay.findViewById(btnLoadId)
        translationOverlayCanvas = hudOverlay.findViewById(canvasId)

        btnAiTranslate.setOnClickListener { captureScreenAndTranslate() }
        btnCloudSave.setOnClickListener { memorySnapshotter.freezeAndSave(currentGameId) }
        btnCloudLoad.setOnClickListener { memorySnapshotter.restoreAndResume(currentGameId) }
    }

    override fun onConnectionStatusChanged(connected: Boolean) {
        activity.runOnUiThread {
            val colorGreen = 0xFF00FF00.toInt()
            val colorRed = 0xFFFF0000.toInt()
            if (connected) {
                connectionIndicator.setBackgroundColor(colorGreen)
                statusText.text = "Nexus: Connected"
            } else {
                connectionIndicator.setBackgroundColor(colorRed)
                statusText.text = "Nexus: Reconnecting..."
            }
        }
    }

    override fun onMessageReceived(type: String, data: JSONObject) {
        activity.runOnUiThread {
            when (type) {
                "prefetch_directive" -> {
                    val fileUrl = data.getString("file_url")
                    val byteRange = data.getString("byte_range")
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
    }

    private fun captureScreenAndTranslate() {
        val rootView = activity.window.decorView.rootView
        val bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        rootView.draw(canvas)

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val imageBytes = outputStream.toByteArray()
        val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)

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

            val textView = TextView(activity).apply {
                text = translatedText
                setTextColor(0xFF000000.toInt())
                setBackgroundColor(0xFFFFB300.toInt())
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
        translationOverlayCanvas.postDelayed({
            translationOverlayCanvas.visibility = View.GONE
            translationOverlayCanvas.removeAllViews()
        }, 8000)
    }
}
