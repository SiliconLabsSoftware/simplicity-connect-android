package com.siliconlabs.bledemo.features.demo.throughput.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import com.siliconlabs.bledemo.R


class SpeedView(context: Context, attributeSet: AttributeSet? = null) : View(context, attributeSet) {
    private var unitsArray = arrayListOf<String>()
    private var gradientPaintRing = Paint(Paint.ANTI_ALIAS_FLAG)
    private var indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var speedUnitPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var greyPaintRing = Paint(Paint.ANTI_ALIAS_FLAG)
    private var speedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var unitPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var rectangle = RectF()
    private var mMatrix = Matrix()

    private var indicatorBitmap: Bitmap? = null

    private var mWidth: Float = 0f
    private var mHeight: Float = 0f
    private var progress: Int = 0
    private var value: String = ""
    private var unit: String = ""
    private var mode: Mode = Mode.NONE

    private val ringColors: IntArray

    init {
        val a = context.obtainStyledAttributes(attributeSet, R.styleable.SpeedView)
        val useBleThroughputRing = a.getBoolean(R.styleable.SpeedView_useBleThroughputSpeedRing, false)
        a.recycle()
        ringColors = if (useBleThroughputRing) {
            intArrayOf(
                ContextCompat.getColor(context, R.color.ble_throughput_speed_ring_start),
                ContextCompat.getColor(context, R.color.ble_throughput_speed_ring_center),
                ContextCompat.getColor(context, R.color.ble_throughput_speed_ring_end),
            )
        } else {
            intArrayOf(
                ContextCompat.getColor(context, R.color.silabs_speedmeter_start),
                ContextCompat.getColor(context, R.color.silabs_speedmeter_center),
                ContextCompat.getColor(context, R.color.silabs_speedmeter_end),
            )
        }
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCCCCC")
        strokeWidth = 3f
    }

    private val positions = floatArrayOf(0f, 0.3f, 1f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        mHeight = getDefaultSize(suggestedMinimumHeight, heightMeasureSpec).toFloat()
        mWidth = getDefaultSize(suggestedMinimumWidth, widthMeasureSpec).toFloat()

        if (mHeight > mWidth) mHeight = mWidth else mWidth = mHeight

        initSpeedPaint()
        initSpeedUnitPaint()
        initRect()
        initGradientPaintRing()
        initGreyPaintRing()
        initUnitPaint()
        initIndicatorBitmap()
        setMeasuredDimension(mWidth.toInt(), mHeight.toInt())
    }

    private fun initSpeedUnitPaint() {
        speedUnitPaint.apply {
            textAlign = Paint.Align.LEFT
            textSize = (mWidth * 0.05).toFloat()
            color = Color.parseColor("#333333")
        }
        applyStolzlMedium(speedUnitPaint)
    }

    private fun initIndicatorBitmap() {
        val bitmap = ContextCompat.getDrawable(context, R.drawable.ic_throughput_indicator)!!.toBitmap()
        indicatorBitmap = Bitmap.createScaledBitmap(bitmap, (mWidth * 0.35).toInt(), (mWidth * 0.35).toInt(), false)
    }

    private fun initSpeedPaint() {
        speedPaint.apply {
            textAlign = Paint.Align.CENTER
            textSize = (mWidth * 0.08).toFloat()
            color = Color.parseColor("#333333")
        }
        applyStolzlMedium(speedPaint)
    }

    private fun initRect() {
        rectangle.apply {
            left = (0.05 * mWidth).toFloat()
            top = (0.05 * mWidth).toFloat()
            right = mWidth - (0.05 * mWidth).toFloat()
            bottom = mHeight - (0.05 * mWidth).toFloat()
        }
    }

    private fun initGreyPaintRing() {
        greyPaintRing.apply {
            color = Color.rgb(189, 189, 189)
            strokeWidth = (0.07 * mWidth).toFloat()
            style = Paint.Style.STROKE
        }
    }

    private fun initUnitPaint() {
        unitPaint.apply {
            textSize = (mWidth * 0.04).toFloat()
            color = Color.parseColor("#666666")
        }
        applyStolzlMedium(unitPaint)
    }

    private fun initGradientPaintRing() {
        gradientPaintRing.apply {
            strokeWidth = (0.07 * mWidth).toFloat()
            style = Paint.Style.STROKE
            shader = LinearGradient(0f, 0f, mWidth, 0f,
                    ringColors,
                    positions,
                    Shader.TileMode.CLAMP)
        }
    }

    private fun applyStolzlMedium(paint: Paint) {
        ResourcesCompat.getFont(context, R.font.stolzl_medium)?.let { paint.typeface = it }
    }

