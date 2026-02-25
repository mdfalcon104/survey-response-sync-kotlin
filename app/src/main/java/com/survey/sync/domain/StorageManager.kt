package com.survey.sync.domain

/**
 * Abstraction for file storage operations.
 * Used to manage media files associated with survey responses.
 */
interface StorageManager {

    /**
     * Delete a file at the given path.
     *
     * @param filePath Absolute path to the file
     * @return true if file was deleted, false otherwise
     */
    suspend fun deleteFile(filePath: String): Boolean

    /**
     * Delete multiple files.
     *
     * @param filePaths List of absolute file paths
     * @return Number of files successfully deleted
     */
    suspend fun deleteFiles(filePaths: List<String>): Int

    /**
     * Check if a file exists.
     *
     * @param filePath Absolute path to the file
     * @return true if file exists, false otherwise
     */
    suspend fun fileExists(filePath: String): Boolean
}
