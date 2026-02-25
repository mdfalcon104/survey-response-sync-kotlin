package com.survey.sync

import com.survey.sync.domain.StorageManager

/**
 * Fake storage manager for testing.
 * Tracks file operations without touching the file system.
 */
class FakeStorageManager : StorageManager {

    private val existingFiles = mutableSetOf<String>()
    private val deletedFiles = mutableListOf<String>()

    /**
     * Add files that should "exist" for testing.
     */
    fun addExistingFiles(vararg paths: String) {
        existingFiles.addAll(paths)
    }

    /**
     * Get all files that were deleted.
     */
    fun getDeletedFiles(): List<String> = deletedFiles.toList()

    /**
     * Reset state for new test.
     */
    fun reset() {
        existingFiles.clear()
        deletedFiles.clear()
    }

    override suspend fun deleteFile(filePath: String): Boolean {
        deletedFiles.add(filePath)
        return existingFiles.remove(filePath) || true
    }

    override suspend fun deleteFiles(filePaths: List<String>): Int {
        var count = 0
        for (path in filePaths) {
            if (deleteFile(path)) {
                count++
            }
        }
        return count
    }

    override suspend fun fileExists(filePath: String): Boolean {
        return filePath in existingFiles
    }
}
