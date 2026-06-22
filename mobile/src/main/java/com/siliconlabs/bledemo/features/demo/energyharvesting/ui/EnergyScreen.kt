package com.siliconlabs.bledemo.features.demo.energyharvesting.ui

import android.graphics.Typeface
import android.view.MotionEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import com.github.mikephil.charting.formatter.IValueFormatter
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.utils.ViewPortHandler
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.features.demo.energyharvesting.presentation.EnergyUiState
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun EnergyScreen(
    state: EnergyUiState,
    modifier: Modifier = Modifier
) {
    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .paint(
                painter = painterResource(id = R.drawable.modal_bg),
                contentScale = ContentScale.Crop
            )

            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = state.status?.name ?: "EH Sensor",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily(Font(R.font.stolzl_medium)),
                fontWeight = FontWeight.Normal,
                fontSize = 20.sp
            ),
        )
        val identifierOrAddress = state.status?.deviceIdentifier ?: state.status?.deviceAddress ?: "--"

        Text(
            text = identifierOrAddress.uppercase(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily(Font(R.font.stolzl_regular)),
                fontWeight = FontWeight.Normal, color = colorResource(R.color.silabs_rebranding_2373_connected_light_text_color)
            ),
        )
        Spacer(Modifier.height(16.dp))

        val liveVoltage = if (state.history.isEmpty()) "--" else state.status?.voltageMv?.let { "$it mV" } ?: "--"
        Text(
            text = liveVoltage,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily(Font(R.font.stolzl_medium)),
                fontWeight = FontWeight.Normal,
            ),
            fontStyle = FontStyle.Normal,
            fontSize = 20.sp
        )
        Spacer(Modifier.height(8.dp))
        val displayTimestamp = state.lastTimestamp?.let { ts -> if (ts.length >= 8) ts.substring(0, 8) else ts }
        Text(
            text = displayTimestamp ?: "--:--:--",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily(Font(R.font.stolzl_regular)),
                fontWeight = FontWeight.Normal,color = colorResource(R.color.silabs_rebranding_2373_connected_light_text_color)
            ),
            fontStyle = FontStyle.Normal,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.wrapContentWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FeatureColumn(
                iconRes = R.drawable.baseline_wifi_24,
                label = state.status?.rssi?.let { "$it dBm" } ?: "-- dBm",
            )
            Box(
                Modifier
                    .height(30.dp)
                    .width(2.dp)
                    .background(Color.LightGray)
            )
            val intervalLabel = state.status?.advIntervalMs?.let { ms ->
                when {
                    ms < 0.5 -> "<0.5 ms"
                    ms < 10 -> String.format(Locale.US, "%.2f ms", ms)
                    ms < 100 -> String.format(Locale.US, "%.1f ms", ms)
                    else -> String.format(Locale.US, "%.0f ms", ms)
                }
            } ?: "-- ms"
            FeatureColumn(
                iconRes = R.drawable.redesign_ic_clock,
                label = intervalLabel,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Viewport management
        val voltages = state.history.map { it.voltageMv }
        val timesMs = state.history.map { it.minuteIndex }
        val windowMs = 60_000f // increase window to 60s (was 30s). Adjust as needed.
        var viewportStartMs by remember { mutableStateOf(0f) }
        val latestTime = (timesMs.maxOrNull() ?: 0).toFloat()
        val maxStart = (latestTime - windowMs).coerceAtLeast(0f)
        var followLatest by remember { mutableStateOf(true) }

        // If data cleared (e.g., new session) resume live following
        LaunchedEffect(voltages.size == 0) {
            if (voltages.isEmpty()) {
                followLatest = true
                viewportStartMs = 0f
            }
        }

        // Auto follow newest data only if followLatest flag is still true
        LaunchedEffect(latestTime, voltages.size, followLatest) {
            if (followLatest) {
                viewportStartMs = maxStart
            }
        }

        EhVoltageChart(
            voltagesMv = voltages,
            timesMs = timesMs,
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
            viewportStartMs = viewportStartMs,
            windowMs = windowMs,
            onUserDrag = { followLatest = false },
            onEdgeReached = { isAtEnd -> if (isAtEnd) followLatest = true }
        )

        val silabsGrey = Color(0xFF7A7878)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = {
                    viewportStartMs = max(0f, viewportStartMs - windowMs / 2f)
                    followLatest = false
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = "Scroll Left",
                        tint = silabsGrey
                    )
                }
                Text(
                    text = "Y: Voltage (mV)",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = {
                    val newStart = min(maxStart, viewportStartMs + windowMs / 2f)
                    viewportStartMs = newStart
                    followLatest = newStart >= maxStart
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Scroll Right",
                        tint = silabsGrey
                    )
                }
                Text(
                    text = "X: Time (s)",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "Info",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Note: Values & graph refresh continuously",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontStyle = FontStyle.Normal,
                    fontWeight = FontWeight.Medium,
                ),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

private fun normalizeEarlyTimes(rawTimes: List<Int>): List<Float> {
    if (rawTimes.isEmpty()) return emptyList()
    val maxRaw = rawTimes.maxOrNull() ?: 0
    return if (maxRaw < 100) {
        rawTimes.mapIndexed { idx, _ -> idx * 1000f }
    } else {
        var last = -1f
        rawTimes.map { t ->
            var x = t.toFloat()
            if (x <= last) x = last + 1f
            last = x
            x
        }
    }
}

