package com.siliconlabs.bledemo.features.demo.devkitsensor917.utils

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.gridlayout.widget.GridLayout
import com.google.android.material.button.MaterialButton
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.databinding.SensorDemoGridItemBinding
import com.siliconlabs.bledemo.features.demo.devkitsensor917.APIInterface
import com.siliconlabs.bledemo.features.demo.devkitsensor917.activities.DevKitSensor917Activity
import com.siliconlabs.bledemo.features.demo.devkitsensor917.model.AccelerometerGyroScopeResponse
import com.siliconlabs.bledemo.features.demo.devkitsensor917.model.LEDResponse
import com.siliconlabs.bledemo.features.demo.devkitsensor917.model.LEDStatusResponse
import com.siliconlabs.bledemo.features.demo.devkitsensor917.model.SensorsResponse
import com.siliconlabs.bledemo.utils.CustomToastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber


open class DevKitSensorControl(
    context: Context,
    description: String?,
    icon: Drawable?
) :
    LinearLayout(context, null, 0), DevKitSensor917Activity.ResponseListener {
    private var btnRedStatus = false
    private var btnGreenStatus = false
    private var btnBlueStatus = false
    private lateinit var ledImageStatus: ImageView
    private lateinit var onLEDBtn: Button
    private lateinit var offLEDBtn: Button
    private lateinit var redLEDBtn: Button
    private lateinit var greenLEDBtn: Button
    private lateinit var blueLEDBtn: Button

    private var tempResponse: String? = null
    private var ambiResponse: String? = null
    private var humiResponse: String? = null
    private var acceloResponse: AccelerometerGyroScopeResponse? = null
    private var gyroResponse: AccelerometerGyroScopeResponse? = null

    constructor(context: Context) : this(context, null, null)

    var sensorDemoGridItemBinding: SensorDemoGridItemBinding =
        SensorDemoGridItemBinding.inflate(LayoutInflater.from(context))

    init {

        val densityDpi = Resources.getSystem().displayMetrics.densityDpi
        val cardView: CardView = sensorDemoGridItemBinding.cardviewEnvTile


        if(densityDpi < 240){
            Log.e("DevKitSensorDisplay","1")
            //small
            // Desired width in dp
            val desiredWidthDp = 150

            // Convert dp to pixels
            val desiredWidthPx = (desiredWidthDp * Resources.getSystem().displayMetrics.density).toInt()

            // Get the current layout parameters
            val layoutParams = cardView.layoutParams as LinearLayout.LayoutParams

            // Set the new width
            layoutParams.width = desiredWidthPx

            // Apply the updated layout parameters
            cardView.layoutParams = layoutParams
        }
        else if(densityDpi in 240..319){
            Log.e("DevKitSensorDisplay","2")

            // Desired width in dp
            val desiredWidthDp = 125

            // Convert dp to pixels
            val desiredWidthPx = (desiredWidthDp * Resources.getSystem().displayMetrics.density).toInt()

            // Get the current layout parameters
            val layoutParams = cardView.layoutParams as LinearLayout.LayoutParams

            // Set the new width
            layoutParams.width = desiredWidthPx

            // Apply the updated layout parameters
            cardView.layoutParams = layoutParams

        }
        else if(densityDpi in 320 .. 479){
            // Desired width in dp
            Log.e("DevKitSensorDisplay","3")

            val desiredWidthDp = 115

            // Convert dp to pixels
            val desiredWidthPx = (desiredWidthDp * Resources.getSystem().displayMetrics.density).toInt()

            // Get the current layout parameters
            val layoutParams = cardView.layoutParams as LinearLayout.LayoutParams

            // Set the new width
            layoutParams.width = desiredWidthPx

            // Apply the updated layout parameters
            cardView.layoutParams = layoutParams
        }
        else{
            // Desired width in dp
            Log.e("DevKitSensorDisplay","4")

            val desiredWidthDp = 100

            // Convert dp to pixels
            val desiredWidthPx = (desiredWidthDp * Resources.getSystem().displayMetrics.density).toInt()

            // Get the current layout parameters
            val layoutParams = cardView.layoutParams as LinearLayout.LayoutParams

            // Set the new width
            layoutParams.width = desiredWidthPx

            // Apply the updated layout parameters
            cardView.layoutParams = layoutParams
        }

    }

    fun setListener(tag: Any, ipAddress: String) {
        val code = tag.toString()
        sensorDemoGridItemBinding.cardviewEnvTile.setOnClickListener {
            when (code) {
                TEMPERATURE -> showTemperatureDialog(code, ipAddress)
                HUMIDITY -> showHumidityDialog(code, ipAddress)
                AMBIENT_LIGHT -> showAmbientLightDialog(code, ipAddress)
                MICROPHONE -> showMicrophoneDialog(code, ipAddress)
                LED -> showLEDControlDialog(code, ipAddress)
                MOTION -> showMotionDialog(code, ipAddress)
            }
        }
    }


    @SuppressLint("SetTextI18n")
    private fun showMotionDialog(title: String, ipAddress: String) {
        var apiJob: Job? = null
        val devkitSensorDialog = Dialog(context, R.style.Style_Dialog_Rounded_Corner)
        devkitSensorDialog.setContentView(R.layout.dev_kit_sesnor_917_motion_control_dialog_layout)
        devkitSensorDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val dialogWidth = (context.resources.displayMetrics.widthPixels * 0.92f).toInt()
        devkitSensorDialog.window?.setLayout(
            dialogWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val header: TextView = devkitSensorDialog.findViewById(R.id.header)
        val yesBtn: MaterialButton = devkitSensorDialog.findViewById(R.id.yes_opt)
        val noBtn: MaterialButton = devkitSensorDialog.findViewById(R.id.no_opt)
        val orientationX: TextView = devkitSensorDialog.findViewById(R.id.orient_x)
        val orientationY: TextView = devkitSensorDialog.findViewById(R.id.orientation_y)
        val orientationZ: TextView = devkitSensorDialog.findViewById(R.id.orientation_z)
        val degreeString = context.getString(R.string.motion_orientation_degree)
        if (gyroResponse != null && gyroResponse!!.x.isNotEmpty()) {
            orientationX.text = String.format(degreeString, gyroResponse!!.x.toFloat())
        } else {
            orientationX.text = String.format(degreeString, 00f)
        }
        if (gyroResponse != null && gyroResponse!!.y.isNotEmpty()) {
            orientationY.text = String.format(degreeString, gyroResponse!!.y.toFloat())
        } else {
            orientationY.text = String.format(degreeString, 00f)
        }
        if (gyroResponse != null && gyroResponse!!.z.isNotEmpty()) {
            orientationZ.text = String.format(degreeString, gyroResponse!!.z.toFloat())
        } else {
            orientationZ.text = String.format(degreeString, 00f)
        }

        val accelerationString = context.getString(R.string.motion_acceleration_g)
        val acceloX: TextView = devkitSensorDialog.findViewById(R.id.accelo_x)
        val acceloY: TextView = devkitSensorDialog.findViewById(R.id.accelo_y)
        val acceloZ: TextView = devkitSensorDialog.findViewById(R.id.accelo_z)
        if (acceloResponse != null && acceloResponse!!.x.isNotEmpty()) {
            acceloX.text = String.format(accelerationString, acceloResponse!!.x.toFloat())
        } else {
            acceloX.text = String.format(accelerationString, 00F)
        }
        if (acceloResponse != null && acceloResponse!!.y.isNotEmpty()) {
            acceloY.text = String.format(accelerationString, acceloResponse!!.y.toFloat())
        } else {
            acceloY.text = String.format(accelerationString, 00F)
        }
        if (acceloResponse != null && acceloResponse!!.z.isNotEmpty()) {
            acceloZ.text = String.format(accelerationString, acceloResponse!!.z.toFloat())
        } else {
            acceloZ.text = String.format(accelerationString, 00F)
        }

        yesBtn.setOnClickListener {
            yesBtn.isClickable = false
            yesBtn.isEnabled = false
            CoroutineScope(Dispatchers.IO).launch {
                doInMotionOrientationBackground(
                    orientationX,
                    orientationY,
                    orientationZ,
                    yesBtn,
                    ipAddress
                )
                doInMotionAccelerometerBackground(acceloX, acceloY, acceloZ, ipAddress)
            }
        }
        noBtn.setOnClickListener {
            devkitSensorDialog.dismiss()
            if (apiJob != null) {
                apiJob?.cancel()
            }
        }
        header.text = title + SPACE + context.getString(R.string.title_sensor)
        devkitSensorDialog.show()
        apiJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                doInMotionOrientationBackground(
                    orientationX,
                    orientationY,
                    orientationZ,
                    yesBtn,
                    ipAddress
                )
                doInMotionAccelerometerBackground(acceloX, acceloY, acceloZ, ipAddress)
                delay(TIME_OUT)
            }
        }
    }


    private suspend fun doInMotionAccelerometerBackground(
        viewX: TextView,
        viewY: TextView,
        viewZ: TextView, ipAddress: String
    ) {
        withContext(Dispatchers.Default) {
            try {
                val url = "http://$ipAddress"
                val retro = Retrofit.Builder().baseUrl(url)
                    .addConverterFactory(GsonConverterFactory.create()).build()
                val response = retro.create(APIInterface::class.java).getAccelerometerStatus()
                println("Response: $response")
                if (response.isSuccessful) {
                    val data = response.body()
                    println("data: $data")
                    withContext(Dispatchers.Main) {
                        if (data != null) {
                            val accelerationString =
                                context.getString(R.string.motion_acceleration_g)
                            viewX.text = String.format(accelerationString, data.x.toFloat())
                            viewY.text = String.format(accelerationString, data.y.toFloat())
                            viewZ.text = String.format(accelerationString, data.z.toFloat())
                        }
                    }
                } else {
                    //df
                    Timber.tag(TAG).e("Response failed: ")
                }
            } catch (e: Exception) {
                // Handle the exception
                Timber.tag(TAG).e("Exception occurred ${e.message}")
            }
        }
    }


    private suspend fun doInMotionOrientationBackground(
        viewX: TextView,
        viewY: TextView,
        viewZ: TextView, btn: View, ipAddress: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                val url = "http://$ipAddress"
                val retro = Retrofit.Builder().baseUrl(url)
                    .addConverterFactory(GsonConverterFactory.create()).build()
                val response = retro.create(APIInterface::class.java).getGyroscopeStatus()
                println("Response: $response")
                if (response.isSuccessful) {
                    val data = response.body()
                    println("data: $data")
                    withContext(Dispatchers.Main) {
                        if (data != null) {
                            val degreeString = context.getString(R.string.motion_orientation_degree)
                            viewX.text = String.format(degreeString, data.x.toFloat())
                            viewY.text = String.format(degreeString, data.y.toFloat())
                            viewZ.text = String.format(degreeString, data.z.toFloat())
                        }
                        btn.isClickable = true
                        btn.isEnabled = true
                    }
                } else {
                    //df
                    withContext(Dispatchers.Main){
                        Timber.tag(TAG)
                            .e("MotionOrientation API Response failed: ${response.message()}")
                        btn.isClickable = true
                        btn.isEnabled = true
                    }

                }
            } catch (e: Exception) {
                // Handle the exception
                withContext(Dispatchers.Main){
                    Timber.tag(TAG).e("MotionOrientation API Exception occurred ${e.message}")
                    btn.isClickable = true
                    btn.isEnabled = true
                }

            } finally {
                withContext(Dispatchers.Main){
                    btn.isClickable = true
                    btn.isEnabled = true
                }

            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showTemperatureDialog(title: String, ipAddress: String) {
        var apiJob: Job? = null
        val devKitSensorDialog = Dialog(context, R.style.Style_Dialog_Rounded_Corner)
        devKitSensorDialog.setContentView(R.layout.dev_kit_sensor_917_dialog_layout)
        devKitSensorDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val dialogWidth = (context.resources.displayMetrics.widthPixels * 0.92f).toInt()
        devKitSensorDialog.window?.setLayout(
            dialogWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val header: TextView = devKitSensorDialog.findViewById(R.id.header)
        val subTitle: TextView = devKitSensorDialog.findViewById(R.id.subTitle)
        val yesBtn: MaterialButton = devKitSensorDialog.findViewById(R.id.yes_opt)
        val noBtn: MaterialButton = devKitSensorDialog.findViewById(R.id.no_opt)
        val image: ImageView = devKitSensorDialog.findViewById(R.id.image)
        val dataHolder: TextView = devKitSensorDialog.findViewById(R.id.txt_value)
        dataHolder.text = context.getString(R.string.matter_init_value)
        // println("tempResponse :${tempResponse!!.temperature_celcius}")
        if (tempResponse != null && tempResponse!!.isNotEmpty()) {
            dataHolder.text = "$tempResponse ℃"
        }
        image.setImageResource(R.drawable.icon_temp)
        header.text = title + SPACE + context.getString(R.string.title_sensor)
        subTitle.text = title
        yesBtn.setOnClickListener {
            yesBtn.isEnabled = false
            yesBtn.isClickable = false
            CoroutineScope(Dispatchers.IO).launch {
                doInTempBackground(dataHolder, yesBtn, ipAddress)
            }
        }
        noBtn.setOnClickListener {
            devKitSensorDialog.dismiss()
            if (apiJob != null) {
                apiJob?.cancel()
            }
        }
        devKitSensorDialog.show()

        apiJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                doInTempBackground(dataHolder, yesBtn, ipAddress)
                delay(TIME_OUT)
            }
        }

    }

    @SuppressLint("SetTextI18n")
    private suspend fun doInAmbientBackground(view: TextView, btn: View, ipAddress: String) {
        // Disable the button immediately on the main thread
        withContext(Dispatchers.Main) {
            btn.isClickable = false
            btn.isEnabled = false
        }
        withContext(Dispatchers.IO) {
            try {
                val url = "http://$ipAddress"
                val retro = Retrofit.Builder().baseUrl(url)
                    .addConverterFactory(GsonConverterFactory.create()).build()
                val response = retro.create(APIInterface::class.java).getAmbitStatus()
                println("Response: $response")
                if (response.isSuccessful) {
                    val data = response.body()
                    println("data: $data")
                    withContext(Dispatchers.Main) {
                        if (data != null) {
                            view.text = data.ambient_light_lux + " lx"
                        }
                        btn.isClickable = true
                        btn.isEnabled = true
                    }
                } else {
                    //df
                    Timber.tag(TAG).e("API Ambient Response failed: ${response.message()}")
                    withContext(Dispatchers.Main) {
                        btn.isClickable = true
                        btn.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                // Handle the exception
                Timber.tag(TAG).e("API Ambient Exception occurred :${e.message}")
                withContext(Dispatchers.Main) {
                    btn.isClickable = true
                    btn.isEnabled = true
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private suspend fun doInMicroPhoneBackground(view: TextView, ipAddress: String) {
        withContext(Dispatchers.IO) {
            try {
                val url = "http://$ipAddress"
                val retro = Retrofit.Builder().baseUrl(url)
                    .addConverterFactory(GsonConverterFactory.create()).build()
                val response = retro.create(APIInterface::class.java).getMicrophoneStatus()
                println("Response: $response")
                if (response.isSuccessful) {
                    val data = response.body()
                    println("data: $data")
                    withContext(Dispatchers.Main) {
                        if (data != null) {
                            view.text = data.microphone_decibel + " dB"
                        }
                    }
                } else {
                    Timber.tag(TAG).e("API Microphone Response failed:${response.message()}")
                }
            } catch (e: Exception) {
                // Handle the exception
                Timber.tag(TAG).e("API Microphone Exception occurred ${e.message}")
            }
        }
    }


    @SuppressLint("SetTextI18n")
    private suspend fun doInHumidityBackground(view: TextView, btn: View, ipAddress: String) {
        withContext(Dispatchers.IO) {
            try {
                val url = "http://$ipAddress"
                val retro = Retrofit.Builder().baseUrl(url)
                    .addConverterFactory(GsonConverterFactory.create()).build()
                val response = retro.create(APIInterface::class.java).getHumidityStatus()
                println("Response: $response")
                if (response.isSuccessful) {
                    val data = response.body()
                    println("data: $data")
                    withContext(Dispatchers.Main) {
                        if (data != null) {
                            view.text = data.humidity_percentage + " %"
                        }
                        btn.isEnabled = true
                        btn.isClickable = true
                    }
                } else {
                    withContext(Dispatchers.Main){
                        btn.isEnabled = true
                        btn.isClickable = true
                    }

                    //df
                    Timber.tag(TAG).e("API Humidity Response failed:${response.message()}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main){
                    btn.isEnabled = true
                    btn.isClickable = true
                }

                // Handle the exception
                Timber.tag(TAG).e("API Humidity Exception occurred ${e.message} ")
            } finally {
                withContext(Dispatchers.Main){
                    btn.isEnabled = true
                    btn.isClickable = true
                }

            }
        }
    }

    @SuppressLint("SetTextI18n")
    private suspend fun doInTempBackground(view: TextView, btn: View, ipAddress: String) {
        // Disable the button immediately on the main thread
        withContext(Dispatchers.Main) {
            btn.isClickable = false
            btn.isEnabled = false
        }
        withContext(Dispatchers.IO) {
            try {
                val url = "http://$ipAddress"
                val retro = Retrofit.Builder().baseUrl(url)
                    .addConverterFactory(GsonConverterFactory.create()).build()
                val response = retro.create(APIInterface::class.java).getTempStatus() // Assuming you have a getTempStatus() method
                println("Response: $response")
                if (response.isSuccessful) {
                    val data = response.body()
                    println("data: $data")
                    withContext(Dispatchers.Main) {
                        if (data != null) {
                            view.text = data.temperature_celcius + " °C" // Assuming you have a temperature field
                        }
                        btn.isClickable = true
                        btn.isEnabled = true
                    }
                } else {
                    Timber.tag(TAG).e("API Temp Response failed: ${response.message()}")
                    withContext(Dispatchers.Main) {
                        btn.isClickable = true
                        btn.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e("API Temp Exception occurred :${e.message}")
                withContext(Dispatchers.Main) {
                    btn.isClickable = true
                    btn.isEnabled = true
                }
            }
        }
    }

    init {
        DevKitSensorSharedData.sensorDescription = description
        sensorDemoGridItemBinding.cardviewEnvTile.apply {
            sensorDemoGridItemBinding.envDescription.text = description
            sensorDemoGridItemBinding.envIcon.setImageDrawable(icon)
        }

        layoutParams = GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED, 1f),
            GridLayout.spec(GridLayout.UNDEFINED, 1f)
        ).apply {
            width = 0
        }

    }

    @SuppressLint("SetTextI18n")
    private fun showMicrophoneDialog(title: String, ipAddress: String) {
        var apiJob: Job? = null
        val devKitSensorDialog = Dialog(context, R.style.Style_Dialog_Rounded_Corner)
        devKitSensorDialog.setContentView(R.layout.dev_kit_sensor_917_dialog_layout)
        devKitSensorDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val dialogWidth = (context.resources.displayMetrics.widthPixels * 0.92f).toInt()
        devKitSensorDialog.window?.setLayout(
            dialogWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val header: TextView = devKitSensorDialog.findViewById(R.id.header)
        val subTitle: TextView = devKitSensorDialog.findViewById(R.id.subTitle)
        val yesBtn: MaterialButton = devKitSensorDialog.findViewById(R.id.yes_opt)
        val noBtn: MaterialButton = devKitSensorDialog.findViewById(R.id.no_opt)
        val image: ImageView = devKitSensorDialog.findViewById(R.id.image)
        val microphoneHolder = devKitSensorDialog.findViewById(R.id.txt_value) as TextView
        microphoneHolder.text = context.getString(R.string.dev_kit_sensor_micro_init_value)
        image.setImageResource(R.drawable.icon_sound)
        header.text = title + SPACE + context.getString(R.string.title_sensor)
        subTitle.text = title
        yesBtn.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                doInMicroPhoneBackground(microphoneHolder, ipAddress)
            }
        }
        noBtn.setOnClickListener {
            devKitSensorDialog.dismiss()
            if (apiJob != null) {
                apiJob?.cancel()
            }
        }
        devKitSensorDialog.show()

        apiJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                doInMicroPhoneBackground(microphoneHolder, ipAddress)
                delay(TIME_OUT)
            }
        }

    }

    @SuppressLint("SetTextI18n")
    private fun showAmbientLightDialog(title: String, ipAddress: String) {
        var apiJob: Job? = null
        val devKitSensorDialog = Dialog(context, R.style.Style_Dialog_Rounded_Corner)
        devKitSensorDialog.setContentView(R.layout.dev_kit_sensor_917_dialog_layout)
        devKitSensorDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val dialogWidth = (context.resources.displayMetrics.widthPixels * 0.92f).toInt()
        devKitSensorDialog.window?.setLayout(
            dialogWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val header: TextView = devKitSensorDialog.findViewById(R.id.header)
        val subTitle: TextView = devKitSensorDialog.findViewById(R.id.subTitle)
        val yesBtn: MaterialButton = devKitSensorDialog.findViewById(R.id.yes_opt)
        val noBtn: MaterialButton = devKitSensorDialog.findViewById(R.id.no_opt)
        val image: ImageView = devKitSensorDialog.findViewById(R.id.image)
        val abiHolder: TextView = devKitSensorDialog.findViewById(R.id.txt_value)

        abiHolder.text = context.getString(R.string.dev_kit_sensor_ambient_init_value)
        if (ambiResponse != null && ambiResponse!!.isNotEmpty()) {
            abiHolder.text = "$ambiResponse lx"
        }
        image.setImageResource(R.drawable.icon_light)
        header.text = title + SPACE + context.getString(R.string.title_sensor)
        subTitle.text = title

        yesBtn.setOnClickListener {
            yesBtn.isEnabled = false
            yesBtn.isClickable = false
            CoroutineScope(Dispatchers.IO).launch {
                doInAmbientBackground(abiHolder, yesBtn, ipAddress)
            }
        }
        noBtn.setOnClickListener {
            devKitSensorDialog.dismiss()
            if (apiJob != null) {
                apiJob?.cancel()
            }
        }
        devKitSensorDialog.show()

        apiJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                doInAmbientBackground(abiHolder, yesBtn, ipAddress)
                delay(TIME_OUT)
            }
        }

    }


    /* LED functionality Implementation */
    private fun imageForLightOn(view: ImageView, red: Boolean, blue: Boolean, green: Boolean) {

        view.setImageResource(R.drawable.light_on)
        var idColor = 0
        if (red && blue && green) {
            idColor = R.color.silabs_yellow
        } else if (red && blue) {
            idColor = R.color.silabs_magenta
        } else if (blue && green) {
            idColor = R.color.silabs_cyan_led
        } else if (green && red) {
            idColor = R.color.silabs_yellow_led
        } else if (blue) {
            idColor = R.color.silabs_blue
        } else if (red) {
            idColor = R.color.silabs_red
        } else if (green) {
            idColor = R.color.silabs_green
        } else {
            idColor = R.color.grey
        }
        view.setColorFilter(
            ContextCompat.getColor(context, idColor),
            android.graphics.PorterDuff.Mode.SRC_IN
        )
    }

    private fun showLEDControlDialog(title: String, ipAddress: String) {
        val apiJob: Job? = null
        val devKitSensorDialog = Dialog(context, R.style.Style_Dialog_Rounded_Corner)
        devKitSensorDialog.setContentView(
            R.layout.dev_kit_sesnor_917_led_control_dialog_layout
        )
        devKitSensorDialog.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val dialogWidth = (context.resources.displayMetrics.widthPixels * 0.92f).toInt()
        devKitSensorDialog.window?.setLayout(
            dialogWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val header: TextView = devKitSensorDialog.findViewById(R.id.header)
        val subTitle: TextView = devKitSensorDialog.findViewById(R.id.subTitle)
        val yesBtn: MaterialButton =
            devKitSensorDialog.findViewById(R.id.yes_opt)
        val noBtn: MaterialButton =
            devKitSensorDialog.findViewById(R.id.no_opt)
        onLEDBtn = devKitSensorDialog.findViewById(R.id.onButton)!!
        offLEDBtn = devKitSensorDialog.findViewById(R.id.offButton)!!
        redLEDBtn = devKitSensorDialog.findViewById(R.id.redButton)!!
        greenLEDBtn = devKitSensorDialog.findViewById(R.id.greenButton)!!
        blueLEDBtn = devKitSensorDialog.findViewById(R.id.blueButton)!!
        ledImageStatus = devKitSensorDialog.findViewById(R.id.imageLight)!!

        header.text = context.getString(R.string.led_control_characteristic_name)
        subTitle.text = title
        subTitle.visibility = View.GONE

        //LED Switch Off condition
        imageForLightOn(view = ledImageStatus, red = false, blue = false, green = false)
        setInitialOnOffBtnBackground()

        CoroutineScope(Dispatchers.IO).launch {
            getLedSwitchStatus(ipAddress)
        }

        yesBtn.setOnClickListener {
            refreshLEDStatus(ipAddress)
        }
        noBtn.setOnClickListener {
            devKitSensorDialog.dismiss()
            if (apiJob != null) {
                apiJob.cancel()
            }
        }
        onLEDBtn.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                setAllLEDOn(ledImageStatus, ipAddress)
            }
        }

        offLEDBtn.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                setAllLEDOff(ledImageStatus, ipAddress,context)
            }
        }

        redLEDBtn.setOnClickListener {
            btnRedStatus = !btnRedStatus
            setLEDControl(ipAddress)
        }
        greenLEDBtn.setOnClickListener {
            btnGreenStatus = !btnGreenStatus
            setLEDControl(ipAddress)
        }

        blueLEDBtn.setOnClickListener {
            btnBlueStatus = !btnBlueStatus
            setLEDControl(ipAddress)
        }

        devKitSensorDialog.setCanceledOnTouchOutside(false)
        devKitSensorDialog.show()
    }

    private fun switchOffLEDStatusOff(ipAddress: String) {
        CoroutineScope(Dispatchers.IO).launch {
            setLEDStatusOff(ledImageStatus, ipAddress)
        }
    }

    /** Initial: both ON and OFF = #7A7879 */
    private fun setInitialOnOffBtnBackground() {
        onLEDBtn.setBackgroundResource(R.drawable.button_background_grey_box)
        offLEDBtn.setBackgroundResource(R.drawable.button_background_grey_box)
        redLEDBtn.setBackgroundResource(R.drawable.button_background_grey_box)
        greenLEDBtn.setBackgroundResource(R.drawable.button_background_grey_box)
        blueLEDBtn.setBackgroundResource(R.drawable.button_background_grey_box)
    }

    /** ON pressed: ON = #333333, OFF = #7A7879 */
    private fun setOnBtnBackground() {
        onLEDBtn.setBackgroundResource(R.drawable.button_background_soft_black_box)
        offLEDBtn.setBackgroundResource(R.drawable.button_background_grey_box)
        redLEDBtn.setBackgroundResource(R.drawable.button_background_red_box)
        greenLEDBtn.setBackgroundResource(R.drawable.button_background_green_box)
        blueLEDBtn.setBackgroundResource(R.drawable.button_background_blue_box)
    }

    /** OFF pressed: OFF = #333333, ON = #7A7879 */
    private fun setOffBtnBackground() {
        onLEDBtn.setBackgroundResource(R.drawable.button_background_grey_box)
        offLEDBtn.setBackgroundResource(R.drawable.button_background_soft_black_box)
        redLEDBtn.setBackgroundResource(R.drawable.button_background_grey_box)
        greenLEDBtn.setBackgroundResource(R.drawable.button_background_grey_box)
        blueLEDBtn.setBackgroundResource(R.drawable.button_background_grey_box)
    }

    private fun applyOnOffToggleHighlight(ledsOn: Boolean) {
        if (ledsOn) {
            onLEDBtn.setBackgroundResource(R.drawable.button_background_soft_black_box)
            offLEDBtn.setBackgroundResource(R.drawable.button_background_grey_box)
        } else {
            onLEDBtn.setBackgroundResource(R.drawable.button_background_grey_box)
            offLEDBtn.setBackgroundResource(R.drawable.button_background_soft_black_box)
        }
    }

    private fun refreshLEDStatus(ipAddress: String) {
        CoroutineScope(Dispatchers.IO).launch {
            getAllLEDStatus(ledImageStatus, ipAddress)
        }
    }

    private fun setBtnColorBackground() {
        if (btnRedStatus) {
            redLEDBtn.setBackgroundResource(R.drawable.button_background_red_box)
        } else {
            redLEDBtn.setBackgroundResource(R.drawable.button_background_grey_box)
        }
        if (btnGreenStatus) {
            greenLEDBtn.setBackgroundResource(R.drawable.button_background_green_box)
        } else {
            greenLEDBtn.setBackgroundResource(R.drawable.button_background_grey_box)
        }
        if (btnBlueStatus) {
            blueLEDBtn.setBackgroundResource(R.drawable.button_background_blue_box)
        } else {
            blueLEDBtn.setBackgroundResource(R.drawable.button_background_grey_box)
        }
        applyOnOffToggleHighlight(btnRedStatus || btnGreenStatus || btnBlueStatus)
    }

    private fun setLEDControl(ipAddress: String) {
        CoroutineScope(Dispatchers.IO).launch {
            setLEDCtrl(
                ledImageStatus,
                btnRedStatus,
                btnGreenStatus,
                btnBlueStatus, ipAddress
            )
        }
    }

    private suspend fun setLEDCtrl(
        view: ImageView, red: Boolean, green: Boolean, blue: Boolean,
        ipAddress: String
    ) {
        withContext(Dispatchers.Default) {

            val url = "http://$ipAddress"
            val retro = Retrofit.Builder().baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create()).build()
            val ledStatus = retro.create(APIInterface::class.java)
            val params: MutableMap<String, String> = HashMap()
            var statusRed: String = OFF
            if (red) {
                statusRed = ON
            }
            params.put(RED, statusRed)
            var statusGreen = OFF
            if (green) {
                statusGreen = ON
            }
            params.put(GREEN, statusGreen)
            var statusBlue = OFF
            if (blue) {
                statusBlue = ON
            }
            params.put(BLUE, statusBlue)

            ledStatus.setRedLEDOn(params).enqueue(object : Callback<LEDResponse> {
                override fun onResponse(call: Call<LEDResponse>, response: Response<LEDResponse>) {
                    println("Response: $response")
                    if (response.isSuccessful) {
                        val data = response.body()
                        println("data: $data")

                        if (data != null) {
                            if (data.red.equals(OFF) && data.green.equals(OFF)
                                && data.blue.equals(OFF)
                            ) {
                                imageForLightOn(view, false, false, false)
                            } else {
                                imageForLightOn(view, red, blue, green)
                            }
                            setBtnColorBackground()
                        }
                    } else {
                            //ignore
                    }
                }

                override fun onFailure(call: Call<LEDResponse>, t: Throwable) {
                    btnRedStatus = false
                    btnBlueStatus = false
                    btnGreenStatus = false
                    setOffBtnBackground()
                    imageForLightOn(view, false, false, false)
                    /*Toast.makeText(
                        context,
                        "Failed to connect. Check your Wi-Fi connection",
                        Toast.LENGTH_SHORT
                    ).show()*/
                    CustomToastManager.show(
                        context,"Failed to connect. Check your Wi-Fi connection",
                        5000
                    )
                }

            })
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun setAllLEDOff(view: ImageView, ipAddress: String, context: Context) {
        withContext(Dispatchers.IO) {
            // 1. Validate ipAddress
            if (ipAddress.isBlank()) {
                // Handle the case where ipAddress is empty
                CustomToastManager.show(
                    context,
                    "IP address is empty",
                    5000
                )
                return@withContext
            }

            // Check if the device is connected to the internet
            if (!isNetworkAvailable(context)) {
                CustomToastManager.show(
                    context,
                    "No internet connection",
                    5000
                )
                return@withContext
            }

            // 2. Use HttpUrl.parse() for robustness
            val url = "http://$ipAddress".toHttpUrlOrNull()
            if (url == null) {
                // Handle the case where the URL is invalid
                CustomToastManager.show(
                    context,
                    "Invalid IP address format",
                    5000
                )
                return@withContext
            }

            val retro = Retrofit.Builder().baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create()).build()
            val ledStatus = retro.create(APIInterface::class.java)
            val body = mapOf(
                RED to OFF,
                GREEN to OFF,
                BLUE to OFF
            )
            ledStatus.setAllLEdsOnOff(body).enqueue(object : Callback<LEDResponse> {

                override fun onResponse(call: Call<LEDResponse>, response: Response<LEDResponse>) {
                    println("Response: $response")
                    if (response.isSuccessful) {
                        val data = response.body()
                        println("data: $data")
                        if (data != null) {
                            if (data.red.equals(OFF) && data.green.equals(OFF)
                                && data.blue.equals(OFF)
                            ) {
                                btnRedStatus = false
                                btnBlueStatus = false
                                btnGreenStatus = false

                                setOffBtnBackground()
                                imageForLightOn(view, btnRedStatus, btnBlueStatus, btnGreenStatus)
                            } else {
                                imageForLightOn(view, false, false, false)
                                setOffBtnBackground()
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<LEDResponse>, t: Throwable) {
                    btnRedStatus = false
                    btnBlueStatus = false
                    btnGreenStatus = false
                    setOffBtnBackground()
                    imageForLightOn(view, false, false, false)
                    CustomToastManager.show(
                        context,
                        "Failed to connect. Check your Wi-Fi connection",
                        5000
                    )

                }

            })

        }
    }

    private suspend fun setAllLEDOn(view: ImageView, ipAddress: String) {
        withContext(Dispatchers.IO) {
            val url = "http://$ipAddress"
            if(null != url){
                val retro = Retrofit.Builder().baseUrl(url)
                    .addConverterFactory(GsonConverterFactory.create()).build()
                val ledStatus = retro.create(APIInterface::class.java)
                val body = mapOf(
                    RED to ON,
                    GREEN to ON,
                    BLUE to ON
                )
                ledStatus.setAllLEdsOnOff(body).enqueue(object : Callback<LEDResponse> {
                    override fun onResponse(call: Call<LEDResponse>, response: Response<LEDResponse>) {
                        println("Response: $response")
                        if (response.isSuccessful) {
                            val data = response.body()
                            println("data: $data")
                            if (data != null) {
                                if (data.red.equals(OFF) && data.green.equals(OFF)
                                    && data.blue.equals(OFF)
                                ) {
                                    btnRedStatus = false
                                    btnBlueStatus = false
                                    btnGreenStatus = false
                                    setOffBtnBackground()
                                    imageForLightOn(
                                        view,
                                        btnRedStatus,
                                        btnBlueStatus,
                                        btnGreenStatus
                                    )
                                } else {
                                    btnRedStatus = true
                                    btnBlueStatus = true
                                    btnGreenStatus = true
                                    setOnBtnBackground()
                                    imageForLightOn(
                                        view,
                                        btnRedStatus,
                                        btnBlueStatus,
                                        btnGreenStatus
                                    )
                                }
                            }
                        }
                    }

                    override fun onFailure(call: Call<LEDResponse>, t: Throwable) {
                        btnRedStatus = false
                        btnBlueStatus = false
                        btnGreenStatus = false
                        setOffBtnBackground()
                        imageForLightOn(view, btnRedStatus, btnBlueStatus, btnGreenStatus)
                        /*Toast.makeText(
                            context,
                            "Failed to connect. Check your Wi-Fi connection",
                            Toast.LENGTH_SHORT
                        ).show()*/
                        CustomToastManager.show(
                            context,"Failed to connect. Check your Wi-Fi connection",
                            5000
                        )
                    }

                })
            }


        }
    }

    private suspend fun getLedSwitchStatus(ipAddress: String) {
        withContext(Dispatchers.IO) {
            try {
                val url = "http://$ipAddress"
                val retro = Retrofit.Builder().baseUrl(url)
                    .addConverterFactory(GsonConverterFactory.create()).build()
                val response = retro.create(APIInterface::class.java).getLedSwitchStatus()
                println("Response: $response")
                if (response.isSuccessful) {
                    val data = response.body()
                    println("data: $data")
                    if (data != null) {
                        if (data.status_led == ON)
                            switchOffLEDStatusOff(ipAddress)
                        else
                            refreshLEDStatus(ipAddress)
                    }
                } else {
                    Timber.tag(TAG).e("getLedSwitch Response failed: ${response.message()}")
                }
            } catch (e: Exception) {
                // Handle the exception
                Timber.tag(TAG).e("getLedSwitch Exception occurred ${e.message}")
            }
        }
    }

    private suspend fun setLEDStatusOff(view: ImageView, ipAddress: String) {
        withContext(Dispatchers.Default) {
            val url = "http://$ipAddress"
            val retro = Retrofit.Builder().baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create()).build()
            val ledStatus = retro.create(APIInterface::class.java)
            val body = mapOf(
                STATUS_LED to OFF
            )
            ledStatus.setLEDStatusOff(body).enqueue(object : Callback<LEDStatusResponse> {
                override fun onResponse(
                    call: Call<LEDStatusResponse>,
                    response: Response<LEDStatusResponse>
                ) {
                    println("Response: $response")
                    if (response.isSuccessful) {
                        val data = response.body()
                        println("data: $data")
                        if (data != null) {
                            //refresh the current LED status
                            refreshLEDStatus(ipAddress)
                        }
                    }
                }

                override fun onFailure(call: Call<LEDStatusResponse>, t: Throwable) {
                    btnRedStatus = false
                    btnBlueStatus = false
                    btnGreenStatus = false
                    setOffBtnBackground()
                    imageForLightOn(view, false, false, false)
                    /*Toast.makeText(
                        context,
                        "Failed to connect. Check your Wi-Fi connection",
                        Toast.LENGTH_SHORT
                    ).show()
           */
                    CustomToastManager.show(
                        context,"Failed to connect. Check your Wi-Fi connection",
                        5000
                    )
                }

            })
        }
    }

    private suspend fun getAllLEDStatus(view: ImageView, ipAddress: String) {
        withContext(Dispatchers.Default) {
            try {
                val url = "http://$ipAddress"
                val retro = Retrofit.Builder().baseUrl(url)
                    .addConverterFactory(GsonConverterFactory.create()).build()
                val response = retro.create(APIInterface::class.java).getLedStatus()
                println("Response: $response")
                if (response.isSuccessful) {
                    val data = response.body()
                    println("data: $data")
                    if (data != null) {
                        withContext(Dispatchers.Main) {
                            btnRedStatus = data.red == ON
                            btnGreenStatus = data.green == ON
                            btnBlueStatus = data.blue == ON
                            imageForLightOn(view, btnRedStatus, btnBlueStatus, btnGreenStatus)
                            setBtnColorBackground()
                        }
                    }
                } else {
                    Timber.tag(TAG).e("getAllLEDStatus Response failed: ${response.message()}")
                }
            } catch (e: Exception) {
                // Handle the exception
                Timber.tag(TAG).e("getAllLEDStatus Exception occurred: ${e.message}")
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showHumidityDialog(title: String, ipAddress: String) {
        var apiJob: Job? = null
        val devKitSensorDialog = Dialog(context, R.style.Style_Dialog_Rounded_Corner)
        devKitSensorDialog.setContentView(
            R.layout.dev_kit_sensor_917_dialog_layout
        )
        devKitSensorDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val dialogWidth = (context.resources.displayMetrics.widthPixels * 0.92f).toInt()
        devKitSensorDialog.window?.setLayout(
            dialogWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val header: TextView = devKitSensorDialog.findViewById(R.id.header)
        val subTitle: TextView = devKitSensorDialog.findViewById(R.id.subTitle)
        val yesBtn: MaterialButton = devKitSensorDialog.findViewById(R.id.yes_opt)
        val noBtn: MaterialButton = devKitSensorDialog.findViewById(R.id.no_opt)
        val image: ImageView = devKitSensorDialog.findViewById(R.id.image)
        val humidityHolder: TextView = devKitSensorDialog.findViewById(R.id.txt_value)
        humidityHolder.text = context.getString(R.string.dev_kit_sensor_humidity_init_value)
        if (humiResponse != null && humiResponse!!.isNotEmpty()) {
            humidityHolder.text = "$humiResponse %"
        }
        image.setImageResource(R.drawable.icon_environment)
        header.text = title + SPACE + context.getString(R.string.title_sensor)
        subTitle.text = title
        yesBtn.setOnClickListener {
            yesBtn.isEnabled = false
            yesBtn.isClickable = false
            CoroutineScope(Dispatchers.IO).launch {
                doInHumidityBackground(humidityHolder, yesBtn, ipAddress)
            }
        }
        noBtn.setOnClickListener {
            devKitSensorDialog.dismiss()
            if (apiJob != null) {
                apiJob?.cancel()
            }
        }
        devKitSensorDialog.show()

        apiJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                doInHumidityBackground(humidityHolder, yesBtn, ipAddress)
                delay(TIME_OUT)
            }
        }

    }


    override fun fetchData(response: SensorsResponse?) {
        if (response != null) {
            //Temp
            if (response.temperature.temperature_celcius.isNotEmpty()) {
                tempResponse = response.temperature.temperature_celcius
            }
            //Humi
            if (response.humidity.humidity_percentage.isNotEmpty()) {
                humiResponse = response.temperature.temperature_celcius
            }
            //Ambi
            if (response.light.ambient_light_lux.isNotEmpty()) {
                ambiResponse = response.light.ambient_light_lux
            }
        }
    }


    companion object {
        const val SPACE = " "
        const val TEMPERATURE = "Temperature"
        const val HUMIDITY = "Humidity"
        const val AMBIENT_LIGHT = "Ambient Light"
        const val MICROPHONE = "Microphone"
        const val LED = "LED"
        const val MOTION = "Motion"
        const val TIME_OUT = 2000L
        const val OFF = "off"
        const val ON = "on"
        const val RED = "red"
        const val GREEN = "green"
        const val BLUE = "blue"
        const val STATUS_LED = "status_led"
        private val TAG = Companion::class.java.simpleName.toString()
    }


}