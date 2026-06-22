package com.siliconlabs.bledemo.features.configure.gatt_configurator.dialogs

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.base.fragments.BaseDialogFragment
import com.siliconlabs.bledemo.bluetooth.parsing.Engine
import com.siliconlabs.bledemo.databinding.DialogGattServerDescriptorBinding
import com.siliconlabs.bledemo.databinding.DialogGattServerServiceBinding
import com.siliconlabs.bledemo.utils.UuidUtils
import com.siliconlabs.bledemo.features.configure.gatt_configurator.adapters.Service16BitAdapter
import com.siliconlabs.bledemo.features.configure.gatt_configurator.models.*
import com.siliconlabs.bledemo.features.configure.gatt_configurator.utils.GattUtils
import com.siliconlabs.bledemo.features.configure.gatt_configurator.utils.Validator
//import kotlinx.android.synthetic.main.dialog_gatt_server_service.*
import java.util.*

class ServiceDialog(val listener: ServiceChangeListener, var service: Service = Service()) :
    BaseDialogFragment() {
    private val predefinedServices: List<Service16Bit> = GattUtils.get16BitServices()
    private lateinit var binding: DialogGattServerServiceBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DialogGattServerServiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        initACTV(binding.actvServiceName, SearchMode.BY_NAME)
        initACTV(binding.actvServiceUuid, SearchMode.BY_UUID)
        initServiceTypeSpinner()

        handleClickEvents()
        handleNameChanges()
        handleUuidChanges()
        updateSaveButtonState()
    }

    private fun initACTV(actv: AutoCompleteTextView, searchMode: SearchMode) {
        val adapter =
            Service16BitAdapter(requireContext(), GattUtils.get16BitServices(), searchMode)
        actv.setAdapter(adapter)

        actv.setOnItemClickListener { _, _, position, _ ->
            val service = adapter.getItem(position)
            binding.actvServiceName.setText(service?.name)
            binding.actvServiceUuid.setText(service?.getIdentifierAsString())
            actv.setSelection(actv.length())
            updateSaveButtonState()
            hideKeyboard()
        }
    }

    private fun initServiceTypeSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.spinner_item_layout,
            resources.getStringArray(R.array.gatt_configurator_service_type)
        )
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_layout)
        binding.spServiceType.adapter = adapter
    }

    private fun handleClickEvents() {
        binding.btnBluetoothGattServices.setOnClickListener {
            launchBluetoothGattServicesWebPage()
        }

        binding.btnClear.setOnClickListener {
            resetInput()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnSave.setOnClickListener {
            setServiceState()
            listener.onServiceChanged(service)
            dismiss()
        }
    }

    private fun setServiceState() {

        if (binding.cbMandatoryRequirements.isChecked) handleMandatoryServiceRequirements()
        service.apply {
            name = binding.actvServiceName.text.toString()
            uuid = Uuid(binding.actvServiceUuid.text.toString())
            type = getServiceTypeFromSelection()
        }
    }

    private fun handleMandatoryServiceRequirements() {
        val uuid = UuidUtils.convert16to128UUID(binding.actvServiceUuid.text.toString())
        service = getMandatoryServiceRequirements(UUID.fromString(uuid))
    }

    private fun getMandatoryServiceRequirements(serviceUUID: UUID): Service {
        val serviceRes = Engine.getService(serviceUUID)
        val service = Service()

        serviceRes?.let {
            for (characteristicRes in it.characteristics!!) {
                val characteristicType =
                    Engine.getCharacteristicByType(characteristicRes.type!!)
                val characteristic = Characteristic().apply {
                    name =
                        characteristicType?.name ?: getString(R.string.unknown_characteristic_label)
                    uuid = characteristicType?.let { charType ->
                        Uuid(UuidUtils.convert128to16UUID(charType.uuid.toString()))
                    }
                    value = Value()

                    if (characteristicRes.isReadPropertyMandatory()) properties[Property.READ] =
                        hashSetOf()
                    if (characteristicRes.isWritePropertyMandatory()) properties[Property.WRITE] =
                        hashSetOf()
                    if (characteristicRes.isWriteWithoutResponsePropertyMandatory()) properties[Property.WRITE_WITHOUT_RESPONSE] =
                        hashSetOf()
                    if (characteristicRes.isReliableWritePropertyMandatory()) properties[Property.RELIABLE_WRITE] =
                        hashSetOf()
                    if (characteristicRes.isNotifyPropertyMandatory()) properties[Property.NOTIFY] =
                        hashSetOf()
                    if (characteristicRes.isIndicatePropertyMandatory()) properties[Property.INDICATE] =
                        hashSetOf()
                }

                for (descriptorRes in characteristicRes.descriptors!!) {
                    val descriptorType = Engine.getDescriptorByType(descriptorRes.type!!)
                    val descriptor = Descriptor().apply {
                        name = descriptorType?.name ?: getString(R.string.unknown_descriptor_label)
                        uuid = descriptorType?.let { descType ->
                            Uuid(UuidUtils.convert128to16UUID(descType.uuid.toString()))
                        }
                        value = Value()

                        if (descriptorRes.isReadPropertyMandatory()) properties[Property.READ] =
                            hashSetOf()
                        if (descriptorRes.isWritePropertyMandatory()) properties[Property.WRITE] =
                            hashSetOf()
                    }
                    characteristic.descriptors.add(descriptor)
                }
                service.characteristics.add(characteristic)
            }
        }

        return service
    }

    private fun getServiceTypeFromSelection(): Service.Type {
        return when (binding.spServiceType.selectedItemPosition) {
            0 -> Service.Type.PRIMARY
            else -> Service.Type.SECONDARY
        }
    }

    private fun handleNameChanges() {
        binding.actvServiceName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSaveButtonState()
                setMandatoryRequirementsCheckBoxState()
                setServiceTypeSpinnerState()
            }
        })
    }

    private fun handleUuidChanges() {
        binding.actvServiceUuid.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val len = s?.length
                if ((len == 8 || len == 13 || len == 18 || len == 23) && count > before) binding.actvServiceUuid.append(
                    "-"
                )

                setMandatoryRequirementsCheckBoxState()
                setServiceTypeSpinnerState()
            }
        })
    }

    private fun updateSaveButtonState() {
        binding.btnSave.isEnabled = isServiceNameFilled()
    }

    private fun isServiceNameFilled(): Boolean {
        return binding.actvServiceName.text.toString().trim().isNotEmpty()
    }

    private fun setMandatoryRequirementsCheckBoxState() {
        if (isInput16BitService()) {
            binding.cbMandatoryRequirements.isEnabled = true
        } else {
            binding.cbMandatoryRequirements.isEnabled = false
            binding.cbMandatoryRequirements.isChecked = false
        }
    }

    private fun setServiceTypeSpinnerState() {
        if (isInput16BitService()) {
            binding.spServiceType.setSelection(0)
            binding.spServiceType.isEnabled = false
        } else {
            binding.spServiceType.isEnabled = true
        }
    }

    private fun isInput16BitService(): Boolean {
        val name = binding.actvServiceName.text.toString()
        val uuid = binding.actvServiceUuid.text.toString()

        val result =
            predefinedServices.filter { it.name == name && it.getIdentifierAsString() == uuid }
        return result.isNotEmpty()
    }

    private fun isUuidValid(uuid: String): Boolean {
        return Validator.is16BitUuidValid(uuid) || Validator.is128BitUuidValid(uuid)
    }

    private fun launchBluetoothGattServicesWebPage() {
        val uriUrl =
            Uri.parse("https://" + getString(R.string.advertiser_url_bluetooth_gatt_services))
        val launchBrowser = Intent(Intent.ACTION_VIEW, uriUrl)
        startActivity(launchBrowser)
    }

    private fun resetInput() {
        binding.actvServiceName.setText("")
        binding.actvServiceUuid.setText("")
        binding.cbMandatoryRequirements.isChecked = false
        binding.spServiceType.setSelection(0)
        updateSaveButtonState()
    }

    interface ServiceChangeListener {
        fun onServiceChanged(service: Service)
    }
}