@Composable
private fun EhVoltageChart(
    voltagesMv: List<Int>,
    timesMs: List<Int>,
    modifier: Modifier = Modifier,
    viewportStartMs: Float,
    windowMs: Float,
    onUserDrag: () -> Unit,
    onEdgeReached: (isAtEnd: Boolean) -> Unit
) {
    val hasData = voltagesMv.isNotEmpty() && timesMs.isNotEmpty()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp)
    ) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx -> LineChart(ctx).apply { setNoDataText("Waiting for data…") } },
            update = { chart ->
                val meta = (chart.tag as? EhChartMeta) ?: EhChartMeta()
                val effectiveTimes = normalizeEarlyTimes(timesMs)
                val latestTimeRaw = if (hasData) (effectiveTimes.maxOrNull() ?: 0f) else 0f

                val entries = if (hasData) effectiveTimes.zip(voltagesMv)
                    .mapIndexed { idx, (t, v) -> Entry(t, v.toFloat()).apply { data = idx } } else emptyList()

                val dataMax = if (hasData) (voltagesMv.maxOrNull() ?: 0).toFloat() else 0f
                val defaultMax = 5000f
                var desiredMax = if (dataMax <= defaultMax) defaultMax else dataMax + 50f
                if (hasData && (desiredMax - dataMax) < 60f) desiredMax = dataMax + 60f

                val dataSet = LineDataSet(entries, "Voltage (mV)").apply {
                    lineWidth = 2f
                    setDrawCircles(hasData)
                    circleRadius = 4f
                    setDrawCircleHole(false)
                    color = ContextCompat.getColor(chart.context,R.color.silabs_red)
                    setCircleColor(ContextCompat.getColor(chart.context,R.color.silabs_red))
                    valueTextSize = 10f
                    setDrawValues(hasData)
                    valueTextColor = android.graphics.Color.BLACK
                    valueTypeface = ResourcesCompat.getFont(chart.context, R.font.helvetica_neue_regular)
                        ?: Typeface.DEFAULT
                    valueFormatter = object : IValueFormatter {
                        override fun getFormattedValue(
                            value: Float,
                            entry: Entry,
                            dataSetIndex: Int,
                            viewPortHandler: ViewPortHandler?
                        ): String = value.toInt().toString()
                    }
                }

                val baselineEntries = listOf(Entry(0f, 0f), Entry(windowMs, 0f))
                val baselineSet = LineDataSet(baselineEntries, "").apply {
                    lineWidth = 0.5f
                    color = 0x22000000
                    setDrawCircles(false)
                    setDrawValues(false)
                }

                chart.data = if (!hasData) LineData(baselineSet) else LineData(baselineSet, dataSet)

                chart.xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    granularity = 1000f
                    axisMinimum = 0f
                    axisMaximum = max(windowMs, latestTimeRaw)
                    valueFormatter = object : IAxisValueFormatter {
                        override fun getFormattedValue(value: Float, axis: AxisBase): String {
                            val secs = (value / 1000f).toInt()
                            return secs.toString() + "s"
                        }
                    }
                    textSize = 11f
                }

                chart.axisLeft.apply {
                    axisMinimum = 0f
                    axisMaximum = desiredMax
                    setLabelCount(6, true)
                    valueFormatter = object : IAxisValueFormatter {
                        override fun getFormattedValue(value: Float, axis: AxisBase): String =
                            value.toInt().toString()
                    }
                    textSize = 11f
                }
                chart.axisRight.isEnabled = false

                if (hasData && desiredMax > defaultMax && !meta.expansionAnimated) {
                    chart.animateY(500)
                    meta.expansionAnimated = true
                }
                meta.lastAxisMax = desiredMax

                chart.setVisibleXRangeMaximum(windowMs)
                chart.moveViewToX(viewportStartMs.coerceAtLeast(0f))

                chart.legend.apply {
                    isEnabled = hasData
                    verticalAlignment = Legend.LegendVerticalAlignment.TOP
                    horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
                    orientation = Legend.LegendOrientation.HORIZONTAL
                    setDrawInside(false)
                    textSize = 12f
                }
                chart.description.isEnabled = false
                chart.setTouchEnabled(true)
                chart.setScaleEnabled(true)
                chart.setPinchZoom(true)

                if (chart.onChartGestureListener == null) {
                    chart.onChartGestureListener = object : OnChartGestureListener {
                        override fun onChartGestureStart(
                            me: MotionEvent,
                            lastPerformedGesture: ChartTouchListener.ChartGesture
                        ) { meta.userInteracting = true; onUserDrag() }
                        override fun onChartGestureEnd(
                            me: MotionEvent,
                            lastPerformedGesture: ChartTouchListener.ChartGesture
                        ) { meta.userInteracting = false; onEdgeReached(chart.lowestVisibleX + chart.visibleXRange >= latestTimeRaw) }
                        override fun onChartLongPressed(me: MotionEvent) {}
                        override fun onChartDoubleTapped(me: MotionEvent) {}
                        override fun onChartSingleTapped(me: MotionEvent) {}
                        override fun onChartFling(
                            me1: MotionEvent,
                            me2: MotionEvent,
                            velocityX: Float,
                            velocityY: Float
                        ) {}
                        override fun onChartScale(me: MotionEvent, scaleX: Float, scaleY: Float) {}
                        override fun onChartTranslate(me: MotionEvent, dX: Float, dY: Float) {}
                    }
                }

                chart.tag = meta
                chart.invalidate()
            }
        )
        if (!hasData) {
            Text(
                text = "Waiting for data…",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .alpha(0.6f),
                color = Color.Gray
            )
        }
    }
}

@Composable
fun FeatureColumn(iconRes: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily(Font(R.font.stolzl_regular)),
                fontWeight = FontWeight.Normal,
            ),
        )
    }
}

private data class EhChartMeta(
    var lastAxisMax: Float = 5000f,
    var expansionAnimated: Boolean = false,
    var followLatest: Boolean = true,
    var userInteracting: Boolean = false
)
