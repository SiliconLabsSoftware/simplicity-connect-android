package com.siliconlabs.bledemo.features.configure.advertiser.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.SpannableString
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.TextWatcher
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.constraintlayout.widget.ConstraintLayout
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.base.activities.BaseActivity
import com.siliconlabs.bledemo.databinding.ActivityAdvertiserConfigBinding
import com.siliconlabs.bledemo.databinding.AdvertiserDataContainerBinding
import com.siliconlabs.bledemo.databinding.DataTypeItemBinding
import com.siliconlabs.bledemo.databinding.DataTypeLayoutBinding
import com.siliconlabs.bledemo.features.configure.advertiser.adapters.DataTypeAdapter
import com.siliconlabs.bledemo.features.configure.advertiser.dialogs.LeaveAdvertiserConfigDialog
import com.siliconlabs.bledemo.features.configure.advertiser.dialogs.ManufacturerDataDialog
import com.siliconlabs.bledemo.features.configure.advertiser.dialogs.RemoveServicesDialog
import com.siliconlabs.bledemo.features.configure.advertiser.dialogs.Service128BitDataDialog
import com.siliconlabs.bledemo.features.configure.advertiser.dialogs.Service16BitDataDialog
import com.siliconlabs.bledemo.features.configure.advertiser.enums.AdvertisingMode
import com.siliconlabs.bledemo.features.configure.advertiser.enums.DataMode
import com.siliconlabs.bledemo.features.configure.advertiser.enums.DataType
import com.siliconlabs.bledemo.features.configure.advertiser.enums.LimitType
import com.siliconlabs.bledemo.features.configure.advertiser.enums.Phy
import com.siliconlabs.bledemo.features.configure.advertiser.models.AdvertiserData
import com.siliconlabs.bledemo.features.configure.advertiser.models.DataPacket
import com.siliconlabs.bledemo.features.configure.advertiser.models.ExtendedSettings
import com.siliconlabs.bledemo.features.configure.advertiser.models.Manufacturer
import com.siliconlabs.bledemo.features.configure.advertiser.models.Service128Bit
import com.siliconlabs.bledemo.features.configure.advertiser.models.Service16Bit
import com.siliconlabs.bledemo.features.configure.advertiser.presenters.AdvertiserConfigActivityPresenter
import com.siliconlabs.bledemo.features.configure.advertiser.utils.AdvertiserStorage
import com.siliconlabs.bledemo.features.configure.advertiser.utils.Translator
import com.siliconlabs.bledemo.features.configure.advertiser.utils.Validator
import com.siliconlabs.bledemo.utils.AppUtil


class AdvertiserConfigActivity : BaseActivity(), IAdvertiserConfigActivityView {
    private lateinit var presenter: AdvertiserConfigActivityPresenter
    private lateinit var advertiserData: AdvertiserData
    private lateinit var startConfigData: AdvertiserData

    private val translator = Translator(this)
    private var position: Int? = null

    private var spLegacyInitialSetup = true
    private var spExtendedInitialSetup = true
    private lateinit var binding: ActivityAdvertiserConfigBinding

    companion object {
        const val EXTRA_ADVERTISER_ITEM = "EXTRA_ADVERTISER_ITEM"
        const val EXTRA_ITEM_POSITION = "EXTRA_ITEM_POSITION"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdvertiserConfigBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        prepareToolbar()

        resetInitialSetupFlags()

        presenter = AdvertiserConfigActivityPresenter(
            this,
            AdvertiserStorage(this@AdvertiserConfigActivity)
        )
        presenter.prepareAdvertisingTypes()
        presenter.preparePhyParameters()

        advertiserData =
            intent.extras?.getParcelable<AdvertiserData>(EXTRA_ADVERTISER_ITEM) as AdvertiserData
        position = intent.getIntExtra(EXTRA_ITEM_POSITION, 0)
        presenter.onItemReceived(
            advertiserData,
            AdvertiserStorage(this@AdvertiserConfigActivity).isAdvertisingExtensionSupported()
        )

        startConfigData = advertiserData.deepCopy()

        prepareDataSpinners()
        loadData()
        handleAdvertisingSetNameChanges()
        handleAdvertisingLimitSelection()
        updateAdvertisingLimitLabels()
    }

