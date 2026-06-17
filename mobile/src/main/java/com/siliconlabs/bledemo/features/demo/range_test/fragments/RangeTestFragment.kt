package com.siliconlabs.bledemo.features.demo.range_test.fragments

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import com.github.mikephil.charting.formatter.IFillFormatter
import com.github.mikephil.charting.interfaces.dataprovider.LineDataProvider
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.renderer.YAxisRenderer
import com.github.mikephil.charting.utils.Transformer
import com.github.mikephil.charting.utils.Utils
import com.github.mikephil.charting.utils.ViewPortHandler
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.databinding.FragmentRangeTestBinding
import com.siliconlabs.bledemo.features.demo.range_test.activities.RangeTestActivity
import com.siliconlabs.bledemo.features.demo.range_test.models.RangeTestMode
import com.siliconlabs.bledemo.features.demo.range_test.models.RangeTestValues
import com.siliconlabs.bledemo.features.demo.range_test.models.TxPower
import com.siliconlabs.bledemo.features.demo.range_test.presenters.RangeTestPresenter
import com.siliconlabs.bledemo.features.demo.range_test.presenters.RangeTestPresenter.Controller
import java.util.*
import java.util.regex.Pattern

/**
 * @author Comarch S.A.
 */
class RangeTestFragment : Fragment(), RangeTestPresenter.RangeTestView {
    private lateinit var controller: Controller

    private var buttonStringIdOff = 0
    private var buttonStringIdOn = 0
    private var series1 = false

    private var mode: RangeTestMode? = null
    private var chartDataSet: LineDataSet? = null

    private var chartData: LineData? = null
    private var seekBarsListener: SeekBarsListener? = null

    private var txPowerValues: List<TxPower>? = null
    private var payloadLengthValues: List<Int>? = null
    private var maWindowSizeValues: List<Int>? = null

    private var phyValues: List<Map.Entry<Int, String>>? = null
    private var txLayouts: ArrayList<View> = ArrayList()
    private var rxLayouts: ArrayList<View> = ArrayList()
    private var disabledLayouts: ArrayList<View> = ArrayList()
    private lateinit var binding: FragmentRangeTestBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = activity as RangeTestActivity

        val modeCode = arguments?.getInt(ARG_MODE)!!
        mode = RangeTestMode.fromCode(modeCode)
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentRangeTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        txLayouts.apply {
            add(binding.rangeTransmittedLayout)
            add(binding.tvRangeTxPowerLayout1)
            add(binding.rangeTxPowerLayout2)
        }

        rxLayouts.apply {
            add(binding.rangeRxMetricsAndChartSection)
        }

        disabledLayouts.apply {
            add(binding.sbRangeTxPower)
            add(binding.sbRangePayloadLength)
            add(binding.rangeSeekMaWindowSize)
            add(binding.spTxPower)
            add(binding.spPayloadLength)
            add(binding.spMaWindowSize)
            add(binding.spRemoteId)
            add(binding.spSelfId)
            add(binding.rangeCheckUartLog)
            add(binding.rangeCheckPacketRepeat)
            add(binding.spPhyConfig)
        }


        mode?.let { setup(it) }
        controller.setView(this)

        binding.rangeTestStartStop.setOnClickListener {
            controller.toggleRunningState()
        }

        binding.rangeCheckPacketRepeat.setOnCheckedChangeListener { _, checked ->

            binding.spPacketCount.isEnabled = !checked
            if (checked) {
                controller.updatePacketCount(RangeTestValues.PACKET_COUNT_REPEAT)
            } else {
                val packetCountIndex = binding.spPacketCount.selectedItemPosition
                val packetCount = RangeTestValues.PACKET_COUNT_LOOKUP[packetCountIndex]
                controller.updatePacketCount(packetCount)
            }
        }

        binding.rangeCheckUartLog.setOnCheckedChangeListener { _, checked ->
            controller.updateUartLogEnabled(checked)
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        val controller: Controller = activity as RangeTestActivity
        controller.setView(null)
    }

