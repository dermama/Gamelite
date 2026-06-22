package com.gamehub.lite.extension;

import android.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CacheManager {
    private static final String TAG = "GameHubNexus-Cache";
    private final File cacheDir;
    private final long maxCacheSize;

    public CacheManager(File cacheDir) {
        // Default 10 GB limit for local cached files
        this(cacheDir, 10L * 1024 * 1024 * 1024);
    }

    public CacheManager(File cacheDir, long maxCacheSize) {
        this.cacheDir = cacheDir;
        this.maxCacheSize = maxCacheSize;
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        enforceCacheLimit();
    }

    public long getCacheSize() {
        return getDirSize(cacheDir);
    }

    public void cleanAllCache() {
        Log.i(TAG, "Cleaning entire L2 cache...");
        File[] files = cacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                deleteRecursive(file);
            }
        }
    }

    public synchronized void enforceCacheLimit() {
        long currentSize = getCacheSize();
        Log.d(TAG, "Current L2 Cache Size: " + (currentSize / (1024 * 1024)) + " MB / " + (maxCacheSize / (1024 * 1024)) + " MB");

        if (currentSize <= maxCacheSize) {
            return;
        }

        Log.i(TAG, "Cache size limit exceeded. Evicting oldest files...");

        List<File> allFiles = new ArrayList<>();
        gatherFiles(cacheDir, allFiles);

        // Sort files by last modified time (oldest first)
        Collections.sort(allFiles, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                return Long.compare(f1.lastModified(), f2.lastModified());
            }
        });

        long sizeToRemove = currentSize - maxCacheSize;
        for (File file : allFiles) {
            if (sizeToRemove <= 0) {
                break;
            }
            if (file.isFile()) {
                long fileSize = file.length();
                if (file.delete()) {
                    sizeToRemove -= fileSize;
                    Log.d(TAG, "Evicted cached file: " + file.getName() + " (Size: " + (fileSize / 1024) + " KB)");
                }
            }
        }

        Log.i(TAG, "Cache eviction completed. Current size: " + (getCacheSize() / (1024 * 1024)) + " MB");
    }

    private long getDirSize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File file : files) {
            if (file.isDirectory()) {
                size += getDirSize(file);
            } else {
                size += file.length();
            }
        }
        return size;
    }

    private void gatherFiles(File dir, List<File> list) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                gatherFiles(file, list);
            } else {
                list.add(file);
            }
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}
