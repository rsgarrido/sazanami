package io.github.rsgarrido.sazanami.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ForwardingExtractorOutput
import androidx.media3.extractor.ForwardingExtractorsFactory
import androidx.media3.extractor.IndexSeekMap
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SniffFailure
import androidx.media3.extractor.mp4.FragmentedMp4Extractor

/** Adds a validated local fragment index while preserving Media3's default extractor set. */
@OptIn(UnstableApi::class)
internal class FragmentedMp4SeekExtractorsFactory(
    context: Context,
    delegate: DefaultExtractorsFactory = DefaultExtractorsFactory()
) : ForwardingExtractorsFactory(delegate) {
    private val indexProvider = LocalFragmentedMp4SeekIndexProvider(context)

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>
    ): Array<Extractor> {
        val extractors = super.createExtractors(uri, responseHeaders)
        val index = indexProvider.indexFor(uri) ?: return extractors
        return extractors.map { extractor ->
            if (extractor.underlyingImplementation is FragmentedMp4Extractor) {
                SeekMapPublishingExtractor(extractor, index)
            } else {
                extractor
            }
        }.toTypedArray()
    }
}

@OptIn(UnstableApi::class)
private class SeekMapPublishingExtractor(
    private val delegate: Extractor,
    private val index: FragmentedMp4SeekIndex
) : Extractor {
    override fun sniff(input: ExtractorInput): Boolean = delegate.sniff(input)

    override fun getSniffFailureDetails(): List<SniffFailure> = delegate.sniffFailureDetails

    override fun init(output: ExtractorOutput) {
        delegate.init(object : ForwardingExtractorOutput(output) {
            override fun seekMap(seekMap: SeekMap) {
                if (seekMap is SeekMap.Unseekable) {
                    super.seekMap(
                        IndexSeekMap(index.positions(), index.timesUs(), index.durationUs)
                    )
                } else {
                    super.seekMap(seekMap)
                }
            }
        })
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int =
        delegate.read(input, seekPosition)

    override fun seek(position: Long, timeUs: Long) = delegate.seek(position, timeUs)

    override fun release() = delegate.release()

    override fun getUnderlyingImplementation(): Extractor = delegate.underlyingImplementation
}
