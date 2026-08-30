package io.github.rsgarrido.sazanami.lyrics

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsMatchingTest {
    @Test
    fun filenameNormalizationHandlesExtensionsWhitespaceAndMultipleDots() {
        assertEquals("thrill", normalizeFileStem("Thrill.flac"))
        assertEquals("thrill", normalizeFileStem("Thrill.LRC"))
        assertEquals("song.live", normalizeFileStem(" Song.live.flac "))
        assertTrue(hasLrcExtension("Track.LrC"))
    }

    @Test
    fun filenameNormalizationUsesNfcAndLocaleIndependentCase() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(normalizeFileStem("Cafe\u0301.FLAC"), normalizeFileStem("Café.lrc"))
            assertEquals("idol", normalizeFileStem("IDOL.FLAC"))
            assertEquals("アイドル", normalizeFileStem("アイドル.flac"))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun filenameNormalizationPreservesMeaningfulPunctuation() {
        assertNotEquals(normalizeFileStem("Song-live.flac"), normalizeFileStem("Song live.lrc"))
        assertEquals("01 - thrill", normalizeFileStem("01 - Thrill.flac"))
    }

    @Test
    fun pathNormalizationHandlesSlashesSeparatorsUnicodeAndEmptyPaths() {
        assertEquals("music/band/album", normalizeLyricsPath("\\Music//BAND\\Album/"))
        assertEquals("音楽/バンド", normalizeLyricsPath("/音楽//バンド/"))
        assertEquals("", normalizeLyricsPath(null))
        assertEquals("", normalizeLyricsPath("///"))
    }

    @Test
    fun pathSuffixRequiresCompleteMatchingDirectorySegments() {
        assertEquals(
            2,
            directorySuffixMatchDepth("Music/BAND-MAID/New Beginning", "BAND-MAID/New Beginning")
        )
        assertEquals(0, directorySuffixMatchDepth("Music/My Album", "Music/Album"))
        assertEquals(0, directorySuffixMatchDepth("", "Music/Album"))
    }

    @Test
    fun exactSiblingAndExactRelativeDirectoryMatch() {
        val result = LocalLyricsMatcher.match(
            song("Thrill.flac", "Music/Band/Album"),
            listOf(file("Thrill.lrc", "Music/Band/Album"))
        )

        assertTrue(result is LyricsMatchResult.Match)
    }

    @Test
    fun validDirectorySuffixMatchWins() {
        val preferred = file("Thrill.lrc", "BAND-MAID/New Beginning", uri = "content://preferred")
        val other = file("Thrill.lrc", "Other Album", uri = "content://other")

        assertEquals(
            preferred,
            (LocalLyricsMatcher.match(
                song("Thrill.flac", "Music/BAND-MAID/New Beginning"),
                listOf(other, preferred)
            ) as LyricsMatchResult.Match).file
        )
    }

    @Test
    fun uniqueExactStemFallsBackWithoutDirectoryMatch() {
        val only = file("Track.LRC", "Unrelated")

        assertEquals(
            only,
            (LocalLyricsMatcher.match(song("track.flac", ""), listOf(only))
                as LyricsMatchResult.Match).file
        )
    }

    @Test
    fun sameFilenameInTwoAlbumsIsAmbiguousWithoutDirectoryEvidence() {
        val result = LocalLyricsMatcher.match(
            song("Intro.flac", ""),
            listOf(file("Intro.lrc", "Album A", "content://a"), file("Intro.lrc", "Album B", "content://b"))
        )

        assertTrue(result is LyricsMatchResult.Ambiguous)
    }

    @Test
    fun volumeIdentityDistinguishesInternalStorageFromSdCard() {
        val internal = file(
            "Intro.lrc",
            "Music/Album",
            uri = "content://internal",
            volume = "primary"
        )
        val sdCard = file(
            "Intro.lrc",
            "Music/Album",
            uri = "content://sd",
            volume = "1234-5678"
        )

        val result = LocalLyricsMatcher.match(
            song("Intro.flac", "Music/Album", volume = "external_primary"),
            listOf(sdCard, internal)
        ) as LyricsMatchResult.Match

        assertEquals(internal, result.file)
    }

    @Test
    fun uniquelyStrongestPathCandidateWins() {
        val shallow = file("Track.lrc", "Album", "content://shallow")
        val deep = file("Track.lrc", "Artist/Album", "content://deep")

        val result = LocalLyricsMatcher.match(
            song("Track.flac", "Music/Artist/Album"),
            listOf(shallow, deep)
        ) as LyricsMatchResult.Match

        assertEquals(deep, result.file)
    }

    @Test
    fun equalStrongestCandidatesRemainAmbiguous() {
        val result = LocalLyricsMatcher.match(
            song("Track.flac", "Music/Album"),
            listOf(
                file("Track.lrc", "Album", "content://a"),
                file("Track.lrc", "Album", "content://b")
            )
        )

        assertTrue(result is LyricsMatchResult.Ambiguous)
    }

    @Test
    fun trackNumberStrippedStemMatchesExactSibling() {
        assertTrue(
            LocalLyricsMatcher.match(
                song("01 - Thrill.flac", "Music/Album"),
                listOf(file("Thrill.lrc", "Music/Album"))
            ) is LyricsMatchResult.Match
        )
    }

    @Test
    fun japaneseFilenameMatchesAndResultIsIndependentOfInputOrder() {
        val preferred = file("アイドル.LRC", "音楽/アルバム", "content://preferred")
        val other = file("アイドル.lrc", "別", "content://other")
        val identity = song("アイドル.flac", "Music/音楽/アルバム")

        val first = LocalLyricsMatcher.match(identity, listOf(preferred, other))
        val second = LocalLyricsMatcher.match(identity, listOf(other, preferred))

        assertEquals(first, second)
        assertEquals(preferred, (first as LyricsMatchResult.Match).file)
    }

    @Test
    fun commonMetadataCandidatesAreGeneratedInStablePriorityOrder() {
        val candidates = generateLyricsNameCandidates(
            song(
                name = "08 Forest.flac",
                relativeDirectory = "Music/System of A Down",
                title = "Forest",
                artist = "System of A Down"
            )
        )

        assertEquals(
            listOf(
                "08 Forest",
                "Forest",
                "System of A Down - Forest",
                "Forest - System of A Down"
            ),
            candidates.map(LyricsNameCandidate::displayStem)
        )
        assertEquals(LyricsNameCandidateSource.AUDIO_STEM, candidates.first().source)
    }

    @Test
    fun physicalDeviceForestAndCherishFilesMatchExactly() {
        val forest = song(
            "08 Forest.flac",
            "Music/System of A Down",
            title = "Forest",
            artist = "System of A Down"
        )
        val cherish = song(
            "ILLIT - Cherish (My Love).flac",
            "Music/ILLIT",
            title = "Cherish (My Love)",
            artist = "ILLIT"
        )

        assertEquals(
            "System of A Down - Forest.lrc",
            (LocalLyricsMatcher.match(
                forest,
                listOf(file("System of A Down - Forest.lrc", "Music/System of A Down"))
            ) as LyricsMatchResult.Match).file.displayName
        )
        assertEquals(
            "Cherish (My Love) - ILLIT.lrc",
            (LocalLyricsMatcher.match(
                cherish,
                listOf(file("Cherish (My Love) - ILLIT.lrc", "Music/ILLIT"))
            ) as LyricsMatchResult.Match).file.displayName
        )
    }

    @Test
    fun trackPrefixesAreConservative() {
        assertEquals("Forest", stripLeadingTrackNumber("08 Forest"))
        assertEquals("Intro", stripLeadingTrackNumber("01 - Intro"))
        assertEquals("Forest", stripLeadingTrackNumber("1-08 Forest"))
        assertEquals("Forest", stripLeadingTrackNumber("1.08 Forest"))
        assertEquals("LOVE 2000", stripLeadingTrackNumber("LOVE 2000"))
        assertEquals("1985", stripLeadingTrackNumber("1985"))
        assertEquals("22", stripLeadingTrackNumber("22"))
    }

    @Test
    fun candidatesPreserveMeaningfulTextAndNormalizeOnlySeparatorVariants() {
        val identity = song(
            "01 Song-live.flac",
            "Music",
            title = "Song-live (Remix)",
            artist = "BANDâ€”NAME",
            albumArtist = "ãƒãƒ³ãƒ‰"
        )
        val candidates = generateLyricsNameCandidates(identity)

        assertTrue(candidates.any { it.displayStem == "Song-live (Remix)" })
        assertTrue(candidates.any { it.displayStem == "ãƒãƒ³ãƒ‰ - Song-live (Remix)" })
        assertEquals(
            normalizeLyricsCandidateStem("Artist - Title"),
            normalizeLyricsCandidateStem("Artist \u2013 Title")
        )
        assertEquals(
            normalizeLyricsCandidateStem("Artist - Title"),
            normalizeLyricsCandidateStem("Artist \u2014 Title")
        )
        assertNotEquals(
            normalizeLyricsCandidateStem("Song-live"),
            normalizeLyricsCandidateStem("Song live")
        )
    }

    @Test
    fun duplicateCandidatesAndUnknownArtistsAreIgnored() {
        val candidates = generateLyricsNameCandidates(
            song(
                "Forest.flac",
                "Music",
                title = "Forest",
                artist = "Unknown Artist",
                albumArtist = "<unknown>"
            )
        )

        assertEquals(listOf("Forest"), candidates.map(LyricsNameCandidate::displayStem))
    }

    @Test
    fun directSiblingStemBeatsMetadataAliasButDirectoryEvidenceWinsGlobally() {
        val identity = song(
            "08 Forest.flac",
            "Music/Album",
            title = "Forest",
            artist = "System of A Down"
        )
        val directSibling = file("08 Forest.lrc", "Music/Album", "content://direct")
        val aliasSibling = file(
            "System of A Down - Forest.lrc",
            "Music/Album",
            "content://alias"
        )
        assertEquals(
            directSibling,
            (LocalLyricsMatcher.match(identity, listOf(aliasSibling, directSibling))
                    as LyricsMatchResult.Match).file
        )

        val directElsewhere = file("08 Forest.lrc", "Elsewhere", "content://elsewhere")
        assertEquals(
            aliasSibling,
            (LocalLyricsMatcher.match(identity, listOf(directElsewhere, aliasSibling))
                    as LyricsMatchResult.Match).file
        )
    }

    @Test
    fun equallyRankedAliasesRemainAmbiguousIndependentOfInputOrder() {
        val identity = song("08 Forest.flac", "", title = "Forest")
        val first = file("Forest.lrc", "A", "content://a")
        val second = file("Forest.lrc", "B", "content://b")

        val forward = LocalLyricsMatcher.match(identity, listOf(first, second))
        val reverse = LocalLyricsMatcher.match(identity, listOf(second, first))

        assertTrue(forward is LyricsMatchResult.Ambiguous)
        assertEquals(forward, reverse)
    }

    private fun song(
        name: String,
        relativeDirectory: String,
        volume: String? = null,
        title: String = "",
        artist: String = "",
        albumArtist: String = ""
    ) = SongLyricsIdentity(
        audioFileName = name,
        title = title,
        artist = artist,
        albumArtist = albumArtist,
        relativeDirectory = relativeDirectory,
        fallbackDirectory = "",
        volumeId = volume
    )

    private fun file(
        name: String,
        directory: String,
        uri: String = "content://lyrics/$name",
        volume: String? = null
    ) = IndexedLyricsFile(
        documentUri = uri,
        rootUri = "content://root",
        displayName = name,
        normalizedStem = normalizeFileStem(name),
        relativeDirectory = directory,
        rootVolumeId = volume
    )
}
