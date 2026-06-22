package com.siliconlabs.bledemo.features.demo.smartlock.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationManager
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresPermission
import androidx.core.content.edit
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.pranavpandey.android.dynamic.toasts.DynamicToast
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.base.activities.BaseDemoActivity
import com.siliconlabs.bledemo.bluetooth.ble.ConnectedDeviceInfo
import com.siliconlabs.bledemo.bluetooth.ble.GattCharacteristic
import com.siliconlabs.bledemo.bluetooth.ble.GattService
import com.siliconlabs.bledemo.bluetooth.ble.TimeoutGattCallback
import com.siliconlabs.bledemo.databinding.ActivitySmartLockDemoBinding
import com.siliconlabs.bledemo.features.configure.advertiser.services.MqttForegroundService
import com.siliconlabs.bledemo.features.configure.advertiser.services.MqttForegroundService.Companion.NOTIFICATION_ID
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.CustomProgressDialog
import com.siliconlabs.bledemo.features.demo.smartlock.dialogs.SmartLockConfigurationDialog
import com.siliconlabs.bledemo.features.demo.smartlock.dialogs.SmartLockConfigurationDialog.Companion.PICK_P12_FILE_REQUEST_CODE
import com.siliconlabs.bledemo.features.demo.smartlock.dialogs.SmartLockSelectionDialog
import com.siliconlabs.bledemo.features.demo.smartlock.models.LockState
import com.siliconlabs.bledemo.features.demo.smartlock.repository.SmartLockConnectionResult
import com.siliconlabs.bledemo.features.demo.smartlock.utils.DebouncedUpdater
import com.siliconlabs.bledemo.features.demo.smartlock.viewmodels.SmartLockAwsViewModel
import com.siliconlabs.bledemo.features.demo.smartlock.viewmodels.SmartLockBleViewModel
import com.siliconlabs.bledemo.features.demo.wifi_commissioning.activities.WifiCommissioningActivity
import com.siliconlabs.bledemo.utils.AppUtil
import com.siliconlabs.bledemo.utils.BLEUtils
import com.siliconlabs.bledemo.utils.GattQueue
import com.siliconlabs.bledemo.utils.Notifications
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.InputStream
import java.security.KeyStore
import java.util.LinkedList
import java.util.UUID
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import kotlin.collections.set

