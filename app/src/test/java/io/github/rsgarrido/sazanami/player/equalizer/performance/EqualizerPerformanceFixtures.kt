package io.github.rsgarrido.sazanami.player.equalizer.performance

import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerFilterSpec
import kotlin.math.PI
import kotlin.math.sin

internal object EqualizerPerformanceFixtures {
    val sampleRates = intArrayOf(
        32_000,
        44_100,
        48_000,
        88_200,
        96_000,
        176_400,
        192_000
    )
    val channelCounts = intArrayOf(1, 2, 6)
    val bufferFrameCounts = intArrayOf(
        1,
        2,
        7,
        32,
        64,
        128,
        256,
        333,
        512,
        1_024,
        4_096,
        5_003
    )

    val namedConfigurations: LinkedHashMap<String, EqualizerConfiguration> =
        linkedMapOf(
            "flat" to EqualizerConfiguration(
                enabled = false,
                preampDb = 0.0,
                filters = emptyList()
            ),
            "graphic-moderate" to graphicConfiguration(
                gainsDb = doubleArrayOf(
                    4.0, 3.5, 2.5, 1.0, 0.0,
                    0.0, 0.0, 0.0, 0.0, 0.0
                ),
                preampDb = 0.0
            ),
            "graphic-worst" to graphicConfiguration(
                gainsDb = doubleArrayOf(
                    12.0, -12.0, 12.0, -12.0, 12.0,
                    -12.0, 12.0, -12.0, 12.0, -12.0
                ),
                preampDb = 6.0
            ),
            "parametric-realistic" to EqualizerConfiguration(
                enabled = true,
                preampDb = -3.0,
                filters = listOf(
                    EqualizerFilterSpec.LowShelf(85.0, 3.5, 0.8),
                    EqualizerFilterSpec.Peaking(240.0, -2.5, 1.2),
                    EqualizerFilterSpec.Peaking(1_850.0, 2.0, 1.7),
                    EqualizerFilterSpec.Peaking(5_600.0, -3.0, 2.2),
                    EqualizerFilterSpec.HighShelf(10_500.0, 2.0, 0.9),
                    EqualizerFilterSpec.Peaking(420.0, 1.5, 1.0),
                    EqualizerFilterSpec.Peaking(850.0, -1.0, 1.3),
                    EqualizerFilterSpec.Peaking(3_200.0, 1.0, 1.8),
                    EqualizerFilterSpec.Peaking(7_200.0, -1.5, 2.0),
                    EqualizerFilterSpec.Peaking(12_500.0, 1.0, 1.2)
                )
            ),
            "parametric-high-q" to EqualizerConfiguration(
                enabled = true,
                preampDb = 6.0,
                filters = List(10) { index ->
                    EqualizerFilterSpec.Peaking(
                        frequencyHz = 700.0 + index * 85.0,
                        gainDb = if (index % 2 == 0) 10.0 else -10.0,
                        q = if (index % 3 == 0) 19.8 else 16.0
                    )
                }
            ),
            "parametric-all-types" to EqualizerConfiguration(
                enabled = true,
                preampDb = -6.0,
                filters = listOf(
                    EqualizerFilterSpec.Peaking(1_000.0, 4.0, 1.4),
                    EqualizerFilterSpec.LowShelf(90.0, 3.0, 0.8),
                    EqualizerFilterSpec.HighShelf(9_000.0, -3.0, 0.8),
                    EqualizerFilterSpec.LowPass(14_000.0, 0.71),
                    EqualizerFilterSpec.HighPass(35.0, 0.71),
                    EqualizerFilterSpec.Notch(3_200.0, 8.0),
                    EqualizerFilterSpec.BandPass(650.0, 1.1)
                )
            )
        )

    fun pcmSignal(
        frameCount: Int,
        channelCount: Int,
        sampleRateHz: Int
    ): FloatArray {
        return FloatArray(frameCount * channelCount) { sampleIndex ->
            val frameIndex = sampleIndex / channelCount
            (
                0.22 * sin(
                    2.0 * PI * 997.0 * frameIndex / sampleRateHz
                ) +
                    0.08 * sin(
                        2.0 * PI * 73.0 * frameIndex / sampleRateHz
                    )
                ).toFloat()
        }
    }

    private fun graphicConfiguration(
        gainsDb: DoubleArray,
        preampDb: Double
    ): EqualizerConfiguration {
        val frequencies = doubleArrayOf(
            31.25,
            62.5,
            125.0,
            250.0,
            500.0,
            1_000.0,
            2_000.0,
            4_000.0,
            8_000.0,
            14_000.0
        )
        return EqualizerConfiguration(
            enabled = true,
            preampDb = preampDb,
            filters = frequencies.indices.map { index ->
                EqualizerFilterSpec.Peaking(
                    frequencyHz = frequencies[index],
                    gainDb = gainsDb[index],
                    q = 1.41
                )
            }
        )
    }
}

/**
 * HotSpot-only allocation counter used by opt-in JVM measurements.
 * Reflection avoids adding desktop management APIs to Android's boot class
 * path during test compilation.
 */
internal class HotSpotThreadAllocationReader private constructor(
    private val bean: Any,
    private val getAllocatedBytes: java.lang.reflect.Method,
    private val threadId: Long
) {
    fun currentThreadBytes(): Long {
        return getAllocatedBytes.invoke(bean, threadId) as Long
    }

    companion object {
        fun create(): HotSpotThreadAllocationReader? {
            return runCatching {
                val managementFactory =
                    Class.forName(
                        "java.lang.management.ManagementFactory"
                    )
                val bean = requireNotNull(
                    managementFactory
                        .getMethod("getThreadMXBean")
                        .invoke(null)
                )
                val threadMxBean =
                    Class.forName(
                        "com.sun.management.ThreadMXBean"
                    )
                val supported = threadMxBean
                    .getMethod(
                        "isThreadAllocatedMemorySupported"
                    )
                    .invoke(bean) as Boolean
                if (!supported) return null
                threadMxBean
                    .getMethod(
                        "setThreadAllocatedMemoryEnabled",
                        Boolean::class.javaPrimitiveType
                    )
                    .invoke(bean, true)
                val threadId = Thread::class.java
                    .getMethod("threadId")
                    .invoke(Thread.currentThread()) as Long
                HotSpotThreadAllocationReader(
                    bean = bean,
                    getAllocatedBytes = threadMxBean.getMethod(
                        "getThreadAllocatedBytes",
                        Long::class.javaPrimitiveType
                    ),
                    threadId = threadId
                )
            }.getOrNull()
        }
    }
}
