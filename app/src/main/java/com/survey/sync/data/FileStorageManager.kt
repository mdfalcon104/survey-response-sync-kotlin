package com.survey.sync.data

import com.survey.sync.domain.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * File system implementation of StorageManager.
 */
class FileStorageManager : StorageManager {

    override suspend fun deleteFile(filePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    file.delete()
                } else {
                    true
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun deleteFiles(filePaths: List<String>): Int {
        return withContext(Dispatchers.IO) {
            var deletedCount = 0
            for (path in filePaths) {
                if (deleteFile(path)) {
                    deletedCount++
                }
            }
            deletedCount
        }
    }

    override suspend fun fileExists(filePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                File(filePath).exists()
            } catch (e: Exception) {
                false
            }
        }
    }
}