    private fun clearChart() {
        if (binding.chart == null || chartDataSet == null) {
            return
        }

        chartDataSet?.apply {
            clear()
            addEntry(Entry((-2).toFloat(), 0f))
            addEntry(Entry((-1).toFloat(), 0f))
            notifyDataSetChanged()
        }

        chartData?.notifyDataChanged()
        binding.chart.notifyDataSetChanged()
        binding.chart.invalidate()
    }

    override fun runOnUiThread(runnable: Runnable?) {
        view?.post(runnable)
    }

    override fun showDeviceName(name: String?) {
        binding.tvRangeDeviceName.text = name
    }

    override fun showModelNumber(number: String?, running: Boolean?) {
        binding.tvRangeModelNumber.text = number
        val pattern = Pattern.compile(SERIES_2_REGEX, Pattern.CASE_INSENSITIVE)
        series1 = !pattern.matcher(number).find()


        if (running != null && running) binding.spChannelNumber.isEnabled = false
        else binding.spChannelNumber.isEnabled = series1
    }

    override fun showTxPower(power: TxPower?, values: List<TxPower>) {
        if (values != txPowerValues) {
            setupSpinnerValues(binding.spTxPower, values)
            binding.sbRangeTxPower.max = values.size - 1
            binding.sbRangeTxPower.setOnSeekBarChangeListener(seekBarsListener)
            binding.spTxPower.onItemSelectedListener =
                SpinnerSeekBarListener(binding.sbRangeTxPower)
            txPowerValues = values
        }
        val index = values.indexOf(power)
        if (index != -1) {
            binding.sbRangeTxPower.progress = index
            binding.spTxPower.setSelection(index)
        }
    }

    override fun showPayloadLength(length: Int, values: List<Int>) {
        if (values != payloadLengthValues) {
            setupSpinnerValues(binding.spPayloadLength, values)
            binding.sbRangePayloadLength.max = values.size - 1
            binding.sbRangePayloadLength.setOnSeekBarChangeListener(seekBarsListener)
            binding.spPayloadLength.onItemSelectedListener =
                SpinnerSeekBarListener(binding.sbRangePayloadLength)
            payloadLengthValues = values
        }
        val index = values.indexOf(length)
        if (index != -1) {
            binding.sbRangePayloadLength.progress = index
            binding.spPayloadLength.setSelection(index)
        }
    }

    override fun showMaWindowSize(size: Int, values: List<Int>) {
        if (values != maWindowSizeValues) {
            setupSpinnerValues(binding.spMaWindowSize, values)
            binding.rangeSeekMaWindowSize.max = values.size - 1
            binding.rangeSeekMaWindowSize.setOnSeekBarChangeListener(seekBarsListener)
            binding.spMaWindowSize.onItemSelectedListener =
                SpinnerSeekBarListener(binding.rangeSeekMaWindowSize)
            maWindowSizeValues = values
        }
        val index = values.indexOf(size)
        if (index != -1) {
            binding.rangeSeekMaWindowSize.progress = index
            binding.spMaWindowSize.setSelection(index)
        }
    }

    override fun showChannelNumber(number: Int) {
        val index = RangeTestValues.CHANNEL_LOOKUP.indexOf(number)
        binding.spChannelNumber.setSelection(index)
    }

    override fun showPacketCountRepeat(enabled: Boolean) {
        binding.rangeCheckPacketRepeat.isChecked = enabled
    }

    override fun showPacketRequired(required: Int) {
        val index = RangeTestValues.PACKET_COUNT_LOOKUP.indexOf(required)
        binding.spPacketCount.setSelection(index)
    }

    override fun showPacketSent(sent: Int) {
        setPacketSent(sent)
    }

    override fun showPer(per: Float) {
        setPer(per)
    }

    override fun showMa(ma: Float) {
        setMa(ma)
    }

    override fun showRemoteId(id: Int) {
        val index = RangeTestValues.ID_LOOKUP.indexOf(id)
        binding.spRemoteId.setSelection(index)
    }

