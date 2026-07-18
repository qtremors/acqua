package dev.qtremors.acqua.data.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSettingsRepositoryTest {
    @Test
    fun settingsPersistAndFolderIsSanitized() {
        val repository = AppSettingsRepository(ApplicationProvider.getApplicationContext())
        repository.setBaseFolder(" Acqua/Unsafe:* ")
        repository.setCategorizeMedia(true)
        repository.setFilenamePattern("{date}_{index}")

        val restored = AppSettingsRepository(ApplicationProvider.getApplicationContext()).downloadSettings()
        assertEquals(" Acqua/Unsafe:* ", restored.baseFolder)
        assertTrue(restored.categorizeMedia)
        assertEquals("{date}_{index}", restored.filenamePattern)
        assertEquals("Acqua_Unsafe__", repository.sanitizedBaseFolder(restored.baseFolder))
    }
}
