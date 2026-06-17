package com.siliconlabs.bledemo.features.demo.awsiot

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationManager
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
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.pranavpandey.android.dynamic.toasts.DynamicToast
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.databinding.ActivityAwsDemoBinding
import com.siliconlabs.bledemo.features.configure.advertiser.services.MqttForegroundService
import com.siliconlabs.bledemo.features.configure.advertiser.services.MqttForegroundService.Companion.NOTIFICATION_ID
import com.siliconlabs.bledemo.features.demo.awsiot.adapter.GridAdapter
import com.siliconlabs.bledemo.features.demo.awsiot.listener.OnMqttGridItemClickListener
import com.siliconlabs.bledemo.features.demo.awsiot.model.GridItem
import com.siliconlabs.bledemo.features.demo.awsiot.repository.ConnectionResult
import com.siliconlabs.bledemo.features.demo.awsiot.viewmodel.MqttViewModel
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.CustomProgressDialog
import com.siliconlabs.bledemo.features.demo.smartlock.dialogs.SmartLockConfigurationDialog
import com.siliconlabs.bledemo.features.demo.smartlock.dialogs.SmartLockConfigurationDialog.Companion.PICK_P12_FILE_REQUEST_CODE
import com.siliconlabs.bledemo.utils.AppUtil
import com.siliconlabs.bledemo.utils.CustomToastManager
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.InputStream
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext


class AWSIOTDemoActivity : AppCompatActivity(), OnMqttGridItemClickListener {
    private lateinit var backPressedCallback: OnBackPressedCallback
    private val messageTimeoutMillis: Long = 60000L
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    private var publishTopic = ""
    private var subscribeTopic = ""
    private var p12EndPointURL = ""
    private var p12FilePathUri: Uri? = null
    private var p12FilePath: String? = null
    private var p12FilePassword: String? = ""
    private lateinit var binding: ActivityAwsDemoBinding

    private lateinit var sharedPreferences: SharedPreferences
    private var customProgressDialog: CustomProgressDialog? = null
    private val mqttViewModel: MqttViewModel by viewModels { MqttViewModelFactory(this) }
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GridAdapter

    // Keep track of the current state of each LED button
    private var isRedOn = false
    private var isGreenOn = false
    private var isBlueOn = false
    private var awsConfigDialog: SmartLockConfigurationDialog? = null

    /** True while waiting for the first successful [updateGrid] after connect; drives red toolbar ProgressBar. */
    private var pendingInitialAwsGrid = false

    private val mqttMessagesObserver = Observer<String> { payload ->
        if (payload.isBlank()) return@Observer

        timeoutRunnable?.let { runnable -> handler.removeCallbacks(runnable) }

        if (!isValidJsonObject(payload)) {
            return@Observer
        }

        removeProgress()
        Log.e("AWS DEMO ACTIVITY", "AWS IOT MESSAGES$payload")

        runOnUiThread {
            updateGrid(JSONObject(payload))
        }
    }


