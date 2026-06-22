package com.siliconlabs.bledemo.home_screen.fragments

import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getColor
import androidx.recyclerview.widget.GridLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.bluetooth.services.BluetoothService
import com.siliconlabs.bledemo.databinding.FragmentDemoBinding
import com.siliconlabs.bledemo.features.demo.channel_sounding.activities.ReflectorDashboardActivity
import com.siliconlabs.bledemo.features.demo.channel_sounding.utils.ChannelSoundingDetectorUtils
import com.siliconlabs.bledemo.features.demo.channel_sounding.utils.ReflectorSessionPreferences
import com.siliconlabs.bledemo.features.demo.energyharvesting.EnergyHarvestingActivity
import com.siliconlabs.bledemo.features.demo.matter_demo.activities.MatterDemoActivity
import com.siliconlabs.bledemo.features.demo.range_test.activities.RangeTestActivity
import com.siliconlabs.bledemo.features.demo.wifi_ota_update.AlertErrorDialog
import com.siliconlabs.bledemo.features.demo.wifi_ota_update.WiFiOtaFileManager
import com.siliconlabs.bledemo.features.demo.wifi_ota_update.WiFiOtaFileSelectionDialog
import com.siliconlabs.bledemo.features.demo.wifi_ota_update.WiFiOtaProgressDialog
import com.siliconlabs.bledemo.features.demo.wifi_provisioning.activities.WiFiProvisioningActivity
import com.siliconlabs.bledemo.features.demo.wifi_throughput.activities.WifiThroughputActivity
import com.siliconlabs.bledemo.home_screen.adapters.DemoAdapter
import com.siliconlabs.bledemo.home_screen.base.BaseServiceDependentMainMenuFragment
import com.siliconlabs.bledemo.home_screen.base.BluetoothDependent
import com.siliconlabs.bledemo.home_screen.base.LocationDependent
import com.siliconlabs.bledemo.home_screen.dialogs.ChannelSoundingModeDialog
import com.siliconlabs.bledemo.home_screen.dialogs.SelectDeviceDialog
import com.siliconlabs.bledemo.home_screen.menu_items.AWSIOTDemo
import com.siliconlabs.bledemo.home_screen.menu_items.Blinky
import com.siliconlabs.bledemo.home_screen.menu_items.ChannelSoundingDemo
import com.siliconlabs.bledemo.home_screen.menu_items.ConnectedLighting
import com.siliconlabs.bledemo.home_screen.menu_items.DemoMenuItem
import com.siliconlabs.bledemo.home_screen.menu_items.DevKitSensorDemo
import com.siliconlabs.bledemo.home_screen.menu_items.EnergyHarvestingDemo
import com.siliconlabs.bledemo.home_screen.menu_items.Environment
import com.siliconlabs.bledemo.home_screen.menu_items.EslDemo
import com.siliconlabs.bledemo.home_screen.menu_items.HealthThermometer
import com.siliconlabs.bledemo.home_screen.menu_items.MatterDemo
import com.siliconlabs.bledemo.home_screen.menu_items.Motion
import com.siliconlabs.bledemo.home_screen.menu_items.OTADemo
import com.siliconlabs.bledemo.home_screen.menu_items.RangeTest
import com.siliconlabs.bledemo.home_screen.menu_items.SmartLockDemo
import com.siliconlabs.bledemo.home_screen.menu_items.Throughput
import com.siliconlabs.bledemo.home_screen.menu_items.WiFiProvisioning
import com.siliconlabs.bledemo.home_screen.menu_items.WiFiThroughput
import com.siliconlabs.bledemo.home_screen.menu_items.WifiCommissioning
import com.google.android.material.button.MaterialButton
import com.siliconlabs.bledemo.utils.BLEUtils
import com.siliconlabs.bledemo.utils.CustomToastManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

