package com.example.cdplaya.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.example.cdplaya.player.audio.AudioOffloadPreference
import org.junit.Assert.fail
import org.junit.Test

class AppBackupJsonTest {
    @Test
    fun encodeBackup_includesCurrentSchemaVersion() {
        val encoded = AppBackupJson.encodeBackup(emptyBackup())

        assertTrue(
            encoded.contains("\"schemaVersion\":${AppBackupJson.CURRENT_SCHEMA_VERSION}")
        )
    }

    @Test
    fun encodeBackup_includesAllBackupSections() {
        val encoded = AppBackupJson.encodeBackup(emptyBackup())

        listOf(
            "schemaVersion",
            "favorites",
            "playlists",
            "listeningHistory",
            "canonicalListeningHistory",
            "songRatings",
            "preferences"
        ).forEach { key ->
            assertTrue("Missing JSON key: $key", encoded.contains("\"$key\""))
        }
    }

    @Test
    fun decodeBackup_decodesValidBackupAndIgnoresUnknownKeys() {
        val decoded = AppBackupJson.decodeBackup(
            """
            {
              "schemaVersion": 2,
              "createdAt": 123,
              "appName": "CDPlaya",
              "favorites": [],
              "playlists": [],
              "listeningHistory": [],
              "preferences": {
                "selectedLibraryFolders": ["/Music"],
                "selectedPlayerThemeId": "classic",
                "replayGainMode": "album",
                "futurePreference": true
              },
              "futureField": "ignored"
            }
            """.trimIndent()
        )

        assertEquals(123L, decoded.createdAt)
        assertEquals(listOf("/Music"), decoded.preferences.selectedLibraryFolders)
        assertEquals("classic", decoded.preferences.selectedPlayerThemeId)
        assertEquals("album", decoded.preferences.replayGainMode)
        assertEquals(
            AudioOffloadPreference.DISABLED,
            AudioOffloadPreference.fromStorageValue(decoded.preferences.audioOffloadPreference)
        )
    }

