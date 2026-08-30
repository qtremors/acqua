package dev.qtremors.acqua.data.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingMigrationTest {

    @Test
    fun `updated installs skip first-run onboarding`() {
        assertTrue(
            shouldSkipOnboardingForExistingInstall(
                firstInstallTime = 1_000L,
                lastUpdateTime = 2_000L,
                hasLegacyPreferences = false
            )
        )
    }

    @Test
    fun `legacy preferences identify restored existing installs`() {
        assertTrue(
            shouldSkipOnboardingForExistingInstall(
                firstInstallTime = 2_000L,
                lastUpdateTime = 2_000L,
                hasLegacyPreferences = true
            )
        )
    }

    @Test
    fun `fresh installs retain first-run onboarding`() {
        assertFalse(
            shouldSkipOnboardingForExistingInstall(
                firstInstallTime = 2_000L,
                lastUpdateTime = 2_000L,
                hasLegacyPreferences = false
            )
        )
    }
}