class DemoFragment : BaseServiceDependentMainMenuFragment(), DemoAdapter.OnDemoItemClickListener,
    DialogInterface.OnDismissListener {

    private val binding by viewBinding(FragmentDemoBinding::bind)
    private var demoAdapter: DemoAdapter? = null
    private val list: ArrayList<DemoMenuItem> = ArrayList()
    private var selectDeviceDialog: SelectDeviceDialog? = null

    private var otaProgressDialog: WiFiOtaProgressDialog? = null
    private var otaFileSelectionDialog: WiFiOtaFileSelectionDialog? = null

    private var otaFileManager: WiFiOtaFileManager? = null
    private var otaFilePath: Uri? = null
    private var socket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private var portId = 8080
    private var isClientConnected = false
    private val kBits = "%.2f Kbit/s"
    private var alertErrorDialog: AlertErrorDialog? = null
    private var packetCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        otaFileManager = WiFiOtaFileManager(requireContext())
        initOtaProgressDialog()


        list.apply {
            add(
                HealthThermometer(
                    R.drawable.redesign_ic_demo_health_thermometer,
                    getString(R.string.title_Health_Thermometer),
                    getString(R.string.main_menu_description_thermometer)
                )
            )
            add(
                ConnectedLighting(
                    R.drawable.redesign_ic_demo_connected_lighting,
                    "Connected Device",
                    getString(R.string.main_menu_description_connected_lighting)
                )
            )
            add(
                RangeTest(
                    R.drawable.redesign_ic_demo_range_test,
                    getString(R.string.title_Range_Test),
                    getString(R.string.main_menu_description_range_test)
                )
            )
            add(
                Blinky(
                    R.drawable.redesign_ic_demo_blinky,
                    getString(R.string.title_Blinky),
                    getString(R.string.main_menu_description_blinky)
                )
            )
            add(
                Throughput(
                    R.drawable.redesign_ic_demo_throughput,
                    getString(R.string.title_Throughput),
                    getString(R.string.main_menu_description_throughput)
                )
            )
            add(
                Motion(
                    R.drawable.redesign_ic_demo_motion,
                    getString(R.string.motion_demo_title),
                    getString(R.string.motion_demo_description)
                )
            )
            add(
                Environment(
                    R.drawable.redesign_ic_demo_environment,
                    getString(R.string.environment_demo_title),
                    getString(R.string.environment_demo_description)
                )
            )
            add(
                WifiCommissioning(
                    R.drawable.redesign_ic_demo_wifi_commissioning,
                    getString(R.string.wifi_commissioning_label),
                    getString(R.string.wifi_commissioning_description)
                )
            )
            add(
                EslDemo(
                    R.drawable.redesign_ic_demo_esl,
                    getString(R.string.demo_item_title_esl_demo),
                    getString(R.string.demo_item_description_esl_demo)
                )
            )
            add(
                MatterDemo(
                    R.drawable.redesign_ic_demo_matter,
                    getString(R.string.matter_demo_title),
                    getString(R.string.matter_demo_description)
                )
            )
            add(
                OTADemo(
                    R.drawable.redesign_ic_demo_ota,
                    getString(R.string.ota_demo_title),
                    getString(R.string.ota_demo_title_desc)
                )
            )
            add(
                DevKitSensorDemo(
                    R.drawable.redesign_ic_demo_dks_917,
                    getString(R.string.dev_kit_sensor_917_title),
                    getString(R.string.dev_kit_sensor_917_desc)
                )
            )
            add(
                WiFiThroughput(
                    R.drawable.redesign_ic_demo_wifi_throughput,
                    getString(R.string.wifi_title_Throughput),
                    getString(R.string.wifi_main_menu_description_wifi_throughput)
                )
            )
            add(
                WiFiProvisioning(
                    R.drawable.redesign_ic_demo_wifi_provisioning,
                    getString(R.string.wifi_provisioning_label),
                    getString(R.string.wifi_provisioning_description)
                )
            )
            add(
                AWSIOTDemo(
                    R.drawable.ic_aws_iot_icon,
                    getString(R.string.aws_title),
                    getString(R.string.aws_desc)
                )
            )
            add(
                SmartLockDemo(
                    R.drawable.ic_smartlock_icon,
                    getString(R.string.smart_lock_demo_title),
                    getString(R.string.smart_lock_demo_description)
                )
            )


            add(
                ChannelSoundingDemo(
                    R.drawable.ic_channel_sound,
                    getString(R.string.channel_sounding_title),
                    getString(R.string.channel_sounding_description)
                )
            )

            add(
                EnergyHarvestingDemo(
                    R.drawable.ic_energy_icon,
                    getString(R.string.energy_harvesting_demo_title),
                    getString(R.string.energy_harvesting_demo_description)
                )
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.fragment_demo, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.main_navigation_demo_title)
        initRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        if (pendingReflectorPairingFlow) {
            pendingReflectorPairingFlow = false
            view?.post {
                if (isAdded && isBluetoothOperationPossible()) {
                    startReflectorModeScan()
                }
            }
        }
    }

    private fun initRecyclerView() {
        demoAdapter = DemoAdapter(list, this@DemoFragment)
        binding.rvDemoMenu.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = demoAdapter
        }
        demoAdapter?.toggleItemsEnabled(isBluetoothOperationPossible())
    }

    override val bluetoothDependent = object : BluetoothDependent {

        override fun onBluetoothStateChanged(isBluetoothOn: Boolean) {
            toggleBluetoothBar(isBluetoothOn, binding.bluetoothBar)
            demoAdapter?.toggleItemsEnabled(isBluetoothOperationPossible())
            if (!isBluetoothOn) selectDeviceDialog?.dismiss()
        }

        override fun onBluetoothPermissionsStateChanged(arePermissionsGranted: Boolean) {
            toggleBluetoothPermissionsBar(arePermissionsGranted, binding.bluetoothPermissionsBar)
            demoAdapter?.toggleItemsEnabled(isBluetoothOperationPossible())
            if (!arePermissionsGranted) selectDeviceDialog?.dismiss()
        }

        override fun refreshBluetoothDependentUi(isBluetoothOperationPossible: Boolean) {
            demoAdapter?.toggleItemsEnabled(isBluetoothOperationPossible)
        }

        override fun setupBluetoothPermissionsBarButtons() {
            binding.bluetoothPermissionsBar.setFragmentManager(childFragmentManager)
        }
    }

    override val locationDependent = object : LocationDependent {

        override fun onLocationStateChanged(isLocationOn: Boolean) {
            toggleLocationBar(isLocationOn, binding.locationBar)
        }

        override fun onLocationPermissionStateChanged(isPermissionGranted: Boolean) {
            toggleLocationPermissionBar(isPermissionGranted, binding.locationPermissionBar)
            demoAdapter?.toggleItemsEnabled(isPermissionGranted)
            if (!isPermissionGranted) selectDeviceDialog?.dismiss()
        }

        override fun setupLocationBarButtons() {
            binding.locationBar.setFragmentManager(childFragmentManager)
        }

        override fun setupLocationPermissionBarButtons() {
            binding.locationPermissionBar.setFragmentManager(childFragmentManager)
        }
    }


    override fun onDemoItemClicked(demoItem: DemoMenuItem) {


        if (demoItem.connectType == BluetoothService.GattConnectType.MATTER_DEMO) {
            requireContext().startActivity(Intent(requireContext(), MatterDemoActivity::class.java))
        } else if (demoItem.connectType == BluetoothService.GattConnectType.RANGE_TEST) {
            BLEUtils.GATT_DEVICE_SELECTED = BluetoothService.GattConnectType.RANGE_TEST
            startActivity(Intent(requireContext(), RangeTestActivity::class.java))
        } else if (demoItem.connectType == BluetoothService.GattConnectType.ENERGY_HARVESTING_DEMO) {
            BLEUtils.GATT_DEVICE_SELECTED = BluetoothService.GattConnectType.ENERGY_HARVESTING_DEMO
            startActivity(Intent(requireContext(), EnergyHarvestingActivity::class.java))
        } else if (demoItem.connectType == BluetoothService.GattConnectType.WIFI_OTA_UPDATE) {
            if (isNetworkAvailable(context)) {
                otaFileSelectionDialog = WiFiOtaFileSelectionDialog(
                    object : WiFiOtaFileSelectionDialog.CancelCallback {
                        override fun onDismiss() {
                            otaFileSelectionDialog?.dismiss()
                        }
                    },
                    listener = fileSelectionListener,
                    getLocalIpAddress()
                ).also {
                    it.show(childFragmentManager, "ota_file_selection_dialog")
                }
            } else {
                CustomToastManager.show(requireContext(), getString(R.string.turn_on_wifi), 5000)
            }
        } else if (demoItem.connectType == BluetoothService.GattConnectType.WIFI_THROUGHPUT_TEST) {
            if (isNetworkAvailable(context)) {
                requireContext().startActivity(
                    Intent(
                        requireContext(),
                        WifiThroughputActivity::class.java
                    )
                )
            } else {
                CustomToastManager.show(requireContext(), getString(R.string.turn_on_wifi), 5000)
            }
        } else if (demoItem.connectType == BluetoothService.GattConnectType.WIFI_PROVISIONING) {
            if (isNetworkAvailable(context)) {
                requireContext().startActivity(
                    Intent(
                        requireContext(),
                        WiFiProvisioningActivity::class.java
                    )
                )
            } else {
                CustomToastManager.show(requireContext(), getString(R.string.turn_on_wifi), 5000)
            }
        } else if (demoItem.connectType == BluetoothService.GattConnectType.CHANNEL_SOUNDING_DEMO) {
            // Channel Sounding Demo - Show mode selection dialog (Phase 2)
            val csSupported =
                ChannelSoundingDetectorUtils.isBleChannelSoundingSupported(requireContext())
            if (!csSupported) {
                CustomToastManager.showError(
                    requireContext(),
                    "Channel Sounding Demo is not supported in this Device"
                )
                return
            }
            showChannelSoundingModeDialog()
        } else {
            BLEUtils.GATT_DEVICE_SELECTED = demoItem.connectType
            // Prevent multiple dialogs
            val existingDialog = childFragmentManager.findFragmentByTag("select_device_dialog")
            if (existingDialog == null || !existingDialog.isVisible) {
                selectDeviceDialog = SelectDeviceDialog.newDialog(
                    demoItem.connectType,
                    showProtocolIcon = demoItem is ConnectedLighting,
                )
                selectDeviceDialog?.show(childFragmentManager, "select_device_dialog")
            }
        }
    }

    /**
     * Shows the Channel Sounding mode selection dialog.
     * User can choose between Initiator mode (existing flow) or Reflector mode (Phase 2 Digital Key).
     */
    private fun showChannelSoundingModeDialog() {
        val existingDialog = childFragmentManager.findFragmentByTag(ChannelSoundingModeDialog.TAG)
        if (existingDialog != null && existingDialog.isVisible) {
            return // Dialog already showing
        }

        val modeDialog = ChannelSoundingModeDialog.newInstance(object : ChannelSoundingModeDialog.Callback {
            override fun onInitiatorModeSelected() {
                // Continue with existing initiator flow
                BLEUtils.GATT_DEVICE_SELECTED = BluetoothService.GattConnectType.CHANNEL_SOUNDING_DEMO
                selectDeviceDialog = SelectDeviceDialog.newDialog(BluetoothService.GattConnectType.CHANNEL_SOUNDING_DEMO)
                selectDeviceDialog?.show(childFragmentManager, "select_device_dialog")
            }

            override fun onReflectorModeSelected() {
                openReflectorDashboardOrStartScan()
            }

            override fun onCancelled() {
                // User cancelled - do nothing
            }
        })
        modeDialog.show(childFragmentManager, ChannelSoundingModeDialog.TAG)
    }

    /**
     * Returning users skip scan/pairing and open the dashboard with the saved Digital Key device.
     * First-time users go through [SelectDeviceDialog] and [ReflectorPairingDialog].
     */
    private fun openReflectorDashboardOrStartScan() {
        val context = requireContext()
        val paired = ReflectorSessionPreferences.getPairedDevice(context)
        if (paired != null && ReflectorSessionPreferences.hasActivePairedSession(context)) {
            startActivity(
                ReflectorDashboardActivity.createIntent(context, paired.first, paired.second)
            )
            return
        }
        if (paired != null) {
            ReflectorSessionPreferences.clearPairedDevice(context)
        }
        startReflectorModeScan()
    }

    /**
     * Starts scanning for CS Initiator devices for Reflector mode (Phase 2).
     * Uses the existing SelectDeviceDialog to scan for "Silabs Example" devices.
     * When a device is selected, shows ReflectorPairingDialog for the 4-step pairing flow.
     */
    private fun startReflectorModeScan() {
        // Use the existing SelectDeviceDialog with reflector mode enabled
        val existingDialog = childFragmentManager.findFragmentByTag("select_device_dialog")
        if (existingDialog == null || !existingDialog.isVisible) {
            selectDeviceDialog = SelectDeviceDialog.newReflectorDialog()
            selectDeviceDialog?.show(childFragmentManager, "select_device_dialog")
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        selectDeviceDialog = null
    }

    @Throws(UnknownHostException::class)
    private fun getLocalIpAddress(): String? {
        val wifiManager =
            (requireContext().getSystemService(Context.WIFI_SERVICE) as WifiManager)
        val wifiInfo = wifiManager.connectionInfo
        val ipInt = wifiInfo.ipAddress
        return InetAddress.getByAddress(
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ipInt).array()
        ).hostAddress
    }

    private fun isNetworkAvailable(context: Context?): Boolean {
        if (context == null) return false
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities != null) {
                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                        return true
                    }
                }
            }
        } else {
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            if (activeNetworkInfo != null && activeNetworkInfo.isConnected) {
                return true
            }
        }
        return false
    }

    private val fileSelectionListener = object : WiFiOtaFileSelectionDialog.FileSelectionListener {
        override fun onSelectFileButtonClicked() {
            otaFileSelectionDialog?.disableUploadButton()
            Intent(Intent.ACTION_GET_CONTENT)
                .apply { type = "*/*" }
                .also {
                    startActivityForResult(
                        Intent.createChooser(
                            it,
                            getString(R.string.ota_choose_file)
                        ),
                        RPS_FILE_CHOICE_REQUEST_CODE
                    )
                }
        }

        override fun onOtaButtonClicked() {
            if (otaFileSelectionDialog?.checkPortNumberValid() == true) {
                otaFileManager?.otaFile?.let {
                    startOtaProcess()
                } ?: if (otaFileManager?.otaFilename != null) {
                    CustomToastManager.show(
                        requireContext(),
                        getString(R.string.incorrect_file),
                        5000
                    )

                } else {
                    CustomToastManager.show(
                        requireContext(),
                        getString(R.string.no_file_chosen),
                        5000
                    )

                }
            } else {
                CustomToastManager.show(
                    requireContext(),
                    getString(R.string.port_id_validation),
                    5000
                )

            }
        }

        override fun onCancelButtonClicked() {
            otaFileSelectionDialog?.dismiss()
            otaFileSelectionDialog = null
        }
    }

    private fun initOtaProgressDialog() {
        otaProgressDialog = WiFiOtaProgressDialog(requireContext())
        initializeToDefaultValues()
        otaProgressDialog?.btnOtaEnd?.setOnClickListener {
            initializeToDefaultValues()
            otaProgressDialog?.dismiss()
            socket?.close()
            serverSocket?.close()
        }
        otaProgressDialog?.btnCancel?.setOnClickListener {
            initializeToDefaultValues()
            otaProgressDialog?.dismiss()
            socket?.close()
            serverSocket?.close()
        }
    }

    private fun initializeToDefaultValues() {
        isClientConnected = false
        otaProgressDialog?.progressBar?.progress = 0
        otaProgressDialog?.dataRate?.visibility = View.INVISIBLE
        otaProgressDialog?.dataSize?.text = getString(R.string.iop_test_n_percent, 0)
        otaProgressDialog?.steps?.visibility = View.INVISIBLE
//        otaProgressDialog?.btnOtaEnd?.isEnabled = false
        (otaProgressDialog?.btnOtaEnd as? MaterialButton)?.backgroundTintList =
            ColorStateList.valueOf(getColor(requireContext(), R.color.silabs_redtheme_button_fill_color))
        otaProgressDialog?.btnOtaEnd?.text = getString(R.string.button_cancel)
        otaProgressDialog?.firmwareStatus?.text = getString(R.string.waiting_for_client_to_connect)
        otaProgressDialog?.firmwareStatus?.setTextColor(
            getColor(
                requireContext(),
                R.color.silabs_redtheme_button_fill_color
            )
        )
    }

    private fun showOtaProgressDialog() {
        otaProgressDialog?.show()
    }

    private fun startOtaProcess() {
        activity?.runOnUiThread {
            showOtaProgressDialog()
            portId = Integer.parseInt(otaFileSelectionDialog?.getPortId())
            otaProgressDialog?.setProgressInfo(
                otaFileManager?.otaFilename,
                otaFileManager?.otaFile?.size,
                getLocalIpAddress(),
                portId.toString()
            )

        }
        //Start OTA_data Upload in another thread
        val otaUpload = Thread {

            try {
                serverSocket = ServerSocket(portId)
                serverSocket?.receiveBufferSize = RECEIVER_BUFFER_SIZE
//                serverSocket.soTimeout = 60000
                while (true) {
                    println("Waiting for client to connect")
                    socket = serverSocket?.accept()
                    println("Accept success")

                    Thread {
                        otaFilePath?.let {
                            getInputStreamFromUri(requireContext(), it)?.let {
                                processRequest(it)
                            }
                        }
                        socket?.close() //close socket
                    }.start()
                }
            } catch (e: SocketTimeoutException) {
                showTimeOutError()
                e.printStackTrace()
            } catch (e: IOException) {
                socket?.close()
                serverSocket?.close()
                e.printStackTrace()
            }

        }
        otaUpload.start()
    }

    private fun showTimeOutAlertDialog() {
        activity?.runOnUiThread {
            alertErrorDialog = AlertErrorDialog(object : AlertErrorDialog.OtaErrorCallback {
                override fun onDismiss() {
                    initializeToDefaultValues()
                    otaProgressDialog?.dismiss()
                    socket?.close()
                    serverSocket?.close()
                }
            })

            alertErrorDialog?.show(childFragmentManager, "error_dialog")
        }
    }

    private fun showTimeOutError() {
        activity?.runOnUiThread {
            if (!isClientConnected) {
                otaProgressDialog?.firmwareStatus?.text = getString(R.string.server_timeout)
                otaProgressDialog?.firmwareStatus?.setTextColor(
                    getColor(
                        requireContext(),
                        R.color.silabs_primary_color
                    )
                )
//                otaProgressDialog?.btnOtaEnd?.isEnabled = true
            }
        }
    }

    private fun getInputStreamFromUri(context: Context, uri: Uri): FileInputStream? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                // Optionally, you may want to copy the content to a temporary file
                // if you need a FileInputStream specifically
                val tempFile = createTempFileFromInputStream(inputStream, context.cacheDir)
                return FileInputStream(tempFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun createTempFileFromInputStream(inputStream: InputStream, cacheDir: File): File {
        val tempFile = File.createTempFile("temp", null, cacheDir)
        tempFile.deleteOnExit()
        FileOutputStream(tempFile).use { fileOutputStream ->
            inputStream.copyTo(fileOutputStream)
        }
        return tempFile
    }

    private fun processRequest(fp: FileInputStream): Int {
        try {
            println("processRequest")
            var ctr = 0
            var retLen: Int
            val txLen = 0
            val data = ByteArray(3)
            val data1 = ByteArray(1503) // 1500 + 3
            var cmdType: Byte
            var length = 0
            val outputStream = socket!!.getOutputStream()
            val inputStream = socket!!.getInputStream()
            val totalPackets =
                Integer.parseInt(otaFileManager?.otaFile?.size?.div(1024).toString()) + 1
            while (true) {
                retLen = inputStream.read(data, 0, 3)
                if (retLen > 0) {
                    cmdType = data[0]
                    if (cmdType == RPS_HEADER) {
                        length = fp.read(data1, 3, 64)
                        data1[0] = RPS_HEADER
                        data1[1] = (length and 0xff).toByte()
                        data1[2] = (length shr 8 and 0xff).toByte()
                        fp.channel.position(0)
                    } else if (cmdType == RPS_DATA) {
                        length = fp.read(data1, 3, 1024)
                        data1[0] = RPS_DATA
                        data1[1] = (length and 0x00ff).toByte()
                        data1[2] = (length shr 8 and 0x00ff).toByte()
                        if (length == -1) {
                            fp.close()
                            outputStream.write(data1, 0, length + 3)
                            length = 0
                            data1[0] = RPS_DATA
                            data1[1] = (length and 0x00ff).toByte()
                            data1[2] = (length shr 8 and 0x00ff).toByte()
                            outputStream.write(data1, 0, length + 3)
                            outputStream.flush()
                            outputStream.close()
                            return ctr
                        }
                    }
                } else if (length == 0) {
                    fp.close()
                    socket!!.close()
                    outputStream.write(data1, 0, length + 3)
                    return ctr
                }
                println("Size of data: $length")
                outputStream.write(data1, 0, length + 3)
                println("Send returns: $txLen")
                if (txLen != 0) {
                    println("Error while sending")
                    return 0
                }
                //Update the Progress Dialog UI
                updateProgressDialog(ctr++, totalPackets)
                packetCount = ctr
                if (ctr < totalPackets)
                    checkTimeOut(ctr)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return 0
        }

    }

    private fun checkTimeOut(ctr: Int) {
        Timer().schedule(object : TimerTask() {
            override fun run() {
                if (packetCount == ctr)
                    showTimeOutAlertDialog()
            }
        }, 30000)
    }

    private fun updateProgressDialog(packetCount: Int, totalPackets: Int) {
        val percentageSent = (packetCount * 100 / totalPackets)
        isClientConnected = true
        activity?.runOnUiThread {
            otaProgressDialog?.progressBar?.progress = percentageSent
            otaProgressDialog?.dataRate?.visibility = View.INVISIBLE
            val datarate = String.format(Locale.US, kBits, 1.024F)
            otaProgressDialog?.dataRate?.text = datarate
            otaProgressDialog?.dataSize?.text =
                getString(R.string.iop_test_n_percent, percentageSent)
            otaProgressDialog?.steps?.visibility = View.VISIBLE
            otaProgressDialog?.steps?.text =
                getString(R.string.ota_count_test_label, packetCount, totalPackets)
            otaProgressDialog?.firmwareStatus?.text = getString(R.string.client_connected)
            otaProgressDialog?.firmwareStatus?.setTextColor(
                getColor(
                    requireContext(),
                    R.color.silabs_primary_color
                )
            )

            if (packetCount == totalPackets) {
//                otaProgressDialog?.btnOtaEnd?.isEnabled = true
                otaProgressDialog?.btnOtaEnd?.text = getString(R.string.done)
                (otaProgressDialog?.btnOtaEnd as? MaterialButton)?.backgroundTintList =
                    ContextCompat.getColorStateList(
                        requireContext(),
                        R.color.dialog_positive_button_selector
                    )
                otaProgressDialog?.firmwareStatus?.text =
                    getString(R.string.firmware_update_completed)
                otaProgressDialog?.firmwareStatus?.setTextColor(
                    getColor(
                        requireContext(),
                        R.color.silabs_green
                    )
                )
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        when (requestCode) {

            RPS_FILE_CHOICE_REQUEST_CODE -> {
                intent?.data?.let {
                    otaFilePath = it
                    otaFileManager?.readFilename(it)
                    otaFileSelectionDialog?.changeFileName(otaFileManager?.otaFilename)
                    if (otaFileManager?.hasCorrectFileExtensionRPS() == true) {
                        otaFileManager?.readFile(it).toString()
                        otaFileSelectionDialog?.enableUploadButton()
                    } else {
                        CustomToastManager.show(
                            requireContext(),
                            getString(R.string.incorrect_file),
                            5000
                        )
                    }
                } ?: CustomToastManager.show(
                    requireContext(),
                    getString(R.string.chosen_file_not_found),
                    5000
                )

            }
        }
    }

    companion object {
        private const val RECEIVER_BUFFER_SIZE = 2920
        private const val RPS_FILE_CHOICE_REQUEST_CODE = 202
        private const val RPS_HEADER: Byte = 0x01
        private const val RPS_DATA: Byte = 0x00

        @Volatile
        private var pendingReflectorPairingFlow: Boolean = false

        fun requestReflectorPairingOnNextResume() {
            pendingReflectorPairingFlow = true
        }
    }
}