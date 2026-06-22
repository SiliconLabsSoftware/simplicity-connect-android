package com.siliconlabs.bledemo.home_screen.dialogs

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.databinding.DialogDeviceInforamtionBinding
import com.siliconlabs.bledemo.utils.DisplayUtils


class DeviceInformationDialog : DialogFragment() {
    private lateinit var binding: DialogDeviceInforamtionBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = DialogDeviceInforamtionBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val dialog: Dialog? = dialog
        if (dialog != null) {
            dialog.setCanceledOnTouchOutside(false)
            dialog.window!!
                .setLayout(
                    (DisplayUtils.getScreenWidth(requireActivity()) * DIA_WINDOW_SIZE).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonClose.setOnClickListener { dismiss() }
        //Device Information
        binding.deviceNameInfo.text = getDeviceName()
        binding.androidVersionInfo.text = getAndroidVersion()
        binding.modelInfo.text = getModel()
        binding.manufacturerInfo.text = getManufacturer()
        binding.boardInfo.text = getBoard()
        binding.productInfo.text = getProduct()
        binding.buildVersionInfo.text = getBuildVersionInfo()
        //Bluetooth Low Energy Supported
        val bleSupported =
            activity?.let { getBluetoothLowEnergySupported() }
        if (bleSupported != null) {
            setTextColor(bleSupported, binding.bluetoothLowEnergySupportedInfo)
        }
        //Native HID Supported
        val bleNativeHIDSupported =
            activity?.let { getNativeHIDSupported() }
        if (bleNativeHIDSupported != null) {
            setTextColor(bleNativeHIDSupported, binding.nativeHidSupportedInfo)
        }
        //Lollipop scanner API supported
        val lollipopAPISupported = activity?.let { getLollipopScannerAPISupported() }
        if (lollipopAPISupported != null) {
            setTextColor(lollipopAPISupported, binding.lollipopScannerApiSupportedInfo)
        }

        val offLoadedFilteringSupport = activity?.let { isOffloadedFilteringSupported() }
        if (null != offLoadedFilteringSupport) {
            setTextColor(offLoadedFilteringSupport, binding.tvShowOffloadFilter)
        }

        val offLoadedScanBatchingSupported = activity?.let { isOffloadedScanBatchingSupported() }
        if (null != offLoadedScanBatchingSupported) {
            setTextColor(offLoadedScanBatchingSupported, binding.tvShowOffloadScanBatch)
        }

        val isPeripheralModeSupported = activity?.let { isPeripheralSupported() }
        if (null != isPeripheralModeSupported) {
            //tv_show_per_mode_supp
            setTextColor(isPeripheralModeSupported, binding.tvShowPerModeSupp)
        }

        val isMultipleAdvertisementSupported = activity?.let { isMultipleAdvertisementSupported() }
        if (null != isMultipleAdvertisementSupported) {
            //tv_show_mult_mode_adv
            setTextColor(isMultipleAdvertisementSupported, binding.tvShowMultModeAdv)
        }
        val isPhy2MSupported = activity?.let { isPhy2MSupported(requireContext()) }

        if (isPhy2MSupported != null) {
            //tv_show_high_speed
            setTextColor(isPhy2MSupported, binding.tvShowHighSpeed)
        }
        val isPhyCodedSupportAvailable = activity?.let { isPhyCodedSupported(requireContext()) }
        if (null != isPhyCodedSupportAvailable) {
            //tv_show_long_range
            setTextColor(isPhyCodedSupportAvailable, binding.tvShowLongRange)
        }

        val isPeriodicAdvertisementSupported =
            activity?.let { isPeriodicAdvertisementSupported(requireContext()) }
        //tv_show_periodic_adv
        if (null != isPeriodicAdvertisementSupported) {
            setTextColor(isPeriodicAdvertisementSupported, binding.tvShowPeriodicAdv)
        }

        val isExtendedAdvertisementSupported =
            activity?.let { isExtendedAdvertisementSupported(requireContext()) }
        if (null != isExtendedAdvertisementSupported) {
            //tv_show_extended_adv
            setTextColor(isExtendedAdvertisementSupported, binding.tvShowExtendedAdv)
        }

        val isMaxAdvDataLengthSupported = activity?.let { getMaxAdvertisingDataLength() }
        if (null != isMaxAdvDataLengthSupported) {
            //maximum_advertising_data_length_info
            binding.maximumAdvertisingDataLengthInfo.text = isMaxAdvDataLengthSupported
        }

        //Bluetooth Audio (only on Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val bleAudioSupported =
                activity?.let {
                    isLEAudioSupported(it)
                }
            if (bleAudioSupported != null) {
                setTextColor(bleAudioSupported, binding.leAudioSupportedInfo)
            }

            val bleAudioBroadcastSupport =
                activity?.let { isLEAudioBroadcastSupported(it) }
            if (bleAudioBroadcastSupport != null) {
                setTextColor(bleAudioBroadcastSupport, binding.leAudioBroadcastSourceSupportedInfo)

            }

            val bleAudioBroadcastAssistSupported =
                activity?.let { isLEAudioBroadcastAssistSupported(it) }
            if (bleAudioBroadcastAssistSupported != null) {
                setTextColor(
                    bleAudioBroadcastAssistSupported,
                    binding.leAudioBroadcastAssistSupportedInfo
                )
            }
            binding.maxConnectAudioDeviceInfo.text = getBluetoothMaxConnection()
        } else {
            // For devices below Android 13, show "Not Supported"
            binding.leAudioSupportedInfo.text = "API < 33"
            binding.leAudioBroadcastSourceSupportedInfo.text = "API < 33"
            binding.leAudioBroadcastAssistSupportedInfo.text = "API < 33"
            binding.maxConnectAudioDeviceInfo.text = "N/A"
        }


        //Screen
        binding.resolutionInfo.text = getResolutionType()
        binding.dimensionPxInfo.text = getDimensionPixel()
        binding.dimensionDipInfo.text = getDimensionDip()
        //binding.wideColorGamutInfo.text =
        val wideColorGamut = activity?.let {
            isWideColorGamutSupported(requireContext())

        }
        if (null != wideColorGamut) {
            binding.wideColorGamutInfo.text = if (wideColorGamut) "Supported" else "Not Supported"
        }
        binding.highDynamicRangeInfo.text =
            if (isHRDSupport()) "Supported" else "Not Supported"
        binding.sizeInfo.text = activity?.let { screenSupport(it) }
        binding.aspectInfo.text = if (isAspectLongSupported() == true) "Long" else "Not Long"
    }

