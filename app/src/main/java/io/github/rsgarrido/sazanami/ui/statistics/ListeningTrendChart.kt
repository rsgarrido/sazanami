package io.github.rsgarrido.sazanami.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.AnalyticsRangePreset
import io.github.rsgarrido.sazanami.data.AnalyticsRangeSelection
import io.github.rsgarrido.sazanami.data.ListeningTrendBucket
import io.github.rsgarrido.sazanami.data.ListeningTrendMetric
import io.github.rsgarrido.sazanami.ui.AppShellAccent
import java.time.ZoneId

@Composable
internal fun ListeningTrendSection(
    buckets: List<ListeningTrendBucket>,
    metric: ListeningTrendMetric,
    zoneId: ZoneId,
    selectedRange: AnalyticsRangeSelection,
    rangeDescription: String,
    hasDetailedEvents: Boolean,
    onMetricSelected: (ListeningTrendMetric) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stackedHeader = maxWidth < 360.dp || LocalConfiguration.current.fontScale >= 1.3f
            if (stackedHeader) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.statistics_trend_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    TrendMetricSelector(
                        metric = metric,
                        onMetricSelected = onMetricSelected,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.statistics_trend_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    TrendMetricSelector(
                        metric = metric,
                        onMetricSelected = onMetricSelected,
                        modifier = Modifier.widthIn(min = 190.dp, max = 220.dp)
                    )
                }
            }
        }
        val noDetailedEventsAnywhere =
            selectedRange == AnalyticsRangeSelection.Preset(AnalyticsRangePreset.ALL_TIME) &&
                    !hasDetailedEvents
        ListeningTrendChart(
            buckets = buckets,
            metric = metric,
            zoneId = zoneId,
            rangeDescription = rangeDescription,
            emptyMessage = stringResource(
                if (noDetailedEventsAnywhere) {
                    R.string.statistics_trend_no_detailed_history
                } else {
                    R.string.statistics_trend_no_data_range
                }
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrendMetricSelector(
    metric: ListeningTrendMetric,
    onMetricSelected: (ListeningTrendMetric) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        ListeningTrendMetric.entries.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = metric == choice,
                onClick = { onMetricSelected(choice) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = ListeningTrendMetric.entries.size
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                icon = {},
                label = {
                    Text(
                        text = stringResource(
                            when (choice) {
                                ListeningTrendMetric.RECORDED_LISTENING_TIME ->
                                    R.string.statistics_trend_metric_time
                                ListeningTrendMetric.QUALIFIED_PLAYS ->
                                    R.string.statistics_trend_metric_plays
                            }
                        ),
                        maxLines = 1
                    )
                }
            )
        }
    }
}

