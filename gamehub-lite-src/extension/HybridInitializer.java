package com.gamehub.lite.extension;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.UUID;

public class HybridInitializer implements HybridConnection.ConnectionListener {
    private static final String TAG = "GameHubNexus-Initializer";
    private static HybridInitializer instance;

    private Activity activity;
    private HybridConnection hybridConnection;
    private TaskRouter taskRouter;
    private MemorySnapshotter memorySnapshotter;
    private CloudFSProvider cloudFSProvider;
    private CacheManager cacheManager;

    private View hudOverlay;
    private View connectionIndicator;
    private TextView statusText;
    private Button btnAiTranslate;
    private Button btnCloudSave;
    private Button btnCloudLoad;
    private FrameLayout translationOverlayCanvas;

    private final String deviceId = UUID.randomUUID().toString();
    private final String currentGameId = "gta_5";

    public static synchronized void init(Activity activity) {
        if (instance != null) return;
        instance = new HybridInitializer();
        instance.initialize(activity);
    }

    private void initialize(Activity activity) {
        this.activity = activity;
        Log.i(TAG, "Initializing GameHub Nexus Hybrid extension...");

        File winePrefixDir = new File(activity.getFilesDir(), "wine_prefix");
        File cacheDir = new File(activity.getCacheDir(), "game_chunks");

        hybridConnection = new HybridConnection(deviceId);
        memorySnapshotter = new MemorySnapshotter(winePrefixDir, hybridConnection);
        taskRouter = new TaskRouter(hybridConnection, memorySnapshotter);
        cloudFSProvider = new CloudFSProvider(cacheDir);
        cacheManager = new CacheManager(cacheDir);

        hybridConnection.setListener(this);
        hybridConnection.connect();

        // Inflate HUD Overlay dynamically to prevent build R mismatch
        int resId = activity.getResources().getIdentifier("hybrid_hud", "layout", activity.getPackageName());
        if (resId == 0) {
            Log.e(TAG, "Failed to find layout resource 'hybrid_hud'");
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(activity);
        hudOverlay = inflater.inflate(resId, null);

        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        activity.addContentView(hudOverlay, layoutParams);

        // Bind views dynamically
        int connIndId = activity.getResources().getIdentifier("connectionIndicator", "id", activity.packageName);
        int statusTextId = activity.getResources().getIdentifier("statusText", "id", activity.packageName);
        int btnAiId = activity.getResources().getIdentifier("btnAiTranslate", "id", activity.packageName);
        int btnSaveId = activity.getResources().getIdentifier("btnCloudSave", "id", activity.packageName);
        int btnLoadId = activity.getResources().getIdentifier("btnCloudLoad", "id", activity.packageName);
        int canvasId = activity.getResources().getIdentifier("translationOverlayCanvas", "id", activity.packageName);

        connectionIndicator = hudOverlay.findViewById(connIndId);
        statusText = (TextView) hudOverlay.findViewById(statusTextId);
        btnAiTranslate = (Button) hudOverlay.findViewById(btnAiId);
        btnCloudSave = (Button) hudOverlay.findViewById(btnSaveId);
        btnCloudLoad = (Button) hudOverlay.findViewById(btnLoadId);
        translationOverlayCanvas = (FrameLayout) hudOverlay.findViewById(canvasId);

        btnAiTranslate.setOnClickListener(v -> captureScreenAndTranslate());
        btnCloudSave.setOnClickListener(v -> memorySnapshotter.freezeAndSave(currentGameId));
        btnCloudLoad.setOnClickListener(v -> memorySnapshotter.restoreAndResume(currentGameId));
    }

    @Override
    public void onConnectionStatusChanged(boolean connected) {
        activity.runOnUiThread(() -> {
            int colorGreen = 0xFF00FF00;
            int colorRed = 0xFFFF0000;
            if (connected) {
                connectionIndicator.setBackgroundColor(colorGreen);
                statusText.setText("Nexus: Connected");
            } else {
                connectionIndicator.setBackgroundColor(colorRed);
                statusText.setText("Nexus: Reconnecting...");
            }
        });
    }

    @Override
    public void onMessageReceived(String type, JSONObject data) {
        activity.runOnUiThread(() -> {
            try {
                switch (type) {
                    case "prefetch_directive":
                        String fileUrl = data.getString("file_url");
                        String byteRange = data.getString("byte_range");
                        cloudFSProvider.prefetchRange(currentGameId, fileUrl, byteRange);
                        break;
                    case "ocr_translate_response":
                        org.json.JSONArray translations = data.getJSONArray("translations");
                        renderTranslationOverlays(translations);
                        break;
                    case "task_result":
                        String task = data.getString("task");
                        String status = data.getString("status");
                        JSONObject result = data.getJSONObject("result");
                        taskRouter.handleTaskResult(task, status, result);
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling message in listener: " + e.getMessage());
            }
        });
    }

    private void captureScreenAndTranslate() {
        View rootView = activity.getWindow().getDecorView().getRootView();
        Bitmap bitmap = Bitmap.createBitmap(rootView.getWidth(), rootView.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        rootView.draw(canvas);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream);
        byte[] imageBytes = outputStream.toByteArray();
        String base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT);

        try {
            JSONObject payload = new JSONObject();
            payload.put("image_data", base64Image);
            taskRouter.routeTask("ocr_translate", payload);
            statusText.setText("Nexus: Translating...");
        } catch (Exception e) {
            Log.e(TAG, "Failed to capture screen: " + e.getMessage());
        }
    }

    private void renderTranslationOverlays(org.json.JSONArray translations) {
        translationOverlayCanvas.removeAllViews();
        translationOverlayCanvas.setVisibility(View.VISIBLE);

        try {
            for (int i = 0; i < translations.length(); i++) {
                JSONObject block = translations.getJSONObject(i);
                String translatedText = block.getString("translated_text");
                int x = block.getInt("x");
                int y = block.getInt("y");
                int width = block.getInt("width");
                int height = block.getInt("height");

                TextView textView = new TextView(activity);
                textView.setText(translatedText);
                textView.setTextColor(0xFF000000);
                textView.setBackgroundColor(0xFFFFB300); // Orange
                textView.setTextSize(12f);
                textView.setPadding(4, 2, 4, 2);

                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
                params.leftMargin = x;
                params.topMargin = y;
                translationOverlayCanvas.addView(textView, params);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to render translation overlays: " + e.getMessage());
        }

        statusText.setText("Nexus: Translated");
        translationOverlayCanvas.postDelayed(() -> {
            translationOverlayCanvas.setVisibility(View.GONE);
            translationOverlayCanvas.removeAllViews();
        }, 8000);
    }
}
