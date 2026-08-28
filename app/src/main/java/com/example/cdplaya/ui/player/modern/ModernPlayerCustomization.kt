package com.example.cdplaya.ui.player.modern

enum class ModernArtworkTransitionStyle(
    val storageValue: String,
    val displayName: String,
    val description: String
) {
    SLIDE(
        storageValue = "slide",
        displayName = "Slide",
        description = "Current artwork slides naturally with the next cover."
    ),
    DEPTH_SCALE(
        storageValue = "depth_scale",
        displayName = "Depth & Scale",
        description = "Covers shrink and fade slightly as they move, creating depth."
    ),
    PARALLAX(
        storageValue = "parallax",
        displayName = "Parallax",
        description = "Artwork and text move at different speeds."
    ),
    COVER_FLOW(
        storageValue = "cover_flow",
        displayName = "Cover Flow",
        description = "Covers tilt slightly as they move off-center."
    ),
    STACK_REVEAL(
        storageValue = "stack_reveal",
        displayName = "Stack Reveal",
        description = "Current cover peels away to reveal the next cover underneath."
    );

    companion object {
        fun fromStorageValue(storageValue: String?): ModernArtworkTransitionStyle {
            return values().firstOrNull { style ->
                style.storageValue == storageValue
            } ?: SLIDE
        }
    }
}

enum class ModernSeekbarStyle(
    val storageValue: String,
    val displayName: String,
    val description: String
) {
    CLASSIC_BAR(
        storageValue = "classic_bar",
        displayName = "Classic Bar",
        description = "The current simple progress bar."
    ),
    SLIM_LINE(
        storageValue = "slim_line",
        displayName = "Slim Line",
        description = "A minimal thin progress line."
    ),
    THICK_CAPSULE(
        storageValue = "thick_capsule",
        displayName = "Thick Capsule",
        description = "A larger rounded progress control."
    ),
    SEGMENTED(
        storageValue = "segmented",
        displayName = "Segmented",
        description = "Small separated blocks that fill with progress."
    ),
    WAVEFORM_PREVIEW(
        storageValue = "waveform_preview",
        displayName = "Waveform Preview",
        description = "Uses analyzed track shape when available, with a generated preview while loading or unsupported."
    ),
    WAVEFORM_PEAKS(
        storageValue = "waveform_peaks",
        displayName = "Waveform Peaks",
        description = "Mirrors analyzed track peaks when available, with a generated preview as fallback."
    ),
    WAVEFORM_GLOW(
        storageValue = "waveform_glow",
        displayName = "Waveform Glow",
        description = "Adds a soft glow to analyzed track shape, with a generated preview as fallback."
    ),
    CONTINUOUS_WAVEFORM(
        storageValue = "continuous_waveform",
        displayName = "Continuous Waveform",
        description = "A connected, filled waveform silhouette."
    ),
    WAVE_LINE(
        storageValue = "wave_line",
        displayName = "Wave Line",
        description = "A smooth line that follows the track's waveform."
    );

    val usesWaveformData: Boolean
        get() = when (this) {
            WAVEFORM_PREVIEW, WAVEFORM_PEAKS, WAVEFORM_GLOW,
            CONTINUOUS_WAVEFORM, WAVE_LINE -> true
            CLASSIC_BAR, SLIM_LINE, THICK_CAPSULE, SEGMENTED -> false
        }

    val usesContinuousPath: Boolean
        get() = this == CONTINUOUS_WAVEFORM || this == WAVE_LINE

    companion object {
        fun fromStorageValue(storageValue: String?): ModernSeekbarStyle {
            return values().firstOrNull { style ->
                style.storageValue == storageValue
            } ?: WAVEFORM_PREVIEW
        }
    }
}

enum class ModernWaveformSize(
    val storageValue: String,
    val displayName: String,
    internal val trackHeightDp: Int
) {
    COMPACT("compact", "Compact", 24),
    STANDARD("standard", "Standard", 32),
    TALL("tall", "Tall", 64);

    companion object {
        fun fromStorageValue(storageValue: String?): ModernWaveformSize =
            entries.firstOrNull { it.storageValue == storageValue } ?: STANDARD
    }
}