    companion object {
        private const val PREFS_NAME = "MqttPrefs"
        private const val KEY_SUBSCRIBE_TOPIC = "subscribeTopic"
        private const val KEY_PUBLISH_TOPIC = "publishTopic"

        fun isValidJsonObject(jsonString: String): Boolean {
            return try {
                JSONObject(jsonString)
                true // Parsing succeeded, it's valid JSON
            } catch (e: JSONException) {
                false // Parsing failed, it's not valid JSON
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAwsDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppUtil.setEdgeToEdge(window, this)
        setSupportActionBar(binding.toolbar)
        val actionBar = supportActionBar
        actionBar!!.setHomeAsUpIndicator(R.drawable.matter_back)
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar?.title = getString(R.string.aws_dashboard)
        actionBar?.setBackgroundDrawable(
            ColorDrawable(ContextCompat.getColor(this, R.color.aws_iot_demo_toolbar))
        )
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        initUI()

        initObservers()


        // Register the back press callback
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("Activity", "Back button pressed with OnBackPressedDispatcher!")

                //showDisconnectConfirmationDialog()
                if (null != mqttViewModel) {
                    mqttViewModel.unsubscribeFromTopic()
                    mqttViewModel.disconnect()
                    finish()
                }
            }
        }

        // Add callback to the dispatcher
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    private fun startAwsInitialLoadProgress() {
        pendingInitialAwsGrid = true
        binding.awsIotLoadProgress.visibility = View.VISIBLE
    }

    private fun cancelAwsInitialLoadProgress() {
        pendingInitialAwsGrid = false
        binding.awsIotLoadProgress.visibility = View.GONE
    }

    private fun initObservers() {


        mqttViewModel.connectionResult.observe(this, Observer { result ->
            when (result) {
                is ConnectionResult.Connecting -> {
                    startAwsInitialLoadProgress()
                    showProgressDialog(getString(R.string.connecting_to_aws))
                }

                is ConnectionResult.Connected -> {
                    startAwsInitialLoadProgress()
                    removeProgress()
                    runOnUiThread {
                        CustomToastManager.show(
                            this,
                            getString(R.string.connection_successful), 5000
                        )

                    }


                }

                is ConnectionResult.Error -> {
                    cancelAwsInitialLoadProgress()
                    mqttViewModel.mqttMessages.removeObserver(mqttMessagesObserver)
                    // Handle the error
                    println("Connection Error: ${result.message}")
                    result.throwable?.printStackTrace() // Print the stack trace for debugging
                    DynamicToast.makeError(this, result.message, 5000).show()
                    runOnUiThread {
                        if (result.message.contains(
                                "MqttException",
                                false
                            ) or result.message.contains(
                                getString(
                                    R.string.subscription_failed
                                ), false
                            )
                        ) {
                            removeProgress()
                            CustomToastManager.show(this, result.message, 5000)
                            // showMqttTopicDialog()
                            initAWSConfigure()
                        } else {
                            CustomToastManager.show(this, result.message, 5000)
                        }

                    }
                }

                ConnectionResult.SubscribeConnected -> {
                    binding.placeholder.visibility = View.GONE
                    showProgressDialog(getString(R.string.loading_data))
                    startMessageTimeout()

                    mqttViewModel.prepareMqttObservationForNewSubscription()
                    mqttViewModel.mqttMessages.removeObserver(mqttMessagesObserver)
                    mqttViewModel.mqttMessages.observe(this, mqttMessagesObserver)
                }

                ConnectionResult.SubscribeConnecting -> {
                    CustomToastManager.show(this,getString(R.string.subscribing_to_the_entered_topic),5000)
                }

                ConnectionResult.Disconnected -> {
                    cancelAwsInitialLoadProgress()
                    mqttViewModel.mqttMessages.removeObserver(mqttMessagesObserver)
                    removeProgress()
                    //this.finish()
                }

                ConnectionResult.Disconnecting -> {
                    showProgressDialog(getString(R.string.disconnecting_from_aws_iot))
                }

                ConnectionResult.DisconnectionError -> {
                    cancelAwsInitialLoadProgress()
                    mqttViewModel.mqttMessages.removeObserver(mqttMessagesObserver)
                    this.finish()
                }
            }
        })
    }

    // Function to start the timeout
    private fun startMessageTimeout() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = Runnable {
            cancelAwsInitialLoadProgress()
            removeProgress()
            showAlertDialogForRetry()
        }
        handler.postDelayed(timeoutRunnable!!, messageTimeoutMillis)
    }



