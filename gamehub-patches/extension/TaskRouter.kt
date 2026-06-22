package com.gamehub.lite.extension

import android.util.Log
import org.json.JSONObject

class TaskRouter(
    private val connection: HybridConnection,
    private val snapshotter: MemorySnapshotter
) {
    private val TAG = "GameHubNexus-Router"

    enum class TaskDestination {
        LOCAL_PHONE,
        SPACE_B_GATEWAY,
        SPACE_C_COMPUTE
    }

    fun routeTask(taskName: String, payload: JSONObject) {
        val destination = classifyTask(taskName)
        Log.i(TAG, "Routing task '$taskName' to destination: $destination")

        when (destination) {
            TaskDestination.LOCAL_PHONE -> {
                executeLocally(taskName, payload)
            }
            TaskDestination.SPACE_B_GATEWAY -> {
                if (taskName == "ocr_translate") {
                    connection.requestTranslation(payload.getString("image_data"))
                }
            }
            TaskDestination.SPACE_C_COMPUTE -> {
                connection.sendTaskToSpaceC(taskName, payload)
            }
        }
    }

    private fun classifyTask(taskName: String): TaskDestination {
        return when (taskName) {
            "render_frame", "play_audio", "read_input", "physics_tick" -> TaskDestination.LOCAL_PHONE
            "ocr_translate", "predict_path" -> TaskDestination.SPACE_B_GATEWAY
            "setup_prefix", "install_mod", "compile_shaders", "save_snapshot", "load_snapshot" -> TaskDestination.SPACE_C_COMPUTE
            else -> TaskDestination.LOCAL_PHONE
        }
    }

    private fun executeLocally(taskName: String, payload: JSONObject) {
        // Run core emulator logic locally on phone (e.g. Vulkan graphics driver execution)
        Log.d(TAG, "Executing $taskName locally on Adreno GPU / Snapdragon CPU")
    }

    fun handleTaskResult(taskName: String, status: String, result: JSONObject) {
        Log.i(TAG, "Received result for task '$taskName' (Status: $status)")
        
        if (status != "success") {
            Log.e(TAG, "Task '$taskName' execution failed: ${result.optString("error", "Unknown error")}")
            return
        }

        when (taskName) {
            "setup_prefix" -> {
                val downloadUrl = result.getString("download_url")
                Log.i(TAG, "Wine Prefix is compiled and ready for download: $downloadUrl")
                // Start downloading and setting up local Wine environment using the compiled package
            }
            "compile_shaders" -> {
                val cacheUrl = result.getString("dxvk_cache_url")
                Log.i(TAG, "DXVK shader cache file is ready: $cacheUrl")
                // Fetch the .dxvk-cache file and save it in prefix directory before launching game
            }
            "load_snapshot" -> {
                val snapshotUrl = result.getString("snapshot_url")
                snapshotter.handleSnapshotDownload(result.optString("game_id", "default"), snapshotUrl)
            }
        }
    }
}