enum class ModernWaveformDensity(
    val storageValue: String,
    val displayName: String,
    internal val barCount: Int,
    internal val gapDp: Int
) {
    SPARSE("sparse", "Sparse", 32, 3),
    BALANCED("balanced", "Balanced", 48, 2),
    DETAILED("detailed", "Detailed", 72, 1);

    companion object {
        fun fromStorageValue(storageValue: String?): ModernWaveformDensity =
            entries.firstOrNull { it.storageValue == storageValue } ?: BALANCED
    }
}

enum class ModernSeekbarColorMode(
    val storageValue: String,
    val displayName: String
) {
    WHITE("white", "White"),
    APP_ACCENT("app_accent", "App Accent"),
    ALBUM_DERIVED("album_derived", "Album Derived");

    companion object {
        fun fromStorageValue(storageValue: String?): ModernSeekbarColorMode =
            entries.firstOrNull { it.storageValue == storageValue } ?: WHITE
    }
}

enum class ModernBackgroundStyle(
    val storageValue: String,
    val displayName: String,
    val description: String,
    val supportsBlur: Boolean,
    val supportsDimming: Boolean
) {
    BLURRED_ARTWORK(
        "blurred_artwork",
        "Blurred Artwork",
        "Immersive album artwork with a soft cinematic blur.",
        supportsBlur = true,
        supportsDimming = true
    ),
    DETAILED_ARTWORK(
        "detailed_artwork",
        "Detailed Artwork",
        "Sharper album artwork with a readability overlay.",
        supportsBlur = true,
        supportsDimming = true
    ),
    ALBUM_GRADIENT(
        "album_gradient",
        "Album Gradient",
        "A multi-color gradient derived from the current artwork.",
        supportsBlur = false,
        supportsDimming = true
    ),
    SOLID_COLOR(
        "solid_color",
        "Solid Color",
        "A user-selected color without artwork.",
        supportsBlur = false,
        supportsDimming = false
    ),
    PURE_BLACK(
        "pure_black",
        "Pure Black",
        "True black for a focused OLED-friendly background.",
        supportsBlur = false,
        supportsDimming = false
    );

    companion object {
        fun fromStorageValue(storageValue: String?): ModernBackgroundStyle =
            entries.firstOrNull { it.storageValue == storageValue } ?: BLURRED_ARTWORK
    }
}

enum class ModernBlurStrength(
    val storageValue: String,
    val displayName: String,
    internal val blurredArtworkRadiusDp: Int,
    internal val detailedArtworkRadiusDp: Int
) {
    LOW("low", "Low", 24, 2),
    MEDIUM("medium", "Medium", 42, 6),
    HIGH("high", "High", 64, 10);

    companion object {
        fun fromStorageValue(storageValue: String?): ModernBlurStrength =
            entries.firstOrNull { it.storageValue == storageValue } ?: MEDIUM
    }
}

enum class ModernDimmingStrength(
    val storageValue: String,
    val displayName: String,
    internal val overlayAlpha: Float
) {
    LOW("low", "Low", 0.30f),
    MEDIUM("medium", "Medium", 0.44f),
    HIGH("high", "High", 0.58f);

    companion object {
        fun fromStorageValue(storageValue: String?): ModernDimmingStrength =
            entries.firstOrNull { it.storageValue == storageValue } ?: MEDIUM
    }
}

data class ModernSeekbarAppearance(
    val style: ModernSeekbarStyle = ModernSeekbarStyle.WAVEFORM_PREVIEW,
    val waveformSize: ModernWaveformSize = ModernWaveformSize.STANDARD,
    val waveformDensity: ModernWaveformDensity = ModernWaveformDensity.BALANCED,
    val colorMode: ModernSeekbarColorMode = ModernSeekbarColorMode.WHITE
)

data class ModernBackgroundAppearance(
    val style: ModernBackgroundStyle = ModernBackgroundStyle.BLURRED_ARTWORK,
    val blurStrength: ModernBlurStrength = ModernBlurStrength.MEDIUM,
    val dimmingStrength: ModernDimmingStrength = ModernDimmingStrength.MEDIUM,
    val solidColorArgb: Long = DEFAULT_MODERN_SOLID_COLOR_ARGB
)

enum class ModernArtworkShape(
    val storageValue: String,
    val displayName: String,
    internal val cornerRadiusDp: Int
) {
    SQUARE("square", "Square", 0),
    SUBTLE_ROUNDED("subtle_rounded", "Subtle Rounded", 10),
    ROUNDED("rounded", "Rounded", 30),
    EXTRA_ROUNDED("extra_rounded", "Extra Rounded", 48);

    companion object {
        fun fromStorageValue(storageValue: String?): ModernArtworkShape =
            entries.firstOrNull { it.storageValue == storageValue } ?: ROUNDED
    }
}

