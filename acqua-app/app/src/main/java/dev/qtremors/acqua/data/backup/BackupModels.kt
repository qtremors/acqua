package dev.qtremors.acqua.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class PreferencesBackupPayload(
    val schemaVersion: Int,
    val createdAtMillis: Long,
    val packageName: String,
    val stores: List<PreferencesBackupStore> = emptyList()
)

@Serializable
data class PreferencesBackupStore(
    val name: String,
    val encodedBytes: String,
    val decodedSizeBytes: Int? = null,
    val sha256: String? = null
)

@Serializable
data class SharedPreferencesBackup(
    val entries: Map<String, SharedPreferenceValue> = emptyMap()
)

@Serializable
data class SharedPreferenceValue(
    val type: SharedPreferenceType,
    val stringValue: String? = null,
    val stringSetValue: Set<String>? = null,
    val longValue: Long? = null,
    val floatValue: Float? = null,
    val booleanValue: Boolean? = null
)

@Serializable
enum class SharedPreferenceType {
    String,
    StringSet,
    Int,
    Long,
    Float,
    Boolean
}

enum class PreferencesBackupItemStatus {
    WillRestore,
    WillReset,
    Restored,
    Reset,
    Unchanged,
    Exported
}

data class PreferencesBackupItem(
    val id: String,
    val label: String,
    val status: PreferencesBackupItemStatus
)

data class PreferencesBackupPreview(
    val createdAtMillis: Long,
    val items: List<PreferencesBackupItem>
)

data class PreferencesBackupFailure(
    val storeName: String,
    val message: String
)

data class PreferencesBackupOperationResult(
    val items: List<PreferencesBackupItem>,
    val failures: List<PreferencesBackupFailure> = emptyList()
)
