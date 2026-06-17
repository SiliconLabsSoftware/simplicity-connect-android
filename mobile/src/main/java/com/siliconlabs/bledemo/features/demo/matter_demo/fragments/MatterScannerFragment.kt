package com.siliconlabs.bledemo.features.demo.matter_demo.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity.MODE_PRIVATE
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import chip.devicecontroller.ChipClusters
import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.ChipStructs
import chip.devicecontroller.ICDClientInfo
import chip.devicecontroller.ICDDeviceInfo
import chip.devicecontroller.ICDRegistrationInfo
import chip.devicecontroller.NetworkCredentials
import chip.setuppayload.SetupPayloadParser
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.databinding.FragmentMatterScannerBinding
import com.siliconlabs.bledemo.features.demo.matter_demo.activities.MatterDemoActivity
import com.siliconlabs.bledemo.features.demo.matter_demo.controller.GenericChipDeviceListener
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterLightFragment.Companion.ON_OFF_CLUSTER_ENDPOINT
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.AIR_QUALITY_SENSOR_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.COLOR_DIMMER_SWITCH
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.COLOR_TEMPERATURE_LIGHT_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.CONTACT_SENSOR_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.DIMMABLE_Light_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.DIMMABLE_PLUG_IN_UNIT_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.DIMMER_SWITCH
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.DISHWASHER_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.DOOR_LOCK_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.ENERGY_EVSE_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.ENHANCED_COLOR_LIGHT_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.GENERIC_SWITCH
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.MICROWAVE_OVEN
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.OCCUPANCY_SENSOR_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.ON_OFF_LIGHT_SWITCH
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.ON_OFF_LIGHT_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.OVEN
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.RANGE_HOOD
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.TEMPERATURE_SENSOR_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.THERMOSTAT_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.WINDOW_COVERING_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.manager.BluetoothManager
import com.siliconlabs.bledemo.features.demo.matter_demo.model.CHIPDeviceInfo
import com.siliconlabs.bledemo.features.demo.matter_demo.model.MatterScannedResultModel
import com.siliconlabs.bledemo.features.demo.matter_demo.model.NetworkCredentialsParcelable
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.ChipClient
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.CustomInputDialog
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.CustomProgressDialog
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.DeviceIDUtil
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.FragmentUtils
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.ICDCheckInCallback
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.MessageDialogFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.MatterTitleMessageOkDialogFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.SharedPrefsUtils
import com.siliconlabs.bledemo.features.iop_test.utils.DialogDeviceInfoFragment
import com.siliconlabs.bledemo.utils.BLEUtils
import com.siliconlabs.bledemo.utils.CustomToastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import matter.onboardingpayload.OnboardingPayload
import matter.onboardingpayload.OnboardingPayloadParser
import timber.log.Timber
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MatterScannerFragment : ICDCheckInCallback, Fragment() {


    private var customProgressDialog: CustomProgressDialog? = null
    private var commissioningTimeoutJob: Job? = null
    val dialogTag = "MessageDialog"

    private var networkSelectionDialog: MatterNetworkSelectionInputDialogFragment? = null
    private var isShortDiscriminator = false
    private var deviceDialog: DialogDeviceInfoFragment? = null
    private var deviceInfoDialogShown = false
    private var otbrEntryDialog: MatterOTBRInputDialogFragment? = null
    private var wifiEntryDialog: MatterWifiInputDialogFragment? = null
    private var gatt: BluetoothGatt? = null
    private var alertDialog: android.app.AlertDialog? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private var deviceId: Long = 0
    private lateinit var typeNtw: String
    private lateinit var networkCredential: NetworkCredentialsParcelable
    private lateinit var scope: CoroutineScope
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var networkMode: String
    private lateinit var payload: OnboardingPayload
    private lateinit var binding: FragmentMatterScannerBinding
    private lateinit var dialog: MessageDialogFragment
    private lateinit var matterScanDevice: BluetoothDevice
    private lateinit var deviceInfo: CHIPDeviceInfo
    private lateinit var deviceController: ChipDeviceController
    private lateinit var qrCodeManualInput: String
    private var scannedDeviceList = ArrayList<MatterScannedResultModel>()
    private lateinit var mPrefs: SharedPreferences
    private var externalICDCheckInMessageCallback: ICDCheckInMessageCallback? = null


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceController = ChipClient.getDeviceController(requireContext())
        ChipClient.setICDCheckInCallback(this)
        mPrefs = requireContext().getSharedPreferences("your_preference_name", MODE_PRIVATE)
        if (!hasLocationPermission()) {
            requestLocationPermission()
        }

        if (!hasCameraPermission()) {
            requestCameraPermission()
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as MatterDemoActivity).showQRScanner()
        scope = viewLifecycleOwner.lifecycleScope
        binding.submitEntry.setOnClickListener {
            qrCodeManualInput = binding.manualCodeEditText.text.toString().trim()
            println("Input MT : $qrCodeManualInput")
            handleManualInput(qrCodeManualInput)
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as MatterDemoActivity).hideQRScanner()
        startCamera()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = FragmentMatterScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun startCamera() {
        Timber.tag(TAG).e("startCamera")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val metrics = DisplayMetrics().also { binding.cameraView.display?.getRealMetrics(it) }
            val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)

            if (binding.cameraView.display.rotation != null) {
                val preview: Preview = Preview.Builder()
                    .setTargetAspectRatio(screenAspectRatio)
                    .setTargetRotation(binding.cameraView.display.rotation)
                    .build()

                preview.setSurfaceProvider(binding.cameraView.surfaceProvider)

                // Setup barcode scanner
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetAspectRatio(screenAspectRatio)
                    .setTargetRotation(binding.cameraView.display.rotation)
                    .build()
                val cameraExecutor = Executors.newSingleThreadExecutor()
                val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient()
                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageProxy(barcodeScanner, imageProxy)
                }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider?.unbindAll()
                    cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
                } catch (exc: Exception) {
                    Timber.tag(TAG).e("Use case binding failed: $exc")
                }
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(
        barcodeScanner: BarcodeScanner,
        imageProxy: ImageProxy
    ) {
        val inputImage =
            InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)
        barcodeScanner.process(inputImage)
            .addOnSuccessListener { barcode ->
                //Timber.tag(TAG).e("Barcodes: $barcode")
                barcode.forEach {
                    handleScannedQrCode(it)
                }
            }
            .addOnFailureListener {
                Timber.tag(TAG).e(it.message ?: it.toString())
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun handleManualInput(qrCode: String) {
        if (qrCode.isBlank()) {
            showMessage(getString(R.string.matter_validation_manual_qrcode))
            return
        }
        try {
            payload = if (qrCode.startsWith("MT:")) {
                OnboardingPayloadParser().parseQrCode(qrCode)
            } else {
                OnboardingPayloadParser().parseManualPairingCode(qrCode)
            }
            println("Payload : $payload")

            if (networkSelectionDialog == null) {
                val prev =
                    requireActivity().supportFragmentManager.findFragmentByTag(
                        ALERT_NTW_MODE_DIALOG_TAG
                    )
                if (prev == null) {
                    val chipDeviceInfo = """
    Version: ${payload.version}
    
    Vendor ID: ${payload.vendorId}
    
    Product ID: ${payload.productId}
    
    Discriminator: ${payload.discriminator}
    
    Setup PIN Code: ${payload.setupPinCode}
    
    Discovery Capabilities: ${
                        payload.discoveryCapabilities.joinToString(", ")
                    }
    
    Commissioning Flow: ${payload.commissioningFlow}
""".trimIndent()
                    showDeviceInfoDialog(chipDeviceInfo)


                }
            }


        } catch (ex: SetupPayloadParser.SetupPayloadException) {
            try {
                payload = OnboardingPayloadParser().parseManualPairingCode(qrCode)
                isShortDiscriminator = true
            } catch (ex: Exception) {
                Timber.tag(TAG).e("Unrecognized Manual Pairing Code $ex")
                showMessageDialog(getString(R.string.matter_invalid_manual_qr_code))
            }
        } catch (ex: SetupPayloadParser.UnrecognizedQrCodeException) {
            Timber.tag(TAG).e("Unrecognized QR Code $ex")
            // Toast.makeText(requireContext(), "Unrecognized QR Code", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Timber.tag(TAG).e("Unrecognized QR Exception $e")
        }

    }

    private fun handleScannedQrCode(barcode: Barcode) {
        println("Matter Camera Scanned QR")

        Handler(Looper.getMainLooper()).post {

            try {
                payload =
                    OnboardingPayloadParser().parseQrCode(barcode.displayValue!!)

                isShortDiscriminator = true


                if (networkSelectionDialog == null) {
                    val prev =
                        requireActivity().supportFragmentManager.findFragmentByTag(
                            ALERT_NTW_MODE_DIALOG_TAG
                        )
                    if (prev == null) {
                        val chipDeviceInfo = """
    Version: ${payload.version}
    
    Vendor ID: ${payload.vendorId}
    
    Product ID: ${payload.productId}
    
    Discriminator: ${payload.discriminator}
    
    Setup PIN Code: ${payload.setupPinCode}
    
    Discovery Capabilities: ${
                            payload.discoveryCapabilities.joinToString(", ")
                        }
    
    Commissioning Flow: ${payload.commissioningFlow}
""".trimIndent()
                        showDeviceInfoDialog(chipDeviceInfo)


                    }
                }

            } catch (ex: SetupPayloadParser.UnrecognizedQrCodeException) {
                Timber.tag(TAG).e("Unrecognized QR Code $ex")
                //  showMessageDialog()
                return@post
            } catch (e: SetupPayloadParser.InvalidEntryCodeFormatException) {
                Timber.tag(TAG).e("InvalidEntryCodeFormatException $e")
                showMessageDialog(getString(R.string.qr_code_unrecognized))
            } catch (d: Exception) {
                Timber.tag(TAG).e("Unrecognized QR Exception $d")
                showMessageDialog(getString(R.string.qr_code_unrecognized))
            }

        }
    }

    private fun showMessageDialog(string: String) {

        try {
            if (isAdded && !requireActivity().isFinishing) {
                requireActivity().runOnUiThread {
                    if (!MessageDialogFragment.isDialogShowing()) {
                        dialog = MessageDialogFragment()
                        dialog.setMessage(string)
                        dialog.setOnDismissListener {
                            dialog.dismiss()
                        }
                        val transaction: FragmentTransaction =
                            requireActivity().supportFragmentManager.beginTransaction()

                        dialog.show(transaction, dialogTag)
                    }
                }
            } else {
                Timber.e("Unrecognized QR Exception")
            }
        } catch (e: Exception) {
            Timber.e("Unrecognized QR Exception $e")
        }

    }


    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    private fun hasCameraPermission(): Boolean {
        return (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ))
    }

    private fun requestCameraPermission() {
        val permissions = arrayOf(Manifest.permission.CAMERA)
        requestPermissions(permissions, REQUEST_CODE_CAMERA_PERMISSION)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestLocationPermission() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        requestPermissions(permissions, REQUEST_CODE_LOCATION_PERMISSION)
    }

    private fun hasLocationPermission(): Boolean {
        val locationPermissionGranted =
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        // Android 12 new permission
        var bleScanPermissionGranted = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bleScanPermissionGranted =
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
        }

        return locationPermissionGranted && bleScanPermissionGranted
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                showCameraPermissionAlert()
            }
        } else if (requestCode == REQUEST_CODE_LOCATION_PERMISSION) {
            if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                showLocationPermissionAlert()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }


    private fun showCameraPermissionAlert() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.matter_camera_unavailable_alert_title)
            .setMessage(R.string.matter_camera_unavailable_alert_subtitle)
            .setPositiveButton(R.string.matter_camera_unavailable_alert_exit) { _, _ ->
                requireActivity().finish()
            }
            .setCancelable(false)
            .create().show()
    }

    private fun showLocationPermissionAlert() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.matter_location_unavailable_alert_title)
            .setMessage(R.string.matter_location_unavailable_alert_subtitle)
            .setPositiveButton(R.string.matter_camera_unavailable_alert_exit) { _, _ ->
                requireActivity().finish()
            }
            .setCancelable(false)
            .create().show()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        deviceInfo = CHIPDeviceInfo.fromSetupPayload(
            payload,
            isShortDiscriminator
        )
        if (resultCode == WIFI_REQ_CODE) {
            println("Matter Wifi Selected")
            typeNtw = INPUT_NETWORK_WIFI_TYPE_SELECTED
            networkMode = data!!.getStringExtra(ARG_PROVISION_NETWORK_TYPE).toString()
            displayCommissioningDialog()

        } else if (resultCode == THREAD_REQ_CODE) {
            println("Matter Thread Selected")
            typeNtw = INPUT_NETWORK_THREAD_TYPE_SELECTED
            networkMode = data!!.getStringExtra(ARG_PROVISION_NETWORK_TYPE).toString()
            displayCommissioningDialog()

        } else if (resultCode == CANCEL_REQ_CODE) {
            cancelNetworkInputDialog()
            startCamera()
        } else if (resultCode == INPUT_WIFI_REQ_CODE) {
            if (data != null) {
                val wifiSSID = data.getStringExtra(WIFI_INPUT_SSID)
                val wifiPassword = data.getStringExtra(WIFI_INPUT_PASSWORD)

                if (wifiSSID != null && wifiPassword != null) {
                    validateWiFiInput(wifiSSID, wifiPassword)
                }
            }
        } else if (resultCode == Activity.RESULT_OK) {
            if (data != null) {
                val receiveOTBRInfo = data.getStringExtra(DIALOG_OTBR_INFO)
                removeAlert()
                validateThreadInput(receiveOTBRInfo!!.trim())
            }
        }
    }

    private fun displayCommissioningDialog() {
        val str = requireContext().getString(R.string.matter_commissioning_device)
        showMatterProgressDialog(str, false)
        lifecycleScope.launch {
            delay(CREDENTIAL_PROMPT_DELAY_MS)
            if (!isAdded) return@launch
            removeProgress()
            displayInputWindowBasedOnProvisionType()
        }
    }

    @SuppressLint("NewApi")
    private fun validateThreadInput(inputOTBR: String) {
        if (inputOTBR.isBlank()) {
            //Toast.makeText(requireContext(), "input OTBR is empty", Toast.LENGTH_SHORT).show()
            CustomToastManager.show(
                requireContext(), "Input OTBR is empty", 5000
            )
            return
        }
        val operationalDataset = dataFromHexString(inputOTBR.trim())

        networkCredential = NetworkCredentialsParcelable
            .forThread(NetworkCredentialsParcelable.ThreadCredentials(operationalDataset))
        startConnectingToDevice()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun validateWiFiInput(wifiSSID: String, wifiPassword: String) {
        networkCredential = NetworkCredentialsParcelable
            .forWiFi(
                NetworkCredentialsParcelable.WiFiCredentials(
                    wifiSSID, wifiPassword
                )
            )
        startConnectingToDevice()
    }


    private fun dataFromHexString(string: String): ByteArray {
        var modifiedString = string
        if (string.length % 2 == 1) {
            modifiedString = "0$string"
        }

        val chars = modifiedString.toCharArray()
        var i = 0
        val len = modifiedString.length


        val data = ByteArray(len / 2)
        val byteChars = CharArray(2)


        while (i < len) {
            byteChars[0] = chars[i++]
            byteChars[1] = chars[i++]
            var wholeByte = 0L
            try {
                wholeByte = byteChars.joinToString("").toLong(16)

            } catch (ex: NumberFormatException) {
                var wholeByte = 0L
                Timber.tag(TAG).e("Number Format Exception Occurred..")
            }

            data[(i - 2) / 2] = wholeByte.toByte()
        }

        return data
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("MissingPermission")
    private fun startConnectingToDevice() {
        if (gatt != null) {
            return
        }
        scope.launch {

            bluetoothManager = BluetoothManager()
            val strId = R.string.rendezvous_over_ble_scanning_text
            val devInfo = deviceInfo.discriminator.toString()
            showMessage(strId, devInfo)
            showMatterProgressDialog(
                getString(R.string.matter_network_selection_alert_title),
                true
            )
            val device = bluetoothManager.getBluetoothDevice(
                requireContext(),
                deviceInfo.discriminator, deviceInfo.isShortDiscriminator
            ) ?: kotlin.run {
                requireActivity().supportFragmentManager.popBackStack()
                requireActivity().supportFragmentManager.popBackStack()
                if (customProgressDialog != null) {
                    if (customProgressDialog!!.isShowing) {
                        customProgressDialog!!.dismiss()
                    }
                }
                showMessage(R.string.rendezvous_over_ble_scanning_failed_text)
                return@launch
            }
            matterScanDevice = device
            Timber.tag(TAG).e("Type : ${device.type}")
            Timber.tag(TAG).e("UUID : ${device.uuids}")
            Timber.tag(TAG).e("Name : ${device.name}")
            Timber.tag(TAG).e("Address : ${device.address}")
            Timber.tag(TAG).e("Alias : ${device.alias}")
            if (customProgressDialog != null) {
                if (customProgressDialog!!.isShowing) {
                    customProgressDialog!!.dismiss()
                }
            }
            showMatterProgressDialog(
                getString(R.string.matter_commissioning_message),
                true
            )
            showMessage(
                R.string.rendezvous_over_ble_connecting_text,
                device.name ?: device.address.toString()
            )
            removeAlert()
            deviceId = DeviceIDUtil.getNextAvailableId(requireContext())
            gatt = bluetoothManager.connect(requireContext(), device)
            deviceController.setCompletionListener(ConnectionCallback())
            val connId = bluetoothManager.connectionId
            var network: NetworkCredentials? = null


            val thread = networkCredential.threadCredentials
            if (thread != null) {
                network =
                    NetworkCredentials.forThread(
                        NetworkCredentials.ThreadCredentials(thread.operationalDataset)
                    )
            }
            val wifi = networkCredential.wiFiCredentials
            if (wifi != null) {
                network =
                    NetworkCredentials.forWiFi(
                        NetworkCredentials.WiFiCredentials(wifi.ssid, wifi.password)
                    )
            }

            setAttestationDelegate()

            deviceController.pairDevice(
                gatt,
                connId,
                deviceId,
                deviceInfo.setupPinCode,
                network
            )
            DeviceIDUtil.setNextAvailableId(requireContext(), deviceId + 1)
        }
    }

    private fun setAttestationDelegate() {
        deviceController
            .setDeviceAttestationDelegate(DEVICE_ATTESTATION_FAILED_TIMEOUT)
            { devicePtr, _,
              errorCode ->
                Timber.tag(TAG)
                    .e(
                        "Device attestation errorCode: $errorCode, \nLook at 'src/credentials/attestation_verifier/DeviceAttestationVerifier.h' \nAttestationVerificationResult enum to understand the errors"
                    )

                val activity = requireActivity()
                Timber.tag(TAG).e("setAttestationDelegate()--errorCode: $errorCode")
                if (errorCode == STATUS_PAIRING_SUCCESS) {
                    Timber.tag(TAG).e("setAttestationDelegate() In--errorCode: $errorCode")
                    activity.runOnUiThread {
                        deviceController.continueCommissioning(devicePtr, true)
                    }

                    return@setDeviceAttestationDelegate
                }
                activity.runOnUiThread(Runnable {
                    if (alertDialog != null && alertDialog?.isShowing == true) {
                        Timber.tag(TAG).e("Dialog is already showing...")
                        return@Runnable
                    }
                    alertDialog = android.app.AlertDialog.Builder(activity)
                        .setPositiveButton(
                            "Continue"
                        ) { alertDialog, id ->
                            deviceController.continueCommissioning(devicePtr, true)
                        }
                        .setNegativeButton(
                            "No"
                        ) { alertDialog, id ->
                            deviceController.continueCommissioning(devicePtr, false)
                        }
                        .setTitle("Device Attestation")
                        .setMessage("Device Attestation failed for device under commissioning. Do you wish to continue pairing?")
                        .show()
                })

            }
    }

    private fun cancelNetworkInputDialog() {
        if (networkSelectionDialog != null) {
            networkSelectionDialog = null
        }
    }

    private fun removeAlert() {
        if (otbrEntryDialog != null) {
            otbrEntryDialog!!.dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancelNetworkInputDialog()
    }

    private fun showMessage(msg: String) {
        requireActivity().runOnUiThread {
            //   Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            CustomToastManager.show(
                requireContext(), msg, 5000
            )
        }
    }

    private fun showMessage(msgResId: Int, stringArgs: String? = null) {
        requireActivity().runOnUiThread {
            val context = requireContext()
            val msg = context.getString(msgResId, stringArgs)
            Timber.tag(TAG).e("showMessage:$msg")
            /*Toast.makeText(context, msg, Toast.LENGTH_SHORT)
                .show()*/
            CustomToastManager.showSuccess(
                requireContext(), msg, 5000
            )
        }
    }

    private fun showDeviceInfoDialog(message: String) {
        try {
            if (isAdded && !requireActivity().isFinishing) {
                requireActivity().runOnUiThread {
                    if (!deviceInfoDialogShown && (deviceDialog == null || !deviceDialog!!.isShowing())) {
                        deviceInfoDialogShown = true
                        deviceDialog = DialogDeviceInfoFragment.Builder()
                            .setTitle(getString(R.string.qr_code_info))
                            .setMessage(message)
                            .setPositiveButton(getString(R.string.start_commissioning)) { dialog, which ->
                                // Positive button click logic
                                networkSelectionDialog =
                                    MatterNetworkSelectionInputDialogFragment.newInstance()

                                networkSelectionDialog!!.setTargetFragment(
                                    this,
                                    DIALOG_NTW_MODE_FRAGMENT
                                )
                                networkSelectionDialog!!.show(
                                    requireActivity().supportFragmentManager,
                                    ALERT_NTW_MODE_DIALOG_TAG
                                )
                                deviceInfoDialogShown = false
                            }
                            .setNegativeButton("Cancel") { dialog, which ->
                                deviceDialog?.dismiss()
                                deviceInfoDialogShown = false
                            }
                            .build()

                        deviceDialog?.show(parentFragmentManager, "DialogDeviceInfoFragment")
                    }
                }
            } else {
                Timber.e("Fail to read device detail")
            }
        } catch (e: Exception) {
            Timber.e("Fail to read device detail $e")
        }
    }

    private fun displayInputWindowBasedOnProvisionType() {
        when (typeNtw) {
            INPUT_NETWORK_WIFI_TYPE_SELECTED /* ProvisionNetworkType.WIFI */ -> {
                if (wifiEntryDialog == null) {
                    val prev =
                        requireActivity().supportFragmentManager.findFragmentByTag(
                            DIALOG_WIFI_INPUT_TAG
                        )
                    if (prev == null) {
                        wifiEntryDialog = MatterWifiInputDialogFragment.newInstance()
                        wifiEntryDialog!!.setTargetFragment(this, DIALOG_WIFI_FRAGMENT)
                        wifiEntryDialog!!.show(
                            requireActivity().supportFragmentManager,
                            DIALOG_WIFI_INPUT_TAG
                        )
                    }
                }
            }

            INPUT_NETWORK_THREAD_TYPE_SELECTED  /*  ProvisionNetworkType.THREAD*/ -> {
                if (otbrEntryDialog == null) {
                    val prev =
                        requireActivity().supportFragmentManager.findFragmentByTag(
                            DIALOG_THREAD_INPUT_TAG
                        )
                    if (prev == null) {
                        otbrEntryDialog = MatterOTBRInputDialogFragment.newInstance()

                        otbrEntryDialog!!.setTargetFragment(this, DIALOG_THREAD_FRAGMENT)
                        otbrEntryDialog!!.show(
                            requireActivity().supportFragmentManager,
                            DIALOG_THREAD_INPUT_TAG
                        )
                    }
                }
            }

            else -> {
                println("Unhandled....")
            }

        }
    }

    private fun showMatterProgressDialog(message: String, showCancelButton: Boolean) {
        cancelCommissioningTimeout()
        customProgressDialog = CustomProgressDialog(requireContext())
        customProgressDialog!!.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        customProgressDialog!!.setMessage(message)
        customProgressDialog!!.setCanceledOnTouchOutside(false)
        customProgressDialog!!.setCustomButtonVisible(showCancelButton) {
            cancelCommissioningAndReturnHome()
        }
        customProgressDialog!!.show()
        if (showCancelButton) {
            scheduleCommissioningTimeout()
        }
    }

    private fun scheduleCommissioningTimeout() {
        cancelCommissioningTimeout()
        commissioningTimeoutJob = lifecycleScope.launch {
            delay(COMMISSIONING_DIALOG_TIME_OUT_MS)
            if (!isAdded || customProgressDialog?.isShowing != true) return@launch
            handleCommissioningTimeout()
        }
    }

    private fun cancelCommissioningTimeout() {
        commissioningTimeoutJob?.cancel()
        commissioningTimeoutJob = null
    }

    private fun handleCommissioningTimeout() {
        removeProgress()
        try {
            deviceController.close()
        } catch (e: Exception) {
            Timber.tag(TAG).e("Commissioning timeout close error $e")
        }
        gatt?.close()
        gatt = null
        if (!isAdded) return
        showCommissioningTimeoutAlert()
    }

    private fun showCommissioningTimeoutAlert() {
        showCommissioningResultAlert(getString(R.string.matter_commissioning_timeout))
    }

    private fun cancelCommissioningAndReturnHome() {
        cancelCommissioningTimeout()
        deviceController.close()
        requireActivity().supportFragmentManager.popBackStack(
            null,
            FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
        sendToScanResultFragment()
        customProgressDialog?.dismiss()
    }

    private fun sendToScanResultFragment() {
        val bundle = Bundle()
        scannedDeviceList = SharedPrefsUtils.retrieveSavedDevices(mPrefs)
        bundle.putParcelableArrayList(ARG_DEVICE_LIST, scannedDeviceList)


        val fragment = MatterScannedResultFragment.newInstance()
        fragment.arguments = bundle

        parentFragmentManager.beginTransaction()
            .replace(
                R.id.matter_container,
                fragment
            )  // R.id.matterContainer should be the container in the activity or fragment layout where the new fragment will be placed
            .addToBackStack(null)  // Add to back stack if you want the user to be able to navigate back
            .commit()
    }

    private fun removeProgress() {
        cancelCommissioningTimeout()
        if (customProgressDialog?.isShowing == true) {
            customProgressDialog?.dismiss()
        }
    }

    private fun onCommissionCompleted() {
        try {
            ChipClient.getDeviceController(requireContext()).close()
        } catch (e: Exception) {
            Timber.tag(TAG).e("onCommissionCompleted error $e")
        }
    }

    private suspend fun getDescriptorClusterForDevice(): ChipClusters.DescriptorCluster {
        return ChipClusters.DescriptorCluster(

            ChipClient.getConnectedDevicePointer(requireContext(), deviceId),
            ON_OFF_CLUSTER_ENDPOINT
        )
    }

    inner class ConnectionCallback : GenericChipDeviceListener() {
        override fun onConnectDeviceComplete() {
            // super.onConnectDeviceComplete()
            Timber.tag(TAG).e("onConnectDeviceComplete")
        }

        override fun onStatusUpdate(status: Int) {
            // super.onStatusUpdate(status)
            Timber.tag(TAG).e("onStatusUpdate : $status.toString()")
        }

        override fun onPairingComplete(errorCode: Long) {
            Timber.tag(TAG).e("onPairingComplete: $errorCode")
            if (errorCode != STATUS_PAIRING_SUCCESS) {
                requireActivity().runOnUiThread {
                    CustomToastManager.showError(
                        requireContext(),
                        getString(R.string.rendezvous_over_ble_pairing_failure_text),
                        5000
                    )
                }
            }
        }

        override fun onOpCSRGenerationComplete(csr: ByteArray?) {
            Timber.tag(TAG).e("onOpCSRGenerationComplete: $String(csr)")
        }

        override fun onCloseBleComplete() {
            Timber.tag(TAG).e("onCloseBleComplete")
        }

        override fun onError(error: Throwable?) {
            Timber.tag(TAG).e("onError: $error")
        }

        override fun onICDRegistrationInfoRequired() {
            Timber.tag(TAG).e("onICDRegistrationInfoRequired")
            deviceController.updateCommissioningICDRegistrationInfo(
                ICDRegistrationInfo.newBuilder()
                    .setICDStayActiveDurationMsec(30000L).build()
            )
        }

        @SuppressLint("StringFormatMatches")
        override fun onICDRegistrationComplete(
            errorCode: Long,
            icdDeviceInfo: ICDDeviceInfo
        ) {
            requireActivity().runOnUiThread {
                val message = getString(
                    R.string.icd_registration_completed,
                    icdDeviceInfo?.userActiveModeTriggerHint.toString(),
                    icdDeviceInfo?.userActiveModeTriggerInstruction,
                    icdDeviceInfo?.idleModeDuration.toString(),
                    icdDeviceInfo?.activeModeDuration.toString(),
                    icdDeviceInfo?.activeModeThreshold.toString()
                )
                Timber.tag(TAG).e("onICDRegistrationComplete:$message ")
                CustomToastManager.show(requireContext(), message, 5000)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCommissioningComplete(nodeId: Long, errorCode: Long) {
            //  super.onCommissioningComplete(nodeId, errorCode)
            Timber.tag(TAG).e("onCommissioningComplete : NodeID:  $nodeId.toString()")
            Timber.tag(TAG).e("%s%s", "onCommissioningComplete : errorCode: ", errorCode.toString())
            removeAlert()
            if (errorCode == STATUS_PAIRING_SUCCESS) {
                if (customProgressDialog?.isShowing == true) {
                    customProgressDialog?.dismiss()
                }
                Timber.tag(TAG).e("pairing success")
                onCommissionCompleted()

                scope.launch {
                    getDescriptorClusterForDevice().readDeviceTypeListAttribute(object :
                        ChipClusters.DescriptorCluster.DeviceTypeListAttributeCallback {
                        override fun onError(error: java.lang.Exception?) {
                            Timber.tag(TAG).e("MatterConnect Error: $error")
                        }

                        override fun onSuccess(valueList: MutableList<ChipStructs.DescriptorClusterDeviceTypeStruct>?) {

                            Timber.tag(TAG)
                                .e("deviceType  $ valueList?.get(0)?.deviceType?.toInt()!!")
                            val deviceType = valueList?.get(0)?.deviceType?.toInt()!!
                            println("device Type: $deviceType}")
                            println("device Info: ${matterScanDevice.name}  DeviceId: ${deviceId}")
                            var device = ""

                            when (deviceType) {
                                DOOR_LOCK_TYPE ->
                                    device =
                                        requireContext().getString(R.string.matter_lock_list)

                                DIMMABLE_PLUG_IN_UNIT_TYPE ->
                                    device =
                                        requireContext().getString(R.string.matter_plug_list)

                                OCCUPANCY_SENSOR_TYPE ->
                                    device =
                                        requireContext().getString(R.string.matter_occupancy_sensor_list)

                                TEMPERATURE_SENSOR_TYPE ->
                                    device =
                                        requireContext().getString(R.string.matter_temperature_sensor_list)

                                CONTACT_SENSOR_TYPE ->
                                    device =
                                        requireContext().getString(R.string.matter_contact_sensor_list)

                                THERMOSTAT_TYPE ->
                                    device =
                                        requireContext().getString(R.string.matter_thermostat_list)

                                DIMMABLE_Light_TYPE, ENHANCED_COLOR_LIGHT_TYPE, ON_OFF_LIGHT_TYPE, COLOR_TEMPERATURE_LIGHT_TYPE ->
                                    device =
                                        requireContext().getString(R.string.matter_light_list)

                                WINDOW_COVERING_TYPE ->
                                    device =
                                        requireContext().getString(R.string.matter_window_list)

                                DISHWASHER_TYPE ->
                                    device =
                                        requireContext().getString(R.string.matter_dish_washer_list)

                                AIR_QUALITY_SENSOR_TYPE ->
                                    device =
                                        requireContext().getString(R.string.matter_air_quality_sensor_list)

                                GENERIC_SWITCH, DIMMER_SWITCH, COLOR_DIMMER_SWITCH,
                                ON_OFF_LIGHT_SWITCH ->
                                    device =
                                        requireContext().getString(R.string.matter_light_switch)

                                ENERGY_EVSE_TYPE ->
                                    device =
                                        requireContext().getString(R.string.matter_ev_charger)

                                OVEN, MICROWAVE_OVEN ->
                                    device = requireContext().getString(R.string.matter_oven_device)

                                RANGE_HOOD->
                                    device = requireContext().getString(R.string.matter_range_hood_device)
                                else -> device = matterScanDevice.name


                                // else -> device = matterScanDevice.name
                            }

                            val deviceName = device + COLON_WITH_SPACE + deviceId
                            println("device Info: ${deviceName}  DeviceId: ${deviceId}")
                            BLEUtils.MATTER_DEVICE_ID = "$deviceId"
                            BLEUtils.MATTER_DEVICE_NAME = deviceName
                            BLEUtils.MATTER_DEVICE_TYPE = deviceType
                            showEditDeviceNameDialog(device, valueList)


                        }

                        private fun showEditDeviceNameDialog(
                            device: String,
                            valueList: MutableList<ChipStructs.DescriptorClusterDeviceTypeStruct>
                        ) {

                            val customInputDialog = CustomInputDialog.newInstance(
                                requireContext(), device, getString(R.string.add_device_name),
                                getString(
                                    R.string.add_device_subtitle
                                )
                            )
                            customInputDialog.setOnButtonClickListener { deviceName ->

                                val matterInfo = MatterScannedResultModel(
                                    deviceName,
                                    matterScanDevice.address,
                                    matterScanDevice.type,
                                    deviceId,
                                    valueList.get(0).deviceType?.toInt()!!,
                                    isDeviceOnline = true
                                )
                                FragmentUtils.getHost(
                                    this@MatterScannerFragment,
                                    CallBack::class.java
                                ).onCommissionCompleteLoadData(matterInfo)
                            }

                            customInputDialog.show(requireFragmentManager(), "CustomInputDialog")


                        }

                    })
                }


            } else {
                if (customProgressDialog?.isShowing() == true) {
                    customProgressDialog?.dismiss()
                }
                requireActivity().runOnUiThread {
                    showAlertWindow(errorCode)
                }
            }
            onCommissionCompleted()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun showAlertWindow(errorCode: Long) {
        if (!isAdded) return
        showCommissioningResultAlert(getString(R.string.matter_commissioning_failed))
    }

    private fun showCommissioningResultAlert(message: String) {
        val fragment = MatterTitleMessageOkDialogFragment.newInstance(
            getString(R.string.matter_delete_alert_title),
            message
        )
        fragment.onOkClick = {
            if (isAdded) {
                deviceController = ChipClient.getDeviceController(requireContext())
                startCamera()
            }
        }
        fragment.show(requireActivity().supportFragmentManager, "MatterCommissioningResultAlert")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
    }

    override fun notifyCheckInMessage(info: ICDClientInfo) {
        externalICDCheckInMessageCallback?.notifyCheckInMessage()
    }


    interface CallBack {
        fun onCommissionCompleteLoadData(
            matterScannedResultModel:
            MatterScannedResultModel
        )
    }

    interface ICDCheckInMessageCallback {
        fun notifyCheckInMessage()
    }

    companion object {
        private val TAG = MatterScannerFragment::class.java.classes.toString()
        private const val COMMISSIONING_DIALOG_TIME_OUT_MS = 60_000L
        private const val CREDENTIAL_PROMPT_DELAY_MS = 1_000L
        const val CANCEL_REQ_CODE = 5000
        const val WIFI_REQ_CODE = 5001
        const val THREAD_REQ_CODE = 5002
        const val INPUT_WIFI_REQ_CODE = 5003
        private const val DEVICE_ATTESTATION_FAILED_TIMEOUT = 600
        private const val DIALOG_THREAD_FRAGMENT = 999
        private const val DIALOG_WIFI_FRAGMENT = 997

        private const val STATUS_PAIRING_SUCCESS = 0L

        private const val REQUEST_CODE_LOCATION_PERMISSION = 101
        private const val REQUEST_CODE_CAMERA_PERMISSION = 100
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
        private const val DIALOG_NTW_MODE_FRAGMENT = 998

        const val SPACE = " "
        const val AQI = "AQI :"
        const val INPUT_NETWORK_WIFI_TYPE_SELECTED = "wifi"
        const val INPUT_NETWORK_THREAD_TYPE_SELECTED = "thread"
        const val WIFI_INPUT_SSID = "wifi_input_ssid"
        const val WIFI_INPUT_PASSWORD = "wifi_input_password"
        const val ARG_PROVISION_NETWORK_TYPE = "dialog_network_mode_info"
        const val DIALOG_OTBR_INFO = "Dialog_OTBR_Info"
        const val DIA_INPUT_NTW_TYPE = "dia_input_ntw_type"

        private const val COLON_WITH_SPACE = " - "
        private const val ALERT_NTW_MODE_DIALOG_TAG = "alert_ntw_mode_dialog_tag"
        private const val DIALOG_THREAD_INPUT_TAG = "MatterThreadDialogFragmentTAG"
        private const val DIALOG_WIFI_INPUT_TAG = "MatterWiFiDialogFragmentTAG"
        private const val ARG_DEVICE_LIST = "device_list"

        fun newInstance(): MatterScannerFragment {
            val args = Bundle()

            val fragment = MatterScannerFragment()
            fragment.arguments = args
            return fragment
        }
    }


}