    private fun resetInitialSetupFlags() {
        spExtendedInitialSetup = true
        spLegacyInitialSetup = true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_advertiser_configuration, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.save_advertiser -> {
                presenter.handleSave()
                true
            }

            android.R.id.home -> {
                exitConfigView()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        exitConfigView()
    }

    private fun prepareToolbar() {
        AppUtil.setEdgeToEdge(window,this)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }

    private fun exitConfigView() {
        if (hasConfigurationChanged() && AdvertiserStorage(this).shouldDisplayLeaveAdvertiserConfigDialog()) {
            LeaveAdvertiserConfigDialog(object : LeaveAdvertiserConfigDialog.Callback {
                override fun onYesClicked() {
                    presenter.handleSave()
                }

                override fun onNoClicked() {
                    super@AdvertiserConfigActivity.onBackPressed()
                }

            }).show(supportFragmentManager, "dialog_leave_advertiser_config")
        } else {
            super.onBackPressed()
        }
    }

    private fun hasConfigurationChanged(): Boolean {

        // 1. Verify if advertising data / scan response data has changed
        if (startConfigData.advertisingData != advertiserData.advertisingData) return true
        else if (startConfigData.scanResponseData != advertiserData.scanResponseData) return true

        // 2. Verify if any text input is currently not valid
        if (isAnyInputNotValid()) return true

        // 3. Verify if other data has changed
        startConfigData.apply {
            when {

                name != binding.advConfigName.etAdvertisingSetName.text.toString() -> return true
                isLegacy != binding.advConfigType.rbLegacyAdvertising.isChecked -> return true
                mode != getAdvertisingMode() -> return true
                settings != getExtendedSettings() -> return true
                advertisingIntervalMs != getAdvertisingInterval() -> return true
                txPower != getTxPower() -> return true
                limitType != getAdvertisingLimitType() -> return true
                timeLimit.toString() != binding.advConfigParam.etTimeLimit.text.toString() -> return true
                isEventLimitAvailable() && eventLimit.toString() != binding.advConfigParam.etEventLimit.text.toString() -> return true
            }
        }

        // 4. If configuration has not changed return false
        return false
    }


    private fun handleAdvertisingSetNameChanges() {
        binding.advConfigName.etAdvertisingSetName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                title = s.toString()
            }
        })
    }

    private fun isEventLimitAvailable(): Boolean {
        return binding.advConfigParam.llEventLimit.visibility == View.VISIBLE
    }

    private fun isTxPowerNotValid(): Boolean {

        return binding.advConfigParam.llTxPower.visibility == View.VISIBLE &&
                !Validator.isTxPowerValid(binding.advConfigParam.etTxPower.text.toString())
    }

    private fun isAdvertisingIntervalNotValid(): Boolean {
        return binding.advConfigParam.llAdvertisingInterval.visibility == View.VISIBLE &&
                !Validator.isAdvertisingIntervalValid(
                    binding.advConfigParam.etAdvertisingInterval.text.toString()
                )
    }

    private fun isTimeLimitNotValid(): Boolean {
        return binding.advConfigParam.rbTimeLimit.isChecked && !Validator.isAdvertisingTimeLimitValid(
            binding.advConfigParam.etTimeLimit.text.toString(),
            true
        )
    }

    private fun isEventLimitNotValid(): Boolean {
        return binding.advConfigParam.rbEventLimit.isChecked &&
                !Validator.isAdvertisingEventLimitValid(binding.advConfigParam.etEventLimit.text.toString())
    }

    private fun isAnyInputNotValid(): Boolean {
        return isTxPowerNotValid() || isAdvertisingIntervalNotValid() || isTimeLimitNotValid() || isEventLimitNotValid()
    }

    override fun onSaveHandled() {
        if (isTxPowerNotValid())
            showMessage(R.string.advertiser_config_message_invalid_tx_power)
        else if (isAdvertisingIntervalNotValid())
            showMessage(R.string.advertiser_config_message_invalid_interval)
        else if (isTimeLimitNotValid())
            showMessage(R.string.advertiser_config_message_invalid_time_limit)
        else if (isEventLimitNotValid())
            showMessage(R.string.advertiser_config_message_invalid_event_limit)
        else {
            val name = binding.advConfigName.etAdvertisingSetName.text.toString()
            val isLegacy = binding.advConfigType.rbLegacyAdvertising.isChecked
            val advertisingMode = getAdvertisingMode()
            val settings = getExtendedSettings()
            val interval = getAdvertisingInterval()
            val txPower = getTxPower()

            val limitType = getAdvertisingLimitType()
            val timeLimit = getAdvertisingTimeLimit()
            val eventLimit = getAdvertisingEventLimit()

            presenter.setAdvertisingName(name)
            presenter.setAdvertisingType(isLegacy, advertisingMode)
            presenter.setAdvertisingParams(settings, interval, txPower)
            presenter.setAdvertisingLimit(limitType, timeLimit, eventLimit)

            val resultIntent = Intent()
            resultIntent.putExtra(EXTRA_ADVERTISER_ITEM, advertiserData)
            resultIntent.putExtra(EXTRA_ITEM_POSITION, position)
            setResult(Activity.RESULT_OK, resultIntent)
            super.onBackPressed()
        }
    }

    private fun getAdvertisingInterval(): Int {
        return binding.advConfigParam.etAdvertisingInterval.text.toString().toInt()
    }

    private fun getTxPower(): Int {
        return binding.advConfigParam.etTxPower.text.toString().toInt()
    }

    private fun getAdvertisingLimitType(): LimitType {
        return when {
            binding.advConfigParam.rbNoLimit.isChecked -> LimitType.NO_LIMIT
            binding.advConfigParam.rbTimeLimit.isChecked -> LimitType.TIME_LIMIT
            else -> LimitType.EVENT_LIMIT
        }
    }

    private fun getAdvertisingTimeLimit(): Int {
        return if (binding.advConfigParam.rbTimeLimit.isChecked) binding.advConfigParam.etTimeLimit.text.toString()
            .toInt()
        else -1
    }

    private fun getAdvertisingEventLimit(): Int {
        return if (binding.advConfigParam.rbEventLimit.isChecked) binding.advConfigParam.etEventLimit.text.toString()
            .toInt()
        else -1
    }

    private fun getAdvertisingMode(): AdvertisingMode {

        return if (binding.advConfigType.rbLegacyAdvertising.isChecked) translator.getStringAsAdvertisingMode(
            binding.advConfigType.spLegacy.selectedItem.toString()
        )
        else translator
            .getStringAsAdvertisingMode(binding.advConfigType.spExtended.selectedItem.toString())
    }

    private fun getExtendedSettings(): ExtendedSettings {

        return if (binding.advConfigParam.spPrimaryPhy.isEnabled) {
            val primaryPhy =
                translator.getStringAsPhy(binding.advConfigParam.spPrimaryPhy.selectedItem.toString())
            val secondaryPhy: Phy =
                translator.getStringAsPhy(binding.advConfigParam.spSecondaryPhy.selectedItem.toString())
            ExtendedSettings(
                binding.advConfigType.cbIncludeTxPower.isChecked,
                binding.advConfigType.cbAnonymous.isChecked,
                primaryPhy,
                secondaryPhy
            )
        } else ExtendedSettings(
            binding.advConfigType.cbIncludeTxPower.isChecked,
            binding.advConfigType.cbAnonymous.isChecked,
            null,
            null
        )
    }

    private fun handleAdvertisingLimitSelection() {
        binding.advConfigParam.rbNoLimit.setOnClickListener {
            binding.advConfigParam.rbNoLimit.isChecked = true
            setTimeLimitState(false)
            setEventLimitState(false)
        }
        binding.advConfigParam.rbTimeLimit.setOnClickListener {
            binding.advConfigParam.rbNoLimit.isChecked = false
            setTimeLimitState(true)
            setEventLimitState(false)
        }
        binding.advConfigParam.rbEventLimit.setOnClickListener {
            binding.advConfigParam.rbNoLimit.isChecked = false
            setTimeLimitState(false)
            setEventLimitState(true)
        }
    }

    private fun setTimeLimitState(enabled: Boolean) {
        binding.advConfigParam.rbTimeLimit.isChecked = enabled
        binding.advConfigParam.etTimeLimit.isEnabled = enabled
    }

    private fun setEventLimitState(enabled: Boolean) {
        binding.advConfigParam.rbEventLimit.isChecked = enabled
        binding.advConfigParam.etEventLimit.isEnabled = enabled
    }

    private fun updateAdvertisingLimitLabels() {
        binding.advConfigParam.rbTimeLimit.text =
            buildLabelWithResizedHint(getString(R.string.advertiser_label_time_limit))
        binding.advConfigParam.rbEventLimit.text =
            buildLabelWithResizedHint(getString(R.string.advertiser_label_event_limit))
    }

    private fun buildLabelWithResizedHint(labelText: String): SpannableString {
        val linebreakIndex = labelText.indexOf('\n')
        val label = SpannableString(labelText)
        if (linebreakIndex > -1) {
            label.setSpan(
                RelativeSizeSpan(12f / 14f),
                linebreakIndex + 1,
                labelText.length,
                SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return label
    }

    override fun onAdvertisingTypesPrepared(
        isLegacy: Boolean,
        legacyModes: List<AdvertisingMode>,
        extendedModes: List<AdvertisingMode>
    ) {
        val legacyAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item_advertiser_config_type,
            translator.getValuesAsStringList(legacyModes)
        )

        legacyAdapter.setDropDownViewResource(R.layout.spinner_dropdown_advertiser_config_type)
        binding.advConfigType.spLegacy.adapter = legacyAdapter

        val extendedAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item_advertiser_config_type,
            translator.getValuesAsStringList(extendedModes)
        )
        extendedAdapter.setDropDownViewResource(R.layout.spinner_dropdown_advertiser_config_type)
        binding.advConfigType.spExtended.adapter = extendedAdapter

        if (isLegacy) binding.advConfigType.tvExtendedAdvNotSupported.visibility = View.VISIBLE
        binding.advConfigType.spExtended.isEnabled = false
        binding.advConfigType.cbAnonymous.isEnabled = false
        binding.advConfigType.cbIncludeTxPower.isEnabled = false
        binding.advConfigType.rbExtendedAdvertising.isEnabled = !isLegacy
        binding.advConfigParam.spPrimaryPhy.isEnabled = false
        binding.advConfigParam.spSecondaryPhy.isEnabled = false

        if (!isLegacy) {
            binding.advConfigType.rbExtendedAdvertising.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    binding.advConfigType.cbAnonymous.isEnabled =
                        binding.advConfigType.spExtended.selectedItem.toString() == getString(R.string.advertiser_mode_non_connectable_non_scannable)
                    binding.advConfigType.rbLegacyAdvertising.isChecked = false
                    binding.advConfigType.spLegacy.isEnabled = false
                    binding.advConfigType.spLegacy.visibility = View.GONE
                    binding.advConfigType.spExtended.isEnabled = true
                    binding.advConfigType.spExtended.visibility = View.VISIBLE
                    binding.advConfigType.cbIncludeTxPower.isEnabled = true
                    binding.advConfigParam.spPrimaryPhy.isEnabled = true
                    binding.advConfigParam.spSecondaryPhy.isEnabled = true

                    presenter.setSupportedData(
                        false,
                        translator.getStringAsAdvertisingMode(binding.advConfigType.spExtended.selectedItem.toString())
                    )
                }
            }

            binding.advConfigType.spExtended.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        if (!spExtendedInitialSetup) {
                            if (binding.advConfigType.spExtended.selectedItem.toString() == getString(
                                    R.string.advertiser_mode_non_connectable_non_scannable
                                )
                            ) {
                                binding.advConfigType.cbAnonymous.isEnabled = true
                            } else {
                                binding.advConfigType.cbAnonymous.isEnabled = false
                                binding.advConfigType.cbAnonymous.isChecked = false
                            }
                            presenter.setSupportedData(
                                false,
                                translator.getStringAsAdvertisingMode(binding.advConfigType.spExtended.selectedItem.toString())
                            )
                        } else {
                            spExtendedInitialSetup = false
                        }
                    }
                }
        }

        binding.advConfigType.rbLegacyAdvertising.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.advConfigType.rbExtendedAdvertising.isChecked = false
                binding.advConfigType.spLegacy.isEnabled = true
                binding.advConfigType.spLegacy.visibility = View.VISIBLE
                binding.advConfigType.spExtended.isEnabled = false
                binding.advConfigType.spExtended.visibility = View.GONE
                binding.advConfigType.cbAnonymous.isEnabled = false
                binding.advConfigType.cbIncludeTxPower.isEnabled = false
                binding.advConfigParam.spPrimaryPhy.isEnabled = false
                binding.advConfigParam.spSecondaryPhy.isEnabled = false
                binding.advConfigType.cbIncludeTxPower.isChecked = false
                binding.advConfigType.cbAnonymous.isChecked = false

                presenter.setSupportedData(
                    true,
                    translator.getStringAsAdvertisingMode(binding.advConfigType.spLegacy.selectedItem.toString())
                )
            }
        }

        binding.advConfigType.spLegacy.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (!spLegacyInitialSetup) {
                        presenter.setSupportedData(
                            true,
                            translator.getStringAsAdvertisingMode(binding.advConfigType.spLegacy.selectedItem.toString())
                        )
                    } else {
                        spLegacyInitialSetup = false
                    }
                }
            }
    }

    override fun onAdvertisingParametersPrepared(
        isLegacy: Boolean,
        primaryPhys: List<Phy>,
        secondaryPhys: List<Phy>
    ) {
        binding.advConfigType
        val primaryPhyAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item_layout_medium,
            translator.getValuesAsStringList(primaryPhys)
        )
        primaryPhyAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_layout)
        binding.advConfigParam.spPrimaryPhy.adapter = primaryPhyAdapter

        val secondaryPhyAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item_layout_medium,
            translator.getValuesAsStringList(secondaryPhys)
        )
        secondaryPhyAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_layout)
        binding.advConfigParam.spSecondaryPhy.adapter = secondaryPhyAdapter
    }

    override fun onSupportedDataPrepared(isAdvertisingData: Boolean, isScanRespData: Boolean) {

        binding.advConfigData.btnAddAdvertisingData.isEnabled = isAdvertisingData
        binding.advConfigData.tvAdvDataAvailableBytes.visibility =
            if (isAdvertisingData) View.VISIBLE else View.GONE
        binding.advConfigData.llDataAdvertisingData.root.visibility =
            if (isAdvertisingData) View.VISIBLE else View.GONE
        binding.advConfigData.btnAddScanResponseData.isEnabled = isScanRespData
        binding.advConfigData.tvScanRespAvailableBytes.visibility =
            if (isScanRespData) View.VISIBLE else View.GONE
        binding.advConfigData.llDataScanRespData.root.visibility =
            if (isScanRespData) View.VISIBLE else View.GONE
    }

    override fun populateUi(data: AdvertiserData, isAdvertisingEventSupported: Boolean) {
        binding.advConfigName.etAdvertisingSetName.setText(data.name)
        title = data.name

        data.isLegacy.let {
            if (data.isLegacy) setSpinnerSelection(data.mode, binding.advConfigType.spLegacy)
            else setSpinnerSelection(data.mode, binding.advConfigType.spExtended)
            binding.advConfigType.rbLegacyAdvertising.isChecked = data.isLegacy
            binding.advConfigType.rbExtendedAdvertising.isChecked = !data.isLegacy
        }

        data.settings.let {
            binding.advConfigType.cbIncludeTxPower.isChecked = data.settings.includeTxPower
            binding.advConfigType.cbAnonymous.isChecked = data.settings.anonymous
            setSpinnerSelection(data.settings.primaryPhy, binding.advConfigParam.spPrimaryPhy)
            setSpinnerSelection(data.settings.secondaryPhy, binding.advConfigParam.spSecondaryPhy)
        }

        binding.advConfigParam.etAdvertisingInterval.setText(data.advertisingIntervalMs.toString())
        binding.advConfigParam.etTxPower.setText(data.txPower.toString())

        binding.advConfigParam.rbTimeLimit.text = getString(R.string.advertiser_label_time_limit)

        binding.advConfigParam.rbNoLimit.isChecked = data.limitType == LimitType.NO_LIMIT
        setTimeLimitState(data.limitType == LimitType.TIME_LIMIT)
        setEventLimitState(data.limitType == LimitType.EVENT_LIMIT)
        binding.advConfigParam.etTimeLimit.setText(data.timeLimit.toString())

        if (isAdvertisingEventSupported) binding.advConfigParam.etEventLimit.setText(data.eventLimit.toString())
        else binding.advConfigParam.llEventLimit.visibility = View.GONE
    }

    private fun setSpinnerSelection(selection: Any?, spinner: Spinner) {
        for (i in 0 until spinner.adapter.count)
            if (spinner.getItemAtPosition(i) == translator.getString(selection)
            ) {
                spinner.setSelection(i); break
            }
    }

    private fun loadData() {
        presenter.loadData(DataMode.ADVERTISING_DATA)
        presenter.loadData(DataMode.SCAN_RESPONSE_DATA)

        handleAddData(
            binding.advConfigData.llDataAdvertisingData.dataFlags,
            DataType.FLAGS,
            DataMode.ADVERTISING_DATA
        )
    }

    private fun prepareDataSpinners() {
        prepareDataSpinner(
            binding.advConfigData.spAdvertisingData,
            binding.advConfigData.llDataAdvertisingData,
            DataMode.ADVERTISING_DATA
        )
        prepareDataSpinner(
            binding.advConfigData.spScanResponseData,
            binding.advConfigData.llDataScanRespData,
            DataMode.SCAN_RESPONSE_DATA
        )
        binding.advConfigData.btnAddAdvertisingData.setOnClickListener {
            binding.advConfigData.spAdvertisingData.performClick()
        }
        binding.advConfigData.btnAddScanResponseData.setOnClickListener {
            binding.advConfigData.spScanResponseData.performClick()
        }
    }

    override fun onDataLoaded(data: DataPacket?, mode: DataMode) {
        val dataContainer =
            if (mode == DataMode.ADVERTISING_DATA) {
                binding.advConfigData.llDataAdvertisingData
            } else {
                binding.advConfigData.llDataScanRespData
            }

        data?.let {
            if (data.includeCompleteLocalName)
                handleAddData(
                    dataContainer.dataCompleteLocalName,
                    DataType.COMPLETE_LOCAL_NAME,
                    mode
                )
            if (data.manufacturers.isNotEmpty())
                for (item in data.manufacturers) handleAddData(
                    dataContainer.dataManufacturerData,
                    DataType.MANUFACTURER_SPECIFIC_DATA,
                    mode,
                    item
                )
            if (data.includeTxPower)
                handleAddData(
                    dataContainer.dataTxPower,
                    DataType.TX_POWER,
                    mode
                )
            if (data.services16Bit.isNotEmpty())
                handleAddData(

                    dataContainer.data16bitServices,
                    DataType.COMPLETE_16_BIT,
                    mode,
                    data.services16Bit
                )
            if (data.services128Bit.isNotEmpty())
                handleAddData(
                    //dataContainer.findViewById(R.id.data_128bit_services),
                    dataContainer.data128bitServices,
                    DataType.COMPLETE_128_BIT,
                    mode,
                    data.services128Bit
                )
        }
    }

    private fun prepareDataSpinner(spinner: Spinner, dataContainer: AdvertiserDataContainerBinding, mode: DataMode) {
        spinner.setSelection(0)
        spinner.adapter = DataTypeAdapter(
            this,
            translator.getAdvertisingDataTypes(),
            object : DataTypeAdapter.Callback {
                override fun onItemClick(position: Int) {
                    when (position) {
                        0 -> handleAddData(
                            //dataContainer.findViewById(R.id.data_complete_local_name),
                            dataContainer.dataCompleteLocalName,
                            DataType.COMPLETE_LOCAL_NAME,
                            mode
                        )

                        1 -> {
                            currentFocus?.clearFocus()
                            hideKeyboard()
                            handleAddData(
                                //dataContainer.findViewById(R.id.data_manufacturer_data),
                                dataContainer.dataManufacturerData,
                                DataType.MANUFACTURER_SPECIFIC_DATA,
                                mode
                            )
                        }

                        2 -> handleAddData(dataContainer.dataTxPower, DataType.TX_POWER, mode)
                        3 -> handleAddData(
                            dataContainer.data16bitServices,
                            DataType.COMPLETE_16_BIT,
                            mode
                        )

                        4 -> handleAddData(
                            dataContainer.data128bitServices,
                            DataType.COMPLETE_128_BIT,
                            mode
                        )
                    }
                }
            })
    }

    private fun handleAddData(
        layout: ViewGroup,
        type: DataType,
        mode: DataMode,
        extra: Any? = null
    ) {
        val baseContainer = prepareBaseContainer(layout, type)
        when (type) {
            DataType.FLAGS -> addFlagsData(layout, baseContainer)
            DataType.COMPLETE_16_BIT -> addServiceData(
                layout,
                baseContainer,
                mode,
                DataType.COMPLETE_16_BIT,
                extra
            )

            DataType.COMPLETE_128_BIT -> addServiceData(
                layout,
                baseContainer,
                mode,
                DataType.COMPLETE_128_BIT,
                extra
            )

            DataType.COMPLETE_LOCAL_NAME -> addCompleteLocalNameData(
                layout,
                baseContainer,
                mode
            )

            DataType.TX_POWER -> addTxPowerData(layout, baseContainer, mode)
            DataType.MANUFACTURER_SPECIFIC_DATA -> addManufacturerSpecificData(
                layout,
                baseContainer,
                mode,
                extra
            )
        }
    }

    private fun addFlagsData(layout: ViewGroup, baseContainer: DataTypeLayoutBinding) {
        baseContainer.ibRemove.visibility = View.INVISIBLE
        val itemContainer =
            prepareItemContainer(baseContainer, getString(R.string.advertiser_label_flags_default))
        itemContainer.ibRemove.visibility = View.GONE
        baseContainer.llData.addView(itemContainer.root)
        layout.addView(baseContainer.root)
    }

    private fun addServiceData(
        layout: ViewGroup,
        baseContainer: DataTypeLayoutBinding,
        mode: DataMode,
        type: DataType,
        extra: Any? = null
    ) {
        baseContainer.apply {
            ibRemove.setOnClickListener { layout.removeView(baseContainer.root) }
            btnAddService.apply {
                visibility = View.VISIBLE
                text =
                    if (type == DataType.COMPLETE_16_BIT) getString(R.string.advertiser_button_add_16bit_service) else getString(
                        R.string.advertiser_button_add_128bit_service
                    )
            }
            llDataSpacer.visibility = View.VISIBLE
            (llData.layoutParams as ConstraintLayout.LayoutParams).apply {
                endToEnd = ibRemove.id
                topToBottom = ibRemove.id
            }
            llData.requestLayout()
            setDataSpinnerItemState(false, mode, if (type == DataType.COMPLETE_16_BIT) 3 else 4)

            btnAddService.setOnClickListener {
                currentFocus?.clearFocus()
                hideKeyboard()
                if (type == DataType.COMPLETE_16_BIT) {
                    Service16BitDataDialog(object : Service16BitDataDialog.Callback {
                        override fun onSave(service: Service16Bit) {
                            val serviceItem =
                                prepareItemContainer(baseContainer, service.toString())
                            presenter.include16BitService(mode, service)

                            serviceItem.ibRemove.setOnClickListener {
                                presenter.exclude16BitService(mode, service)
                                llData.removeView(serviceItem.root)
                            }

                            llData.addView(serviceItem.root)
                        }
                    }).show(supportFragmentManager, "dialog_16bit_service_data")
                } else {
                    Service128BitDataDialog(object : Service128BitDataDialog.Callback {
                        override fun onSave(service: Service128Bit) {
                            val serviceItem =
                                prepareItemContainer(baseContainer, service.uuid.toString())
                            presenter.include128BitService(mode, service)

                            serviceItem.ibRemove.setOnClickListener {
                                presenter.exclude128BitService(mode, service)
                                llData.removeView(serviceItem.root)
                            }

                            llData.addView(serviceItem.root)
                        }
                    }).show(supportFragmentManager, "dialog_128bit_service_data")
                }
            }

            ibRemove.setOnClickListener {
                currentFocus?.clearFocus()
                hideKeyboard()
                val count = llData.childCount
                removeServicesIfAllowed(layout, baseContainer, count, mode, type)
            }

            extra?.let {
                for (service in extra as List<*>) {
                    val serviceItem = prepareItemContainer(
                        baseContainer,
                        if (service is Service16Bit) service.toString() else (service as Service128Bit).uuid.toString()
                    )

                    serviceItem.ibRemove.setOnClickListener {
                        if (service is Service16Bit) presenter.exclude16BitService(mode, service)
                        else if (service is Service128Bit) presenter.exclude128BitService(
                            mode,
                            service
                        )
                        llData.removeView(serviceItem.root)
                    }

                    llData.addView(serviceItem.root)
                }
            }
        }

        layout.addView(baseContainer.root)
    }

    @SuppressLint("MissingPermission")
    private fun addCompleteLocalNameData(
        layout: ViewGroup, baseContainer: DataTypeLayoutBinding,
        mode: DataMode
    ) {
        val itemContainer =
            prepareItemContainer(baseContainer, BluetoothAdapter.getDefaultAdapter().name)
        itemContainer.ibRemove.visibility = View.GONE
        setDataSpinnerItemState(false, mode, 0)

        presenter.includeCompleteLocalName(mode)

        baseContainer.ibRemove.setOnClickListener {
            layout.removeView(baseContainer.root)
            presenter.excludeCompleteLocalName(mode)
            setDataSpinnerItemState(true, mode, 0)
        }

        baseContainer.llData.addView(itemContainer.root)
        layout.addView(baseContainer.root)
    }

    private fun addTxPowerData(
        layout: ViewGroup, baseContainer: DataTypeLayoutBinding,
        mode: DataMode
    ) {
        val itemContainer =
            prepareItemContainer(baseContainer, getString(R.string.unit_value_dbm, getTxPower()))

        binding.advConfigParam.etTxPower.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (Validator.isTxPowerValid(binding.advConfigParam.etTxPower.text.toString())) {
                    itemContainer.tvDataText.text =
                        getString(
                            R.string.unit_value_dbm,
                            binding.advConfigParam.etTxPower.text.toString().toInt()
                        )
                }
            }
        })

        itemContainer.ibRemove.visibility = View.GONE
        setDataSpinnerItemState(false, mode, 2)

        presenter.includeTxPower(mode)

        baseContainer.ibRemove.setOnClickListener {
            layout.removeView(baseContainer.root)
            presenter.excludeTxPower(mode)
            setDataSpinnerItemState(true, mode, 2)
        }

        baseContainer.llData.addView(itemContainer.root)
        layout.addView(baseContainer.root)
    }

    private fun addManufacturerSpecificData(
        layout: ViewGroup,
        baseContainer: DataTypeLayoutBinding,
        mode: DataMode,
        extra: Any? = null
    ) {
        if (extra == null) {
            ManufacturerDataDialog(
                presenter.getManufacturers(mode),
                object : ManufacturerDataDialog.Callback {
                    override fun onSave(manufacturer: Manufacturer) {
                        val itemContainer =
                            prepareItemContainer(baseContainer, manufacturer.getAsDescriptiveText())
                        itemContainer.ibRemove.visibility = View.GONE

                        presenter.includeManufacturerSpecificData(mode, manufacturer)

                        baseContainer.ibRemove.setOnClickListener {
                            presenter.excludeManufacturerSpecificData(mode, manufacturer)
                            layout.removeView(baseContainer.root)
                        }

                        baseContainer.llData.addView(itemContainer.root)
                        layout.addView(baseContainer.root)
                    }
                }).show(supportFragmentManager, "dialog_manufacturer_data")
        } else {
            val manufacturer = extra as Manufacturer
            val itemContainer =
                prepareItemContainer(baseContainer, manufacturer.getAsDescriptiveText())
            itemContainer.ibRemove.visibility = View.GONE

            baseContainer.ibRemove.setOnClickListener {
                presenter.excludeManufacturerSpecificData(mode, manufacturer)
                layout.removeView(baseContainer.root)
            }

            baseContainer.llData.addView(itemContainer.root)
            layout.addView(baseContainer.root)
        }
    }

    private fun prepareBaseContainer(layout: ViewGroup, type: DataType):
            DataTypeLayoutBinding {
        lateinit var binding: DataTypeLayoutBinding
        val container = LayoutInflater.from(this@AdvertiserConfigActivity)
        binding = DataTypeLayoutBinding.inflate(container)
        setBaseContainerTitle(binding, type)
        return binding
    }

    private fun prepareItemContainer(
        baseContainer: DataTypeLayoutBinding,
        text: String
    ): DataTypeItemBinding {
        lateinit var binding: DataTypeItemBinding
        val container = LayoutInflater.from(this@AdvertiserConfigActivity)
        binding = DataTypeItemBinding.inflate(container)
        binding.tvDataText.text = text
        return binding
    }

    private fun setBaseContainerTitle(container: DataTypeLayoutBinding, type: DataType) {
        val name = "<b>".plus(
            type.getIdentifier().plus(" </b>").plus(translator.getDataTypeAsString(type))
        )
        container.tvName.text = Html.fromHtml(name, Html.FROM_HTML_MODE_LEGACY)
    }

    private fun removeServicesIfAllowed(
        layout: ViewGroup,
        container: DataTypeLayoutBinding,
        count: Int,
        mode: DataMode,
        type: DataType
    ) {
        if (count > 0 && AdvertiserStorage(this).shouldDisplayRemoveServicesDialog()) {
            RemoveServicesDialog(object : RemoveServicesDialog.Callback {
                override fun onOkClicked() {
                    presenter.excludeServices(mode, type)
                    layout.removeView(container.root)
                    setDataSpinnerItemState(
                        true,
                        mode,
                        if (type == DataType.COMPLETE_16_BIT) 3 else 4
                    )
                }
            }).show(supportFragmentManager, "dialog_remove_services")
        } else {
            presenter.excludeServices(mode, type)
            layout.removeView(container.root)
            setDataSpinnerItemState(true, mode, if (type == DataType.COMPLETE_16_BIT) 3 else 4)
        }
    }

    private fun setDataSpinnerItemState(enabled: Boolean, mode: DataMode, position: Int) {

        if (mode == DataMode.ADVERTISING_DATA) (binding.advConfigData.spAdvertisingData.adapter as DataTypeAdapter)
            .setItemState(
                enabled,
                position
            )
        else (binding.advConfigData.spScanResponseData.adapter as DataTypeAdapter).setItemState(
            enabled,
            position
        )
    }

    override fun updateAvailableBytes(
        advDataBytes: Int,
        scanRespDataBytes: Int,
        maxPacketSize: Int,
        includeFlags: Boolean
    ) {
        changeDataContainerPadding(
            advDataBytes < maxPacketSize,
            binding.advConfigData.llDataAdvertisingData.root
        )
        changeDataContainerPadding(
            scanRespDataBytes < maxPacketSize,
            binding.advConfigData.llDataScanRespData.root
        )

        binding.advConfigData.llDataAdvertisingData.dataFlags.visibility =
            if (includeFlags) View.VISIBLE else View.GONE

        binding.advConfigData.tvAdvDataAvailableBytes.setTextAppearance(if (advDataBytes >= 0) R.style.TextViewNoteInfo else R.style.TextViewNoteWarning)
        binding.advConfigData.tvAdvDataAvailableBytes.text = (if (advDataBytes >= 0) getString(
            R.string.advertiser_note_x_bytes_available,
            advDataBytes
        )
        else getString(R.string.advertiser_note_x_bytes_beyond, -advDataBytes))

        binding.advConfigData.tvScanRespAvailableBytes.setTextAppearance(if (scanRespDataBytes >= 0) R.style.TextViewNoteInfo else R.style.TextViewNoteWarning)
        binding.advConfigData.tvScanRespAvailableBytes.text =
            (if (scanRespDataBytes >= 0) getString(
                R.string.advertiser_note_x_bytes_available,
                scanRespDataBytes
            )
            else getString(R.string.advertiser_note_x_bytes_beyond, -scanRespDataBytes))
    }

    private fun changeDataContainerPadding(showPadding: Boolean, container: View) {
        val padding8Dp =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics)
                .toInt()
        if (showPadding) container.setPadding(0, padding8Dp, 0, padding8Dp)
        else container.setPadding(0, 0, 0, 0)
    }

}
