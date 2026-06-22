package com.siliconlabs.bledemo.features.scan.browser.dialogs

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.siliconlabs.bledemo.base.fragments.BaseDialogFragment
import com.siliconlabs.bledemo.bluetooth.data_types.Characteristic
import com.siliconlabs.bledemo.bluetooth.data_types.Field
import com.siliconlabs.bledemo.bluetooth.parsing.Common
import com.siliconlabs.bledemo.features.scan.browser.fragments.FragmentCharacteristicDetail
import com.siliconlabs.bledemo.features.scan.browser.utils.FieldViewHelper
import com.siliconlabs.bledemo.features.scan.browser.views.*
import com.siliconlabs.bledemo.features.scan.browser.utils.GlucoseManagement
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.databinding.DialogCharacteristicWriteBinding
import com.siliconlabs.bledemo.utils.Converters
import com.siliconlabs.bledemo.utils.CustomToastManager
import com.siliconlabs.bledemo.utils.UuidUtils
import kotlin.collections.ArrayList

class CharacteristicWriteDialog(
    private val listener: WriteDialogListener,
    private val writeType: FragmentCharacteristicDetail.WriteType,
    private val characteristic: Characteristic?,
    private var value: ByteArray,
    private val mGattService: BluetoothGattService?,
    private val mGattCharacteristic: BluetoothGattCharacteristic?,
    private var isRawValue: Boolean = false,
    private val parseProblem: Boolean
) : BaseDialogFragment(
    hasCustomWidth = true,
    isCanceledOnTouchOutside = false
) {

    private lateinit var _binding: DialogCharacteristicWriteBinding

    private var fieldViewHelper = FieldViewHelper(characteristic)

    val fieldsValidMap = mutableMapOf<Field, Boolean>()
    val fieldsInRangeMap = mutableMapOf<Field, Boolean>()

    private val editableFields = ArrayList<EditText>()
    var offset = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCharacteristicWriteBinding.inflate(inflater)
        return _binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.let {
            initValueViews()
            displayWriteMethods(
                Common.isSetProperty(Common.PropertyType.WRITE, mGattCharacteristic!!.properties),
                Common.isSetProperty(
                    Common.PropertyType.WRITE_NO_RESPONSE,
                    mGattCharacteristic.properties
                )
            )
            loadValueViews(isRawValue, parseProblem)
            setupUiListeners()
        }
    }

    private fun setupUiListeners() {
        _binding.apply {
            saveBtn.setOnClickListener {
                if (isInputValid()) {
                    val valueToSet =
                        if (GlucoseManagement.isRecordAccessControlPoint(characteristic))
                            GlucoseManagement.updateValueToWrite(value)
                        else value
                    listener.onNewValueSet(valueToSet, writeType)
                }
            }
            clearBtn.setOnClickListener {
                editableFields.forEach {
                    it.setText("")
                }
            }
            cancelBtn.setOnClickListener { dismiss() }
        }
    }

    private fun isInputValid(): Boolean {
        var isValid = false

        if (!isAnyWriteFieldEmpty()) {
            if (isRawValue) {
                isValid = true
            } else {
                if (!isAnyInputInvalid()) isValid = true
            }
        }

        return isValid
    }

    private fun initValueViews() {
        val serviceName = Common.getServiceName(mGattService?.uuid!!, requireContext())
        val characteristicName =
            Common.getCharacteristicName(mGattCharacteristic?.uuid, requireContext())

        _binding.apply {
            characteristicDialogServiceName.text = serviceName
            characteristicDialogCharacteristicName.text = characteristicName
            characteristicDialogCharacteristicUuid.text =
                UuidUtils.getUuidText(mGattCharacteristic?.uuid!!)

            writeMethodRadioGroup.visibility =
                if (writeType == FragmentCharacteristicDetail.WriteType.REMOTE_WRITE) View.VISIBLE
                else View.GONE

            saveBtn.text = getString(
                when (writeType) {
                    FragmentCharacteristicDetail.WriteType.REMOTE_WRITE -> R.string.button_send
                    FragmentCharacteristicDetail.WriteType.LOCAL_WRITE -> R.string.button_save
                    FragmentCharacteristicDetail.WriteType.LOCAL_INDICATE -> R.string.button_indicate
                    FragmentCharacteristicDetail.WriteType.LOCAL_NOTIFY -> R.string.button_notify
                }
            )
        }
    }

    fun getStatusMethod(): Boolean {
        return _binding.writeWithRespRadioButton.isChecked
    }

    fun getChosenWriteMethod(): Int {
        return if (_binding.writeWithRespRadioButton.isChecked) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
    }

    private fun displayWriteMethods(isWritable: Boolean, isWritableWithoutResponse: Boolean) {
        _binding.apply {
            writeWithRespRadioButton.isEnabled = isWritable
            writeWithoutRespRadioButton.isEnabled = isWritableWithoutResponse

            if (writeWithRespRadioButton.isEnabled) {
                writeWithRespRadioButton.isChecked = true
            } else if (writeWithoutRespRadioButton.isEnabled) {
                writeWithoutRespRadioButton.isChecked = true
            }
        }
    }

    abstract inner class CharacteristicValueListener : ValueView.ValueListener {
        override fun onRawValueChanged(newValue: ByteArray) {
            value = newValue
        }

        override fun onFieldsChanged() {
            resetDialogFieldViews()
        }

        override fun handleFieldView(fieldViewContainer: View) {
            _binding.characteristicWritableFieldsContainer.addView(fieldViewContainer)
        }

        override fun addEditTexts(editTexts: ArrayList<EditText>) {
            editableFields.addAll(editTexts)
        }

        override fun addInRangeCheck(pair: Pair<Field, Boolean>) {
            fieldsInRangeMap[pair.first] = pair.second
        }

        override fun addValidityCheck(pair: Pair<Field, Boolean>) {
            fieldsValidMap[pair.first] = pair.second
        }
    }

    private val valueListener = object : CharacteristicValueListener() {
        override fun onValueChanged(field: Field, newValue: ByteArray, fieldOffset: Int) {
            if (field.isNibbleFormat()) {
                handleNibbleWrite(field, newValue[0], fieldOffset)
            } else {
                newValue.copyInto(value, fieldOffset)
            }
        }
    }

    private val emptyWriteValueListener = object : CharacteristicValueListener() {
        override fun onValueChanged(field: Field, newValue: ByteArray, fieldOffset: Int) {
            if (field.isNibbleFormat()) {
                handleNibbleWrite(field, newValue[0], fieldOffset)
            } else {
                val newLength = fieldOffset + newValue.size
                val newRawValue = ByteArray(newLength)
                value.copyOfRange(0, fieldOffset).copyInto(newRawValue, 0)
                newValue.copyInto(newRawValue, fieldOffset)
                value = newRawValue
            }
        }
    }

    private fun isAnyInputInvalid(): Boolean {
        var validField: Boolean
        for ((_, value1) in fieldsValidMap) {
            validField = value1
            if (!validField) {
                /*Toast.makeText(
                    context,
                    R.string.characteristic_dialog_invalid_input,
                    Toast.LENGTH_SHORT
                ).show()*/
                CustomToastManager.show(requireContext(), getString(R.string.characteristic_dialog_invalid_input), 5000)

                return true
            }
        }

        var entryInRange: Boolean
        for ((_, value1) in fieldsInRangeMap) {
            entryInRange = value1
            if (!entryInRange) {
                /*Toast.makeText(
                    context,
                    R.string.characteristic_dialog_invalid_out_of_range,
                    Toast.LENGTH_SHORT
                ).show()*/
                CustomToastManager.show(requireContext(), getString(R.string.characteristic_dialog_invalid_out_of_range), 5000)

                return true
            }
        }

        return false
    }

    private fun loadValueViews(isRawValue: Boolean, parseProblem: Boolean) {
        if (isRawValue || parseProblem) {
            this.isRawValue = isRawValue
            RawValueView(context, value).apply {
                onEditorActionListener = this@CharacteristicWriteDialog.onEditorActionListener
                createViewForWrite(0, this@CharacteristicWriteDialog.valueListener)
            }
        } else {
            addNormalValue()
        }
    }

    private fun addNormalValue() {
        characteristic?.fields?.forEach {
            addField(it, characteristic.isLastField(it))
            if (GlucoseManagement.isCgmSpecificOpsControlPoint(characteristic) && it.name == "Operand") {
                return
            }
        }
    }

    private fun addField(field: Field, isLast: Boolean) {
        if (fieldViewHelper.isFieldPresent(field, value)) {
            if (field.referenceFields?.size!! > 0) {
                for (subField in field.referenceFields!!) {
                    addField(subField, isLast && field.isLastReferenceField(subField))
                }
            } else {
                if (field.reference == null) {
                    val currentValue = value
                    val currentOffset = offset
                    val fieldSize = calculateFieldSize(field)
                    val currentRange =
                        currentValue.copyOfRange(currentOffset, currentOffset + fieldSize)

                    if (field.bitfield != null) {
                        BitFieldView(context, field, currentRange).createViewForWrite(
                            currentOffset,
                            valueListener
                        )
                        offset += field.getSizeInBytes()

                    } else if (field.enumerations != null && field.enumerations?.size!! > 0) {
                        if (field.isNibbleFormat()) {
                            handleNibbleRead(field, currentRange[0])
                        } else {
                            EnumerationView(context, field, currentRange).createViewForWrite(
                                currentOffset,
                                valueListener
                            )
                            offset += field.getSizeInBytes()
                        }
                    } else {
                        NormalValueView(context, field, currentRange, isLast).createViewForWrite(
                            currentOffset,
                            if (isLast and currentRange.isEmpty()) emptyWriteValueListener else valueListener
                        )
                        offset += fieldSize
                    }
                }
            }
        } else {
            offset += field.getSizeInBytes()
        }
    }

    private fun calculateFieldSize(field: Field): Int {
        return if (field.getSizeInBytes() != 0) {
            field.getSizeInBytes()
        } else when (field.format) {
            "utf8s", "utf16s" -> value.size - offset
            "variable" -> field.getVariableFieldLength(characteristic, value)
            else -> 0
        }
    }

    private fun handleNibbleRead(field: Field, data: Byte) {
        val firstNibble = Converters.byteToUnsignedInt(data) shr 4
        val secondNibble = Converters.byteToUnsignedInt(data) and 0x0f

        if (fieldViewHelper.isFirstNibbleInByte(field)) {
            EnumerationView(context, field, byteArrayOf(firstNibble.toByte())).createViewForWrite(
                offset,
                valueListener
            )
        } else {
            EnumerationView(context, field, byteArrayOf(secondNibble.toByte())).createViewForWrite(
                offset,
                valueListener
            )
            offset += field.getSizeInBytes()
        }
    }

    private fun handleNibbleWrite(field: Field, newValue: Byte, fieldOffset: Int) {
        var nibbleByte = value[fieldOffset].toInt() and 0xff

        if (fieldViewHelper.isFirstNibbleInByte(field)) {
            val firstNibble = newValue.toInt() and 0x0f shl 4
            nibbleByte = nibbleByte and 0x0f
            nibbleByte = nibbleByte or firstNibble
        } else {
            val secondNibble = newValue.toInt() and 0x0f
            nibbleByte = nibbleByte and 0xf0
            nibbleByte = nibbleByte or secondNibble
        }
        byteArrayOf(nibbleByte.toByte()).copyInto(value, fieldOffset)
    }

    private fun isAnyWriteFieldEmpty(): Boolean {
        editableFields.forEach {
            if (it.text.toString().isEmpty()) {
                /*Toast.makeText(
                    context,
                    R.string.You_cannot_send_empty_value_to_charac,
                    Toast.LENGTH_SHORT
                ).show()*/
                CustomToastManager.show(requireContext(), getString(R.string.You_cannot_send_empty_value_to_charac), 5000)

                return true
            }
        }
        return false
    }

    private val onEditorActionListener = object : TextView.OnEditorActionListener {
        override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (isInputValid()) listener.onNewValueSet(value, writeType)
                return true
            }
            return false
        }
    }

    private fun resetDialogFieldViews() {
        offset = 0
        _binding.characteristicWritableFieldsContainer.removeAllViews()
        loadValueViews(isRawValue = false, parseProblem = false)
    }


    interface WriteDialogListener {
        fun onNewValueSet(newValue: ByteArray, writeType: FragmentCharacteristicDetail.WriteType)
    }
}