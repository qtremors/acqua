package dev.qtremors.acqua.data.backup

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import android.net.Uri
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.emptyPreferences
import dev.qtremors.acqua.core.data.R
import dev.qtremors.acqua.data.onboarding.onboardingDataStore
import dev.qtremors.acqua.data.settings.AppSettingsRepository
import dev.qtremors.acqua.settings.themeDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

interface PreferencesBackupGateway {
    suspend fun preview(uri: Uri): Result<PreferencesBackupPreview>
    suspend fun restoreFrom(uri: Uri): Result<PreferencesBackupOperationResult>
}

class PreferencesBackupManager(private val context: Context) : PreferencesBackupGateway {

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
        const val MIN_SUPPORTED_SCHEMA_VERSION = 1
        const val INTEGRITY_SCHEMA_VERSION = 2

        const val MAX_ENVELOPE_BYTES = 10 * 1024 * 1024
        const val MAX_STORE_BYTES = 2 * 1024 * 1024
        const val MAX_TOTAL_STORE_BYTES = 8 * 1024 * 1024
        const val MAX_ENCODED_STORE_CHARS = 4 * 1024 * 1024

        const val THEME_STORE_NAME = "acqua_theme_prefs"
        const val ONBOARDING_STORE_NAME = "acqua_onboarding_prefs"
        const val APP_SETTINGS_STORE_NAME = AppSettingsRepository.PREFS_NAME
    }

    private val preferenceStoreNames = listOf(
        THEME_STORE_NAME,
        ONBOARDING_STORE_NAME,
        APP_SETTINGS_STORE_NAME
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private fun livePreferenceStores(): Map<String, DataStore<Preferences>> = linkedMapOf(
        THEME_STORE_NAME to context.themeDataStore,
        ONBOARDING_STORE_NAME to context.onboardingDataStore
    )

    private fun appSettings(): SharedPreferences = context.getSharedPreferences(
        APP_SETTINGS_STORE_NAME,
        Context.MODE_PRIVATE
    )

    suspend fun exportTo(uri: Uri): Result<PreferencesBackupOperationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val failedStores = mutableListOf<String>()
            var totalBytes = 0L
            val stores = livePreferenceStores().mapNotNull { (storeName, store) ->
                runCatching {
                    val bytes = serializePreferences(store.data.first())
                    require(bytes.size <= MAX_STORE_BYTES) { "$storeName is too large to export" }
                    totalBytes += bytes.size
                    require(totalBytes <= MAX_TOTAL_STORE_BYTES) { "Settings data is too large to export" }
                    PreferencesBackupStore(
                        name = storeName,
                        encodedBytes = Base64.encodeToString(bytes, Base64.NO_WRAP),
                        decodedSizeBytes = bytes.size,
                        sha256 = bytes.sha256()
                    )
                }.getOrElse {
                    failedStores += storeName
                    null
                }
            }.toMutableList()
            runCatching {
                val bytes = serializeSharedPreferences(appSettings().all)
                require(bytes.size <= MAX_STORE_BYTES) { "$APP_SETTINGS_STORE_NAME is too large to export" }
                totalBytes += bytes.size
                require(totalBytes <= MAX_TOTAL_STORE_BYTES) { "Settings data is too large to export" }
                stores += PreferencesBackupStore(
                    name = APP_SETTINGS_STORE_NAME,
                    encodedBytes = Base64.encodeToString(bytes, Base64.NO_WRAP),
                    decodedSizeBytes = bytes.size,
                    sha256 = bytes.sha256()
                )
            }.onFailure {
                failedStores += APP_SETTINGS_STORE_NAME
            }

            require(failedStores.isEmpty()) {
                "Unable to read ${failedStores.size} preference store(s)"
            }

            val payload = PreferencesBackupPayload(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                createdAtMillis = System.currentTimeMillis(),
                packageName = context.packageName,
                stores = stores
            )

            require(payload.stores.isNotEmpty()) {
                "No settings are available to export yet"
            }

            val encodedPayload = json.encodeToString(payload).toByteArray()
            require(encodedPayload.size <= MAX_ENVELOPE_BYTES) { "Settings backup is too large" }

            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(encodedPayload)
            } ?: error("Unable to open backup destination")

            PreferencesBackupOperationResult(
                items = stores.map {
                    PreferencesBackupItem(it.name, it.name.displayName(), PreferencesBackupItemStatus.Exported)
                }
            )
        }
    }

    override suspend fun preview(uri: Uri): Result<PreferencesBackupPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = readPayload(uri)
            validatePayload(payload)
            PreferencesBackupPreview(
                createdAtMillis = payload.createdAtMillis,
                items = preferenceStoreNames.map { storeName ->
                    PreferencesBackupItem(
                        id = storeName,
                        label = storeName.displayName(),
                        status = if (payload.stores.any { it.name == storeName }) {
                            PreferencesBackupItemStatus.WillRestore
                        } else if (storeName == APP_SETTINGS_STORE_NAME) {
                            PreferencesBackupItemStatus.Unchanged
                        } else {
                            PreferencesBackupItemStatus.WillReset
                        }
                    )
                }
            )
        }
    }

    override suspend fun restoreFrom(uri: Uri): Result<PreferencesBackupOperationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = readPayload(uri)
            validatePayload(payload)
            val stores = livePreferenceStores()
            val desired = decodeDesiredPreferences(payload)
            val desiredAppSettings = decodeDesiredAppSettings(payload)
            val originals = stores.mapValues { (_, store) -> store.data.first() }
            val originalAppSettings = appSettings().all
            val committed = mutableListOf<String>()

            try {
                stores.keys.forEach { storeName ->
                    stores.getValue(storeName).updateData { desired.getValue(storeName) }
                    committed += storeName
                }
                desiredAppSettings?.let { settings ->
                    committed += APP_SETTINGS_STORE_NAME
                    replaceSharedPreferences(appSettings(), settings)
                }
            } catch (restoreError: Throwable) {
                withContext(NonCancellable) {
                    committed.asReversed().forEach { storeName ->
                        runCatching {
                            if (storeName == APP_SETTINGS_STORE_NAME) {
                                replaceSharedPreferences(appSettings(), originalAppSettings)
                            } else {
                                stores.getValue(storeName).updateData { originals.getValue(storeName) }
                            }
                        }.onFailure(restoreError::addSuppressed)
                    }
                }
                throw restoreError
            }

            PreferencesBackupOperationResult(
                items = preferenceStoreNames.map { storeName ->
                    PreferencesBackupItem(
                        id = storeName,
                        label = storeName.displayName(),
                        status = if (payload.stores.any { it.name == storeName }) {
                            PreferencesBackupItemStatus.Restored
                        } else if (storeName == APP_SETTINGS_STORE_NAME) {
                            PreferencesBackupItemStatus.Unchanged
                        } else {
                            PreferencesBackupItemStatus.Reset
                        }
                    )
                },
            )
        }
    }

    private fun readPayload(uri: Uri): PreferencesBackupPayload {
        val encoded = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBounded(MAX_ENVELOPE_BYTES).decodeToString()
        } ?: error("Unable to open backup file")
        return json.decodeFromString(encoded)
    }

    private fun validatePayload(payload: PreferencesBackupPayload) {
        require(payload.schemaVersion in MIN_SUPPORTED_SCHEMA_VERSION..CURRENT_SCHEMA_VERSION) {
            "Unsupported backup version"
        }
        require(payload.packageName == context.packageName) { "Backup belongs to a different app" }
        require(payload.stores.size <= preferenceStoreNames.size) { "Backup contains too many stores" }
        val names = payload.stores.map(PreferencesBackupStore::name)
        require(names.size == names.distinct().size) { "Backup contains duplicate stores" }
        require(names.all { it in preferenceStoreNames }) { "Backup contains an unknown store" }
        var declaredTotalBytes = 0L
        payload.stores.forEach { store ->
            require(store.encodedBytes.length <= MAX_ENCODED_STORE_CHARS) {
                "${store.name} is too large"
            }
            if (payload.schemaVersion >= INTEGRITY_SCHEMA_VERSION) {
                require(store.decodedSizeBytes != null && store.sha256 != null) {
                    "${store.name} is missing integrity metadata"
                }
                require(store.decodedSizeBytes in 0..MAX_STORE_BYTES) {
                    "${store.name} is too large"
                }
                declaredTotalBytes += store.decodedSizeBytes
                require(declaredTotalBytes <= MAX_TOTAL_STORE_BYTES) {
                    "Settings payload is too large"
                }
            }
        }
    }

    private suspend fun decodeDesiredPreferences(payload: PreferencesBackupPayload): Map<String, Preferences> {
        var totalDecodedBytes = 0L
        val decoded = payload.stores
            .filterNot { it.name == APP_SETTINGS_STORE_NAME }
            .associate { store ->
                val bytes = decodeStoreBytes(store, totalDecodedBytes)
                totalDecodedBytes += bytes.size
                store.name to PreferencesFileSerializer.readFrom(ByteArrayInputStream(bytes))
            }
        return livePreferenceStores().keys.associateWith { storeName ->
            decoded[storeName] ?: emptyPreferences()
        }
    }

    private fun decodeDesiredAppSettings(payload: PreferencesBackupPayload): Map<String, Any?>? {
        val store = payload.stores.firstOrNull { it.name == APP_SETTINGS_STORE_NAME } ?: return null
        val bytes = decodeStoreBytes(store, 0L)
        return json.decodeFromString<SharedPreferencesBackup>(bytes.decodeToString()).entries.mapValues { (_, value) ->
            value.toPreferenceValue()
        }
    }

    private fun decodeStoreBytes(store: PreferencesBackupStore, previouslyDecodedBytes: Long): ByteArray {
        val bytes = try {
            Base64.decode(store.encodedBytes, Base64.NO_WRAP)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("${store.name} is not valid Base64", error)
        }
        require(bytes.size <= MAX_STORE_BYTES) { "${store.name} is too large" }
        require(previouslyDecodedBytes + bytes.size <= MAX_TOTAL_STORE_BYTES) { "Settings payload is too large" }
        store.decodedSizeBytes?.let { expected ->
            require(bytes.size == expected) { "${store.name} has an invalid length" }
        }
        store.sha256?.let { expected ->
            require(bytes.sha256().equals(expected, ignoreCase = true)) {
                "${store.name} failed its integrity check"
            }
        }
        return bytes
    }

    private suspend fun serializePreferences(preferences: Preferences): ByteArray {
        val output = ByteArrayOutputStream()
        PreferencesFileSerializer.writeTo(preferences, output)
        return output.toByteArray()
    }

    private fun serializeSharedPreferences(values: Map<String, *>): ByteArray {
        val entries = values.mapValues { (key, value) ->
            value.toSharedPreferenceValue(key)
        }
        return json.encodeToString(SharedPreferencesBackup(entries)).encodeToByteArray()
    }

    private fun replaceSharedPreferences(preferences: SharedPreferences, values: Map<String, Any?>) {
        preferences.edit(commit = true) {
            clear()
            values.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Boolean -> putBoolean(key, value)
                    null -> Unit
                    else -> error("$key has an unsupported preference type")
                }
            }
        }
    }

    private fun Any?.toSharedPreferenceValue(key: String): SharedPreferenceValue = when (this) {
        is String -> SharedPreferenceValue(SharedPreferenceType.String, stringValue = this)
        is Set<*> -> SharedPreferenceValue(
            SharedPreferenceType.StringSet,
            stringSetValue = map { value ->
                value as? String ?: error("$key contains a non-string set value")
            }.toSet()
        )
        is Int -> SharedPreferenceValue(SharedPreferenceType.Int, longValue = toLong())
        is Long -> SharedPreferenceValue(SharedPreferenceType.Long, longValue = this)
        is Float -> SharedPreferenceValue(SharedPreferenceType.Float, floatValue = this)
        is Boolean -> SharedPreferenceValue(SharedPreferenceType.Boolean, booleanValue = this)
        else -> error("$key has an unsupported preference type")
    }

    private fun SharedPreferenceValue.toPreferenceValue(): Any = when (type) {
        SharedPreferenceType.String -> requireNotNull(stringValue)
        SharedPreferenceType.StringSet -> requireNotNull(stringSetValue)
        SharedPreferenceType.Int -> requireNotNull(longValue).let {
            require(it in Int.MIN_VALUE..Int.MAX_VALUE) { "Integer preference is out of range" }
            it.toInt()
        }
        SharedPreferenceType.Long -> requireNotNull(longValue)
        SharedPreferenceType.Float -> requireNotNull(floatValue)
        SharedPreferenceType.Boolean -> requireNotNull(booleanValue)
    }

    private fun String.displayName(): String = when (this) {
        THEME_STORE_NAME -> "Theme and appearance"
        ONBOARDING_STORE_NAME -> "Onboarding state"
        APP_SETTINGS_STORE_NAME -> "App and download settings"
        else -> this
    }

    private fun InputStream.readBounded(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(DEFAULT_BUFFER_SIZE, maxBytes))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "Settings backup is too large" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun ByteArray.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(this)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
