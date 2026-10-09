package com.example.telegram.model

enum class NetworkPreference(val label: String) {
    WIFI_ONLY("Wi-Fi only"),
    MOBILE_DATA("Mobile data"),
    WIFI_AND_MOBILE("Wi-Fi and mobile data")
}

enum class BackupItemStatus {
    PENDING,
    UPLOADING,
    COMPLETED,
    FAILED
}

data class BackupStats(
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val completedCount: Int = 0
) {
    val totalCount: Int
        get() = pendingCount + failedCount + completedCount
}

data class CloudBackupMetadata(
    val fileName: String,
    val mediaType: String, // "IMAGE" or "VIDEO"
    val sizeBytes: Long,
    val fileHash: String,
    val timestamp: Long
)

data class DiscoveredCloudItem(
    val messageId: Long,
    val telegramFileId: Int,
    val metadata: CloudBackupMetadata,
    val telegramDate: Int,
    val isAlreadyLocal: Boolean = false
)
