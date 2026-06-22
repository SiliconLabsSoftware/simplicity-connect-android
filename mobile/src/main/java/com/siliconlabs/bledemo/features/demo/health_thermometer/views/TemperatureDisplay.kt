package com.siliconlabs.bledemo.features.demo.health_thermometer.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.siliconlabs.bledemo.features.demo.health_thermometer.models.TemperatureReading
import com.siliconlabs.bledemo.R
import java.util.Locale
import kotlin.math.round

class TemperatureDisplay : LinearLayout {
    private var mainTempText: TextView? = null
    private var decimalText: TextView? = null
    private var degreeSymbol: TextView? = null
    private var defaultTextSize = 0f
    private var largeTextSize = 0f
    private var smallTextSize = 0f
    private var temp: Double? = null
    private var currentType: TemperatureReading.Type? = null
    private var currentReading: TemperatureReading? = null

    constructor(context: Context?) : super(context) {
        init(null)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(attrs)
    }

    private fun init(attrs: AttributeSet?) {
        defaultTextSize = if (isInEditMode) 15f else context.resources.getDimension(R.dimen.thermo_graph_time_text_size)
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.TemperatureDisplay)
            largeTextSize = typedArray.getDimension(R.styleable.TemperatureDisplay_large_text_size, defaultTextSize)
            smallTextSize = typedArray.getDimension(R.styleable.TemperatureDisplay_small_text_size, defaultTextSize)
            typedArray.recycle()
        } else {
            largeTextSize = defaultTextSize
            smallTextSize = largeTextSize
        }
        currentType = TemperatureReading.Type.FAHRENHEIT
        View.inflate(context, R.layout.temperature_display, this)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        val accent = ContextCompat.getColor(context, R.color.silabs_redtheme_primary_color)
        mainTempText = findViewById(R.id.temp_display_primary)
        mainTempText?.let {
           // it.setTextSize(TypedValue.COMPLEX_UNIT_PX, largeTextSize)
            it.setTextColor(accent)
            it.includeFontPadding = false
        }
        degreeSymbol = findViewById(R.id.temp_degree_symbol)
        degreeSymbol?.let {
            val degreePx = smallTextSize * 0.42f
           // it.setTextSize(TypedValue.COMPLEX_UNIT_PX, degreePx)
            it.setTextColor(accent)
            it.includeFontPadding = false
        }
        decimalText = findViewById(R.id.temp_display_secondary)
        decimalText?.let {
           // it.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize)
            it.setTextColor(accent)
            it.includeFontPadding = false
        }
        val thin = ResourcesCompat.getFont(context, R.font.helvetica_neue_thin)
        if (thin != null) {
            mainTempText?.typeface = thin
            decimalText?.typeface = thin
            degreeSymbol?.typeface = thin
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setTemperature(temperature: Double) {
        val rounded = round(temperature * 10) / 10
        val parts = String.format(Locale.US, "%.1f", rounded).split(".")
        mainTempText?.text = parts.elementAtOrNull(0) ?: "0"
        val frac = parts.elementAtOrNull(1) ?: "0"
        decimalText?.text = ".$frac"
    }

    fun setTemperature(reading: TemperatureReading?) {
        if (reading != null) {
            currentReading = reading
            setTemperature(reading.getTemperature(currentType!!))
        }
    }

    fun setCurrentType(currentType: TemperatureReading.Type?) {
        this.currentType = currentType
        setTemperature(currentReading)
    }
}