    @Test
    fun decodeBackup_migratesV1PreferencesReferencesAndHistoryToV8() {
        val decoded = AppBackupJson.decodeBackup(
            """
            {
              "schemaVersion": 1,
              "createdAt": 123,
              "preferences": {
                "selectedLibraryFolders": ["/Music"],
                "selectedPlayerThemeId": "classic_wheel",
                "replayGainMode": "OFF"
              }
            }
            """.trimIndent()
        )

        assertEquals(AppBackupJson.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals("slide", decoded.preferences.modernArtworkTransitionStyle)
        assertEquals("classic_bar", decoded.preferences.modernSeekbarStyle)
        assertEquals(emptyMap<String, BackupPlayerThemeTokenOverrides>(), decoded.preferences.playerThemeTokenOverrides)
        assertEquals("list", decoded.preferences.songsViewMode)
        assertEquals(2, decoded.preferences.songsGridColumnCount)
        assertEquals("classic_wheel", decoded.preferences.selectedPlayerThemeId)
        assertEquals(
            BackupEqualizerPreferences(),
            decoded.preferences.equalizer
        )
    }

    @Test
    fun v5Backup_roundTripsAllDurablePreferenceAndReferenceFields() {
        val preferences = BackupPreferences(
            selectedLibraryFolders = listOf("Music"),
            selectedPlayerThemeId = "retro_rack",
            replayGainMode = "TRACK",
            audioOffloadPreference = "AUTOMATIC",
            modernArtworkTransitionStyle = "cover_flow",
            modernSeekbarStyle = "waveform_glow",
            playerThemeTokenOverrides = mapOf(
                "retro_rack" to BackupPlayerThemeTokenOverrides(
                    shellArgb = 0xFF010203L,
                    accentArgb = 0xFFAABBCCL,
                    secondaryAccentArgb = 0xFF102030L
                )
            ),
            songsViewMode = "grid",
            albumsViewMode = "list",
            artistsViewMode = "grid",
            songsGridColumnCount = 4,
            albumsGridColumnCount = 3,
            artistsGridColumnCount = 2
        )
        val reference = BackupSongReference(
            relativePath = "Music/Album/",
            displayName = "track.flac",
            fileSizeBytes = 42L,
            duration = 1_000L,
            title = "Track",
            artist = "Artist",
            album = "Album",
            legacyStableKey = "legacy",
            portableKey = "portable:v1:key"
        )
        val backup = emptyBackup().copy(
            preferences = preferences,
            favorites = listOf(
                BackupFavoriteSong("legacy", "Track", "Artist", "Album", 1_000L, 3L, reference)
            )
        )

        val decoded = AppBackupJson.decodeBackup(AppBackupJson.encodeBackup(backup))

        assertEquals(preferences, decoded.preferences)
        assertEquals(reference, decoded.favorites.single().reference)
    }

    @Test
    fun v2FixtureStillRestoresAsLegacyReference() {
        val decoded = AppBackupJson.decodeBackup(
            """
            {
              "schemaVersion": 2,
              "createdAt": 123,
              "favorites": [{
                "songKey": "old-key",
                "title": "Track",
                "artist": "Artist",
                "album": "Album",
                "duration": 1000,
                "createdAt": 9
              }]
            }
            """.trimIndent()
        )

        assertEquals(AppBackupJson.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals("old-key", decoded.favorites.single().reference?.legacyStableKey)
    }

    @Test
    fun decodeBackup_rejectsUnsupportedSchemaVersion() {
        val exception = expectIllegalArgumentException {
            AppBackupJson.decodeBackup(
                AppBackupJson.encodeBackup(
                    emptyBackup().copy(schemaVersion = AppBackupJson.CURRENT_SCHEMA_VERSION + 1)
                )
            )
        }

        assertTrue(
            exception.message.orEmpty().contains(
                "Unsupported CDPlaya backup schema version " +
                    (AppBackupJson.CURRENT_SCHEMA_VERSION + 1)
            )
        )
    }

    @Test
    fun decodeBackup_rejectsVersion7WithoutCanonicalHistorySection() {
        val exception = expectIllegalArgumentException {
            AppBackupJson.decodeBackup("{\"schemaVersion\":7,\"createdAt\":1}")
        }

        assertTrue(exception.message.orEmpty().contains("requires canonical listening history"))
    }

    @Test
    fun emptyBackup_roundTrips() {
        val backup = emptyBackup()

        assertEquals(backup, AppBackupJson.decodeBackup(AppBackupJson.encodeBackup(backup)))
    }

    @Test
    fun backup9MigratesToBackup10WithFormat2AndNoInferredReconciliations() {
        val backup9 = emptyBackup().copy(
            schemaVersion = 9,
            canonicalListeningHistory = BackupListeningHistoryV2(formatVersion = 1)
        )

        val decoded = AppBackupJson.decodeBackup(AppBackupJson.encodeBackup(backup9))

        assertEquals(AppBackupJson.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(2, decoded.canonicalListeningHistory?.formatVersion)
        assertTrue(decoded.canonicalListeningHistory?.reconciliations.orEmpty().isEmpty())
    }

    @Test
    fun backup10SerializesReconciliationThroughBackupIdentityIdsOnly() {
        val encoded = AppBackupJson.encodeBackup(
            emptyBackup().copy(
                canonicalListeningHistory = BackupListeningHistoryV2(
                    reconciliations = listOf(
                        BackupListeningIdentityReconciliation(
                            sourceIdentityBackupId = 7L,
                            targetIdentityBackupId = 9L,
                            reconciledAt = 123L
                        )
                    )
                )
            )
        )

        assertTrue(encoded.contains("\"sourceIdentityBackupId\":7"))
        assertTrue(encoded.contains("\"targetIdentityBackupId\":9"))
        assertTrue(encoded.contains("\"reconciledAt\":123"))
        assertFalse(encoded.contains("\"sourceIdentityId\""))
        assertFalse(encoded.contains("\"targetIdentityId\""))
    }

    @Test
    fun v5EqualizerLimiterAndUserPresetsRoundTripWithoutRuntimeState() {
        val equalizer = BackupEqualizerPreferences(
            enabled = true,
            preampDb = -2.5,
            automaticHeadroomEnabled = false,
            bandGainsDb = listOf(
                4.0, 3.5, 2.5, 1.0, 0.0,
                -0.5, -1.0, -1.5, -2.0, -2.5
            ),
            limiterEnabled = true,
            limiterCeilingDbfs = -2.3,
            userPresets = listOf(
                BackupEqualizerPreset(
                    id = "stable-id",
                    name = "Road",
                    preampDb = -1.0,
                    automaticHeadroomEnabled = true,
                    bandGainsDb = List(10) { index ->
                        index / 10.0
                    }
                )
            )
        )
        val encoded = AppBackupJson.encodeBackup(
            emptyBackup().copy(
                preferences = BackupPreferences(
                    equalizer = equalizer
                )
            )
        )
        val decoded = AppBackupJson.decodeBackup(encoded)

        assertEquals(
            equalizer,
            decoded.preferences.equalizer
        )
        assertTrue(!encoded.contains("comparisonBypassed"))
        assertTrue(!encoded.contains("runtimeState"))
        assertTrue(!encoded.contains("Bass Lift"))
        assertTrue(!encoded.contains("preLimiterPeakDbfs"))
        assertTrue(!encoded.contains("limiterPrimed"))
    }

    @Test
    fun v4BackupMigratesToDisabledDefaultLimiter() {
        val decoded = AppBackupJson.decodeBackup(
            """
            {
              "schemaVersion": 4,
              "createdAt": 123,
              "preferences": {
                "equalizer": {
                  "enabled": true,
                  "preampDb": -2.0,
                  "automaticHeadroomEnabled": false,
                  "bandGainsDb": [0,0,0,0,0,0,0,0,0,0],
                  "userPresets": []
                }
              }
            }
            """.trimIndent()
        )

        assertEquals(AppBackupJson.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertFalse(decoded.preferences.equalizer.limiterEnabled)
        assertEquals(
            -1.0,
            decoded.preferences.equalizer.limiterCeilingDbfs,
            0.0
        )
        assertEquals(-2.0, decoded.preferences.equalizer.preampDb, 0.0)
        assertEquals("GRAPHIC", decoded.preferences.equalizer.mode)
        assertTrue(decoded.preferences.equalizer.parametricFilters.isEmpty())
    }

    @Test
    fun v6ParametricEqualizerRoundTripsEveryFilterTypeAndOrder() {
        val filters = listOf(
            BackupParametricFilter(
                "peak", "PEAKING", true, 1_000.0,
                gainDb = 3.5, q = 1.25
            ),
            BackupParametricFilter(
                "low-shelf", "LOW_SHELF", true, 100.0,
                gainDb = 4.0, slope = 0.8
            ),
            BackupParametricFilter(
                "high-shelf", "HIGH_SHELF", false, 8_000.0,
                gainDb = -2.0, slope = 1.0
            ),
            BackupParametricFilter(
                "low-pass", "LOW_PASS", true, 16_000.0, q = 0.71
            ),
            BackupParametricFilter(
                "high-pass", "HIGH_PASS", true, 40.0, q = 0.8
            ),
            BackupParametricFilter(
                "notch", "NOTCH", true, 2_000.0, q = 8.0
            ),
            BackupParametricFilter(
                "band-pass", "BAND_PASS", true, 500.0, q = 2.0
            )
        )
        val equalizer = BackupEqualizerPreferences(
            enabled = true,
            mode = "PARAMETRIC",
            parametricPreampDb = -2.5,
            parametricAutomaticHeadroomEnabled = false,
            parametricFilters = filters,
            parametricUserPresets = listOf(
                BackupParametricEqualizerPreset(
                    id = "preset-id",
                    name = "Headphones",
                    preampDb = -1.0,
                    automaticHeadroomEnabled = true,
                    filters = filters.reversed()
                )
            )
        )

        val decoded = AppBackupJson.decodeBackup(
            AppBackupJson.encodeBackup(
                emptyBackup().copy(
                    preferences = BackupPreferences(
                        equalizer = equalizer
                    )
                )
            )
        )

        assertEquals(equalizer, decoded.preferences.equalizer)
        assertEquals(
            filters.map { it.id },
            decoded.preferences.equalizer.parametricFilters
                .map { it.id }
        )
    }

    @Test
    fun v6RejectsDuplicateOverLimitAndMalformedParametricFilters() {
        val filter = BackupParametricFilter(
            "same", "PEAKING", true, 1_000.0,
            gainDb = 3.0, q = 1.0
        )
        listOf(
            listOf(filter, filter),
            List(11) { index -> filter.copy(id = "$index") },
            listOf(
                filter.copy(
                    id = "bad",
                    type = "LOW_PASS",
                    gainDb = 3.0
                )
            )
        ).forEach { filters ->
            val malformed = emptyBackup().copy(
                preferences = BackupPreferences(
                    equalizer = BackupEqualizerPreferences(
                        parametricFilters = filters
                    )
                )
            )
            expectIllegalArgumentException {
                AppBackupJson.decodeBackup(
                    AppBackupJson.encodeBackup(malformed)
                )
            }
        }
    }

    @Test
    fun invalidLimiterCeilingIsRejected() {
        val malformed = emptyBackup().copy(
            preferences = BackupPreferences(
                equalizer = BackupEqualizerPreferences(
                    limiterEnabled = true,
                    limiterCeilingDbfs = -3.1
                )
            )
        )

        expectIllegalArgumentException {
            AppBackupJson.decodeBackup(
                AppBackupJson.encodeBackup(malformed)
            )
        }
    }

    @Test
    fun malformedEqualizerBandCountsAndReservedNamesAreRejected() {
        val malformed = emptyBackup().copy(
            preferences = BackupPreferences(
                equalizer = BackupEqualizerPreferences(
                    bandGainsDb = List(9) { 0.0 }
                )
            )
        )
        expectIllegalArgumentException {
            AppBackupJson.decodeBackup(
                AppBackupJson.encodeBackup(malformed)
            )
        }

        val reserved = emptyBackup().copy(
            preferences = BackupPreferences(
                equalizer = BackupEqualizerPreferences(
                    userPresets = listOf(
                        BackupEqualizerPreset(
                            id = "id",
                            name = "bass lift",
                            preampDb = 0.0,
                            automaticHeadroomEnabled = true,
                            bandGainsDb = List(10) { 0.0 }
                        )
                    )
                )
            )
        )
        expectIllegalArgumentException {
            AppBackupJson.decodeBackup(
                AppBackupJson.encodeBackup(reserved)
            )
        }
    }

    @Test
    fun decodeBackup_rejectsInvalidJsonWithClearMessage() {
        val exception = expectIllegalArgumentException {
            AppBackupJson.decodeBackup("not json")
        }

        assertEquals("Invalid CDPlaya backup JSON.", exception.message)
    }

    @Test
    fun encodedBackup_doesNotContainWaveformOrDerivedCacheData() {
        val encoded = AppBackupJson.encodeBackup(emptyBackup())

        assertTrue(!encoded.contains("waveform", ignoreCase = true))
        assertTrue(!encoded.contains("cache", ignoreCase = true))
    }

    @Test
    fun portableSongReferenceOmitsAbsoluteAndDeviceLocalPaths() {
        val reference = com.example.cdplaya.data.SongReference(
            mediaStoreId = 44L,
            contentUri = "content://media/external/audio/44",
            relativePath = "/storage/emulated/0/Music",
            displayName = "track.flac",
            title = "Track",
            artist = "Artist",
            album = "Album",
            duration = 1_000L,
            portableKey = "portable:v1:key"
        ).toBackupSongReference()
        val encoded = AppBackupJson.encodeBackup(
            emptyBackup().copy(
                preferences = BackupPreferences(
                    selectedLibraryFolders = listOf("/private/music"),
                    homePins = listOf(
                        BackupHomePin(
                            id = "pin-private",
                            type = "SONG",
                            title = "Track",
                            anchor = BackupSongReference(
                                relativePath = "/private/pinned",
                                displayName = "track.flac"
                            )
                        )
                    )
                ),
                favorites = listOf(
                    BackupFavoriteSong("legacy", "Track", "Artist", "Album", 1_000L, 1L, reference)
                )
            )
        )

        assertEquals("", reference.relativePath)
        assertTrue(!encoded.contains("/storage/"))
        assertTrue(!encoded.contains("/private/"))
        assertTrue(encoded.contains("private/music"))
        assertTrue(!encoded.contains("content://"))
        assertTrue(!encoded.contains("mediaStoreId"))
    }

    @Test
    fun folderSelectionsBecomePortableRelativeTokens() {
        assertEquals("Music/Rock", "/storage/emulated/0/Music/Rock".toPortableFolderSelection())
        assertEquals("Music/Rock", "/storage/ABCD-1234/Music/Rock".toPortableFolderSelection())
        assertEquals("Music/Rock", "/sdcard/Music/Rock".toPortableFolderSelection())
    }


    @Test
    fun homePinsAndRecentlyAddedVisibilityRoundTripWithPreferences() {
        val pin = BackupHomePin(
            id = "pin-1",
            type = "ALBUM",
            title = "Pinned Album",
            subtitle = "Pinned Artist",
            anchor = BackupSongReference(
                relativePath = "Music/Pinned Album/",
                displayName = "01.flac",
                title = "Track",
                artist = "Pinned Artist",
                album = "Pinned Album"
            )
        )
        val backup = emptyBackup().copy(
            preferences = BackupPreferences(
                homePins = listOf(pin),
                showRecentlyAddedOnHome = false
            )
        )

        val decoded = AppBackupJson.decodeBackup(AppBackupJson.encodeBackup(backup))

        assertEquals(listOf(pin), decoded.preferences.homePins)
        assertFalse(decoded.preferences.showRecentlyAddedOnHome)
    }

    private fun emptyBackup() = AppBackup(
        createdAt = 123L,
        canonicalListeningHistory = BackupListeningHistoryV2()
    )

    private fun expectIllegalArgumentException(block: () -> Unit): IllegalArgumentException {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (exception: IllegalArgumentException) {
            return exception
        }

        throw AssertionError("Expected IllegalArgumentException")
    }
}
