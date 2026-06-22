package com.siliconlabs.bledemo.features.configure.gatt_configurator.dialogs

import android.app.Dialog
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.AdapterView
import androidx.core.view.WindowCompat
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.base.fragments.BaseDialogFragment
import com.siliconlabs.bledemo.databinding.DialogGattServerCharacteristicBinding
import com.siliconlabs.bledemo.features.configure.gatt_configurator.adapters.Characteristic16BitAdapter
import com.siliconlabs.bledemo.features.configure.gatt_configurator.models.*
import com.siliconlabs.bledemo.features.configure.gatt_configurator.utils.GattUtils
import com.siliconlabs.bledemo.features.configure.gatt_configurator.utils.Validator


class CharacteristicDialog(
    val listener: CharacteristicChangeListener,
    val characteristic: Characteristic = Characteristic()
) : BaseDialogFragment() {
    private lateinit var binding: DialogGattServerCharacteristicBinding
    private var globalFocusChangeListener: ViewTreeObserver.OnGlobalFocusChangeListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                // Let IME insets drive translation; avoid fighting adjustResize on edge-to-edge hosts.
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val displayMetrics = resources.displayMetrics
        dialog?.window?.apply {
            setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            val params = attributes
            params.verticalMargin = DIALOG_TOP_MARGIN_FRACTION
            attributes = params
            setLayout(
                (displayMetrics.widthPixels * DIALOG_WIDTH_SCREEN_FRACTION).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        capScrollableContentHeight()
        registerGlobalFocusListener()
    }

    private fun registerGlobalFocusListener() {
        if (globalFocusChangeListener != null) return
        globalFocusChangeListener =
            ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
                if (!shouldSlideDialogForFocus(newFocus)) {
                    binding.root.translationY = 0f
                }
                ViewCompat.requestApplyInsets(binding.root)
            }
        dialog?.window?.decorView?.viewTreeObserver?.addOnGlobalFocusChangeListener(
            globalFocusChangeListener!!
        )
    }

    private fun capScrollableContentHeight() {
        val maxScrollHeight =
            (resources.displayMetrics.heightPixels * DIALOG_SCROLL_MAX_HEIGHT_FRACTION).toInt()
        binding.svCharacteristicContent.post {
            val content = binding.svCharacteristicContent.getChildAt(0) ?: return@post
            val contentHeight = content.measuredHeight
            if (contentHeight > maxScrollHeight) {
                binding.svCharacteristicContent.layoutParams.height = maxScrollHeight
                binding.svCharacteristicContent.requestLayout()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DialogGattServerCharacteristicBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initACTV(binding.actvCharacteristicName, SearchMode.BY_NAME)
        initACTV(binding.actvCharacteristicUuid, SearchMode.BY_UUID)
        initInitialValueSpinner()

        handleClickEvents()
        handleUuidChanges()
        handleNameChanges()
        handlePropertyStateChanges()
        handleInitialValueEditTextChanges()
        setupKeyboardInsets()
        setupInitialValueKeyboardScrolling()
        prepopulateFields()
        updateSaveButtonState()
    }

    override fun onDestroyView() {
        globalFocusChangeListener?.let { listener ->
            dialog?.window?.decorView?.viewTreeObserver
                ?.removeOnGlobalFocusChangeListener(listener)
        }
        globalFocusChangeListener = null
        super.onDestroyView()
    }

    /**
     * Only slide the dialog for bottom initial-value fields. Name/UUID stay fixed at the top.
     */
    private fun shouldSlideDialogForFocus(focused: View?): Boolean {
        var view = focused
        while (view != null) {
            when (view.id) {
                R.id.et_initial_value_text, R.id.et_initial_value_hex -> return true
                R.id.actv_characteristic_name, R.id.actv_characteristic_uuid -> return false
            }
            view = view.parent as? View
        }
        return false
    }

    private fun currentFocusedView(): View? =
        dialog?.currentFocus ?: binding.root.findFocus()

    private fun applyKeyboardTranslation(imeBottom: Int) {
        val slide = imeBottom > 0 && shouldSlideDialogForFocus(currentFocusedView())
        binding.root.translationY = if (slide) -imeBottom.toFloat() else 0f
    }

    private fun setupKeyboardInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            applyKeyboardTranslation(imeInsets.bottom)
            insets
        }

        preventSlideForTopInputField(binding.actvCharacteristicName)
        preventSlideForTopInputField(binding.actvCharacteristicUuid)

        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun preventSlideForTopInputField(field: View) {
        field.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                binding.root.translationY = 0f
            }
            false
        }
        field.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.root.translationY = 0f
        }
    }

    private fun setupInitialValueKeyboardScrolling() {
        val scrollToField = { view: View ->
            binding.svCharacteristicContent.post {
                val scrollView = binding.svCharacteristicContent
                val targetRect = Rect()
                view.getDrawingRect(targetRect)
                scrollView.offsetDescendantRectToMyCoords(view, targetRect)
                scrollView.requestRectangleOnScreen(targetRect, true)
            }
        }

        binding.initialValue.etInitialValueText.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                ViewCompat.requestApplyInsets(binding.root)
                scrollToField(view)
            }
        }
        binding.initialValue.etInitialValueHex.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                ViewCompat.requestApplyInsets(binding.root)
                scrollToField(view)
            }
        }
    }

    private fun handleClickEvents() {
        binding.btnSave.setOnClickListener {
            setCharacteristicState()
            listener.onCharacteristicChanged(characteristic)
            dismiss()
        }
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
        binding.btnClear.setOnClickListener {
            clearAllFields()
        }
    }

    private fun prepopulateFields() {
        binding.actvCharacteristicName.setText(characteristic.name)
        binding.actvCharacteristicUuid.setText(characteristic.uuid?.uuid)
        prepopulateProperties()
        prepopulatePropertyTypes()
        prepopulateInitialValue()
        applyInitialValueFieldVisibility()
    }

    private fun prepopulateProperties() {
        characteristic.properties.apply {
            binding.propertiesContent.swRead.isChecked = containsKey(Property.READ)
            binding.propertiesContent.swWrite.isChecked = containsKey(Property.WRITE)
            binding.propertiesContent.swWriteWithoutResp.isChecked =
                containsKey(Property.WRITE_WITHOUT_RESPONSE)
            binding.propertiesContent.swReliableWrite.isChecked =
                containsKey(Property.RELIABLE_WRITE)
            binding.propertiesContent.swNotify.isChecked = containsKey(Property.NOTIFY)
            binding.propertiesContent.swIndicate.isChecked = containsKey(Property.INDICATE)
        }
    }

    private fun prepopulatePropertyTypes() {
        characteristic.properties.apply {
            this[Property.READ]?.apply {
                binding.propertiesContent.cbReadBonded.isChecked = contains(Property.Type.BONDED)
                binding.propertiesContent.cbReadMitm.isChecked =
                    contains(Property.Type.AUTHENTICATED)
            }

            this[Property.WRITE]?.apply {
                binding.propertiesContent.cbWriteBonded.isChecked = contains(Property.Type.BONDED)
                binding.propertiesContent.cbWriteMitm.isChecked =
                    contains(Property.Type.AUTHENTICATED)
                return
            }

            this[Property.WRITE_WITHOUT_RESPONSE]?.apply {
                binding.propertiesContent.cbWriteBonded.isChecked = contains(Property.Type.BONDED)
                binding.propertiesContent.cbWriteMitm.isChecked =
                    contains(Property.Type.AUTHENTICATED)
                return
            }

            this[Property.RELIABLE_WRITE]?.apply {
                binding.propertiesContent.cbWriteBonded.isChecked = contains(Property.Type.BONDED)
                binding.propertiesContent.cbWriteMitm.isChecked =
                    contains(Property.Type.AUTHENTICATED)
                return
            }
        }
    }

    private fun prepopulateInitialValue() {
        when (characteristic.value?.type) {
            Value.Type.USER -> {
                binding.initialValue.spInitialValue.setSelection(POSITION_INITIAL_VALUE_EMPTY)
            }

            Value.Type.UTF_8 -> {
                binding.initialValue.spInitialValue.setSelection(POSITION_INITIAL_VALUE_TEXT)
                binding.initialValue.etInitialValueText.setText(characteristic.value?.value)
            }

            Value.Type.HEX -> {
                binding.initialValue.spInitialValue.setSelection(POSITION_INITIAL_VALUE_HEX)
                binding.initialValue.etInitialValueHex.setText(characteristic.value?.value)
            }

            else -> Unit
        }
    }

    private fun setCharacteristicState() {
        characteristic.name = binding.actvCharacteristicName.text.toString()
        characteristic.uuid = Uuid(binding.actvCharacteristicUuid.text.toString())
        setPropertiesState()
        setInitialValue()
    }

    private fun setPropertiesState() {
        characteristic.properties.clear()
        if (binding.propertiesContent.swRead.isChecked) characteristic.properties[Property.READ] =
            getSelectedReadTypes()
        if (binding.propertiesContent.swWrite.isChecked) characteristic.properties[Property.WRITE] =
            getSelectedWriteTypes()
        if (binding.propertiesContent.swWriteWithoutResp.isChecked) {
            characteristic.properties[Property.WRITE_WITHOUT_RESPONSE] = getSelectedWriteTypes()
        }
        if (binding.propertiesContent.swReliableWrite.isChecked) {
            characteristic.properties[Property.RELIABLE_WRITE] = getSelectedWriteTypes()
        }
        if (binding.propertiesContent.swNotify.isChecked) characteristic.properties[Property.NOTIFY] =
            hashSetOf()
        if (binding.propertiesContent.swIndicate.isChecked) characteristic.properties[Property.INDICATE] =
            hashSetOf()
        handlePropertiesUsingDescriptors()
    }

    private fun handlePropertiesUsingDescriptors() {
        if (binding.propertiesContent.swReliableWrite.isChecked) setReliableWritePropertyDescriptor()
        else removeReliableWritePropertyDescriptor()

        if (binding.propertiesContent.swIndicate.isChecked || binding.propertiesContent.swNotify.isChecked) {
            setIndicateOrNotifyPropertyDescriptor()
        } else {
            removeIndicateOrNotifyPropertyDescriptor()
        }
    }

    private fun setReliableWritePropertyDescriptor() {
        val result =
            characteristic.descriptors.filter {
                it.name == GattUtils.getReliableWriteDescriptor().name && it.isPredefined
            }
        if (result.isEmpty()) {
            characteristic.descriptors.add(GattUtils.getReliableWriteDescriptor())
        }
    }

    private fun removeReliableWritePropertyDescriptor() {
        val descriptor =
            characteristic.descriptors.find {
                it.name == GattUtils.getReliableWriteDescriptor().name && it.isPredefined
            }
        characteristic.descriptors.remove(descriptor)
    }

    private fun setIndicateOrNotifyPropertyDescriptor() {
        val result =
            characteristic.descriptors.filter {
                it.name == GattUtils.getIndicateOrNotifyDescriptor().name && it.isPredefined
            }
        if (result.isEmpty()) {
            characteristic.descriptors.add(GattUtils.getIndicateOrNotifyDescriptor())
        }
    }

    private fun removeIndicateOrNotifyPropertyDescriptor() {
        val descriptor =
            characteristic.descriptors.find {
                it.name == GattUtils.getIndicateOrNotifyDescriptor().name && it.isPredefined
            }
        characteristic.descriptors.remove(descriptor)
    }

    private fun getSelectedReadTypes(): HashSet<Property.Type> {
        return hashSetOf<Property.Type>().apply {
            if (binding.propertiesContent.cbReadBonded.isChecked) add(Property.Type.BONDED)
            if (binding.propertiesContent.cbReadMitm.isChecked) add(Property.Type.AUTHENTICATED)
        }
    }

    private fun getSelectedWriteTypes(): HashSet<Property.Type> {
        return hashSetOf<Property.Type>().apply {
            if (binding.propertiesContent.cbWriteBonded.isChecked) add(Property.Type.BONDED)
            if (binding.propertiesContent.cbWriteMitm.isChecked) add(Property.Type.AUTHENTICATED)
        }
    }

    private fun setInitialValue() {
        when (binding.initialValue.spInitialValue.selectedItemPosition) {
            POSITION_INITIAL_VALUE_EMPTY -> {
                characteristic.value = Value(
                    value = "",
                    type = Value.Type.USER
                )
            }

            POSITION_INITIAL_VALUE_TEXT -> {
                characteristic.value = Value(
                    value = binding.initialValue.etInitialValueText.text.toString(),
                    type = Value.Type.UTF_8,
                    length = binding.initialValue.etInitialValueText.text.length
                )
            }

            POSITION_INITIAL_VALUE_HEX -> {
                characteristic.value = Value(
                    value = binding.initialValue.etInitialValueHex.text.toString(),
                    type = Value.Type.HEX,
                    length = binding.initialValue.etInitialValueHex.length() / 2
                )
            }
        }
    }

    private fun handleNameChanges() {
        binding.actvCharacteristicName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSaveButtonState()
            }
        })
    }

    private fun handleUuidChanges() {
        binding.actvCharacteristicUuid.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val len = s?.length
                if ((len == 8 || len == 13 || len == 18 || len == 23) && count > before) {
                    binding.actvCharacteristicUuid.append("-")
                }
                updateSaveButtonState()
            }
        })
    }

    private fun handleInitialValueEditTextChanges() {
        val textWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSaveButtonState()
            }
        }
        binding.initialValue.etInitialValueText.addTextChangedListener(textWatcher)
        binding.initialValue.etInitialValueHex.addTextChangedListener(textWatcher)
    }

    private fun updateSaveButtonState() {
        binding.btnSave.isEnabled = canEnableSave()
    }

    private fun canEnableSave(): Boolean {
        if (!isCharacteristicNameFilled()) {
            return false
        }
        if (hasEmptyRequiredCharacteristicField()) {
            return false
        }
        if (!isInitialValueRequirementMet()) {
            return false
        }
        return isUuidValidWhenFilled()
    }

    private fun isCharacteristicNameFilled(): Boolean {
        return binding.actvCharacteristicName.text.toString().trim().isNotEmpty()
    }

    /** Name and UUID only; initial value fields follow [isInitialValueRequirementMet] by spinner. */
    private fun hasEmptyRequiredCharacteristicField(): Boolean {
        return isVisibleEnabledEmptyTextField(binding.actvCharacteristicName) ||
            isVisibleEnabledEmptyTextField(binding.actvCharacteristicUuid)
    }

    private fun isVisibleEnabledEmptyTextField(field: EditText): Boolean {
        return field.visibility == View.VISIBLE &&
            field.isEnabled &&
            field.text.toString().trim().isEmpty()
    }

    private fun isInitialValueRequirementMet(): Boolean {
        return when (binding.initialValue.spInitialValue.selectedItemPosition) {
            POSITION_INITIAL_VALUE_EMPTY -> true
            POSITION_INITIAL_VALUE_TEXT ->
                binding.initialValue.etInitialValueText.text.toString().trim().isNotEmpty()
            POSITION_INITIAL_VALUE_HEX ->
                binding.initialValue.etInitialValueHex.text.toString().trim().isNotEmpty()
            else -> true
        }
    }

    private fun isUuidValidWhenFilled(): Boolean {
        val uuidText = binding.actvCharacteristicUuid.text.toString().trim()
        if (uuidText.isEmpty()) {
            return false
        }
        return isUuidValid(uuidText)
    }

    private fun handlePropertyStateChanges() {
        binding.propertiesContent.swRead.setOnCheckedChangeListener { _, _ ->
            setPropertyParametersState(
                binding.propertiesContent.swRead.isChecked,
                binding.propertiesContent.cbReadBonded,
                binding.propertiesContent.cbReadMitm
            )
        }
        binding.propertiesContent.swWrite.setOnCheckedChangeListener { _, _ ->
            setPropertyParametersState(
                binding.propertiesContent.swWrite.isChecked ||
                    binding.propertiesContent.swWriteWithoutResp.isChecked ||
                    binding.propertiesContent.swReliableWrite.isChecked,
                binding.propertiesContent.cbWriteBonded,
                binding.propertiesContent.cbWriteMitm
            )
        }
        binding.propertiesContent.swWriteWithoutResp.setOnCheckedChangeListener { _, _ ->
            setPropertyParametersState(
                binding.propertiesContent.swWrite.isChecked ||
                    binding.propertiesContent.swWriteWithoutResp.isChecked ||
                    binding.propertiesContent.swReliableWrite.isChecked,
                binding.propertiesContent.cbWriteBonded,
                binding.propertiesContent.cbWriteMitm
            )
        }
        binding.propertiesContent.swReliableWrite.setOnCheckedChangeListener { _, _ ->
            setPropertyParametersState(
                binding.propertiesContent.swWrite.isChecked ||
                    binding.propertiesContent.swWriteWithoutResp.isChecked ||
                    binding.propertiesContent.swReliableWrite.isChecked,
                binding.propertiesContent.cbWriteBonded,
                binding.propertiesContent.cbWriteMitm
            )
        }
    }

    private fun setPropertyParametersState(
        switchState: Boolean,
        cbBonded: CheckBox,
        cbMitm: CheckBox
    ) {
        cbBonded.isEnabled = switchState
        cbMitm.isEnabled = switchState
        if (!switchState) {
            cbBonded.isChecked = false
            cbMitm.isChecked = false
        }
    }

    private fun isUuidValid(uuid: String): Boolean {
        return Validator.is16BitUuidValid(uuid) || Validator.is128BitUuidValid(uuid)
    }

    private fun initACTV(actv: AutoCompleteTextView, searchMode: SearchMode) {
        val adapter = Characteristic16BitAdapter(
            requireContext(),
            GattUtils.get16BitCharacteristics(),
            searchMode
        )
        actv.setAdapter(adapter)

        actv.setOnItemClickListener { _, _, position, _ ->
            val selected = adapter.getItem(position)
            binding.actvCharacteristicName.setText(selected?.name)
            binding.actvCharacteristicUuid.setText(selected?.getIdentifierAsString())
            actv.setSelection(actv.length())
            updateSaveButtonState()
            hideKeyboard()
        }
    }

    private fun initInitialValueSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.spinner_item_layout,
            resources.getStringArray(R.array.gatt_configurator_initial_value)
        )
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_layout)
        binding.initialValue.spInitialValue.adapter = adapter

        handleInitialValueSelection()
    }

    private fun handleInitialValueSelection() {
        binding.initialValue.spInitialValue.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    applyInitialValueFieldVisibility()
                    updateSaveButtonState()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    private fun applyInitialValueFieldVisibility() {
        when (binding.initialValue.spInitialValue.selectedItemPosition) {
            POSITION_INITIAL_VALUE_TEXT ->
                binding.initialValue.etInitialValueText.visibility = View.VISIBLE
            else ->
                binding.initialValue.etInitialValueText.visibility = View.GONE
        }
        binding.initialValue.llInitialValueHex.visibility =
            if (binding.initialValue.spInitialValue.selectedItemPosition == POSITION_INITIAL_VALUE_HEX) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun clearAllFields() {
        binding.actvCharacteristicName.setText("")
        binding.actvCharacteristicUuid.setText("")
        binding.propertiesContent.swRead.isChecked = true
        binding.propertiesContent.swWrite.isChecked = false
        binding.propertiesContent.swWriteWithoutResp.isChecked = false
        binding.propertiesContent.swReliableWrite.isChecked = false
        binding.propertiesContent.swNotify.isChecked = false
        binding.propertiesContent.swIndicate.isChecked = false
        binding.propertiesContent.cbReadBonded.isChecked = false
        binding.propertiesContent.cbReadMitm.isChecked = false
        binding.propertiesContent.cbWriteBonded.isChecked = false
        binding.propertiesContent.cbWriteMitm.isChecked = false
        binding.initialValue.spInitialValue.setSelection(0)
        binding.initialValue.etInitialValueText.setText("")
        binding.initialValue.etInitialValueHex.setText("")
        applyInitialValueFieldVisibility()
        updateSaveButtonState()
    }

    interface CharacteristicChangeListener {
        fun onCharacteristicChanged(characteristic: Characteristic)
    }

    companion object {
        private const val POSITION_INITIAL_VALUE_EMPTY = 0
        private const val POSITION_INITIAL_VALUE_TEXT = 1
        private const val POSITION_INITIAL_VALUE_HEX = 2

        private const val DIALOG_WIDTH_SCREEN_FRACTION = 0.9f
        private const val DIALOG_SCROLL_MAX_HEIGHT_FRACTION = 0.55f
        private const val DIALOG_TOP_MARGIN_FRACTION = 0.05f
    }
}