enum class ModernArtworkSize(
    val storageValue: String,
    val displayName: String,
    internal val maximumScale: Float,
    internal val maximumHeightFraction: Float
) {
    COMPACT("compact", "Compact", 0.78f, 0.34f),
    STANDARD("standard", "Standard", 1f, 0.42f),
    LARGE("large", "Large", 1.18f, 0.50f);

    companion object {
        fun fromStorageValue(storageValue: String?): ModernArtworkSize =
            entries.firstOrNull { it.storageValue == storageValue } ?: STANDARD
    }
}

enum class ModernArtworkFit(val storageValue: String, val displayName: String) {
    CROP("crop", "Fill Frame"),
    SHOW_FULL("show_full", "Fit / Contained");

    companion object {
        fun fromStorageValue(storageValue: String?): ModernArtworkFit =
            entries.firstOrNull { it.storageValue == storageValue } ?: CROP
    }
}

enum class ModernArtworkShadow(
    val storageValue: String,
    val displayName: String,
    internal val elevationDp: Int
) {
    NONE("none", "None", 0),
    SOFT("soft", "Soft", 10),
    STRONG("strong", "Strong", 22);

    companion object {
        fun fromStorageValue(storageValue: String?): ModernArtworkShadow =
            entries.firstOrNull { it.storageValue == storageValue } ?: SOFT
    }
}

data class ModernArtworkAppearance(
    val shape: ModernArtworkShape = ModernArtworkShape.ROUNDED,
    val size: ModernArtworkSize = ModernArtworkSize.STANDARD,
    val fit: ModernArtworkFit = ModernArtworkFit.CROP,
    val shadow: ModernArtworkShadow = ModernArtworkShadow.SOFT
)

enum class ModernControlStyle(val storageValue: String, val displayName: String) {
    MINIMAL("minimal", "Minimal"),
    GLASS("glass", "Glass"),
    TONAL("tonal", "Tonal"),
    OUTLINE("outline", "Outline");

    companion object {
        fun fromStorageValue(storageValue: String?): ModernControlStyle =
            entries.firstOrNull { it.storageValue == storageValue } ?: GLASS
    }
}

enum class ModernControlSize(
    val storageValue: String,
    val displayName: String,
    internal val primarySizeDp: Int,
    internal val navigationIconSizeDp: Int,
    internal val modeContainerSizeDp: Int
) {
    COMPACT("compact", "Compact", 68, 32, 48),
    STANDARD("standard", "Standard", 82, 38, 52),
    LARGE("large", "Large", 96, 44, 58);

    companion object {
        fun fromStorageValue(storageValue: String?): ModernControlSize =
            entries.firstOrNull { it.storageValue == storageValue } ?: STANDARD
    }
}

enum class ModernControlAccent(val storageValue: String, val displayName: String) {
    WHITE("white", "White"),
    APP_ACCENT("app_accent", "App Accent"),
    ALBUM_DERIVED("album_derived", "Album Derived");

    companion object {
        fun fromStorageValue(storageValue: String?): ModernControlAccent =
            entries.firstOrNull { it.storageValue == storageValue } ?: WHITE
    }
}

data class ModernControlAppearance(
    val style: ModernControlStyle = ModernControlStyle.GLASS,
    val size: ModernControlSize = ModernControlSize.STANDARD,
    val accent: ModernControlAccent = ModernControlAccent.WHITE
)

enum class ModernLayoutDensity(
    val storageValue: String,
    val displayName: String,
    internal val minimumFlexibleGapDp: Int,
    internal val maximumFlexibleGapDp: Int?
) {
    COMPACT("compact", "Compact", 12, 20),
    BALANCED("balanced", "Balanced", 18, 72),
    RELAXED("relaxed", "Relaxed", 24, null);

    companion object {
        fun fromStorageValue(storageValue: String?): ModernLayoutDensity =
            entries.firstOrNull { it.storageValue == storageValue } ?: BALANCED
    }
}

enum class ModernMetadataAlignment(val storageValue: String, val displayName: String) {
    LEFT("left", "Left"),
    CENTER("center", "Center");

    companion object {
        fun fromStorageValue(storageValue: String?): ModernMetadataAlignment =
            entries.firstOrNull { it.storageValue == storageValue } ?: LEFT
    }
}

