package io.github.rsgarrido.sazanami.data

import java.text.Normalizer
import java.util.Locale

/**
 * Comparison-only metadata. Raw values remain available for display and diagnostics; none of the
 * normalized values are persisted back to imported history or local tags.
 */
data class ReconciliationComparisonMetadata(
    val rawTitle: String,
    val rawArtist: String,
    val rawAlbum: String,
    val ordinaryTitle: String,
    val ordinaryArtist: String,
    val ordinaryAlbum: String,
    val canonicalTitle: String,
    val canonicalArtist: String,
    val canonicalAlbum: String
)

internal fun reconciliationComparisonMetadata(
    title: String,
    artist: String,
    album: String
) = ReconciliationComparisonMetadata(
    rawTitle = title,
    rawArtist = artist,
    rawAlbum = album,
    ordinaryTitle = candidateConservativeNormalize(title),
    ordinaryArtist = candidateConservativeNormalize(artist),
    ordinaryAlbum = candidateConservativeNormalize(album),
    canonicalTitle = candidateCanonicalNormalize(title),
    canonicalArtist = candidateCanonicalNormalize(artist),
    canonicalAlbum = candidateCanonicalNormalize(album)
)

/** Ordinary exact-match normalization: locale-independent case normalization and edge trimming. */
internal fun candidateConservativeNormalize(value: String): String = value
    .lowercase(Locale.ROOT)
    .trim()

/**
 * Safe canonical comparison normalization. This deliberately does not remove punctuation or
 * combining marks. It only folds typographical representations that do not change metadata
 * meaning, plus common UTF-8/legacy-decoder punctuation artifacts seen in local tags.
 */
internal fun candidateCanonicalNormalize(value: String): String = Normalizer
    .normalize(value, Normalizer.Form.NFC)
    .replace("\u00e2\u20ac\u2122", "'") // UTF-8 right single quote decoded as Windows-1252
    .replace("\u00e2\u20ac\u02dc", "'") // UTF-8 left single quote decoded as Windows-1252
    .replace("\u00e2\u20ac\u0153", "\"") // UTF-8 left double quote decoded as Windows-1252
    .replace("\u00e2\u20ac\u009d", "\"") // UTF-8 right double quote decoded as Windows-1252
    .replace("\u00e2\u20ac\u201c", "-") // UTF-8 en dash decoded as Windows-1252
    .replace("\u00e2\u20ac\u201d", "-") // UTF-8 em dash decoded as Windows-1252
    .replace("\u00e2\u20ac\u00a6", "...") // UTF-8 ellipsis decoded as Windows-1252
    .replace("\u00e2\u0080\u0098", "'") // UTF-8 left single quote decoded as Latin-1
    .replace("\u00e2\u0080\u0099", "'") // UTF-8 right single quote decoded as Latin-1
    .replace("\u00e2\u0080\u009c", "\"") // UTF-8 left double quote decoded as Latin-1
    .replace("\u00e2\u0080\u009d", "\"") // UTF-8 right double quote decoded as Latin-1
    .replace("\u00e2\u0080\u0093", "-") // UTF-8 en dash decoded as Latin-1
    .replace("\u00e2\u0080\u0094", "-") // UTF-8 em dash decoded as Latin-1
    .replace("\u00e2\u0080\u00a6", "...") // UTF-8 ellipsis decoded as Latin-1
    .replace(Regex("[\\u2018\\u2019\\u201a\\u201b\\u2032\\u2035\\uff07\\u02bc]"), "'")
    .replace(Regex("[\\u201c\\u201d\\u201e\\u201f\\u2033\\u2036\\uff02]"), "\"")
    .replace(Regex("[\\u2010-\\u2015\\u2212\\ufe58\\ufe63\\uff0d]"), "-")
    .replace("\u2026", "...")
    .replace(Regex("[\\u200b\\ufeff]"), "")
    .lowercase(Locale.ROOT)
    .replace(Regex("[\\p{Z}\\s]+"), " ")
    .trim()

/** Accent folding is intentionally a weaker lookup signal, never canonical equivalence. */
internal fun candidateAccentNormalize(value: String): String = Normalizer
    .normalize(candidateCanonicalNormalize(value), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")

/** Bounded punctuation folding is intentionally a weaker lookup signal. */
internal fun candidatePunctuationNormalize(value: String): String = candidateAccentNormalize(value)
    .replace(Regex("(?<=\\p{L})\\.(?=\\p{L})"), "")
    .replace(Regex("\\s*#\\s*"), "#")
    .replace(Regex("[\\p{Z}\\s]+"), " ")
    .trim()
