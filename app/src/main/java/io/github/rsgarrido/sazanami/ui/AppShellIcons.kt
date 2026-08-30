package io.github.rsgarrido.sazanami.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppShellIcons {
    val Deck: ImageVector by lazy {
        shellIcon("SazanamiDeck") {
            strokedPath {
                moveTo(4.5f, 7f)
                curveTo(3.7f, 7f, 3f, 7.7f, 3f, 8.5f)
                verticalLineTo(17.5f)
                curveTo(3f, 18.3f, 3.7f, 19f, 4.5f, 19f)
                horizontalLineTo(19.5f)
                curveTo(20.3f, 19f, 21f, 18.3f, 21f, 17.5f)
                verticalLineTo(8.5f)
                curveTo(21f, 7.7f, 20.3f, 7f, 19.5f, 7f)
                close()

                moveTo(3.5f, 10.5f)
                horizontalLineTo(20.5f)

                moveTo(7f, 14.75f)
                horizontalLineTo(10.5f)

                moveTo(17.75f, 14.75f)
                curveTo(17.75f, 15.72f, 16.97f, 16.5f, 16f, 16.5f)
                curveTo(15.03f, 16.5f, 14.25f, 15.72f, 14.25f, 14.75f)
                curveTo(14.25f, 13.78f, 15.03f, 13f, 16f, 13f)
                curveTo(16.97f, 13f, 17.75f, 13.78f, 17.75f, 14.75f)
                close()
            }
        }
    }

    val AlbumStack: ImageVector by lazy {
        shellIcon("SazanamiAlbumStack") {
            strokedPath {
                moveTo(6f, 4f)
                horizontalLineTo(18f)
                curveTo(19.1f, 4f, 20f, 4.9f, 20f, 6f)
                verticalLineTo(18f)
                curveTo(20f, 19.1f, 19.1f, 20f, 18f, 20f)
                horizontalLineTo(6f)
                curveTo(4.9f, 20f, 4f, 19.1f, 4f, 18f)
                verticalLineTo(6f)
                curveTo(4f, 4.9f, 4.9f, 4f, 6f, 4f)
                close()

                moveTo(8f, 1.75f)
                horizontalLineTo(18f)

                moveTo(2f, 8f)
                verticalLineTo(18f)

                moveTo(15.25f, 12f)
                curveTo(15.25f, 13.8f, 13.8f, 15.25f, 12f, 15.25f)
                curveTo(10.2f, 15.25f, 8.75f, 13.8f, 8.75f, 12f)
                curveTo(8.75f, 10.2f, 10.2f, 8.75f, 12f, 8.75f)
                curveTo(13.8f, 8.75f, 15.25f, 10.2f, 15.25f, 12f)
                close()

                moveTo(12f, 11.6f)
                verticalLineTo(12.4f)
            }
        }
    }

    val Search: ImageVector by lazy {
        shellIcon("SazanamiSearch") {
            strokedPath {
                moveTo(16.75f, 10.75f)
                curveTo(16.75f, 14.06f, 14.06f, 16.75f, 10.75f, 16.75f)
                curveTo(7.44f, 16.75f, 4.75f, 14.06f, 4.75f, 10.75f)
                curveTo(4.75f, 7.44f, 7.44f, 4.75f, 10.75f, 4.75f)
                curveTo(14.06f, 4.75f, 16.75f, 7.44f, 16.75f, 10.75f)
                close()

                moveTo(15.25f, 15.25f)
                lineTo(20f, 20f)
            }
        }
    }

    val GridView: ImageVector by lazy {
        shellIcon("SazanamiGridView") {
            strokedPath {
                moveTo(4.5f, 4.5f)
                horizontalLineTo(10f)
                verticalLineTo(10f)
                horizontalLineTo(4.5f)
                close()

                moveTo(14f, 4.5f)
                horizontalLineTo(19.5f)
                verticalLineTo(10f)
                horizontalLineTo(14f)
                close()

                moveTo(4.5f, 14f)
                horizontalLineTo(10f)
                verticalLineTo(19.5f)
                horizontalLineTo(4.5f)
                close()

                moveTo(14f, 14f)
                horizontalLineTo(19.5f)
                verticalLineTo(19.5f)
                horizontalLineTo(14f)
                close()
            }
        }
    }

    val ListView: ImageVector by lazy {
        shellIcon("SazanamiListView") {
            strokedPath {
                moveTo(5f, 6.5f)
                horizontalLineTo(7f)
                verticalLineTo(8.5f)
                horizontalLineTo(5f)
                close()
                moveTo(10f, 7.5f)
                horizontalLineTo(19f)

                moveTo(5f, 11f)
                horizontalLineTo(7f)
                verticalLineTo(13f)
                horizontalLineTo(5f)
                close()
                moveTo(10f, 12f)
                horizontalLineTo(19f)

                moveTo(5f, 15.5f)
                horizontalLineTo(7f)
                verticalLineTo(17.5f)
                horizontalLineTo(5f)
                close()
                moveTo(10f, 16.5f)
                horizontalLineTo(19f)
            }
        }
    }


    val Folder: ImageVector by lazy {
        shellIcon("SazanamiFolder") {
            strokedPath {
                moveTo(3.5f, 7.25f)
                curveTo(3.5f, 6.28f, 4.28f, 5.5f, 5.25f, 5.5f)
                horizontalLineTo(9.1f)
                lineTo(11.1f, 7.5f)
                horizontalLineTo(18.75f)
                curveTo(19.72f, 7.5f, 20.5f, 8.28f, 20.5f, 9.25f)
                verticalLineTo(17.25f)
                curveTo(20.5f, 18.22f, 19.72f, 19f, 18.75f, 19f)
                horizontalLineTo(5.25f)
                curveTo(4.28f, 19f, 3.5f, 18.22f, 3.5f, 17.25f)
                close()
            }
        }
    }

    val MusicNote: ImageVector by lazy {
        shellIcon("SazanamiMusicNote") {
            strokedPath {
                moveTo(10f, 17.25f)
                curveTo(10f, 18.49f, 8.88f, 19.5f, 7.5f, 19.5f)
                curveTo(6.12f, 19.5f, 5f, 18.49f, 5f, 17.25f)
                curveTo(5f, 16.01f, 6.12f, 15f, 7.5f, 15f)
                curveTo(8.88f, 15f, 10f, 16.01f, 10f, 17.25f)
                close()

                moveTo(10f, 17.25f)
                verticalLineTo(6f)
                lineTo(18.5f, 4.5f)
                verticalLineTo(15.25f)

                moveTo(18.5f, 15.25f)
                curveTo(18.5f, 16.49f, 17.38f, 17.5f, 16f, 17.5f)
                curveTo(14.62f, 17.5f, 13.5f, 16.49f, 13.5f, 15.25f)
                curveTo(13.5f, 14.01f, 14.62f, 13f, 16f, 13f)
                curveTo(17.38f, 13f, 18.5f, 14.01f, 18.5f, 15.25f)
                close()

                moveTo(10f, 9f)
                lineTo(18.5f, 7.5f)
            }
        }
    }

    val Lyrics: ImageVector by lazy {
        shellIcon("SazanamiLyrics") {
            strokedPath {
                moveTo(6f, 3.5f)
                horizontalLineTo(14.5f)
                lineTo(18.5f, 7.5f)
                verticalLineTo(20.5f)
                horizontalLineTo(6f)
                curveTo(5.17f, 20.5f, 4.5f, 19.83f, 4.5f, 19f)
                verticalLineTo(5f)
                curveTo(4.5f, 4.17f, 5.17f, 3.5f, 6f, 3.5f)
                close()

                moveTo(14.5f, 3.75f)
                verticalLineTo(7.5f)
                horizontalLineTo(18.25f)

                moveTo(8f, 11f)
                horizontalLineTo(15f)
                moveTo(8f, 14.25f)
                horizontalLineTo(15f)
                moveTo(8f, 17.5f)
                horizontalLineTo(12.5f)
            }
        }
    }

    val Equalizer: ImageVector by lazy {
        shellIcon("SazanamiEqualizer") {
            strokedPath {
                moveTo(5f, 5f)
                verticalLineTo(19f)
                moveTo(12f, 5f)
                verticalLineTo(19f)
                moveTo(19f, 5f)
                verticalLineTo(19f)

                moveTo(3f, 9f)
                horizontalLineTo(7f)
                moveTo(10f, 14f)
                horizontalLineTo(14f)
                moveTo(17f, 8f)
                horizontalLineTo(21f)
            }
        }
    }

    val Gauge: ImageVector by lazy {
        shellIcon("SazanamiGauge") {
            strokedPath {
                moveTo(4.25f, 17.5f)
                curveTo(3.75f, 16.4f, 3.5f, 15.2f, 3.5f, 14f)
                curveTo(3.5f, 9.31f, 7.31f, 5.5f, 12f, 5.5f)
                curveTo(16.69f, 5.5f, 20.5f, 9.31f, 20.5f, 14f)
                curveTo(20.5f, 15.2f, 20.25f, 16.4f, 19.75f, 17.5f)

                moveTo(6.5f, 15f)
                horizontalLineTo(17.5f)
                moveTo(12f, 14.75f)
                lineTo(16.25f, 9.75f)
            }
        }
    }

    val AudioRoute: ImageVector by lazy {
        shellIcon("SazanamiAudioRoute") {
            strokedPath {
                moveTo(4f, 9f)
                horizontalLineTo(8f)
                lineTo(12f, 5.5f)
                verticalLineTo(18.5f)
                lineTo(8f, 15f)
                horizontalLineTo(4f)
                close()

                moveTo(15f, 9f)
                curveTo(16f, 10f, 16.5f, 11f, 16.5f, 12f)
                curveTo(16.5f, 13f, 16f, 14f, 15f, 15f)

                moveTo(17.5f, 6.5f)
                curveTo(19f, 8f, 20f, 9.75f, 20f, 12f)
                curveTo(20f, 14.25f, 19f, 16f, 17.5f, 17.5f)
            }
        }
    }

    val Timer: ImageVector by lazy {
        shellIcon("SazanamiTimer") {
            strokedPath {
                moveTo(12f, 6f)
                curveTo(16.14f, 6f, 19.5f, 9.36f, 19.5f, 13.5f)
                curveTo(19.5f, 17.64f, 16.14f, 21f, 12f, 21f)
                curveTo(7.86f, 21f, 4.5f, 17.64f, 4.5f, 13.5f)
                curveTo(4.5f, 9.36f, 7.86f, 6f, 12f, 6f)
                close()

                moveTo(9f, 3f)
                horizontalLineTo(15f)
                moveTo(12f, 6f)
                verticalLineTo(3f)
                moveTo(12f, 13.5f)
                lineTo(15.25f, 11.25f)
            }
        }
    }

    val Palette: ImageVector by lazy {
        shellIcon("SazanamiPalette") {
            strokedPath {
                moveTo(12f, 3.5f)
                curveTo(7.03f, 3.5f, 3f, 7.08f, 3f, 11.5f)
                curveTo(3f, 15.92f, 6.58f, 19.5f, 11f, 19.5f)
                horizontalLineTo(12.25f)
                curveTo(13.08f, 19.5f, 13.75f, 18.83f, 13.75f, 18f)
                curveTo(13.75f, 17.17f, 13.08f, 16.5f, 12.25f, 16.5f)
                horizontalLineTo(11.5f)
                curveTo(10.67f, 16.5f, 10f, 15.83f, 10f, 15f)
                curveTo(10f, 14.17f, 10.67f, 13.5f, 11.5f, 13.5f)
                horizontalLineTo(16.5f)
                curveTo(19f, 13.5f, 21f, 11.5f, 21f, 9f)
                curveTo(21f, 5.96f, 16.97f, 3.5f, 12f, 3.5f)
                close()

                moveTo(7.25f, 9f)
                verticalLineTo(9.1f)
                moveTo(10.25f, 6.75f)
                verticalLineTo(6.85f)
                moveTo(14f, 6.75f)
                verticalLineTo(6.85f)
                moveTo(17f, 9f)
                verticalLineTo(9.1f)
            }
        }
    }

    val Transition: ImageVector by lazy {
        shellIcon("SazanamiTransition") {
            strokedPath {
                moveTo(4f, 6f)
                horizontalLineTo(13f)
                verticalLineTo(15f)
                horizontalLineTo(4f)
                close()

                moveTo(11f, 9f)
                horizontalLineTo(20f)
                verticalLineTo(18f)
                horizontalLineTo(11f)

                moveTo(15f, 5f)
                lineTo(18f, 8f)
                lineTo(15f, 11f)
            }
        }
    }

    val Seekbar: ImageVector by lazy {
        shellIcon("SazanamiSeekbar") {
            strokedPath {
                moveTo(4f, 12f)
                horizontalLineTo(20f)

                moveTo(14.5f, 12f)
                curveTo(14.5f, 13.38f, 13.38f, 14.5f, 12f, 14.5f)
                curveTo(10.62f, 14.5f, 9.5f, 13.38f, 9.5f, 12f)
                curveTo(9.5f, 10.62f, 10.62f, 9.5f, 12f, 9.5f)
                curveTo(13.38f, 9.5f, 14.5f, 10.62f, 14.5f, 12f)
                close()
            }
        }
    }

    val Export: ImageVector by lazy {
        shellIcon("SazanamiExport") {
            strokedPath {
                moveTo(5f, 10f)
                verticalLineTo(19f)
                horizontalLineTo(19f)
                verticalLineTo(10f)

                moveTo(12f, 15f)
                verticalLineTo(3f)
                moveTo(8.5f, 6.5f)
                lineTo(12f, 3f)
                lineTo(15.5f, 6.5f)
            }
        }
    }

    val Restore: ImageVector by lazy {
        shellIcon("SazanamiRestore") {
            strokedPath {
                moveTo(5f, 10f)
                verticalLineTo(19f)
                horizontalLineTo(19f)
                verticalLineTo(10f)

                moveTo(12f, 3f)
                verticalLineTo(15f)
                moveTo(8.5f, 11.5f)
                lineTo(12f, 15f)
                lineTo(15.5f, 11.5f)
            }
        }
    }

    val Diagnostics: ImageVector by lazy {
        shellIcon("SazanamiDiagnostics") {
            strokedPath {
                moveTo(4f, 15f)
                horizontalLineTo(7f)
                lineTo(9f, 9f)
                lineTo(12f, 18f)
                lineTo(15f, 6f)
                lineTo(17f, 15f)
                horizontalLineTo(20f)

                moveTo(4f, 4f)
                horizontalLineTo(20f)
                verticalLineTo(20f)
                horizontalLineTo(4f)
                close()
            }
        }
    }

    val Info: ImageVector by lazy {
        shellIcon("SazanamiInfo") {
            strokedPath {
                moveTo(20f, 12f)
                curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
                curveTo(7.58f, 20f, 4f, 16.42f, 4f, 12f)
                curveTo(4f, 7.58f, 7.58f, 4f, 12f, 4f)
                curveTo(16.42f, 4f, 20f, 7.58f, 20f, 12f)
                close()

                moveTo(12f, 10.5f)
                verticalLineTo(16f)
                moveTo(12f, 7.5f)
                verticalLineTo(7.6f)
            }
        }
    }

    private fun shellIcon(
        name: String,
        paths: ImageVector.Builder.() -> Unit
    ): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(paths).build()
    }

    private fun ImageVector.Builder.strokedPath(
        commands: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit
    ) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = commands
        )
    }
}
