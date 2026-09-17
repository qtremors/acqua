package dev.qtremors.acqua.data.backup

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesBackupPayloadTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Test
    fun `backup payload serializes and deserializes correctly`() {
        val payload = PreferencesBackupPayload(
            schemaVersion = 2,
            createdAtMillis = 1756543210000L,
            packageName = "dev.qtremors.acqua",
            stores = listOf(
                PreferencesBackupStore(
                    name = "acqua_theme_prefs",
                    encodedBytes = "AQIDBA==",
                    decodedSizeBytes = 4,
                    sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
                )
            )
        )

        val encoded = json.encodeToString(payload)
        assertTrue(encoded.contains("dev.qtremors.acqua"))
        assertTrue(encoded.contains("acqua_theme_prefs"))

        val decoded: PreferencesBackupPayload = json.decodeFromString(encoded)
        assertEquals(payload.schemaVersion, decoded.schemaVersion)
        assertEquals(payload.createdAtMillis, decoded.createdAtMillis)
        assertEquals(payload.packageName, decoded.packageName)
        assertEquals(1, decoded.stores.size)
        assertEquals("acqua_theme_prefs", decoded.stores[0].name)
        assertEquals("AQIDBA==", decoded.stores[0].encodedBytes)
        assertEquals(4, decoded.stores[0].decodedSizeBytes)
    }

    @Test
    fun `backup preview and item status are correctly modeled`() {
        val preview = PreferencesBackupPreview(
            createdAtMillis = 1756543210000L,
            items = listOf(
                PreferencesBackupItem("acqua_theme_prefs", "Theme and appearance", PreferencesBackupItemStatus.WillRestore),
                PreferencesBackupItem("acqua_onboarding_prefs", "Onboarding state", PreferencesBackupItemStatus.WillReset)
            )
        )

        assertEquals(2, preview.items.size)
        assertEquals(PreferencesBackupItemStatus.WillRestore, preview.items[0].status)
        assertEquals(PreferencesBackupItemStatus.WillReset, preview.items[1].status)
    }

    @Test
    fun `shared preferences backup preserves supported value types`() {
        val backup = SharedPreferencesBackup(
            entries = mapOf(
                "folder" to SharedPreferenceValue(SharedPreferenceType.String, stringValue = "Acqua"),
                "height" to SharedPreferenceValue(SharedPreferenceType.Int, longValue = 1080),
                "sessions" to SharedPreferenceValue(SharedPreferenceType.Boolean, booleanValue = true),
                "sites" to SharedPreferenceValue(
                    SharedPreferenceType.StringSet,
                    stringSetValue = setOf("example.com", "example.org")
                )
            )
        )

        val decoded = json.decodeFromString<SharedPreferencesBackup>(json.encodeToString(backup))

        assertEquals(backup, decoded)
        assertNotNull(decoded.entries["height"]?.longValue)
    }
}