    override fun showSelfId(id: Int) {
        val index = RangeTestValues.ID_LOOKUP.indexOf(id)
        binding.spSelfId.setSelection(index)
    }

    override fun showUartLogEnabled(enabled: Boolean) {
        binding.rangeCheckUartLog.isChecked = enabled
    }

    override fun showRunningState(running: Boolean) {
        setupRunning(running)
    }

    override fun showTestRssi(rssi: Int) {
        setRssi(rssi)
        appendChartData(rssi.toFloat())
    }

    override fun showTestRx(received: Int, required: Int) {
        setRx(received, required)
    }

    override fun showPhy(phy: Int, values: LinkedHashMap<Int, String>) {
        if (!values.containsKey(phy)) {
            return
        }

        val valuesList: List<Map.Entry<Int, String>> =
            ArrayList<Map.Entry<Int, String>>(values.entries)
        if (valuesList != phyValues) {
            setupSpinnerValues(binding.spPhyConfig, ArrayList(values.values))
            phyValues = valuesList
        }

        for (i in valuesList.indices) {
            val entry = valuesList[i]
            if (entry.key == phy) {
                binding.spPhyConfig.setSelection(i)
                return
            }
        }
    }

    override fun clearTestResults() {
        clearChart()
    }

    private fun setup(mode: RangeTestMode) {
        this.mode = mode
        seekBarsListener = SeekBarsListener()
        if (mode == RangeTestMode.Tx) {
            setVisibility(rxLayouts, View.GONE)
            setupSpinnerValues(binding.spTxPower, listOf(""))
            buttonStringIdOff = R.string.range_tx_start
            buttonStringIdOn = R.string.range_tx_stop
            setPacketSent(0)
        } else {
            setVisibility(txLayouts, View.GONE)
            setupChartView()
            buttonStringIdOff = R.string.range_rx_start
            buttonStringIdOn = R.string.range_rx_waiting
            setRx(0, 0)
            setRssi(0)
            setMa(0f)
            setPer(0f)
        }

        setupSpinnerValues(binding.spPayloadLength, listOf(""))
        setupSpinnerValues(binding.spMaWindowSize, listOf(""))
        setupSpinnerValues(binding.spChannelNumber, RangeTestValues.CHANNEL_LOOKUP)
        setupSpinnerValues(binding.spPacketCount, RangeTestValues.PACKET_COUNT_LOOKUP)
        setupSpinnerValues(binding.spRemoteId, RangeTestValues.ID_LOOKUP)
        setupSpinnerValues(binding.spSelfId, RangeTestValues.ID_LOOKUP)

        binding.spChannelNumber.onItemSelectedListener = object : SpinnerListener() {
            override fun onItemSelected(index: Int) {
                val channel = RangeTestValues.CHANNEL_LOOKUP[index]
                controller.updateChannel(channel)
            }
        }

        binding.spPacketCount.onItemSelectedListener = object : SpinnerListener() {
            override fun onItemSelected(index: Int) {
                val packetCount = RangeTestValues.PACKET_COUNT_LOOKUP[index]
                controller.updatePacketCount(packetCount)
            }
        }

        binding.spRemoteId.onItemSelectedListener = object : SpinnerListener() {
            override fun onItemSelected(index: Int) {
                val id = RangeTestValues.ID_LOOKUP[index]
                controller.updateRemoteId(id)
            }
        }

        binding.spSelfId.onItemSelectedListener = object : SpinnerListener() {
            override fun onItemSelected(index: Int) {
                val id = RangeTestValues.ID_LOOKUP[index]
                controller.updateSelfId(id)
            }
        }

        binding.spPhyConfig.onItemSelectedListener = object : SpinnerListener() {
            override fun onItemSelected(index: Int) {
                val id = phyValues!![index].key
                controller.updatePhyConfig(id)
            }
        }

        binding.sbRangeTxPower.isEnabled = false
        binding.spTxPower.isEnabled = false
        binding.sbRangePayloadLength.isEnabled = false
        binding.spPayloadLength.isEnabled = false
        binding.rangeSeekMaWindowSize.isEnabled = false
        binding.spMaWindowSize.isEnabled = false
        binding.spPhyConfig.isEnabled = false
        setupRunning(false)
    }