data class ModernLayoutAppearance(
    val density: ModernLayoutDensity = ModernLayoutDensity.BALANCED,
    val metadataAlignment: ModernMetadataAlignment = ModernMetadataAlignment.LEFT,
    val showAudioQualityBadge: Boolean = true
)

data class ModernPlayerAppearance(
    val seekbar: ModernSeekbarAppearance = ModernSeekbarAppearance(),
    val background: ModernBackgroundAppearance = ModernBackgroundAppearance(),
    val artwork: ModernArtworkAppearance = ModernArtworkAppearance(),
    val controls: ModernControlAppearance = ModernControlAppearance(),
    val layout: ModernLayoutAppearance = ModernLayoutAppearance()
) {
    companion object {
        val Default = ModernPlayerAppearance()
    }
}

enum class ModernAppearancePreset(val displayName: String) {
    CDPLAYA("CDPlaya"),
    ARTWORK_FOCUS("Artwork Focus"),
    MINIMAL("Minimal"),
    COLORFUL("Colorful");

    fun appearance(): ModernPlayerAppearance = when (this) {
        CDPLAYA -> ModernPlayerAppearance.Default
        ARTWORK_FOCUS -> ModernPlayerAppearance(
            seekbar = ModernSeekbarAppearance(
                style = ModernSeekbarStyle.WAVEFORM_PREVIEW,
                waveformSize = ModernWaveformSize.STANDARD,
                waveformDensity = ModernWaveformDensity.DETAILED
            ),
            background = ModernBackgroundAppearance(
                style = ModernBackgroundStyle.DETAILED_ARTWORK,
                blurStrength = ModernBlurStrength.LOW,
                dimmingStrength = ModernDimmingStrength.MEDIUM
            ),
            artwork = ModernArtworkAppearance(size = ModernArtworkSize.LARGE),
            controls = ModernControlAppearance(size = ModernControlSize.COMPACT)
        )
        MINIMAL -> ModernPlayerAppearance(
            seekbar = ModernSeekbarAppearance(
                style = ModernSeekbarStyle.WAVE_LINE,
                waveformSize = ModernWaveformSize.COMPACT
            ),
            background = ModernBackgroundAppearance(style = ModernBackgroundStyle.PURE_BLACK),
            artwork = ModernArtworkAppearance(
                shape = ModernArtworkShape.SUBTLE_ROUNDED,
                size = ModernArtworkSize.COMPACT,
                fit = ModernArtworkFit.SHOW_FULL,
                shadow = ModernArtworkShadow.NONE
            ),
            controls = ModernControlAppearance(
                style = ModernControlStyle.MINIMAL,
                size = ModernControlSize.COMPACT
            ),
            layout = ModernLayoutAppearance(
                density = ModernLayoutDensity.COMPACT,
                showAudioQualityBadge = false
            )
        )
        COLORFUL -> ModernPlayerAppearance(
            seekbar = ModernSeekbarAppearance(
                style = ModernSeekbarStyle.CONTINUOUS_WAVEFORM,
                waveformDensity = ModernWaveformDensity.DETAILED,
                colorMode = ModernSeekbarColorMode.ALBUM_DERIVED
            ),
            background = ModernBackgroundAppearance(
                style = ModernBackgroundStyle.ALBUM_GRADIENT,
                dimmingStrength = ModernDimmingStrength.MEDIUM
            ),
            artwork = ModernArtworkAppearance(
                shape = ModernArtworkShape.EXTRA_ROUNDED,
                shadow = ModernArtworkShadow.STRONG
            ),
            controls = ModernControlAppearance(
                style = ModernControlStyle.TONAL,
                accent = ModernControlAccent.ALBUM_DERIVED
            ),
            layout = ModernLayoutAppearance(
                metadataAlignment = ModernMetadataAlignment.CENTER
            )
        )
    }

    companion object {
        fun matching(appearance: ModernPlayerAppearance): ModernAppearancePreset? =
            entries.firstOrNull { it.appearance() == appearance }
    }
}

const val DEFAULT_MODERN_SOLID_COLOR_ARGB: Long = 0xFF17191F

fun sanitizeModernSolidColorArgb(value: Long?): Long =
    value
        ?.takeIf { it in 0L..0xFFFFFFFFL }
        ?.let { it or 0xFF000000L }
        ?: DEFAULT_MODERN_SOLID_COLOR_ARGB
