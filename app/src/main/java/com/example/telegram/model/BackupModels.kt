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

data class AuditReport(
    val localDiscoveredCount: Int = 0,
    val uniqueLocalIdentitiesCount: Int = 0,
    val pendingQueueCount: Int = 0,
    val completedQueueCount: Int = 0,
    val failedQueueCount: Int = 0,
    val pagesFetched: Int = 0,
    val messagesExamined: Int = 0,
    val oldestMessageId: Long = 0L,
    val newestMessageId: Long = 0L,
    val photosDiscovered: Int = 0,
    val videosDiscovered: Int = 0,
    val documentsDiscovered: Int = 0,
    val totalMediaDiscovered: Int = 0,
    val odinBackupMessagesFound: Int = 0,
    val validMetadataParsed: Int = 0,
    val validRemoteMappings: Int = 0,
    val galleryVisibleCount: Int = 0,
    val duplicateLocalRecords: Int = 0,
    val duplicateRemoteMessages: Int = 0,
    val skippedSummary: Map<String, Int> = emptyMap()
)
