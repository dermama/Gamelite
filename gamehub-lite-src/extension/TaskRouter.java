package com.gamehub.lite.extension;

import android.util.Log;
import org.json.JSONObject;

public class TaskRouter {
    private static final String TAG = "GameHubNexus-Router";
    private final HybridConnection connection;
    private final MemorySnapshotter snapshotter;

    public enum TaskDestination {
        LOCAL_PHONE,
        SPACE_B_GATEWAY,
        SPACE_C_COMPUTE
    }

    public TaskRouter(HybridConnection connection, MemorySnapshotter snapshotter) {
        this.connection = connection;
        this.snapshotter = snapshotter;
    }

    public void routeTask(String taskName, JSONObject payload) {
        TaskDestination destination = classifyTask(taskName);
        Log.i(TAG, "Routing task '" + taskName + "' to destination: " + destination);

        switch (destination) {
            case LOCAL_PHONE:
                executeLocally(taskName, payload);
                break;
            case SPACE_B_GATEWAY:
                if ("ocr_translate".equals(taskName)) {
                    try {
                        connection.requestTranslation(payload.getString("image_data"));
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to route translation task: " + e.getMessage());
                    }
                }
                break;
            case SPACE_C_COMPUTE:
                connection.sendTaskToSpaceC(taskName, payload);
                break;
        }
    }

    private TaskDestination classifyTask(String taskName) {
        switch (taskName) {
            case "render_frame":
            case "play_audio":
            case "read_input":
            case "physics_tick":
                return TaskDestination.LOCAL_PHONE;
            case "ocr_translate":
            case "predict_path":
                return TaskDestination.SPACE_B_GATEWAY;
            case "setup_prefix":
            case "install_mod":
            case "compile_shaders":
            case "save_snapshot":
            case "load_snapshot":
                return TaskDestination.SPACE_C_COMPUTE;
            default:
                return TaskDestination.LOCAL_PHONE;
        }
    }

    private void executeLocally(String taskName, JSONObject payload) {
        Log.d(TAG, "Executing " + taskName + " locally on Adreno GPU / Snapdragon CPU");
    }

    public void handleTaskResult(String taskName, String status, JSONObject result) {
        Log.i(TAG, "Received result for task '" + taskName + "' (Status: " + status + ")");

        if (!"success".equals(status)) {
            Log.e(TAG, "Task '" + taskName + "' execution failed: " + result.optString("error", "Unknown error"));
            return;
        }

        try {
            switch (taskName) {
                case "setup_prefix":
                    String downloadUrl = result.getString("download_url");
                    Log.i(TAG, "Wine Prefix is compiled and ready for download: " + downloadUrl);
                    break;
                case "compile_shaders":
                    String cacheUrl = result.getString("dxvk_cache_url");
                    Log.i(TAG, "DXVK shader cache file is ready: " + cacheUrl);
                    break;
                case "load_snapshot":
                    String snapshotUrl = result.getString("snapshot_url");
                    snapshotter.handleSnapshotDownload(result.optString("game_id", "default"), snapshotUrl);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling task result: " + e.getMessage());
        }
    }
}