    @SuppressLint("ResourceAsColor")
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun isLEAudioSupported(activity: Activity): String {
        val bluetoothManager =
            activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val leAudioSupported = bluetoothAdapter.isLeAudioSupported

        val res = if (leAudioSupported == FEATURE_SUPPORTED) { //10 LE Supported
            activity.getString(R.string.le_audio_supported_yes)
        } else {
            activity.getString(R.string.le_audio_supported_no)
        }
        return res.uppercase()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun isLEAudioBroadcastSupported(activity: Activity): String {
        val bluetoothManager =
            activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val leAudioSupported = bluetoothAdapter.isLeAudioSupported
        val leAudioBroadcast = bluetoothAdapter.isLeAudioBroadcastSourceSupported

        val res =
            if (leAudioSupported == FEATURE_SUPPORTED && leAudioBroadcast == FEATURE_SUPPORTED) {
                activity.getString(R.string.le_audio_broadcast_source_supported_yes)
            } else {
                activity.getString(R.string.le_audio_broadcast_source_supported_no)
            }
        return res.uppercase()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun isLEAudioBroadcastAssistSupported(activity: Activity): String {
        val bluetoothManager =
            activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val leAudioSupported = bluetoothAdapter.isLeAudioSupported
        val leAudioBroadcastAssist = bluetoothAdapter.isLeAudioBroadcastAssistantSupported

        val res =
            if (leAudioSupported == FEATURE_SUPPORTED && leAudioBroadcastAssist == FEATURE_SUPPORTED) {
                activity.getString(R.string.le_audio_broadcast_source_supported_yes)
            } else {
                activity.getString(R.string.le_audio_broadcast_source_supported_no)
            }
        return res.uppercase()
    }

    @SuppressLint("ResourceAsColor")
    private fun setTextColor(inputString: String, txtView: TextView) {
        txtView.text = inputString
        when (inputString) {
            NO -> txtView.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_text))
            YES -> txtView.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.green_text
                )
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun getBluetoothMaxConnection(): String {

        val bluetoothManager =
            activity?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        var count = 0
        bluetoothAdapter?.let { adapter ->
            if (adapter.isEnabled) {
                try {
                    val pairedDevices = adapter.maxConnectedAudioDevices
                    count = pairedDevices
                } catch (e: SecurityException) {
                    //Handle Security
                }
            }
        }
        return count.toString()
    }

    private fun getManufacturer(): String = Build.MANUFACTURER
    private fun getDeviceName(): String = Build.MODEL.uppercase()
    private fun getAndroidVersion(): String = Build.VERSION.RELEASE
    private fun getModel(): String = Build.MODEL
    private fun getBoard(): String = Build.BOARD
    private fun getProduct(): String = Build.PRODUCT
    private fun getBuildVersionInfo(): String = Build.ID.uppercase()

    private fun getResolutionType(): String {
        val res = when (requireActivity().resources.displayMetrics.densityDpi) {
            DisplayMetrics.DENSITY_LOW -> requireContext().getString(R.string.device_information_ldpi)
            DisplayMetrics.DENSITY_MEDIUM -> requireContext().getString(R.string.device_information_mdpi)
            DisplayMetrics.DENSITY_TV, DisplayMetrics.DENSITY_HIGH -> requireContext().getString(R.string.device_information_hdpi)

            DisplayMetrics.DENSITY_260, DisplayMetrics.DENSITY_280, DisplayMetrics.DENSITY_300,
            DisplayMetrics.DENSITY_XHIGH -> requireContext().getString(R.string.device_information_xhdpi)


            DisplayMetrics.DENSITY_340, DisplayMetrics.DENSITY_360, DisplayMetrics.DENSITY_400,
            DisplayMetrics.DENSITY_420, DisplayMetrics.DENSITY_440,
            DisplayMetrics.DENSITY_XXHIGH -> requireContext().getString(R.string.device_information_xxhdpi)

            DisplayMetrics.DENSITY_560,
            DisplayMetrics.DENSITY_XXXHIGH -> requireContext().getString(R.string.device_information_xxxhdpi)

            else -> requireContext().getString(R.string.device_information_unknown)
        }
        return res.uppercase()
    }

    private fun getDimensionPixel(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = requireActivity().windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            "${bounds.width()} x ${bounds.height()}"
        } else {
            val display = requireActivity().windowManager.defaultDisplay
            val size = Point()
            display.getSize(size)
            "${size.x} x ${size.y}"
        }
    }