    fun updateSpeed(progress: Int, value: String, unit: String, mode: Mode) {
        when {
            progress < 0 -> this.progress = 0
            progress > 100 -> this.progress = 100
            else -> this.progress = progress
        }

        this.value = value
        this.unit = unit
        this.mode = mode
        invalidate()
    }

    fun setUnitsArray(array: ArrayList<String>) {
        if (array.size != 9) {
            throw IllegalArgumentException("You should provide array containing 9 elements")
        }
        this.unitsArray = array
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val startAngle = (135f + (progress / 100.0) * 270).toFloat()
        val sweepAngle = (270 * (100.0 - progress) / 100.0).toFloat()
        val px = (mWidth / 2.0).toFloat()
        val py = (mHeight / 2.0).toFloat()

        mMatrix.apply {
            reset()
            indicatorBitmap?.let { postTranslate(-(0.188 * it.width).toFloat(), -(it.height * 0.436).toFloat()) }
            postRotate(135f + ((progress / 100.0) * 270.0).toFloat())
            postTranslate(px, py)
        }

        canvas.apply {
            indicatorBitmap?.let { drawBitmap(it, mMatrix, indicatorPaint) }
            drawArc(rectangle, 135f, 270f, false, gradientPaintRing)
            drawArc(rectangle, startAngle, sweepAngle, false, greyPaintRing)
            drawUnits(this)
            drawSpeed(this)
        }

    }

    private fun drawUnits(canvas: Canvas) {
        if (unitsArray.size > 0) {
            canvas.apply {
                // LEFT
                unitPaint.textAlign = Paint.Align.LEFT
                drawText(unitsArray[0], (mWidth * 0.22).toFloat(), (mHeight * 0.77).toFloat(), unitPaint)
                drawText(unitsArray[1], (mWidth * 0.12).toFloat(), (mHeight * 0.59).toFloat(), unitPaint)
                drawText(unitsArray[2], (mWidth * 0.13).toFloat(), (mHeight * 0.38).toFloat(), unitPaint)
                drawText(unitsArray[3], (mWidth * 0.23).toFloat(), (mHeight * 0.23).toFloat(), unitPaint)

                // CENTER
                unitPaint.textAlign = Paint.Align.CENTER
                drawText(unitsArray[4], mWidth / 2, (0.14 * mHeight).toFloat(), unitPaint)

                // RIGHT
                unitPaint.textAlign = Paint.Align.RIGHT
                drawText(unitsArray[5], (mWidth * 0.77).toFloat(), (mHeight * 0.23).toFloat(), unitPaint)
                drawText(unitsArray[6], (mWidth * 0.87).toFloat(), (mHeight * 0.38).toFloat(), unitPaint)
                drawText(unitsArray[7], (mWidth * 0.88).toFloat(), (mHeight * 0.59).toFloat(), unitPaint)
                drawText(unitsArray[8], (mWidth * 0.78).toFloat(), (mHeight * 0.77).toFloat(), unitPaint)
            }
        }
    }

    private fun drawSpeed(canvas: Canvas) {
        canvas.apply {
            drawText(value, mWidth / 2, (mHeight * 0.8).toFloat(), speedPaint)
            drawLine((mWidth * 0.39).toFloat(), (mHeight * 0.83).toFloat(), (mWidth * 0.61).toFloat(), (mHeight * 0.83).toFloat(), linePaint)
            drawText(unit, (mWidth * 0.47).toFloat(), (mHeight * 0.89).toFloat(), speedUnitPaint)
            drawMode(this)
        }
    }

    private fun drawMode(canvas: Canvas) {
        when (mode) {
            Mode.UPLOAD -> {
                val bitmap = ContextCompat.getDrawable(context, R.drawable.ic_arrow_up)!!.toBitmap()
                canvas.drawBitmap(Bitmap.createScaledBitmap(bitmap, (mWidth * 0.05 * 5.0 / 6.0).toInt(), (mWidth * 0.05).toInt(), false), (mWidth * 0.41).toFloat(), (mHeight * 0.85).toFloat(), null)
            }
            Mode.DOWNLOAD -> {
                val bitmap = ContextCompat.getDrawable(context, R.drawable.ic_arrow_down)!!.toBitmap()
                canvas.drawBitmap(Bitmap.createScaledBitmap(bitmap, (mWidth * 0.05 * 5.0 / 6.0).toInt(), (mWidth * 0.05).toInt(), false), (mWidth * 0.41).toFloat(), (mHeight * 0.85).toFloat(), null)
            }
            Mode.NONE -> {
            }
        }
    }

    enum class Mode {
        UPLOAD,
        DOWNLOAD,
        NONE
    }
}