class SmartLockActivity : BaseDemoActivity(),
    SmartLockSelectionDialog.SmartLockOptionSelectedListener {
    private lateinit var filePickedUri: Uri
    private lateinit var backPressedCallback: OnBackPressedCallback
    private lateinit var binding: ActivitySmartLockDemoBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var connectionType: String
    private var smartLockPublishTopic = ""
    private var smartLockSubscribeTopic = ""
    private lateinit var smartLockMqttUrl: Uri
    private var smartLockMqttPassword: String? = ""
    private var smartLockMqttCert = ""
    private var customProgressDialog: CustomProgressDialog? = null
    private var fragmentRefreshListener: SmartLockImageRefreshClickListener? = null
    private var smartLockSelectionDialog: SmartLockSelectionDialog? = null
    private var connectType: String = AWS_CONNECTION
    private val processor = GattProcessor()
    private lateinit var viewModelBle: SmartLockBleViewModel
    private var serviceIsSet: Boolean = false
    private var characteristicWrite: BluetoothGattCharacteristic? = null
    private var characteristicRead: BluetoothGattCharacteristic? = null
    private var characteristicNotification: BluetoothGattCharacteristic? = null
    private lateinit var viewModelAws: SmartLockAwsViewModel
    private lateinit var gattQueue: GattQueue
    private var connProcess = false
    private var certSelectionDialog: SmartLockConfigurationDialog? = null
    private var smartLockFilePath: String? = null
    private var connDevice: List<ConnectedDeviceInfo>? = null
    private val pendingWrites = mutableMapOf<UUID, CompletableDeferred<Boolean>>()
    private lateinit var awsLockUiDebouncer: DebouncedUpdater<Boolean>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySmartLockDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        DynamicToast.Config.getInstance().setTextSize(18)
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        AppUtil.setEdgeToEdge(window, this)
        setSupportActionBar(binding.toolbar)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.matter_back)
            actionBar.setDisplayHomeAsUpEnabled(true)
        }
        binding.awsPlaceHolder.visibility = View.VISIBLE
        viewModelBle = ViewModelProvider(this).get(SmartLockBleViewModel::class.java)
        viewModelAws = ViewModelProvider(this).get(SmartLockAwsViewModel::class.java)
        awsLockUiDebouncer = DebouncedUpdater(
            lifecycleScope,
            LOCK_UI_DEBOUNCE_MS,
            LOCK_COMMAND_SUPPRESS_MS
        )
        initSmartLockAWSObservers()
        initBleConnection()
        setupToolbarIconListener()
        backKeyHandling()
    }

    private fun backKeyHandling() {
        // Register the back press callback
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Timber.tag(TAG)
                    .d("S---- Back button pressed with OnBackPressedDispatcher!!!")
                if (viewModelAws != null) {
                    viewModelAws.disconnect()
                }
            }
        }
        // Add callback to the dispatcher
        onBackPressedDispatcher.addCallback(
            this,
            backPressedCallback
        )
    }

    private fun setupToolbarIconListener() {
        binding.smartLockConfigure.setOnClickListener {
            if (!isMqttConfigValid()) {
                initSmartLockConfigure()
            } else {
                showAwsAlreadyConfiguredAlert()
            }
        }
    }

    private fun initSmartLockConfigure() {
        certSelectionDialog = SmartLockConfigurationDialog(
            this, getString(R.string.smart_lock_alert_config_title),
            listener = fileSelectionListener
        ).also {
            it.show(supportFragmentManager, "SmartLockConfigurationDialog")
        }
    }

    private val fileSelectionListener =
        object : SmartLockConfigurationDialog.FileSelectionListener {
            override fun onSelectFileButtonClicked() {
                Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .apply {
                        type = "application/x-pkcs12"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }.also {
                        startActivityForResult(
                            Intent.createChooser(
                                it,
                                getString(R.string.ota_choose_file)
                            ), SmartLockConfigurationDialog.PICK_P12_FILE_REQUEST_CODE
                        )
                    }
            }

            override fun onCancelButtonClicked() {
                if (certSelectionDialog != null && certSelectionDialog?.isShowing() == true) {
                    certSelectionDialog?.dismiss()
                }
            }

            override fun onConnectButtonClicked(
                uriPath: Uri?,
                password: String?,
                endPoint: String,
                subcribeTopic: String,
                pubTopic: String
            ) {
                Timber.tag(TAG).d("SmartLockActivity: onConnectButtonClicked: $connectType")
                Timber.tag(TAG)
                    .d("SmartLockActivity: smartLockPublishTopic: $smartLockPublishTopic")
                smartLockSubscribeTopic = subcribeTopic
                smartLockPublishTopic = pubTopic
                if (uriPath != null) {
                    smartLockMqttUrl = uriPath
                }
                smartLockMqttPassword = password
                smartLockMqttCert = endPoint
                showSmartLockProgressDialog(getString(R.string.demo_connection_progress_dialog_loading))
                processP12File(uriPath, endPoint, password)
                if (certSelectionDialog != null && certSelectionDialog?.isShowing() == true) {
                    certSelectionDialog?.dismiss()
                }
            }

        }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        when (requestCode) {
            PICK_P12_FILE_REQUEST_CODE -> {
                intent?.data?.let {
                    filePickedUri = it
                    //smartLockFilePath = it
                    Timber.tag(TAG).d("Selected file path: $smartLockFilePath")
                    if (certSelectionDialog != null) {
                        smartLockFilePath = certSelectionDialog?.readFilename(it)
                        certSelectionDialog?.uriSelected(it)
                        certSelectionDialog?.changeFileName(
                            smartLockFilePath
                        )
                    }
                    //Timber.tag(TAG).d("Selected file path: ${smartLockFilePath?.path}")
                } ?: run {
                    Timber.tag(TAG).e("No file selected or intent data is null.")
                }
            }
        }
    }

    private fun processP12File(
        fileUri: Uri?,
        endPoint: String,
        certificatePassword: String?
    ): SSLContext? {
        val inputStream: InputStream? = contentResolver?.openInputStream(fileUri!!)
        return inputStream?.use { stream ->
            try {
                val keyStore = KeyStore.getInstance("PKCS12")
                val password = certificatePassword?.toCharArray() // Replace with actual password
                keyStore.load(stream, password)

                val keyManagerFactory =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                keyManagerFactory.init(keyStore, password)

                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(keyManagerFactory.keyManagers, null, null)
                println("SSLContext ${sslContext.toString()}")
                if (isNetworkAvailable(this)) {
                    runOnUiThread {
                        DynamicToast.makeSuccess(
                            this,
                            getString(R.string.smart_lock_device_connected),
                            5000
                        ).show()
                    }
                    viewModelAws.connect(smartLockSubscribeTopic, endPoint, sslContext)
                    sslContext

                } else {
                    runOnUiThread {
                        DynamicToast.makeError(
                            this,
                            getString(R.string.smart_lock_internet_not_available),
                            5000
                        ).show()
                    }
                    removeSmartLockProgress()
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Timber.tag(TAG).e("Error reading the .p12 file: $e")
                clearSmartLockConfiguration()
                runOnUiThread {
                    DynamicToast.makeError(this, "Error reading the .p12 file: $e", 5000).show()
                }
                removeSmartLockProgress()
                null
            }
        }
    }

    private fun clearSmartLockConfiguration() {
        Timber.tag(TAG).d("Clearing Smart Lock Configuration")
        smartLockMqttUrl = Uri.EMPTY
        smartLockMqttPassword = ""
        smartLockMqttCert = ""
        smartLockSubscribeTopic = ""
        smartLockPublishTopic = ""
    }

    private fun promptUserOptionToControlSmartLock() {
        removeSmartLockProgress()
        if (!this.isFinishing && !this.isDestroyed) {
            runOnUiThread {
                smartLockSelectionDialog = SmartLockSelectionDialog(
                    object : SmartLockSelectionDialog.CancelCallback {
                        override fun onDismiss() {
                            if (smartLockSelectionDialog?.isAdded == true && smartLockSelectionDialog?.isShowing() == true) {
                                smartLockSelectionDialog?.dismiss()
                                smartLockSelectionDialog = null
                            }
                        }
                    }, this@SmartLockActivity
                ).also {
                    it.show(this.supportFragmentManager, "SmartLockSelectionDialog")
                }
            }
        }
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


    override fun onBluetoothServiceBound() {

        service?.registerGattCallback(true, processor)
//        service?.registerGattCallback(mBluetoothGattCallback)
        service?.getActiveConnections()?.let { connDev ->
            if (connDev.isNotEmpty()) {
                val dev = connDev[0].connection.gatt
                if (gatt == null) {
                    gatt = dev
                    gattQueue = GattQueue(gatt)
                    lifecycleScope.launch {
                        gatt?.discoverServices()
                    }
                    service?.refreshGattServices(service?.connectedGatt)
                }
            } else {
                Timber.tag(TAG).e("No active connections found.")
            }
        } ?: Timber.tag(TAG).e("Service is null or has no active connections.")

    }

    fun hideConfigureButton() {
        binding.smartLockConfigure.visibility = View.GONE
    }

    fun showConfigureButton() {
        binding.smartLockConfigure.visibility = View.VISIBLE
    }

    fun isMqttConfigValid(): Boolean {
        return smartLockSubscribeTopic.isNotBlank() && smartLockPublishTopic.isNotBlank() &&
                smartLockMqttUrl.toString()
                    .isNotBlank() && smartLockMqttPassword?.isNotBlank() == true && smartLockMqttCert.isNotBlank()
    }


    private fun initSmartLockAWSObservers() {
        viewModelAws.smartLockAwsConnectionResult.observe(this, Observer { result ->
            println("SmartLockActivities: Connection Result: $result")
            when (result) {

                is SmartLockConnectionResult.AWSConnected -> {
                    if (!connProcess) {
                        connProcess = true
                        binding.placeholder.visibility = View.GONE
                        binding.smartLockPlaceHolder.visibility = View.VISIBLE
                        runOnUiThread {
                            DynamicToast.make(this, getString(R.string.connection_successful), 3000)
                                .show()
                        }
                        connProcess = false
                    }
                }

                is SmartLockConnectionResult.AWSConnectError -> {
                    removeSmartLockProgress()
                    // Handle the error
                    Timber.tag(TAG).e("Connection Error: ${result.message}")
                    result.throwable?.printStackTrace() // Print the stack trace for debugging
                    clearSmartLockConfiguration()
                    if (getFragmentRefreshListener() != null) {
                        getFragmentRefreshListener()?.onSmartLockAWSConfigured()
                    } else {
                        Timber.tag(TAG).e("Fragment refresh listener is null")
                    }
                    runOnUiThread {
                        if (result.message.contains(
                                getString(R.string.publish_failed),
                                false
                            ) or result.message.contains(
                                getString(
                                    R.string.subscription_failed
                                ), false
                            )
                        ) {
                            removeSmartLockProgress()
                            DynamicToast.makeError(this, result.message, 5000).show()
                            //showSmartLockMqttTopicDialog()
                        } else {
                            DynamicToast.makeError(this, result.message, 5000).show()
                        }
                    }
                }

                SmartLockConnectionResult.AWSSubscribed -> {
                    runOnUiThread {
                        DynamicToast.make(this, "Subscribed to $smartLockSubscribeTopic", 3000)
                            .show()
                    }
                    if (getFragmentRefreshListener() != null) {
                        getFragmentRefreshListener()?.onSmartLockAWSConfigured()
                    } else {
                        Timber.tag(TAG).e("Fragment refresh listener is null")
                    }
                    removeSmartLockProgress()
                    binding.placeholder.visibility = View.GONE
                    binding.smartLockPlaceHolder.visibility = View.VISIBLE

                    onAWSSmartLockWakeUpCmd()

                    viewModelAws.smartLockAwsMessageLiveData.observe(this, Observer { message ->
                        if (connectType != AWS_CONNECTION) return@Observer
                        removeSmartLockProgress()
                        binding.placeholder.visibility = View.GONE
                        binding.smartLockPlaceHolder.visibility = View.VISIBLE
                        val payload = message.orEmpty().trim()
                        if (payload.isEmpty()) return@Observer
                        if (isAwsMqttCommandEcho(payload)) {
                            Timber.tag(TAG).d("AWS MQTT ignored echo: $payload")
                            return@Observer
                        }
                        Timber.tag(TAG).d("AWS MQTT status: $payload")
                        val unlocked = parseAwsLockUnlocked(payload) ?: return@Observer
                        refreshAwsLockUi(unlocked)
                    })
                }

                SmartLockConnectionResult.AWSPublished -> {
                    binding.placeholder.visibility = View.GONE
                    binding.smartLockPlaceHolder.visibility = View.VISIBLE
                    removeSmartLockProgress()
                }

                SmartLockConnectionResult.AWSDisconnected -> {
                    removeSmartLockProgress()
                }


                else -> {}
            }

        })
    }


    private fun showSmartLockProgressDialog(message: String) {
        if (!this.isFinishing && !this.isDestroyed) {
            runOnUiThread {
                customProgressDialog = CustomProgressDialog(this)
                customProgressDialog!!.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                customProgressDialog!!.setMessage(message)
                customProgressDialog!!.show()

            }
        }
    }

    private fun removeSmartLockProgress() {
        if (!this.isFinishing && !this.isDestroyed) {
            runOnUiThread {
                if (customProgressDialog?.isShowing == true) {
                    customProgressDialog?.dismiss()
                    customProgressDialog = null
                }
            }
        }
    }

    fun awsSmartLockClose() {
        viewModelAws.disconnect()
    }


    fun areMqttTopicsValid(publishTopic: String, subscribeTopic: String): Boolean {
        // Common Validation for both topics (Non-empty, no leading/trailing slashes)
        fun isValidCommonTopic(topic: String): Boolean {
            if (topic.isBlank() || topic.startsWith("/") || topic.endsWith("/")) return false
            if (topic.split("/").any { it.isEmpty() }) return false  // No empty levels
            return true
        }

        // Validate Publish Topic (No wildcards)
        fun isValidPublishTopic(topic: String): Boolean {
            return isValidCommonTopic(topic) && !topic.contains("+") && !topic.contains("#")
        }

        // Validate Subscribe Topic (Allow wildcards with correct usage)
        fun isValidSubscribeTopic(topic: String): Boolean {
            if (!isValidCommonTopic(topic)) return false
            val levels = topic.split("/")
            for (level in levels) {
                if (level.contains("+") && level.length > 1) return false // '+' must be a single-level
                if (level.contains("#") && levels.last() != level) return false // '#' only at the end
            }
            return true
        }

        // Final Validation
        val isPublishValid = isValidPublishTopic(publishTopic)
        val isSubscribeValid = isValidSubscribeTopic(subscribeTopic)

        runOnUiThread {
            if (!isPublishValid)
                DynamicToast.make(this, "Invalid Publish Topic: $publishTopic", 3000).show()
            if (!isSubscribeValid)
                DynamicToast.make(this, "Invalid Subscribe Topic: $subscribeTopic", 3000).show()
        }

        return isPublishValid && isSubscribeValid
    }

    private fun loadSavedTopics(
        subscribeEditText: TextInputEditText,
        publishEditText: TextInputEditText
    ) {
        val savedSubscribeTopic = sharedPreferences.getString(KEY_SUBSCRIBE_TOPIC, "")
        val savedPublishTopic = sharedPreferences.getString(KEY_PUBLISH_TOPIC, "")

        if (savedPublishTopic?.isEmpty() == true && savedSubscribeTopic?.isEmpty() == true) {
            subscribeEditText.setText(resources.getString(R.string.smart_lock_subscribed_topic))
            publishEditText.setText(resources.getString(R.string.smart_lock_published_topic))
        } else {
            subscribeEditText.setText(savedSubscribeTopic)
            publishEditText.setText(savedPublishTopic)
        }
    }

    private fun saveTopics(subscribeTopic: String, publishTopic: String) {
        sharedPreferences.edit {
            putString(KEY_SUBSCRIBE_TOPIC, subscribeTopic)
            putString(KEY_PUBLISH_TOPIC, publishTopic)
        }
    }


    private fun showAwsNotConfiguredAlert() {
        AlertDialog.Builder(this).let {
            it.setTitle(getString(R.string.alert))
            it.setMessage(getString(R.string.smart_lock_alert_config_message))
            it.setCancelable(false)
            it.setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                dialog.dismiss()
            }
        }.show()
    }

    private fun showAwsAlreadyConfiguredAlert() {
        AlertDialog.Builder(this).let {
            it.setTitle(getString(R.string.alert))
            it.setMessage(getString(R.string.smart_lock_already_configured_alert))
            it.setNeutralButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            it.setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                dialog.dismiss()
                initSmartLockConfigure()
            }
        }.show()
    }

    fun onAWSSmartLockButtonClicked() {
        Timber.tag(TAG).d("onAWSSmartLockButtonClicked: $connectType")
        publishAwsControlCommand(unlocked = false)
    }

    fun onAWSSmartUnlockButtonClicked() {
        Timber.tag(TAG).d("onAWSSmartUnlockButtonClicked: $connectType")
        publishAwsControlCommand(unlocked = true)
    }

    private fun publishAwsControlCommand(unlocked: Boolean) {
        if (!isMqttConfigValid() || connectType != AWS_CONNECTION) {
            if (!isFinishing && !isDestroyed) {
                showAwsNotConfiguredAlert()
            }
            return
        }
        refreshAwsLockUi(unlocked, fromUserCommand = true)
        val command = if (unlocked) UNLOCK_CMD else LOCK_CMD
        viewModelAws.publish(topic = smartLockPublishTopic, message = command)
    }

    private fun refreshAwsLockUi(unlocked: Boolean, fromUserCommand: Boolean = false) {
        if (connectType != AWS_CONNECTION) return
        val apply: (Boolean) -> Unit = { value ->
            Timber.tag(TAG).d("AWS UI update: unlocked=$value")
            getFragmentRefreshListener()?.onAwsSmartLockRefresh(value, connectType)
        }
        if (fromUserCommand) {
            awsLockUiDebouncer.applyUserCommand(unlocked, apply)
        } else {
            awsLockUiDebouncer.scheduleIncoming(unlocked, apply)
        }
    }

    private fun isAwsMqttCommandEcho(message: String): Boolean {
        val lower = message.trim().lowercase()
        return lower.contains(" from ble") ||
            lower.contains(" from mqtt") ||
            lower.contains(" is active")
    }

    private fun parseAwsLockUnlocked(message: String): Boolean? {
        val lower = message.trim().lowercase()
        if (isAwsMqttCommandEcho(lower)) {
            return null
        }
        return when {
            lower == "query" || lower == "lock" || lower == "unlock" -> null
            lower == "unlocked" -> true
            lower == "locked" -> false
            lower.contains("\"state\"") && Regex("\\bunlocked\\b").containsMatchIn(lower) -> true
            lower.contains("\"state\"") && Regex("\\blocked\\b").containsMatchIn(lower) -> false
            Regex("\\bunlocked\\b").containsMatchIn(lower) -> true
            Regex("\\blocked\\b").containsMatchIn(lower) -> false
            else -> null
        }
    }

    private fun applyAwsLockStateFromBle(characteristic: BluetoothGattCharacteristic?) {
        if (connectType != AWS_CONNECTION) return
        val valueArray = characteristic?.value ?: return
        if (valueArray.isEmpty()) return

        val unlocked = when {
            valueArray.size == 1 -> when (valueArray[0].toInt()) {
                0 -> false
                1 -> true
                else -> null
            }
            else -> {
                val value0 = valueArray.getOrNull(0)?.toInt()
                val value1 = valueArray.getOrNull(1)?.toInt()
                val value2 = valueArray.getOrNull(2)?.toInt()
                when {
                    (value0 == 9 || value0 == 11) && value1 == 1 && value2 == 0 -> true
                    (value0 == 9 || value0 == 11) && value1 == 1 && value2 == 1 -> false
                    else -> null
                }
            }
        } ?: return

        Timber.tag(TAG).d("AWS lock state from device (BLE notify): unlocked=$unlocked")
        refreshAwsLockUi(unlocked)
    }

    fun onAWSSmartLockWakeUpCmd() {
        if (connectType == AWS_CONNECTION) {
            refreshAwsLockUi(unlocked = false, fromUserCommand = true)
            val mqttMessage = GET_STATUS
            viewModelAws.publish(topic = smartLockPublishTopic, message = mqttMessage)
        } else {
            Timber.tag(TAG).e("onAWSSmartUnlockButtonClicked: Invalid connection type $connectType")
            runOnUiThread {
                DynamicToast.make(this, "Invalid connection type: $connectType", 3000).show()
            }
        }
    }

    fun onAWSSmartLockSubscribeButtonClicked(awsSubData: String) {
        Timber.tag(TAG).d("onAWSSmartLockStatusButtonClicked: $connectType")
        if (isMqttConfigValid() && connectType == AWS_CONNECTION) {
            val mqttMessage = awsSubData
            viewModelAws.publish(topic = smartLockPublishTopic, message = mqttMessage)
        } else {
            runOnUiThread {
                if (!this.isFinishing || !this.isDestroyed) {
                    showAwsNotConfiguredAlert()
                }
            }
        }
    }


    fun getConnectionType(): String {
        return connectType
    }


    fun getFragmentRefreshListener(): SmartLockImageRefreshClickListener? {
        return fragmentRefreshListener
    }

    fun setFragmentRefreshListener(fragmentRefreshListener: SmartLockImageRefreshClickListener?) {
        this.fragmentRefreshListener = fragmentRefreshListener
    }


    private fun stopMqttForegroundService() {
        val intent = Intent(this, MqttForegroundService::class.java)
        this.stopService(intent)
    }

    private fun cancelNotification() {
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        // Unregister the back press callback
        // backPressedCallback.remove()
        // Clean up the handler to prevent memory leaks
        stopMqttForegroundService()
        cancelNotification()
        removeSmartLockProgress()
        if (customProgressDialog != null) {
            customProgressDialog = null
        }
        if (certSelectionDialog != null) {
            certSelectionDialog = null
        }
        service?.disconnectAllGatts()
        service?.clearConnectedGatt()
        service?.unregisterGattCallback()
        gatt?.close()
        if (::awsLockUiDebouncer.isInitialized) {
            awsLockUiDebouncer.cancel()
        }
        viewModelBle.resetDebouncers()
        super.onDestroy()

    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.getItemId()) {
            android.R.id.home -> {
                onBackPressed()
                closeSmartLock()
                this.finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun closeSmartLock() {
        val resultIntent = Intent()
        resultIntent.putExtra(WifiCommissioningActivity.CLOSE, true)
        setResult(RESULT_OK, resultIntent)
    }


    override fun onSmartLockOptionSelected(type: String) {
        if (::awsLockUiDebouncer.isInitialized) {
            awsLockUiDebouncer.reset()
        }
        viewModelBle.resetDebouncers()
        when (type) {
            AWS_CONNECTION -> {
                connectType = type

                getFragmentRefreshListener()?.onSmartLockConnectionTypeSelected()
                Timber.tag(TAG).d("AWS Smart Lock option selected")
                runOnUiThread {
                    DynamicToast.make(this, "AWS Smart Lock option selected", 3000).show()
                }

            }

            BLE_CONNECTION -> {
                connectType = type

                getFragmentRefreshListener()?.onSmartLockConnectionTypeSelected()
                Timber.tag(TAG).d("BLE Smart Lock option selected")
                if (!isDestroyed && !isFinishing) {
                    runOnUiThread {
                        //CustomToastManager.show(this, "BLE Smart Lock option selected", TIME_OUT_3SECS)
                        DynamicToast.make(this, "BLE Smart Lock option selected", 3000).show()
                    }
                }
            }

            else -> {
                connectType = UNKNOW_CONNECTION
                Timber.tag(TAG).e("Unknown Smart Lock option selected: $type")
                runOnUiThread {
                    DynamicToast.make(this, "Unknown Smart Lock option selected", 3000).show()
                }
            }
        }
    }


    private fun initBleConnection() {
        binding.placeholder.visibility = View.VISIBLE
        binding.smartLockPlaceHolder.visibility = View.GONE

        Timber.tag(TAG).d("BLE Smart Lock option selected")
        connectionType = BLE_CONNECTION
        initSmartLockBleObservers()
    }

    private fun getSmartLockCharacteristic(characteristic: GattCharacteristic): BluetoothGattCharacteristic? {
        val gattService = gatt?.getService(GattService.WifiCommissioningService.number)
        return gattService?.getCharacteristic(characteristic.uuid)
    }

    private fun setSmartLockCharacteristic(): BluetoothGattCharacteristic? {
        return getSmartLockCharacteristic(GattCharacteristic.WifiCommissioningWrite)
    }

    private fun readSmartLockStatus(): BluetoothGattCharacteristic? {
        return getSmartLockCharacteristic(GattCharacteristic.WifiCommissioningNotify)
    }

    private fun getSmartLockQuery() {
        gatt?.let { processor.initQuery(it) }
    }

    private fun getDeviceCharacteristic(
        gattService: GattService,
        characteristic: GattCharacteristic
    ): BluetoothGattCharacteristic? {
        return gatt?.getService(gattService.number)?.getCharacteristic(characteristic.uuid)
    }

    private fun initSmartLockBleObservers() {
        binding.placeholder.visibility = View.GONE
        binding.smartLockPlaceHolder.visibility = View.VISIBLE
        viewModelBle.lockState.observe(this, Observer { lockState ->
            if (connectType != BLE_CONNECTION) return@Observer
            Timber.tag(TAG).d("Lock state changed: $lockState")
            when (lockState) {
                LockState.LOCKED -> gatt?.let { processor.controlLock(it) }
                LockState.UNLOCKED -> gatt?.let { processor.controlUnlock(it) }
                LockState.UNKNOWN -> {
                    Timber.tag(TAG).d("Querying Smart Lock state")
                    getSmartLockQuery()
                }

                else -> Timber.tag(TAG).e("Unknown lock state: $lockState")
            }
        })
    }

    private class GattCommand(
        val type: Type,
        val gatt: BluetoothGatt?,
        val characteristic: BluetoothGattCharacteristic?
    ) {

        enum class Type {
            READ, WRITE, NOTIFY
        }
    }


    private inner class GattProcessor : TimeoutGattCallback() {
        private val commands: java.util.Queue<GattCommand> = LinkedList()
        private val lock: Lock = ReentrantLock()
        private var processing = false

        suspend fun queueWriteAndAwait(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            timeOutMs: Long = TIME_OUT_5SECS
        ): Boolean {
            if (gatt == null || characteristic == null) return false
            val uuid = characteristic.uuid
            val deferred = CompletableDeferred<Boolean>()
            pendingWrites[uuid] = deferred

            queue(GattCommand(GattCommand.Type.WRITE, gatt, characteristic))
            val res = withTimeoutOrNull(timeOutMs) {
                deferred.await()
            }
            pendingWrites.remove(uuid)
            return res == true
        }

        private fun queueWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            queue(GattCommand(GattCommand.Type.WRITE, gatt, characteristic))
        }

        private fun queue(gatt: GattCommand) {
            lock.lock()
            try {
                commands.add(gatt)
                if (!processing) {
                    processing = true
                    processNextCommand()
                }
            } finally {
                lock.unlock()
            }
        }

        @SuppressLint("MissingPermission")
        private fun processNextCommand() {
            var success = false
            val command = commands.poll()

            if (command?.gatt != null && command.characteristic != null) {
                val gatt = command.gatt
                val characteristic = command.characteristic

                success = when (command.type) {
                    GattCommand.Type.WRITE -> gatt.writeCharacteristic(characteristic)
                    GattCommand.Type.READ -> gatt.readCharacteristic(characteristic)
                    GattCommand.Type.NOTIFY -> {
                        val gattCharacteristic = GattCharacteristic.fromUuid(characteristic.uuid)
                        val gattService = GattService.fromUuid(characteristic.service.uuid)
                        BLEUtils.setNotificationForCharacteristic(
                            gatt, gattService,
                            gattCharacteristic, Notifications.NOTIFY
                        )
                    }
                }
            }
            processing = success
        }


        private fun handleCommandProcessed() {
            lock.lock()
            try {
                if (commands.isEmpty()) {
                    processing = false
                } else {
                    processNextCommand()
                }
            } finally {
                lock.unlock()
            }
        }


        private fun readOperation(): BluetoothGattCharacteristic? {
            gatt?.readCharacteristic(characteristicRead)
            println("SmartLockActivity: Read operation completed: $characteristicRead")
            when (connectType) {
                BLE_CONNECTION -> viewModelBle.handleReadLockState(characteristicRead)
                AWS_CONNECTION -> applyAwsLockStateFromBle(characteristicRead)
            }
            return characteristicRead
        }

        fun initQuery(gatt: BluetoothGatt) {
            val characteristicToWrite =
                setSmartLockCharacteristic()
            if (characteristicToWrite != null) {
                characteristicToWrite.apply {
                    val res = BLE_QUERY_VALUE // Legacy query payload retained
                    characteristicToWrite.value = res
                    println("SmartLockActivity: Writing QUERY ${res} payload to characteristic: ${characteristicToWrite.uuid} value: ${characteristicToWrite.value?.contentToString()}")
                }
               // queueWrite(gatt, characteristicToWrite)
                lifecycleScope.launch {
                    val success = queueWriteAndAwait(gatt, characteristicToWrite)
                    if (!success) {
                        Timber.tag(TAG).d("QUERY write failed or timed out")
                    }
                    // onCharacteristicWrite will still handle subsequent readOperation
                }
            } else {
                Timber.tag(TAG).e("SmartLock characteristic not found")
            }
        }

        fun controlLock(gatt: BluetoothGatt) {
            val characteristicToWrite =
                setSmartLockCharacteristic()

            if (characteristicToWrite != null) {
                characteristicToWrite.apply {
                    // Updated legacy two-byte payload to new single-byte lock value
                    // val res = byteArrayOf(0x31, 0x30)
                    val res = byteArrayOf(0x1) // New single-byte lock command
                    characteristicToWrite.value = res
                    println("SmartLockActivity: Writing LOCK (${res}) to characteristic: ${characteristicToWrite.uuid} value: ${characteristicToWrite.value?.contentToString()}")
                }
               // queueWrite(gatt, characteristicToWrite)
                lifecycleScope.launch {
                    val success =
                        queueWriteAndAwait(gatt, characteristicToWrite, TIME_OUT_5SECS)
                    if (!success) {
                        Timber.tag(TAG).d("QUERY write failed or timed out")
                    }
                }
            } else {
                Timber.tag(TAG).e("SmartLock characteristic not found")
            }
        }

        fun controlUnlock(gatt: BluetoothGatt) {
            val characteristicToWrite =
                setSmartLockCharacteristic()
            if (characteristicToWrite != null) {
                characteristicToWrite.apply {
                    // Updated legacy two-byte payload to new single-byte unlock value
                    // val byteArray = byteArrayOf(0x31, 0x31) // Example byte array for lock command
                    val byteArray = byteArrayOf(0x0) // New single-byte unlock command
                    characteristicToWrite.value = byteArray
                    println(
                        "SmartLockActivity: Writing UNLOCK (${byteArray}) to characteristic: ${characteristicToWrite.uuid} value: ${characteristicToWrite.value?.contentToString()}"
                    )
                }
               // queueWrite(gatt, characteristicToWrite)
                lifecycleScope.launch {
                    val success = queueWriteAndAwait(gatt, characteristicToWrite)
                    if (!success) {
                        Timber.tag(TAG).d("QUERY write failed or timed out")
                    }
                    // onCharacteristicWrite will still handle subsequent readOperation
                }
            } else {
                Timber.tag(TAG).e("SmartLock characteristic not found")
            }
        }


        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                println("SmartLockActivity: Disconnected from Smart Lock device : if")

                when (status) {
                    133 -> {
                        println("SmartLockActivity: Disconnected from Smart Lock device :133")
                        showReconnectionMessage()
                    }

                    else -> {
                        println("SmartLockActivity: Disconnected from Smart Lock device")
                        closeSmartLock()
                        onDeviceDisconnected()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            gatt?.getService(GattService.WifiCommissioningService.number)?.let {
                characteristicWrite =
                    it.getCharacteristic(GattCharacteristic.WifiCommissioningWrite.uuid)
                characteristicRead =
                    it.getCharacteristic(GattCharacteristic.WifiCommissioningNotify.uuid)
                characteristicNotification =
                    it.getCharacteristic(GattCharacteristic.WifiCommissioningNotify.uuid)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val gattCharacteristic = GattCharacteristic.fromUuid(characteristic.uuid)
            //  println("SmartLockActivity: onCharacteristicRead: ${gattCharacteristic?.name} - value: ${value.contentToString()}")

        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {

            handleCommandProcessed()
            val gattCharacteristic = GattCharacteristic.fromUuid(characteristic!!.uuid)
            println("SmartLockActivity: onCharacteristicWrite: ${gattCharacteristic?.name} - status: $status")
            when (gattCharacteristic) {
                GattCharacteristic.WifiCommissioningWrite -> {
                    readOperation()
                }

                else -> {
                    Timber.tag(TAG).d("Unhandled characteristic write: ${gattCharacteristic?.name}")
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val gattCharacteristic = GattCharacteristic.fromUuid(characteristic.uuid)
            when (gattCharacteristic) {
                GattCharacteristic.WifiCommissioningNotify -> {
                    readOperation()
                }

                else -> {
                    Timber.tag(TAG)
                        .d("Unhandled characteristic changed: ${gattCharacteristic?.name}")
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            handleCommandProcessed()
        }

    }

    private fun showReconnectionMessage() {
        if (!this.isFinishing && !this.isDestroyed) {
            runOnUiThread {
                DynamicToast.make(this, getString(R.string.connection_failed_reconnecting), 3000)
                    .show()
            }
        }
    }


    interface SmartLockImageRefreshClickListener {
        fun onAwsSmartLockRefresh(buttonClickedStatus: Boolean, connectionType: String)
        fun onSmartLockConnectionTypeSelected()
        fun onSmartLockAWSConfigured()
    }

    companion object {

        private const val PREFS_NAME = "MqttPrefs"
        private const val KEY_SUBSCRIBE_TOPIC = "subscribeTopic"
        private const val KEY_PUBLISH_TOPIC = "publishTopic"
        private const val TAG = "SmartLockActivities"
        const val TIME_OUT_5SECS: Long = 0 // 5 seconds
        const val TIME_OUT_3SECS: Long = 3000 // 5 seconds
        private const val UNLOCK_CMD = "unlock"
        private const val LOCK_CMD = "lock"
        private const val GET_STATUS = "query"
        private const val LOCK_UI_DEBOUNCE_MS = 400L
        private const val LOCK_COMMAND_SUPPRESS_MS = 1200L
        const val BLE_CONNECTION = "BLE"
        const val AWS_CONNECTION = "AWS"
        const val UNKNOW_CONNECTION = "UNKNOWN"
        private val sleepForRead: Long = 500

        // New BLE protocol single-byte values aligned with iOS implementation
        private val BLE_QUERY_VALUE: ByteArray =
            byteArrayOf(0x39) // Query command (unchanged)
    }
}