    private fun getDimensionDip(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = requireActivity().windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            "${pxToDp(bounds.width())} x ${pxToDp(bounds.height())}"
        } else {
            val display = requireActivity().windowManager.defaultDisplay
            val size = Point()
            display.getSize(size)
            "${pxToDp(size.x)} x ${pxToDp(size.y)}"
        }
    }

    private fun screenSupport(activity: Activity): String {
        val metrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(metrics)
        val config = resources.configuration
        val screen = config.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        val res = when (screen) {
            Configuration.SCREENLAYOUT_SIZE_SMALL -> resources.getString(R.string.device_information_screen_size_small)
            Configuration.SCREENLAYOUT_SIZE_NORMAL -> resources.getString(R.string.device_information_screen_size_normal)
            Configuration.SCREENLAYOUT_SIZE_LARGE -> resources.getString(R.string.device_information_screen_size_large)
            Configuration.SCREENLAYOUT_SIZE_XLARGE -> resources.getString(R.string.device_information_screen_size_xlarge)
            else -> resources.getString(R.string.device_information_screen_size_unknown)
        }
        return res
    }

    private fun isWideColorGamutSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val isSupported = display.isWideColorGamut
            //Log.d(TAG, "Wide Color Gamut Supported: $isSupported")
            return isSupported
        } else {
            //Log.d(TAG, "Wide Color Gamut Detection Not Supported on this Android version (API level < 26)")
            return false // Not supported on older Android versions
        }
    }

    private fun pxToDp(px: Int): Int {
        return (px / resources.displayMetrics.density).toInt()
    }

    private fun getBluetoothLowEnergySupported(): String {
        val hasBLE =
            requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val res = if (hasBLE) { //10 LE Supported
            requireActivity().getString(R.string.le_audio_supported_yes)
        } else {
            requireActivity().getString(R.string.le_audio_supported_no)
        }
        return res.uppercase()
    }

    private fun getNativeHIDSupported(): String {
        val hidSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            true
        } else {
            false
        }
        val res = if (hidSupported) {
            requireActivity().getString(R.string.le_audio_supported_yes)
        } else {
            requireActivity().getString(R.string.le_audio_supported_no)
        }
        return res.uppercase()
    }

    private fun getLollipopScannerAPISupported(): String {
        val apiSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            true
        } else {
            false
        }
        val res = if (apiSupported) {
            requireActivity().getString(R.string.le_audio_supported_yes)
        } else {
            requireActivity().getString(R.string.le_audio_supported_no)
        }
        return res.uppercase()
    }

    private fun isPeripheralSupported(): String {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        return if (adapter?.isMultipleAdvertisementSupported == true) YES else NO
    }

    private fun isMultipleAdvertisementSupported(): String {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        return if (adapter?.isMultipleAdvertisementSupported == true) YES else NO
    }

    private fun isOffloadedFilteringSupported(): String {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        return if (adapter?.isOffloadedFilteringSupported == true) YES else NO
    }

    private fun isOffloadedScanBatchingSupported(): String {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        return if (adapter?.isOffloadedScanBatchingSupported == true) YES else NO
    }

    private fun isPhy2MSupported(context: Context): String {
        return try {
            val bluetoothManager =
                context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (adapter.isLe2MPhySupported) {
                    YES
                } else {
                    NO
                }
            } else {
                "API < 26"
            }
        } catch (e: Exception) {
            //"UNKNOWN"
            resources.getString(R.string.device_information_screen_size_unknown)
        }
    }

    private fun isPhyCodedSupported(context: Context): String {
        return try {
            val bluetoothManager =
                context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (adapter.isLeCodedPhySupported) {
                    YES
                } else {
                    NO
                }
            } else {
                NA
            }
        } catch (e: Exception) {
            resources.getString(R.string.device_information_screen_size_unknown)
        }
    }

    private fun isExtendedAdvertisementSupported(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return "API < 26"
        }
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (adapter.isLeExtendedAdvertisingSupported) YES else NO
        } else {
            NA
        }
    }

    private fun isPeriodicAdvertisementSupported(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return "API < 26"
        }

        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (adapter.isLePeriodicAdvertisingSupported) YES else NO
        } else {
            "API < 26"
        }
    }

    private fun getMaxAdvertisingDataLength(): String {
        val advertiser = BluetoothAdapter.getDefaultAdapter()?.bluetoothLeAdvertiser
        return if (advertiser != null) {
            // Extended advertising max length is 1650 as per spec
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && BluetoothAdapter.getDefaultAdapter().isLeExtendedAdvertisingSupported) {
                "1650"
            } else {
                "31"
            }
        } else {
            UNKNOWN
        }
    }

    private fun isHRDSupport(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }
        val dm = requireActivity().getSystemService(DisplayManager::class.java) ?: return false
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return false
        // Display.Mode.supportedHdrTypes exists from API 34; on API 24–33 use HdrCapabilities.
        val hdrTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            display.mode?.supportedHdrTypes
        } else {
            display.hdrCapabilities?.supportedHdrTypes
        }
        return hdrTypes?.isNotEmpty() == true
    }

    private fun isAspectLongSupported(): Any {
        val width: Int
        val height: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = requireActivity().windowManager.currentWindowMetrics.bounds
            width = bounds.width()
            height = bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            requireActivity().windowManager.defaultDisplay.getMetrics(metrics)
            width = metrics.widthPixels
            height = metrics.heightPixels
        }
        val aspectRatio = height.toFloat() / width.toFloat()
        val res = aspectRatio > 1.78f // Example threshold for long aspect ratio
        println("Aspect Ratio: $aspectRatio, Long Aspect Ratio Supported: $res")
        return res
    }

    companion object {
        const val NO = "NO"
        const val YES = "YES"
        const val UNKNOWN = "UNKNOWN"
        const val NA = "N/A"
        const val FEATURE_SUPPORTED = 10
        const val FEATURE_NOT_SUPPORTED = 11
        const val FEATURE_NOT_CONFIGURE = 30
        const val DIA_WINDOW_SIZE = 0.9f
        private val TAG = Companion::class.java.simpleName.toString()
    }
}