    private fun appendChartData(rssi: Float) {
        if (binding.chart == null || chartDataSet == null) {
            return
        }
        val index = chartDataSet!!.entryCount
        chartDataSet!!.addEntry(Entry(index.toFloat(), rssi))
        binding.chart.xAxis.axisMinimum = 0f
        binding.chart.xAxis.axisMaximum = Math.max(50, index).toFloat()
        binding.chart.setVisibleXRange(50f, 50f)
        binding.chart.moveViewToX(index.toFloat())
        chartDataSet?.notifyDataSetChanged()
        chartData?.notifyDataChanged()
        binding.chart.notifyDataSetChanged()
        binding.chart.invalidate()
    }


    private fun setPacketSent(packetSent: Int) {
        binding.tvRangeTestPacketCount.text = packetSent.toString()
    }

    private fun setRx(received: Int, required: Int) {

        setValue(binding.tvRangeTestRx, R.string.range_rx_rx, received, required)
    }

    private fun setRssi(rssi: Int) {

        setValue(binding.tvRangeTestRssi, R.string.range_rx_rssi, rssi)
    }

    private fun setMa(ma: Float) {

        binding.tvRangeTestMa.text = String.format(Locale.US, "%.1f", ma).plus("%")
    }

    private fun setPer(per: Float) {
        binding.tvRangeTestPer.text = String.format(Locale.US, "%.1f", per).plus("%")
    }

    private fun setValue(view: TextView, resId: Int, vararg args: Any) {
        view.text = view.context.getString(resId, *args)
    }

    private fun updateControllerFromSeekBar(seekBar: SeekBar) {
        val progress = seekBar.progress
        when {
            seekBar === binding.sbRangeTxPower -> {
                val power = txPowerValues!![progress]
                controller.updateTxPower(power.asCharacteristicValue())
            }

            seekBar === binding.sbRangePayloadLength -> {
                val payloadLength = payloadLengthValues!![progress]
                controller.updatePayloadLength(payloadLength)
            }

            seekBar === binding.rangeSeekMaWindowSize -> {
                val maWindowSize = maWindowSizeValues!![progress]
                controller.updateMaWindowSize(maWindowSize)
            }
        }
    }

    private fun setupRunning(running: Boolean) {
        if (running) {
            setEnabled(disabledLayouts, false)
            binding.spChannelNumber.isEnabled = false
            binding.spPacketCount.isEnabled = false
            binding.rangeTestStartStop.setText(buttonStringIdOn)
            binding.rangeTestStartStop.isEnabled = mode != RangeTestMode.Rx
        } else {
            setEnabled(disabledLayouts, true)
            binding.spChannelNumber.isEnabled = series1
            binding.spPacketCount.isEnabled = !binding.rangeCheckPacketRepeat.isChecked
            binding.rangeTestStartStop.setText(buttonStringIdOff)
            binding.rangeTestStartStop.isEnabled = true
        }
    }