    // Function to show AlertDialog for topic re-entry
    private fun showAlertDialogForRetry() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.subscription_timeout))
            .setMessage(getString(R.string.no_mess_received_within_thirtysec))
            .setPositiveButton(getString(R.string.retry)) { _, _ ->
                // Reopen the dialog for topic entry
                // showMqttTopicDialog()
                initAWSConfigure()
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                this.finish()
            }
            .show()
    }

    private fun updateGrid(jsonObject: JSONObject) {
        val orderedKeys = listOf(
            getString(R.string.aws_temperature),
            getString(R.string.aws_humidity), getString(R.string.aws_ambient_light),
            getString(R.string.aws_white_light)
        )
        val newItems = mutableListOf<GridItem>()
        var motionItem: GridItem? = null // Use null to indicate no motion yet

        var accelerometerData = ""
        var gyroData = ""

        // Process accelerometer and gyro data first
        jsonObject.keys().forEach { key ->
            val value = jsonObject.get(key)

            when (key) {
                getString(R.string.aws_accelerometer) -> {
                    val acc = value as JSONObject
                    accelerometerData =
                        "Acc: (${acc.getDouble("x")}, ${acc.getDouble("y")}, ${acc.getDouble("z")})"
                }

                getString(R.string.aws_gyro) -> {
                    val gyro = value as JSONObject
                    gyroData =
                        "Gyro: (${gyro.getDouble("x")}, ${gyro.getDouble("y")}, ${gyro.getDouble("z")})"
                }
            }
        }

        // Create Motion item if data is present
        if (accelerometerData.isNotEmpty() && gyroData.isNotEmpty()) {
            val motionData = "$accelerometerData\n$gyroData"
            motionItem = GridItem(
                "Motion",
                motionData,
                R.drawable.ic_motion_container
            )
        }
        // Process other keys according to ordered key
        orderedKeys.forEach { key ->
            if (jsonObject.has(key)) {
                val value = jsonObject.get(key)
                if (value !is JSONObject) {
                    newItems.add(
                        GridItem(
                            key.replaceFirstChar { it.uppercase().replace("_", " ") },
                            value.toString(),
                            getIconForKey(key)
                        )
                    )
                }
            }
        }

        // Find and Extract Items to Reorder
        val itemsToReorder = mutableListOf<GridItem>()
        val itemsToRemove = mutableListOf<GridItem>()

        newItems.forEach { gridItem ->
            when (gridItem.title.lowercase()) {
                "temperature" -> {
                    itemsToReorder.add(gridItem)
                    itemsToRemove.add(gridItem)
                }
                "humidity" -> {
                    itemsToReorder.add(gridItem)
                    itemsToRemove.add(gridItem)
                }
                "ambient light" -> {
                    itemsToReorder.add(gridItem)
                    itemsToRemove.add(gridItem)
                }
                "white light" -> {
                    itemsToReorder.add(gridItem)
                    itemsToRemove.add(gridItem)
                }
            }
        }

        // Remove items from newItems
        newItems.removeAll(itemsToRemove)

        // Add the item into the newItems list according to the index
        if(itemsToReorder.size > 0){
            if(itemsToReorder.size > 0)
                newItems.add(0,itemsToReorder.get(0))
            if(itemsToReorder.size > 1)
                newItems.add(1,itemsToReorder.get(1))
            if(itemsToReorder.size > 2)
                newItems.add(2,itemsToReorder.get(2))
            if(itemsToReorder.size > 3)
                newItems.add(3,itemsToReorder.get(3))
        }


        // Add motion in 5th position if any
        if (motionItem != null) {
            // Ensure there are at least 5 items by adding placeholders if needed
            while (newItems.size < 4) {
                newItems.add(GridItem("", "", R.drawable.ic_motion_container))//placeholder icon
            }
            newItems.add(motionItem)
        }

        // Add LED as the last item
        newItems.add(GridItem(getString(R.string.aws_led), " ", R.drawable.icon_dks_917_led))

        // Update the adapter
        adapter.updateData(newItems)
        if (pendingInitialAwsGrid) {
            binding.mqttRv.post {
                if (pendingInitialAwsGrid) {
                    pendingInitialAwsGrid = false
                    binding.awsIotLoadProgress.visibility = View.GONE
                }
            }
        }
    }

    private fun getIconForKey(key: String): Int {
        return when (key.lowercase()) {
            getString(R.string.aws_temperature) -> R.drawable.icon_temp
            getString(R.string.aws_humidity) -> R.drawable.icon_environment
            getString(R.string.aws_ambient_light) -> R.drawable.icon_light
            getString(R.string.aws_white_light) -> R.drawable.icon_light
            getString(R.string.motion_demo_title) -> R.drawable.ic_motion_container

            else -> R.drawable.background_grey_box
        }
    }

    private fun initUI() {
        recyclerView = binding.mqttRv
        recyclerView.layoutManager = GridLayoutManager(this, 3) // 2 columns

        adapter = GridAdapter(emptyList(), this, mqttViewModel, this)
        recyclerView.adapter = adapter
        // showMqttTopicDialog()
        initAWSConfigure()
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                //showDisconnectConfirmationDialog()
                if (null != mqttViewModel) {
                    mqttViewModel.unsubscribeFromTopic()
                    mqttViewModel.disconnect()
                    finish()
                }
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }



    private fun removeProgress() {
        runOnUiThread {
            if (customProgressDialog?.isShowing() == true) {
                customProgressDialog?.dismiss()
            }
        }
    }

    private fun showProgressDialog(message: String) {
        runOnUiThread {
            customProgressDialog = CustomProgressDialog(this)
            customProgressDialog!!.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            customProgressDialog!!.setMessage(message)
            customProgressDialog!!.show()
        }

    }

    @SuppressLint("MissingInflatedId")
    private fun showMqttTopicDialog() {
        lateinit var cleanEndPointUrl: String

        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogView: View = inflater.inflate(R.layout.dialog_mqtt, null)
        builder.setView(dialogView)

        val titleTextView: TextView = dialogView.findViewById(R.id.mqttDialogTitle)
        titleTextView.text = getString(R.string.aws_iot_mqtt_dialog_title)
        val subscribeEditText = dialogView.findViewById<TextInputEditText>(R.id.editSubTopic)
        val publishEditText = dialogView.findViewById<TextInputEditText>(R.id.editPubTopic)
        val submitButton: MaterialButton = dialogView.findViewById(R.id.submitMqttButton)
        val cancelButton: TextView = dialogView.findViewById(R.id.submitMqttCancelButton)
        val filePath = dialogView.findViewById<Button>(R.id.select_p12_file_btn)
        val p12EndPoint = dialogView.findViewById<TextInputEditText>(R.id.editEndPoint)
        val password = dialogView.findViewById<TextInputEditText>(R.id.editPassword)

        builder.setCancelable(false)

        val dialog = builder.create()

        // Load saved topics from SharedPreferences
        loadSavedTopics(subscribeEditText, publishEditText)

        filePath.setOnClickListener {
           // pickfile()
        }

        submitButton.setOnClickListener { _ ->
            filePath.text = p12FilePathUri?.path ?: ""
            subscribeTopic = subscribeEditText.text.toString().trim()
            publishTopic = publishEditText.text.toString().trim()
            p12FilePassword = password.text.toString().trim()
            p12EndPointURL = p12EndPoint.text.toString().trim()

            when {
                p12EndPointURL.isBlank() -> {
                    CustomToastManager.show(
                        this,
                        getString(R.string.smart_lock_config_alert_end_point_message),
                        5000
                    )
                }

                p12FilePassword?.isBlank() == true -> {
                    CustomToastManager.show(
                        this,
                        getString(R.string.smart_lock_config_alert_password_message),
                        5000
                    )
                }

                subscribeTopic.isBlank() && publishTopic.isBlank() -> {
                    // Both are empty or contain only whitespace
                    CustomToastManager.show(
                        this,
                        getString(R.string.subscribe_and_publish_topics_should_not_be_empty),
                        5000
                    )
                }

                subscribeTopic.isBlank() -> {
                    // Only subscribeTopic is empty or contains only whitespace
                    CustomToastManager.show(
                        this,
                        getString(R.string.subscribe_topic_should_not_be_empty),
                        5000
                    )
                }

                publishTopic.isBlank() -> {
                    // Only publishTopic is empty or contains only whitespace
                    CustomToastManager.show(
                        this,
                        getString(R.string.publish_topic_should_not_be_empty),
                        5000
                    )
                }


                else -> {
                    // Both are valid
                    cleanEndPointUrl = SmartLockConfigurationDialog.removeProtocols(
                        p12EndPointURL
                    )
                    filePath.text = p12FilePathUri?.path ?: ""
                    if (areMqttTopicsValid(publishTopic, subscribeTopic)) {
                        processP12File(p12FilePathUri, cleanEndPointUrl, p12FilePassword)
                        // mqttViewModel.connect(subscribeTopic, p12EndPointURL,p12FilePassword)
                        saveTopics(subscribeTopic, publishTopic)
                        dialog.dismiss()
                    }
                }
            }
        }
        cancelButton.setOnClickListener {
            dialog.dismiss()
            this.finish()
        }

        dialog.show()
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
                if(isNetworkAvailable(this)) {
                    runOnUiThread {
                        startAwsInitialLoadProgress()
                        DynamicToast.makeSuccess(
                            this,
                            getString(R.string.smart_lock_device_connected),
                            5000
                        )
                        mqttViewModel.connect(subscribeTopic, endPoint, sslContext)
                    }
                    sslContext
                }else{
                    runOnUiThread {
                        DynamicToast.makeError(
                            this,
                            getString(R.string.smart_lock_internet_not_available),
                            5000
                        )
                    }
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Timber.tag("AWSIOT").e("Error reading the .p12 file: $e")
                runOnUiThread {
                    DynamicToast.makeError(this, "Error reading the .p12 file: $e", 5000).show()
                }
                null
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


    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        when (requestCode) {
            PICK_P12_FILE_REQUEST_CODE -> {
                intent?.data?.let {
                    if (resultCode == RESULT_OK) {
                        // Handle the selected file URI
                        val fileUri = it
                        Timber.tag("AWSIOTDemoActivity").d("Selected file: $fileUri")
                        p12FilePathUri = it
                        p12FilePath = awsConfigDialog?.readFilename(it)
                        awsConfigDialog?.uriSelected(it)
                        awsConfigDialog?.changeFileName(
                            p12FilePath
                        )
                        loadSubPubSavedTopics()

                    } else {
                        runOnUiThread {
                            // DynamicToast.make(this, getString(R.string.file_selection_cancelled), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
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

        if (!isPublishValid) CustomToastManager.show(
            this,
            "Invalid Publish Topic: $publishTopic",
            5000
        )
        if (!isSubscribeValid) CustomToastManager.show(
            this,
            "Invalid Subscribe Topic: $subscribeTopic",
            5000
        )

        return isPublishValid && isSubscribeValid
    }

    private fun loadSubPubSavedTopics() {
        // Load saved topics from SharedPreferences
        val savedSubscribeTopic = sharedPreferences.getString(KEY_SUBSCRIBE_TOPIC, "")
        val savedPublishTopic = sharedPreferences.getString(KEY_PUBLISH_TOPIC, "")

        if (awsConfigDialog != null) {
            awsConfigDialog?.displaySubscribeTopic(savedSubscribeTopic ?: "")
            awsConfigDialog?.displayPublishTopic(savedPublishTopic ?: "")
        } else {
            Timber.tag("AWSIOTDemoActivity").e("awsConfigDialog is null or not showing")
        }
    }

    private fun loadSavedTopics(
        subscribeEditText: TextInputEditText,
        publishEditText: TextInputEditText
    ) {
        val savedSubscribeTopic = sharedPreferences.getString(KEY_SUBSCRIBE_TOPIC, "")
        val savedPublishTopic = sharedPreferences.getString(KEY_PUBLISH_TOPIC, "")

        subscribeEditText.setText(savedSubscribeTopic)
        publishEditText.setText(savedPublishTopic)
    }

    private fun saveTopics(subscribeTopic: String, publishTopic: String) {
        val editor = sharedPreferences.edit()
        editor.putString(KEY_SUBSCRIBE_TOPIC, subscribeTopic)
        editor.putString(KEY_PUBLISH_TOPIC, publishTopic)
        editor.apply()
    }


    override fun onItemClick(
        ledRedButtonControlStatus: Boolean,
        ledGreenButtonControlStatus: Boolean,
        ledBlueButtonControlStatus: Boolean
    ) {
        var mqttMessage: String? = null

        // Handle Red Button
        if (ledRedButtonControlStatus) {
            isRedOn = !isRedOn
            mqttMessage = if (isRedOn) {
                "{\n" +
                        "  \"red\": \"on\",\n" +
                        "  \"green\": \"off\",\n" +
                        "  \"blue\": \"off\"\n" +
                        "}"
            } else {
                "{\n" +
                        "  \"red\": \"off\",\n" +
                        "  \"green\": \"off\",\n" +
                        "  \"blue\": \"off\"\n" +
                        "}"
            }
            // Turn off other colors when Red is toggled
            isGreenOn = false
            isBlueOn = false
        }

        // Handle Green Button
        else if (ledGreenButtonControlStatus) {
            isGreenOn = !isGreenOn
            mqttMessage = if (isGreenOn) {
                "{\n" +
                        "  \"red\": \"off\",\n" +
                        "  \"green\": \"on\",\n" +
                        "  \"blue\": \"off\"\n" +
                        "}"
            } else {
                "{\n" +
                        "  \"red\": \"off\",\n" +
                        "  \"green\": \"off\",\n" +
                        "  \"blue\": \"off\"\n" +
                        "}"
            }
            // Turn off other colors when Green is toggled
            isRedOn = false
            isBlueOn = false
        }

        // Handle Blue Button
        else if (ledBlueButtonControlStatus) {
            isBlueOn = !isBlueOn
            mqttMessage = if (isBlueOn) {
                "{\n" +
                        "  \"red\": \"off\",\n" +
                        "  \"green\": \"off\",\n" +
                        "  \"blue\": \"on\"\n" +
                        "}"
            } else {
                "{\n" +
                        "  \"red\": \"off\",\n" +
                        "  \"green\": \"off\",\n" +
                        "  \"blue\": \"off\"\n" +
                        "}"
            }
            // Turn off other colors when Blue is toggled
            isRedOn = false
            isGreenOn = false
        }

        if (mqttMessage != null) {
            mqttViewModel.publish(topic = publishTopic, message = mqttMessage)
        }
    }

    override fun onCloseButtonClicked(buttonClickedStatus: Boolean) {
        val mqttMessage = "{\n" +
                "  \"red\": \"off\",\n" +
                "  \"green\": \"off\",\n" +
                "  \"blue\": \"off\"\n" +
                "}"
        mqttViewModel.publish(topic = publishTopic, message = mqttMessage)
    }

    override fun onDestroy() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        mqttViewModel.mqttMessages.removeObserver(mqttMessagesObserver)
        cancelAwsInitialLoadProgress()
        backPressedCallback.remove()
        stopMqttForegroundService()
        cancelNotification()
        super.onDestroy()
    }

    private fun Context.stopMqttForegroundService() {
        val intent = Intent(this, MqttForegroundService::class.java)
        this.stopService(intent)
    }

    private fun cancelNotification() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun initAWSConfigure() {
        awsConfigDialog = SmartLockConfigurationDialog(
            this, getString(R.string.aws_iot_mqtt_dialog_title),
            listener = fileSelectionListener
        ).also {
            it.show(supportFragmentManager, "SmartLockConfigurationDialog")
        }
    }

    private fun closeApp() {
        this.finish()
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
                if (awsConfigDialog != null && awsConfigDialog?.isShowing() == true) {
                    awsConfigDialog?.dismiss()
                    awsConfigDialog = null
                }
                closeApp()
            }

            override fun onConnectButtonClicked(
                uriPath: Uri?,
                password: String?,
                endPoint: String,
                subcribeTopic: String,
                pubTopic: String
            ) {

                subscribeTopic = subcribeTopic
                publishTopic = pubTopic
                if (uriPath != null) {
                    p12FilePathUri = uriPath
                }
                p12FilePassword = password
                p12EndPointURL = endPoint

                if (areMqttTopicsValid(publishTopic, subscribeTopic)) {
                    saveTopics(subscribeTopic, publishTopic)
                    processP12File(p12FilePathUri, p12EndPointURL, p12FilePassword)

                }
                if (awsConfigDialog != null && awsConfigDialog?.isShowing() == true) {
                    awsConfigDialog?.dismiss()
                }
            }

        }
}

class MqttViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MqttViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MqttViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }


}