@Composable
internal fun ListeningTrendChart(
    buckets: List<ListeningTrendBucket>,
    metric: ListeningTrendMetric,
    zoneId: ZoneId,
    rangeDescription: String,
    emptyMessage: String,
    modifier: Modifier = Modifier
) {
    val maximum = remember(buckets, metric) { trendMaximum(buckets, metric) }
    val total = remember(buckets, metric) { trendTotal(buckets, metric) }
    val peak = remember(buckets, metric) { trendPeak(buckets, metric) }
    val metricLabel = stringResource(
        when (metric) {
            ListeningTrendMetric.RECORDED_LISTENING_TIME -> R.string.statistics_trend_metric_time
            ListeningTrendMetric.QUALIFIED_PLAYS -> R.string.statistics_trend_metric_plays
        }
    )
    val locale = LocalConfiguration.current.locales[0]
    val emptyDescription = stringResource(
        R.string.statistics_trend_accessibility_empty,
        metricLabel,
        rangeDescription,
        emptyMessage
    )

    Card(
        modifier = modifier.fillMaxWidth().heightIn(min = 236.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        if (maximum <= 0L || buckets.isEmpty()) {
            Text(
                text = emptyMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .semantics {
                        contentDescription = emptyDescription
                    }
                    .testTag("listening_trend_empty"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Card
        }

        val totalLabel = trendMetricValueText(total, metric)
        val maximumLabel = trendMetricValueText(maximum, metric)
        val peakPeriod = remember(peak, zoneId, locale) {
            peak?.let { formatTrendPeakPeriod(it, zoneId, locale) }.orEmpty()
        }
        val peakValue = peak?.let { trendMetricValueText(it.valueFor(metric), metric) }.orEmpty()
        val chartDescription = stringResource(
            R.string.statistics_trend_accessibility,
            metricLabel,
            buckets.size,
            totalLabel,
            peakPeriod,
            peakValue,
            rangeDescription
        )

        Column(
            modifier = Modifier
                .padding(14.dp)
                .semantics { contentDescription = chartDescription }
                .testTag("listening_trend_chart"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.statistics_trend_axis_maximum_compact, maximumLabel),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val viewportWidth = maxWidth
                val contentWidth = trendContentWidthDp(buckets.size, viewportWidth.value).dp
                val slotWidth = contentWidth / buckets.size.coerceAtLeast(1)
                val maximumLabels = (viewportWidth.value / 72f).toInt().coerceIn(2, 8)
                val labelIndices = remember(buckets.size, maximumLabels) {
                    selectTrendLabelIndices(buckets.size, maximumLabels)
                }
                val labels = remember(buckets, labelIndices, zoneId, locale) {
                    labelIndices.associateWith { index ->
                        formatTrendBucketLabel(buckets[index], buckets, zoneId, locale)
                    }
                }
                val accent = AppShellAccent
                val gridColor = MaterialTheme.colorScheme.outlineVariant
                val baselineColor = MaterialTheme.colorScheme.outline
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .testTag("listening_trend_horizontal_scroll")
                ) {
                    Canvas(
                        modifier = Modifier
                            .width(contentWidth)
                            .height(132.dp)
                    ) {
                        val baselineY = size.height - 1f
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = baselineColor,
                            start = Offset(0f, baselineY),
                            end = Offset(size.width, baselineY),
                            strokeWidth = 2f
                        )
                        val slotPx = size.width / buckets.size
                        val barWidth = (slotPx * 0.62f).coerceAtMost(14.dp.toPx()).coerceAtLeast(2.dp.toPx())
                        buckets.forEachIndexed { index, bucket ->
                            val height = normalizedTrendHeight(
                                bucket.valueFor(metric),
                                maximum,
                                size.height - 4.dp.toPx(),
                                2.dp.toPx()
                            )
                            if (height > 0f) {
                                val left = index * slotPx + (slotPx - barWidth) / 2f
                                drawRoundRect(
                                    color = accent,
                                    topLeft = Offset(left, baselineY - height),
                                    size = Size(barWidth, height),
                                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.width(contentWidth).height(28.dp)) {
                        labels.forEach { (index, label) ->
                            // When each bucket has enough horizontal room (for example the
                            // seven-day view), give the label that exact bucket slot. This keeps
                            // the first and last labels centered beneath their bars instead of
                            // pinning their text to the chart edges. Denser ranges retain the
                            // wider clamped label boxes so dates such as "Aug 29" stay readable.
                            val useBucketSlot = slotWidth >= 40.dp
                            val labelWidth = if (useBucketSlot) slotWidth else 72.dp
                            val maximumLeft = (contentWidth - labelWidth).coerceAtLeast(0.dp)
                            val isFirstBucket = index == 0
                            val isLastBucket = index == buckets.lastIndex
                            val idealLeft = slotWidth * (index + 0.5f) - labelWidth / 2f
                            val left = when {
                                useBucketSlot -> slotWidth * index
                                isFirstBucket -> 0.dp
                                isLastBucket -> maximumLeft
                                else -> idealLeft.coerceIn(0.dp, maximumLeft)
                            }
                            val textAlign = when {
                                useBucketSlot -> TextAlign.Center
                                isFirstBucket -> TextAlign.Start
                                isLastBucket -> TextAlign.End
                                else -> TextAlign.Center
                            }
                            Text(
                                text = label,
                                modifier = Modifier.offset(x = left).width(labelWidth),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = textAlign,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            peak?.let {
                Text(
                    text = stringResource(
                        R.string.statistics_trend_peak_summary,
                        peakPeriod,
                        peakValue
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun trendMetricValueText(value: Long, metric: ListeningTrendMetric): String = when (metric) {
    ListeningTrendMetric.QUALIFIED_PLAYS -> pluralStringResource(
        R.plurals.statistics_trend_plays,
        value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        formatTrendCount(value)
    )
    ListeningTrendMetric.RECORDED_LISTENING_TIME -> {
        val compact = compactDurationValue(value)
        pluralStringResource(
            when (compact.unit) {
                CompactDurationUnit.MINUTES -> R.plurals.statistics_trend_minutes
                CompactDurationUnit.HOURS -> R.plurals.statistics_trend_hours
                CompactDurationUnit.DAYS -> R.plurals.statistics_trend_days
            },
            compact.amount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            formatTrendCount(compact.amount)
        )
    }
}
