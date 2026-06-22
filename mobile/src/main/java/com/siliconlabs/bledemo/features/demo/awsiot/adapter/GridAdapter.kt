package com.siliconlabs.bledemo.features.demo.awsiot.adapter

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.PorterDuff
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.semantics.text
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.features.demo.awsiot.AWSIOTDemoActivity
import com.siliconlabs.bledemo.features.demo.awsiot.listener.OnMqttGridItemClickListener
import com.siliconlabs.bledemo.features.demo.awsiot.model.GridItem
import com.siliconlabs.bledemo.features.demo.awsiot.model.SensorData
import com.siliconlabs.bledemo.features.demo.awsiot.viewmodel.MqttViewModel
import com.siliconlabs.bledemo.features.demo.devkitsensor917.model.AccelerometerGyroScopeResponse
import com.siliconlabs.bledemo.features.demo.devkitsensor917.utils.DevKitSensorControl.Companion.SPACE
import com.siliconlabs.bledemo.features.demo.thunderboard_demos.demos.environment.control.EnvironmentControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class GridAdapter(private var items: List<GridItem>,
                  private val listener: OnMqttGridItemClickListener,
                  private val mqttViewModel: MqttViewModel,
                  private val lifecycleOwner: LifecycleOwner  ) : RecyclerView.Adapter<GridAdapter.ViewHolder>() {
    // Use AtomicBoolean to ensure thread safety
    private val isMotionDialogShowing = AtomicBoolean(false)
    private var currentDialog: Dialog? = null // Keep track of the currently displayed dialog
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconImageView: ImageView = view.findViewById(R.id.itemIcon)
        val titleTextView: TextView = view.findViewById(R.id.itemTitle)
        val valueTextView: TextView = view.findViewById(R.id.itemValue)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.grid_aws_item, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.iconImageView.setImageResource(item.iconResId)
        when (item.title) {
            "Ambient_light" -> holder.titleTextView.text = "Ambient Light"
            "temperature" -> holder.titleTextView.text = "Temperature"
            "White_light" -> holder.titleTextView.text = "White Light"
            "humidity" -> holder.titleTextView.text = "Humidity"
            else -> holder.titleTextView.text = item.title // Default case
        }
        if(item.title == "Motion"){
            holder.valueTextView.text = ""
        }else if(item.title.equals("Temperature",true)){
            holder.valueTextView.text = item.value.toString() + holder.itemView.context.getString(R.string.beacon_details_dialog_unit_degrees_celsius)
        }else if(item.title.equals("Humidity",true)){
            holder.valueTextView.text = String.format(holder.itemView.context.getString(R.string.environment_humidity_measure), item.value.toInt())
        }else if(item.title.equals("Ambient_light",true) || item.title.equals("White_light",true)){
            holder.valueTextView.text = item.value.toString().plus("lx")
        }else{
            holder.valueTextView.text = item.value
        }

        holder.itemView.setOnClickListener {
            if(item.title == "Motion"){
                mqttViewModel.mqttMessages.observe(lifecycleOwner, Observer {
                    if(AWSIOTDemoActivity.isValidJsonObject(it)){
                        val jsonObject = JSONObject(it)
                        var accelerometerData = ""
                        var gyroData = ""

                        jsonObject.keys().forEach { key ->
                            val value = jsonObject.get(key)

                            when (key) {
                                "accelerometer" -> {
                                    val acc = value as JSONObject
                                    accelerometerData =
                                        "Acc: (${acc.getDouble("x")}, ${acc.getDouble("y")}, ${acc.getDouble("z")})"
                                }

                                "gyro" -> {
                                    val gyro = value as JSONObject
                                    gyroData =
                                        "Gyro: (${gyro.getDouble("x")}, ${gyro.getDouble("y")}, ${gyro.getDouble("z")})"
                                }


                            }
                        }

                        // Combine Accelerometer and Gyro into one "Motion" tile
                        if (accelerometerData.isNotEmpty() && gyroData.isNotEmpty()) {
                            val motionData = "$accelerometerData\n$gyroData"
                            // Only show the dialog if it's not currently showing and when the user clicks it.
                            /*if(holder.itemView.isPressed) {
                                if (!isMotionDialogShowing.get()) {
                                    showMotionDetails(holder.itemView.context, motionData, position)
                                }
                            }*/

                            //showMotionDetails(holder.itemView.context,motionData,position)
                            if (!isMotionDialogShowing.get() && holder.itemView.isPressed) {
                                showMotionDetails(holder.itemView.context, motionData, position)
                            } else if (isMotionDialogShowing.get()) {
                                updateMotionDetails(motionData)
                            }
                        }
                    }

                })

            }
            if(item.title == "LED"){
                showLEDControlDialog(item.title,holder.itemView.context)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showLEDControlDialog(title: String, context: Context) {
         var btnRedStatus = false
         var btnGreenStatus = false
         var btnBlueStatus = false
        val apiJob: Job? = null
        val devKitSensorDialog = Dialog(context, R.style.Style_Dialog_Rounded_Corner)
        devKitSensorDialog.setContentView(
            R.layout.dev_mqtt_led
        )
        devKitSensorDialog.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val header: TextView = devKitSensorDialog.findViewById(R.id.header)
        val subTitle: TextView = devKitSensorDialog.findViewById(R.id.subTitle)
        val yesBtn: TextView = devKitSensorDialog.findViewById(R.id.yes_opt)
        yesBtn.visibility = View.GONE
        val noBtn: TextView = devKitSensorDialog.findViewById(R.id.no_opt)
        val onLEDBtn = devKitSensorDialog.findViewById<MaterialButton>(R.id.onButton)
        onLEDBtn.visibility = View.GONE
        val offLEDBtn = devKitSensorDialog.findViewById<MaterialButton>(R.id.offButton)
        offLEDBtn.visibility = View.GONE
        val redLEDBtn = devKitSensorDialog.findViewById<MaterialButton>(R.id.redButton)
        val greenLEDBtn = devKitSensorDialog.findViewById<MaterialButton>(R.id.greenButton)
        val blueLEDBtn = devKitSensorDialog.findViewById<MaterialButton>(R.id.blueButton)
        val ledImageStatus = devKitSensorDialog.findViewById<ImageView>(R.id.imageLight)

        header.text = title + SPACE + context.getString(R.string.title_control)


        //LED Switch Off condition
        //1. Initially all are in Off Condition
        imageForLightOn(view = ledImageStatus, red = false, blue = false, green = false, context = context)
        setBtnColorBackground(false, false, false, redLEDBtn, greenLEDBtn, blueLEDBtn,context)

        //Click on Dismiss Button
        noBtn.setOnClickListener {
            //listener.onCloseButtonClicked(buttonClickedStatus = true)
            devKitSensorDialog.dismiss()
        }

        //Click on RedLED Button
        redLEDBtn.setOnClickListener {
            listener.onItemClick(ledRedButtonControlStatus = true,
                ledGreenButtonControlStatus = false,ledBlueButtonControlStatus = false)
            btnRedStatus = !btnRedStatus
            btnGreenStatus = false
            btnBlueStatus = false
            setLEDControl("", ledImageStatus, btnRedStatus, btnGreenStatus, btnBlueStatus, greenLEDBtn, redLEDBtn, blueLEDBtn, context)
        }
        //Click on GREEN Led Button
        greenLEDBtn.setOnClickListener {
            listener.onItemClick(ledRedButtonControlStatus = false,
                ledGreenButtonControlStatus = true,ledBlueButtonControlStatus = false)
            btnGreenStatus = !btnGreenStatus
            btnRedStatus = false
            btnBlueStatus = false
            setLEDControl("", ledImageStatus, btnRedStatus, btnGreenStatus, btnBlueStatus, greenLEDBtn, redLEDBtn, blueLEDBtn, context)
        }

        //Click on BLUE led Button
        blueLEDBtn.setOnClickListener {
            listener.onItemClick(ledRedButtonControlStatus = false,
                ledGreenButtonControlStatus = false,ledBlueButtonControlStatus = true)
            btnBlueStatus = !btnBlueStatus
            btnRedStatus = false
            btnGreenStatus = false
            setLEDControl("", ledImageStatus, btnRedStatus, btnGreenStatus, btnBlueStatus, greenLEDBtn, redLEDBtn, blueLEDBtn, context)
        }

        devKitSensorDialog.setCanceledOnTouchOutside(false)
        devKitSensorDialog.show()
    }

    private fun setLEDControl(
        ipAddress: String,
        ledImageStatus: ImageView,
        btnRedStatus: Boolean,
        btnGreenStatus: Boolean,
        btnBlueStatus: Boolean,
        greenLEDBtn: Button,
        redLEDBtn: Button,
        blueLEDBtn: Button,
        context: Context
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            setLEDCtrl(
                ledImageStatus,
                btnRedStatus,
                btnGreenStatus,
                btnBlueStatus, ipAddress, redLEDBtn, greenLEDBtn, blueLEDBtn, context
            )
        }
    }

    private suspend fun setLEDCtrl(
        view: ImageView, red: Boolean, green: Boolean, blue: Boolean,
        ipAddress: String, redLEDBtn: Button, greenLEDBtn: Button, blueLEDBtn: Button, context: Context
    ) {
        withContext(Dispatchers.Default) {
            imageForLightOn(view, red, green, blue, context)
            setBtnColorBackground(red, green, blue, redLEDBtn, greenLEDBtn, blueLEDBtn, context)
        }
    }

    private fun setBtnColorBackground(
        btnRedStatus: Boolean,
        btnGreenStatus: Boolean,
        btnBlueStatus: Boolean,
        redLEDBtn: Button,
        greenLEDBtn: Button,
        blueLEDBtn: Button,
        context: Context
    ) {
        when {
            btnRedStatus -> {
                redLEDBtn.backgroundTintList = ContextCompat.getColorStateList(context,R.color.silabs_red)
                greenLEDBtn.backgroundTintList = ContextCompat.getColorStateList(context,R.color.grey)
                blueLEDBtn.backgroundTintList = ContextCompat.getColorStateList(context,R.color.grey)
            }
            btnGreenStatus -> {
                redLEDBtn.backgroundTintList = ContextCompat.getColorStateList(context,R.color.grey)
                greenLEDBtn.backgroundTintList = ContextCompat.getColorStateList(context,R.color.silabs_green)
                blueLEDBtn.backgroundTintList = ContextCompat.getColorStateList(context,R.color.grey)
            }
            btnBlueStatus -> {
                redLEDBtn.backgroundTintList = ContextCompat.getColorStateList(context,R.color.grey)
                greenLEDBtn.backgroundTintList = ContextCompat.getColorStateList(context,R.color.grey)
                blueLEDBtn.backgroundTintList = ContextCompat.getColorStateList(context,R.color.silabs_blue)
            }
            else -> {
                // All buttons are off
                redLEDBtn.backgroundTintList = ContextCompat.getColorStateList(context,R.color.grey)
                greenLEDBtn.backgroundTintList = ContextCompat.getColorStateList(context,R.color.grey)
                blueLEDBtn.backgroundTintList = ContextCompat.getColorStateList(context,R.color.grey)
            }
        }
    }



    private fun imageForLightOn(
        view: ImageView,
        red: Boolean,
        green: Boolean,
        blue: Boolean,
        context: Context
    ) {
        view.setImageResource(R.drawable.light_on) // Set the light bulb image

        val colorId = when {
            red -> R.color.silabs_red // Red only
            green -> R.color.silabs_green // Green only
            blue -> R.color.silabs_blue // Blue only
            else -> R.color.grey // All off (grey)
        }

        view.setColorFilter(
            ContextCompat.getColor(context, colorId),
            PorterDuff.Mode.SRC_IN
        )
    }


    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<GridItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun parseSensorData(dataString: String): SensorData? {
        return try {
            // Split the string into accelerometer and gyro parts
            val parts = dataString.split("Gyro:")
            if (parts.size != 2) {
                throw IllegalArgumentException("Invalid data format")
            }

            val accString = parts[0].replace("Acc: ", "").trim()
            val gyroString = parts[1].trim()

            // Parse the accelerometer and gyro strings
            val accelerometer = parseAccelerometer(accString)
            val gyro = parseGyroscope(gyroString)

            SensorData(accelerometer, gyro)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun parseAccelerometer(accString: String): AccelerometerGyroScopeResponse {
        // Remove parentheses and split by comma
        val values = accString.replace("(", "").replace(")", "").split(",")
        if (values.size != 3) {
            throw IllegalArgumentException("Invalid accelerometer format")
        }

        val x = values[0].trim().toDouble()
        val y = values[1].trim().toDouble()
        val z = values[2].trim().toDouble()

        return AccelerometerGyroScopeResponse(x.toString(), y.toString(), z.toString())
    }

    fun parseGyroscope(gyroString: String): AccelerometerGyroScopeResponse {
        // Remove parentheses and split by comma
        val values = gyroString.replace("(", "").replace(")", "").split(",")
        if (values.size != 3) {
            throw IllegalArgumentException("Invalid gyroscope format")
        }

        val x = values[0].trim().toDouble()
        val y = values[1].trim().toDouble()
        val z = values[2].trim().toDouble()

        return AccelerometerGyroScopeResponse(x.toString(), y.toString(), z.toString())
    }

    private fun showMotionDetails(context: Context, motionData: String, position: Int)
    {
        // Split the motionData string into Accelerometer and Gyro parts
        Log.e("", "Just Motion Data ${motionData}")
        val sensorData = parseSensorData(motionData)
        Log.e("", "ConnectedLightingScreen Sensor Data ${sensorData.toString()}")
        if (sensorData != null) {
            val accelerometer = sensorData.accelerometer
            val gyro = sensorData.gyro

            println("Accelerometer: x=${accelerometer.x}, y=${accelerometer.y}, z=${accelerometer.z}")
            println("Gyroscope: x=${gyro.x}, y=${gyro.y}, z=${gyro.z}")

            currentDialog = Dialog(context, R.style.Style_Dialog_Rounded_Corner)
            currentDialog!!.setContentView(R.layout.dev_kit_sesnor_917_motion_control_dialog_layout)
            currentDialog!!.window?.setLayout(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            // Set the flag to true when the dialog is shown
            isMotionDialogShowing.set(true)
            val header: TextView = currentDialog!!.findViewById(R.id.header)
            val yesBtn: TextView = currentDialog!!.findViewById(R.id.yes_opt)
            yesBtn.visibility = View.GONE
            val noBtn: TextView = currentDialog!!.findViewById(R.id.no_opt)
            val orientationX: TextView = currentDialog!!.findViewById(R.id.orient_x)
            val orientationY: TextView = currentDialog!!.findViewById(R.id.orientation_y)
            val orientationZ: TextView = currentDialog!!.findViewById(R.id.orientation_z)
            val degreeString = context.getString(R.string.motion_orientation_degree)
            if (gyro != null && gyro!!.x.isNotEmpty()) {
                orientationX.text = String.format(degreeString, gyro!!.x.toFloat())
            } else {
                orientationX.text = String.format(degreeString, 00f)
            }
            if (gyro != null && gyro!!.y.isNotEmpty()) {
                orientationY.text = String.format(degreeString, gyro!!.y.toFloat())
            } else {
                orientationY.text = String.format(degreeString, 00f)
            }
            if (gyro != null && gyro!!.z.isNotEmpty()) {
                orientationZ.text = String.format(degreeString, gyro!!.z.toFloat())
            } else {
                orientationZ.text = String.format(degreeString, 00f)
            }

            val accelerationString = context.getString(R.string.motion_acceleration_g)
            val acceloX: TextView = currentDialog!!.findViewById(R.id.accelo_x)
            val acceloY: TextView = currentDialog!!.findViewById(R.id.accelo_y)
            val acceloZ: TextView = currentDialog!!.findViewById(R.id.accelo_z)
            if (accelerometer != null && accelerometer.x.isNotEmpty()) {
                acceloX.text = String.format(accelerationString, accelerometer.x.toFloat())
            } else {
                acceloX.text = String.format(accelerationString, 00F)
            }
            if (accelerometer != null && accelerometer.y.isNotEmpty()) {
                acceloY.text = String.format(accelerationString, accelerometer.y.toFloat())
            } else {
                acceloY.text = String.format(accelerationString, 00F)
            }
            if (accelerometer != null && accelerometer.z.isNotEmpty()) {
                acceloZ.text = String.format(accelerationString, accelerometer.z.toFloat())
            } else {
                acceloZ.text = String.format(accelerationString, 00F)
            }


            noBtn.setOnClickListener {
                currentDialog!!.dismiss()

            }
            header.text = "Motion" + SPACE + context.getString(R.string.title_sensor)
            currentDialog!!.setOnDismissListener {
                // Set the flag to false when the dialog is dismissed
                isMotionDialogShowing.set(false)
                currentDialog = null
            }
            currentDialog!!.show()
        }




    }

    private fun updateMotionDetails(motionData: String) {
        if (currentDialog != null && currentDialog!!.isShowing) {
            val sensorData = parseSensorData(motionData)
            if (sensorData != null) {
                val accelerometer = sensorData.accelerometer
                val gyro = sensorData.gyro
                val orientationX: TextView = currentDialog!!.findViewById(R.id.orient_x)
                val orientationY: TextView = currentDialog!!.findViewById(R.id.orientation_y)
                val orientationZ: TextView = currentDialog!!.findViewById(R.id.orientation_z)
                val degreeString = currentDialog!!.context.getString(R.string.motion_orientation_degree)
                if (gyro != null && gyro.x.isNotEmpty()) {
                    orientationX.text = String.format(degreeString, gyro.x.toFloat())
                } else {
                    orientationX.text = String.format(degreeString, 00f)
                }
                if (gyro != null && gyro.y.isNotEmpty()) {
                    orientationY.text = String.format(degreeString, gyro.y.toFloat())
                } else {
                    orientationY.text = String.format(degreeString, 00f)
                }
                if (gyro != null && gyro.z.isNotEmpty()) {
                    orientationZ.text = String.format(degreeString, gyro.z.toFloat())
                } else {
                    orientationZ.text = String.format(degreeString, 00f)
                }

                val accelerationString = currentDialog!!.context.getString(R.string.motion_acceleration_g)
                val acceloX: TextView = currentDialog!!.findViewById(R.id.accelo_x)
                val acceloY: TextView = currentDialog!!.findViewById(R.id.accelo_y)
                val acceloZ: TextView = currentDialog!!.findViewById(R.id.accelo_z)
                if (accelerometer != null && accelerometer.x.isNotEmpty()) {
                    acceloX.text = String.format(accelerationString, accelerometer.x.toFloat())
                } else {
                    acceloX.text = String.format(accelerationString, 00F)
                }
                if (accelerometer != null && accelerometer.y.isNotEmpty()) {
                    acceloY.text = String.format(accelerationString, accelerometer.y.toFloat())
                } else {
                    acceloY.text = String.format(accelerationString, 00F)
                }
                if (accelerometer != null && accelerometer.z.isNotEmpty()) {
                    acceloZ.text = String.format(accelerationString, accelerometer.z.toFloat())
                } else {
                    acceloZ.text = String.format(accelerationString, 00F)
                }
            }
        }
    }
}