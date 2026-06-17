package com.siliconlabs.bledemo.features.demo.wifi_ota_update

import android.app.Dialog
import android.content.Context
import android.widget.Button
import android.widget.Chronometer
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.siliconlabs.bledemo.R

class WiFiOtaProgressDialog(context: Context) : Dialog(context) {

    var progressBar: ProgressBar
    var dataRate: TextView
    var dataSize: TextView
    var filename: TextView
    var steps: TextView
    var chrono: Chronometer
    var btnOtaEnd: MaterialButton
    var btnCancel: Button
    var sizename: TextView
    var firmwareStatus: TextView
    var wifiIpAddress: TextView
    var wifiPort: TextView


    init {
        setContentView(R.layout.dialog_wifi_ota_progress)
        window?.apply {
            setLayout(
                    (context.resources.displayMetrics.widthPixels * 0.9f).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
            )
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }

        progressBar = findViewById(R.id.ota_progress_bar)
        dataRate = findViewById(R.id.data_rate)
        dataSize = findViewById(R.id.ota_progress_percent)
        filename = findViewById(R.id.file_name)
        steps = findViewById(R.id.ota_number_of_steps)
        chrono = findViewById(R.id.ota_chronometer)
        btnOtaEnd = findViewById(R.id.otabutton)
        btnCancel = findViewById(R.id.cancel)
        sizename = findViewById(R.id.file_size)
        firmwareStatus = findViewById(R.id.text_firmware_status)
        wifiIpAddress = findViewById(R.id.wifi_ip_address)
        wifiPort = findViewById(R.id.wifi_port)
    }

    override fun show() {
        super.show()
        setCanceledOnTouchOutside(false)
    }

    fun setProgressInfo(fileName: String?, fileSize: Int?, ipAddress: String?, port: String?) {
        filename.text = fileName
        steps.text = context.getString(R.string.iop_test_label_1_of_1)
        sizename.text = context.getString(R.string.iop_test_n_bytes, fileSize)
        wifiIpAddress.text = ipAddress
        wifiPort.text = port
    }

}