package com.classschedule.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedPolicyTest {
    @Test
    fun freshInstallCanInitialize() {
        assertFalse(SeedPolicy.hasInitialized(emptyMap<String, Any>()))
    }

    @Test
    fun permanentMarkerPreventsRefillingAnIntentionallyEmptyDatabase() {
        assertTrue(SeedPolicy.hasInitialized(mapOf(SeedPolicy.INITIALIZED_KEY to true)))
    }

    @Test
    fun anySuccessfulLegacySeedSurvivesVersionUpgrades() {
        for (version in listOf(1, 2, 12, 999)) {
            assertTrue(SeedPolicy.hasInitialized(mapOf("seeded_v$version" to true)))
        }
    }

    @Test
    fun oldSuccessIsStillRecognizedAlongsideNewFalseMarker() {
        assertTrue(SeedPolicy.hasInitialized(mapOf(
            "seeded_v1" to true,
            "seeded_v2" to false,
            SeedPolicy.INITIALIZED_KEY to false,
        )))
    }

    @Test
    fun unsuccessfulOrUnrelatedFlagsDoNotBlockInitialization() {
        assertFalse(SeedPolicy.hasInitialized(mapOf(
            "seeded_v1" to false,
            "seeded_v2" to "true",
            "seeded_version" to true,
            "seeded_v3_backup" to true,
            SeedPolicy.INITIALIZED_KEY to false,
        )))
    }
}
