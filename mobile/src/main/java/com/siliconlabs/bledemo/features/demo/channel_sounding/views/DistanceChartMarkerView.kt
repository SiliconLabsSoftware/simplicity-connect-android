package com.siliconlabs.bledemo.features.demo.channel_sounding.views

import android.content.Context
import android.graphics.Canvas
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.siliconlabs.bledemo.R
import java.util.Locale

/**
 * Custom MarkerView for the distance chart that displays:
 * - "⚠️ Spike" for raw data points that deviate significantly from filtered values
 * - "~ Smoothing" for points where Kalman filter is actively smoothing
 * - "✓ Stable" for points where raw and filtered are very close
 *
 * This provides visual feedback on where the Kalman filter is actively working.
 * Tap on any point on the chart to see the marker with spike/smoothing information.
 */
class DistanceChartMarkerView(
    context: Context,
    layoutResource: Int = R.layout.chart_marker_view
) : MarkerView(context, layoutResource) {

    private val markerTypeTextView: TextView = findViewById(R.id.markerType)
    private val markerValueTextView: TextView = findViewById(R.id.markerValue)

    // Store the chart data points for spike detection
    private var chartDataPoints: List<ChartDataPointInfo> = emptyList()

    // Threshold for detecting a spike (difference between raw and filtered in meters)
    private var spikeThreshold: Float = 0.3f

    /**
     * Data class to hold chart point information for marker display
     */
    data class ChartDataPointInfo(
        val timestamp: Float,
        val rawDistance: Float,
        val filteredDistance: Float
    )

    /**
     * Update the chart data points used for spike/smoothing detection
     */
    fun setChartDataPoints(dataPoints: List<ChartDataPointInfo>) {
        chartDataPoints = dataPoints
    }

    /**
     * Set the threshold for spike detection
     * @param threshold Difference in meters between raw and filtered to be considered a spike
     */
    fun setSpikeThreshold(threshold: Float) {
        spikeThreshold = threshold
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e == null) return

        val timestamp = e.x
        val value = e.y

        // Find the corresponding data point
        val dataPoint = chartDataPoints.find {
            kotlin.math.abs(it.timestamp - timestamp) < 0.1f
        }

        if (dataPoint != null) {
            val deviation = kotlin.math.abs(dataPoint.rawDistance - dataPoint.filteredDistance)

            when {
                // Spike detected: raw value deviates significantly from filtered
                deviation > spikeThreshold -> {
                    markerTypeTextView.text = context.getString(R.string.channel_sounding_chart_marker_spike)
                    markerTypeTextView.setTextColor(context.getColor(R.color.silabs_red))
                    markerValueTextView.text = String.format(Locale.US, "Raw: %.2f m\nFiltered: %.2f m",
                        dataPoint.rawDistance, dataPoint.filteredDistance)
                }
                // Smoothing active: filter is working but deviation is moderate
                deviation > spikeThreshold * 0.3f -> {
                    markerTypeTextView.text = context.getString(R.string.channel_sounding_chart_marker_smoothing)
                    markerTypeTextView.setTextColor(context.getColor(R.color.silabs_blue))
                    markerValueTextView.text = String.format(Locale.US, "Raw: %.2f m\nFiltered: %.2f m",
                        dataPoint.rawDistance, dataPoint.filteredDistance)
                }
                // Stable: raw and filtered are very close
                else -> {
                    markerTypeTextView.text = context.getString(R.string.channel_sounding_chart_marker_stable)
                    markerTypeTextView.setTextColor(context.getColor(R.color.silabs_green))
                    markerValueTextView.text = String.format(Locale.US, "%.2f m", value)
                }
            }
        } else {
            // Fallback if data point not found
            markerTypeTextView.text = context.getString(R.string.channel_sounding_chart_marker_distance)
            markerTypeTextView.setTextColor(context.getColor(R.color.silabs_white))
            markerValueTextView.text = String.format(Locale.US, "%.2f m", value)
        }

        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        // Position the marker above the point, centered horizontally
        return MPPointF(-(width / 2f), -height.toFloat() - 10f)
    }

    override fun draw(canvas: Canvas?, posX: Float, posY: Float) {
        // Ensure marker stays within chart bounds
        var adjustedPosX = posX
        var adjustedPosY = posY

        val canvasWidth = canvas?.width ?: 0

        // Adjust X position to keep marker on screen
        if (posX + width / 2f > canvasWidth) {
            adjustedPosX = canvasWidth - width / 2f
        } else if (posX - width / 2f < 0) {
            adjustedPosX = width / 2f
        }

        // Adjust Y position if marker would go above chart
        if (posY - height - 10f < 0) {
            adjustedPosY = height.toFloat() + 10f
        }

        super.draw(canvas, adjustedPosX, adjustedPosY)
    }
}