    private fun <T> setupSpinnerValues(spinner: Spinner, values: List<T>) {
        val adapter = ArrayAdapter<T>(spinner.context, R.layout.spinner_item_layout, values)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_layout)
        spinner.adapter = adapter
        spinner.setSelection(0, false)
    }

    private fun setVisibility(views: List<View>, visibility: Int) {
        for (view in views) {
            view.visibility = visibility
        }
    }

    private fun setEnabled(views: List<View>, enabled: Boolean) {
        for (view in views) {
            view.isEnabled = enabled
        }
    }

    private fun setupChartView() {
        val textColor = ContextCompat.getColor(requireContext(), R.color.silabs_dark_gray_text)
        val axisColor = ContextCompat.getColor(requireContext(), R.color.silabs_dark_gray_text)
        val graphColor = ContextCompat.getColor(requireContext(), R.color.silabs_red)
        val axisTypeface = ResourcesCompat.getFont(requireContext(), R.font.stolzl_regular)

        binding.chart.apply {
            description?.isEnabled = false
            legend?.isEnabled = false
            setScaleEnabled(false)
            isHighlightPerTapEnabled = false
            isHighlightPerDragEnabled = false
            isDoubleTapToZoomEnabled = false
            axisRight.isEnabled = false
            axisLeft.valueFormatter = YAxisValueFormatter()
            axisLeft.textSize = 10f
            axisLeft.textColor = textColor
            axisLeft.typeface = axisTypeface
            axisLeft.axisMinimum = -100f
            axisLeft.axisMaximum = 25f
            axisLeft.granularity = 25f
            axisLeft.setDrawGridLines(false)
            axisLeft.setDrawAxisLine(true)
            axisLeft.axisLineWidth = 0.5f
            axisLeft.axisLineColor = axisColor
            rendererLeftYAxis = YAxisArrowRenderer(
                binding.chart.viewPortHandler,
                binding.chart.axisLeft,
                binding.chart.rendererLeftYAxis.transformer
            )
            xAxis.setDrawGridLines(false)
            xAxis.setDrawAxisLine(false)
            xAxis.axisMinimum = 0f
            xAxis.axisMaximum = 50f
            xAxis.valueFormatter = XAxisValueFormatter()
            xAxis.typeface = axisTypeface
            minOffset = 0f
            setExtraOffsets(-4f, 0f, 4f, 4f)
            axisLeft.addLimitLine(createLimitLine(0f, axisColor))
            axisLeft.addLimitLine(createDashLine(-100f, axisColor))
            axisLeft.addLimitLine(createDashLine(-75f, axisColor))
            axisLeft.addLimitLine(createDashLine(-50f, axisColor))
            axisLeft.addLimitLine(createDashLine(-25f, axisColor))
            axisLeft.addLimitLine(createDashLine(25f, axisColor))
        }

        val chartData = createChartData(graphColor)
        binding.chart.data = chartData
        chartDataSet!!.notifyDataSetChanged()
        chartData.notifyDataChanged()
        binding.chart.notifyDataSetChanged()
        binding.chart.invalidate()
    }

    private fun createChartData(color: Int): LineData {
        val entries: MutableList<Entry> = ArrayList(1024)
        entries.add(Entry((-2).toFloat(), 0f))
        entries.add(Entry((-1).toFloat(), 0f))

        chartDataSet = LineDataSet(entries, null)
        chartDataSet?.apply {
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.1f
            setDrawCircles(false)
            setDrawFilled(true)
            lineWidth = 0f
            this.color = color
            fillColor = color
            fillAlpha = 255
            setDrawHorizontalHighlightIndicator(false)
            setDrawVerticalHighlightIndicator(false)
            fillFormatter = CubicLineSampleFillFormatter()
        }

        chartData = LineData(chartDataSet)
        chartData?.setDrawValues(false)
        return chartData!!
    }

    private fun createLimitLine(position: Float, color: Int): LimitLine {
        return ArrowLimitLine(position).apply {
            lineColor = color
            lineWidth = 0.5f
        }
    }

    private fun createDashLine(position: Float, color: Int): LimitLine {
        val line = LimitLine(position)
        val lineLength = Utils.convertDpToPixel(4f)
        val spaceLength = Utils.convertDpToPixel(2f)
        line.lineColor = color
        line.lineWidth = 0.5f
        line.enableDashedLine(lineLength, spaceLength, 0f)
        return line
    }

    private inner class SeekBarsListener : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) {
                when {
                    seekBar === binding.sbRangeTxPower -> {
                        binding.spTxPower.setSelection(progress)
                    }

                    seekBar === binding.sbRangePayloadLength -> {
                        binding.spPayloadLength.setSelection(progress)
                    }

                    seekBar === binding.rangeSeekMaWindowSize -> {
                        binding.spMaWindowSize.setSelection(progress)
                    }
                }
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {}
        override fun onStopTrackingTouch(seekBar: SeekBar) {
            updateControllerFromSeekBar(seekBar)
        }
    }

    private inner class SpinnerSeekBarListener(private val seekBar: SeekBar) :
        AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
            seekBar.progress = position
            updateControllerFromSeekBar(seekBar)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {}

    }

    private abstract inner class SpinnerListener : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
            onItemSelected(position)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {}
        protected abstract fun onItemSelected(index: Int)
    }

    private inner class YAxisValueFormatter : IAxisValueFormatter {
        override fun getFormattedValue(value: Float, axis: AxisBase): String {
            return (if (value > 0) "+$value" else value).toString() + " dBm"
        }
    }

    private inner class XAxisValueFormatter : IAxisValueFormatter {
        override fun getFormattedValue(value: Float, axis: AxisBase): String {
            return ""
        }
    }

    private inner class CubicLineSampleFillFormatter : IFillFormatter {
        override fun getFillLinePosition(
            dataSet: ILineDataSet,
            dataProvider: LineDataProvider
        ): Float {
            return (-100).toFloat()
        }
    }

    private class YAxisArrowRenderer(
        viewPortHandler: ViewPortHandler,
        yAxis: YAxis,
        trans: Transformer
    ) : YAxisRenderer(viewPortHandler, yAxis, trans) {
        private val ARROW_SIZE = Utils.convertDpToPixel(3.5f)
        private val arrowPath = Path()
        private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            arrowPaint.style = Paint.Style.FILL_AND_STROKE
        }

        override fun renderAxisLine(c: Canvas) {
            if (!mYAxis.isEnabled || !mYAxis.isDrawAxisLineEnabled) return
            mAxisLinePaint.color = mYAxis.axisLineColor
            mAxisLinePaint.strokeWidth = mYAxis.axisLineWidth
            arrowPaint.color = mYAxis.axisLineColor
            arrowPath.reset()
            if (mYAxis.axisDependency == YAxis.AxisDependency.LEFT) {
                c.drawLine(
                    mViewPortHandler.contentLeft(),
                    mViewPortHandler.contentTop() - ARROW_SIZE * 1.5f,
                    mViewPortHandler.contentLeft(),
                    mViewPortHandler.contentBottom(),
                    mAxisLinePaint
                )
                arrowPath.moveTo(
                    mViewPortHandler.contentLeft(),
                    mViewPortHandler.contentTop() - ARROW_SIZE * 1.5f
                )
                arrowPath.lineTo(
                    mViewPortHandler.contentLeft() + ARROW_SIZE / 2f,
                    mViewPortHandler.contentTop() - ARROW_SIZE * 0.5f
                )
                arrowPath.lineTo(
                    mViewPortHandler.contentLeft() - ARROW_SIZE / 2f,
                    mViewPortHandler.contentTop() - ARROW_SIZE * 0.5f
                )
                arrowPath.close()
            } else {
                c.drawLine(
                    mViewPortHandler.contentRight(),
                    mViewPortHandler.contentTop() - ARROW_SIZE * 1.5f,
                    mViewPortHandler.contentRight(),
                    mViewPortHandler.contentBottom(),
                    mAxisLinePaint
                )
                arrowPath.moveTo(
                    mViewPortHandler.contentRight(),
                    mViewPortHandler.contentTop() - ARROW_SIZE * 1.5f
                )
                arrowPath.lineTo(
                    mViewPortHandler.contentRight() + ARROW_SIZE / 2f,
                    mViewPortHandler.contentTop() - ARROW_SIZE * 0.5f
                )
                arrowPath.lineTo(
                    mViewPortHandler.contentRight() - ARROW_SIZE / 2f,
                    mViewPortHandler.contentTop() - ARROW_SIZE * 0.5f
                )
                arrowPath.close()
            }
            c.drawPath(arrowPath, arrowPaint)
        }

        override fun renderLimitLines(c: Canvas) {
            val limitLines = mYAxis.limitLines
            if (limitLines == null || limitLines.size <= 0) return
            val pts = mRenderLimitLinesBuffer
            pts[0] = 0f
            pts[1] = 0f
            val limitLinePath = mRenderLimitLines
            limitLinePath.reset()
            for (i in limitLines.indices) {
                val l = limitLines[i]
                if (!l.isEnabled) continue
                val clipRestoreCount = c.save()
                mLimitLineClippingRect.set(mViewPortHandler.contentRect)
                mLimitLineClippingRect.inset(0f, -l.lineWidth)
                c.clipRect(mLimitLineClippingRect)
                mLimitLinePaint.style = Paint.Style.STROKE
                mLimitLinePaint.color = l.lineColor
                mLimitLinePaint.strokeWidth = l.lineWidth
                mLimitLinePaint.pathEffect = l.dashPathEffect
                pts[1] = l.limit
                mTrans.pointValuesToPixel(pts)
                limitLinePath.moveTo(mViewPortHandler.contentLeft(), pts[1])
                limitLinePath.lineTo(mViewPortHandler.contentRight(), pts[1])
                c.drawPath(limitLinePath, mLimitLinePaint)
                limitLinePath.reset()
                // c.drawLines(pts, mLimitLinePaint);
                if (l is ArrowLimitLine) {
                    arrowPaint.color = l.getLineColor()
                    arrowPath.reset()
                    arrowPath.moveTo(mViewPortHandler.contentRight(), pts[1])
                    arrowPath.lineTo(
                        mViewPortHandler.contentRight() - ARROW_SIZE,
                        pts[1] + ARROW_SIZE / 2f
                    )
                    arrowPath.lineTo(
                        mViewPortHandler.contentRight() - ARROW_SIZE,
                        pts[1] - ARROW_SIZE / 2f
                    )
                    arrowPath.close()
                    c.drawPath(arrowPath, arrowPaint)
                }
                val label = l.label

                // if drawing the limit-value label is enabled
                if (label != null && label != "") {
                    mLimitLinePaint.style = l.textStyle
                    mLimitLinePaint.pathEffect = null
                    mLimitLinePaint.color = l.textColor
                    mLimitLinePaint.typeface = l.typeface
                    mLimitLinePaint.strokeWidth = 0.5f
                    mLimitLinePaint.textSize = l.textSize
                    val labelLineHeight = Utils.calcTextHeight(mLimitLinePaint, label).toFloat()
                    val xOffset = Utils.convertDpToPixel(4f) + l.xOffset
                    val yOffset = l.lineWidth + labelLineHeight + l.yOffset
                    val position = l.labelPosition
                    if (position == LimitLine.LimitLabelPosition.RIGHT_TOP) {
                        mLimitLinePaint.textAlign = Paint.Align.RIGHT
                        c.drawText(
                            label,
                            mViewPortHandler.contentRight() - xOffset,
                            pts[1] - yOffset + labelLineHeight, mLimitLinePaint
                        )
                    } else if (position == LimitLine.LimitLabelPosition.RIGHT_BOTTOM) {
                        mLimitLinePaint.textAlign = Paint.Align.RIGHT
                        c.drawText(
                            label,
                            mViewPortHandler.contentRight() - xOffset,
                            pts[1] + yOffset, mLimitLinePaint
                        )
                    } else if (position == LimitLine.LimitLabelPosition.LEFT_TOP) {
                        mLimitLinePaint.textAlign = Paint.Align.LEFT
                        c.drawText(
                            label,
                            mViewPortHandler.contentLeft() + xOffset,
                            pts[1] - yOffset + labelLineHeight, mLimitLinePaint
                        )
                    } else {
                        mLimitLinePaint.textAlign = Paint.Align.LEFT
                        c.drawText(
                            label,
                            mViewPortHandler.offsetLeft() + xOffset,
                            pts[1] + yOffset, mLimitLinePaint
                        )
                    }
                }
                c.restoreToCount(clipRestoreCount)
            }
        }
    }

    private inner class ArrowLimitLine(limit: Float) : LimitLine(limit)

    companion object {
        const val ARG_MODE = "ARG_MODE"
        private const val SERIES_2_REGEX = "BRD41(71|80|81)"
    }
}

