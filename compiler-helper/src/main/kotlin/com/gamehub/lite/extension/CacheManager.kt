package com.gamehub.lite.extension

import android.util.Log
import java.io.File

class CacheManager(
    private val cacheDir: File,
    private val maxCacheSize: Long = 10L * 1024 * 1024 * 1024 // 10 GB limit for local cached files
) {
    private val TAG = "GameHubNexus-Cache"

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        enforceCacheLimit()
    }

    fun getCacheSize(): Long {
        return getDirSize(cacheDir)
    }

    fun cleanAllCache() {
        Log.i(TAG, "Cleaning entire L2 cache...")
        cacheDir.listFiles()?.forEach { file ->
            deleteRecursive(file)
        }
    }

    /**
     * Enforces the local cache size limit using Least Recently Used (LRU) policy.
     * Deletes the oldest files if cache directory exceeds maxCacheSize.
     */
    @Synchronized
    fun enforceCacheLimit() {
        val currentSize = getCacheSize()
        Log.d(TAG, "Current L2 Cache Size: ${currentSize / (1024 * 1024)} MB / ${maxCacheSize / (1024 * 1024)} MB")

        if (currentSize <= maxCacheSize) return

        Log.i(TAG, "Cache size limit exceeded. Evicting oldest files...")

        // Gather all files in cache recursively
        val allFiles = mutableListOf<File>()
        gatherFiles(cacheDir, allFiles)

        // Sort files by last modified time (oldest first)
        allFiles.sortBy { it.lastModified() }

        var sizeToRemove = currentSize - maxCacheSize
        for (file in allFiles) {
            if (sizeToRemove <= 0) break
            
            if (file.isFile) {
                val fileSize = file.length()
                if (file.delete()) {
                    sizeToRemove -= fileSize
                    Log.d(TAG, "Evicted cached file: ${file.name} (Size: ${fileSize / 1024} KB)")
                }
            }
        }
        
        Log.i(TAG, "Cache eviction completed. Current size: ${getCacheSize() / (1024 * 1024)} MB")
    }

    private fun getDirSize(dir: File): Long {
        var size: Long = 0
        val files = dir.listFiles() ?: return 0
        for (file in files) {
            size += if (file.isDirectory) {
                getDirSize(file)
            } else {
                file.length()
            }
        }
        return size
    }

    private fun gatherFiles(dir: File, list: MutableList<File>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                gatherFiles(file, list)
            } else {
                list.add(file)
            }
        }
    }

    private fun deleteRecursive(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursive(it) }
        }
        file.delete()
    }
}
