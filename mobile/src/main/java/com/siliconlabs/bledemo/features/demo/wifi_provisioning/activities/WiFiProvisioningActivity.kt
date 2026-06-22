package com.siliconlabs.bledemo.features.demo.wifi_provisioning.activities

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.databinding.ActivityWifiProvisioningBinding
import com.siliconlabs.bledemo.features.demo.devkitsensor917.model.ProvisionResponse
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.CustomProgressDialog
import com.siliconlabs.bledemo.features.demo.wifi_provisioning.adapters.APAdapter
import com.siliconlabs.bledemo.features.demo.wifi_provisioning.fragment.WiFiInputDialogFragment
import com.siliconlabs.bledemo.features.demo.wifi_provisioning.interfaces.WiFiProvisionInterface
import com.siliconlabs.bledemo.features.demo.wifi_provisioning.model.ScanResult
import com.siliconlabs.bledemo.utils.AppUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber

class WiFiProvisioningActivity : AppCompatActivity(),
    WiFiInputDialogFragment.DialogResultListener {

    private lateinit var binding: ActivityWifiProvisioningBinding
    private var customProgressDialog: CustomProgressDialog? = null
    private lateinit var apAdapter: APAdapter
    private var scanResults = ArrayList<ScanResult>()
    private var isItemClicked = false
    private var clickedAccessPoint: ScanResult? = null
    private var wifiInputDialog: WiFiInputDialogFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWifiProvisioningBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppUtil.setEdgeToEdge(window, this)
        setSupportActionBar(binding.toolbar)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.matter_back)
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.title = this.getString(R.string.wifi_provisioning_label)
        }
        initScannerPlaceHolder()
        if (isNetworkAvailable(this)) {
            binding.placeholder.visibility = View.GONE
            binding.scannerPlaceHolder.visibility = View.VISIBLE
            binding.scannerPlaceDesc.visibility = View.GONE
            showProgressDialog(this.getString(R.string.dev_kit_progress_bar_message))
            setupRecyclerView(apAdapter)
            lifecycleScope.launch {
                kotlinx.coroutines.delay(2000L)
                doInAPScanBackground(IP_ADDRESS)
            }
        } else {
            binding.scannerPlaceHolder.visibility = View.GONE
            binding.placeholder.visibility = View.VISIBLE
            binding.scannerPlaceDesc.visibility = View.GONE
            Toast.makeText(
                this, getString(R.string.wifi_provisioning_wifi_setting_on),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun initScannerPlaceHolder() {
        binding.scannerPlace.layoutManager = LinearLayoutManager(this)
        apAdapter = APAdapter(scanResults, object : APAdapter.OnItemClickListener {
            override fun onItemClick(itemView: View?, position: Int) {
                onAccessPointClicked(position)
            }
        })
    }


    private fun displayErrorMessage(code: Int) {
        val res = when (code) {
            404 -> getString(R.string.wifi_provisioning_error_404)
            405 -> getString(R.string.wifi_provisioning_error_405)
            400 -> getString(R.string.wifi_provisioning_error_400)
            else -> getString(R.string.wifi_provisioning_error_unknown)
        }
        showErrorDialog(res)
    }


    @SuppressLint("SetTextI18n")
    private suspend fun doInAPScanBackground(ipAddress: String) {
        withContext(Dispatchers.IO) {
            try {
                val url = "http://$ipAddress"
                val retro = Retrofit.Builder().baseUrl(url)
                    .addConverterFactory(GsonConverterFactory.create()).build()
                val response =
                    retro.create(WiFiProvisionInterface::class.java).getWiFiProvisionScanner()

                println("Response: $response")
                if (response.isSuccessful) {
                    val data = response.body()
                    println("data: $data")
                    withContext(Dispatchers.Main) {
                        if (data != null) {
                            removeProgress()
                            updateAdapter(data.scan_results)
                        } else {
                            hideScannerPlaceholder()
                            displayErrorMessage(response.code())
                        }
                    }
                } else {
                    removeProgress()
                    runOnUiThread {
                        hideScannerPlaceholder()
                        displayErrorMessage(response.code())
                    }
                    Timber.tag(TAG).e("API Scan Response failed:${response.message()}")
                }
            } catch (e: Exception) {
                removeProgress()
                hideScannerPlaceholder()
                showErrorDialog(getString(R.string.wifi_provisioning_error_message_unable_connect_to_server))
                Timber.tag(TAG).e("API Scan Exception occurred ${e.message}")
            }
        }
    }

    private fun updateAdapter(scanResult: List<ScanResult>) {
        binding.scannerPlaceDesc.visibility = View.VISIBLE
        binding.scannerPlaceHolder.visibility = View.VISIBLE
        apAdapter.updateData(scanResult)
    }

    private fun hideScannerPlaceholder() {
        runOnUiThread {
            binding.placeholder.visibility = View.VISIBLE
            binding.scannerPlaceHolder.visibility = View.GONE
            binding.scannerPlace.visibility = View.GONE
        }
    }

    private fun setupRecyclerView(apAdapter: APAdapter) {
        binding.scannerPlaceHolder.visibility = View.VISIBLE
        binding.scannerPlace.visibility = View.VISIBLE
        binding.scannerPlace.adapter = apAdapter
    }

    private fun showProgressDialog(message: String) {
        runOnUiThread {
            customProgressDialog = CustomProgressDialog(this)
            customProgressDialog!!.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            customProgressDialog!!.setMessage(message)
            customProgressDialog!!.show()
        }
    }

    private fun showWiFiProvisioningDialog(accessPoint: ScanResult?) {
        if (accessPoint != null && !accessPoint.requiresPassword()) {
            provisionAccessPoint(accessPoint, passphrase = "")
            return
        }
        // Build and display the alert dialog
        if (accessPoint != null) {
            if (wifiInputDialog == null) {
                wifiInputDialog = WiFiInputDialogFragment.newInstance(
                    accessPoint.ssid,
                    accessPoint.bssid, accessPoint.rssi, accessPoint.security_type
                )
                wifiInputDialog!!.show(supportFragmentManager, DIALOG_WIFI_PROV_INPUT_TAG)
            }
        }
    }

    private fun removeProgress() {
        runOnUiThread {
            if (customProgressDialog?.isShowing == true) {
                customProgressDialog?.dismiss()
            }
        }
    }

    private fun onAccessPointClicked(position: Int) {
        isItemClicked = true
        clickedAccessPoint = apAdapter.getItem(position)
        val accessPoint = clickedAccessPoint ?: return
        removeAlert()
        if (accessPoint.requiresPassword()) {
            showWiFiProvisioningDialog(accessPoint)
        } else {
            provisionAccessPoint(accessPoint, passphrase = "")
        }
    }

    private fun provisionAccessPoint(accessPoint: ScanResult, passphrase: String) {
        showProgressDialog(getString(R.string.dev_kit_progress_bar_message))
        lifecycleScope.launch {
            doInAPConnectBackground(
                IP_ADDRESS,
                accessPoint.ssid,
                passphrase,
                accessPoint.security_type
            )
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

    override fun onDestroy() {
        super.onDestroy()
        removeAlert()
    }

    private fun showProvisionSuccessAlert() {
        val customView = layoutInflater.inflate(R.layout.wifi_provision_success_dialog, null)
        val alertDialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(customView)
            .create()
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        alertDialog.setOnShowListener {
            val width = (resources.displayMetrics.widthPixels * 0.75).toInt() // 75% of screen width
            alertDialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val submitButton = customView.findViewById<Button>(R.id.btnSubmit)
        submitButton.setOnClickListener {
            alertDialog.dismiss()
            this.finish()
        }
        alertDialog.show()
    }

    override fun onDialogResult(ssid: String, passphrase: String, securityType: String) {
        provisionAccessPoint(
            ScanResult(
                ssid = ssid,
                security_type = securityType,
                network_type = "",
                bssid = clickedAccessPoint?.bssid.orEmpty(),
                channel = clickedAccessPoint?.channel.orEmpty(),
                rssi = clickedAccessPoint?.rssi.orEmpty()
            ),
            passphrase = passphrase
        )
    }

    override fun onDialogCancel() {
        removeAlert()
    }


    @SuppressLint("SetTextI18n")
    private suspend fun doInAPConnectBackground(
        ipAddress: String,
        ssid: String,
        passphrase: String,
        securityType: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                val url = "http://$ipAddress"
                // val okHttpClient = setHTTPstatus()
                val retro = Retrofit.Builder().baseUrl(url)
                    //     .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create()).build()
                val provisionStatus = retro.create(WiFiProvisionInterface::class.java)
                val body = mapOf(
                    AP_SSID to ssid,
                    AP_PASSPHRASE to passphrase,
                    AP_SECURITY_TYPE to securityType
                )
                provisionStatus.setWiFiProvisionConnect(body)
                    .enqueue(object : Callback<ProvisionResponse> {
                        override fun onResponse(
                            call: Call<ProvisionResponse>,
                            response: Response<ProvisionResponse>
                        ) {
                            println("Response: $response")
                            if (response.isSuccessful) {
                                val data = response.body()
                                println("data: $data")
                                removeProgress()
                                if (data != null) {
                                    removeAlert()
                                    showProvisionSuccessAlert()
                                }
                            }
                        }

                        override fun onFailure(call: Call<ProvisionResponse>, t: Throwable) {
                            runOnUiThread {
                                removeProgress()
                                Toast.makeText(
                                    baseContext,
                                    "Failed to connect. Check your Wi-Fi connection",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                    })
            } catch (e: Exception) {
                removeProgress()
                Timber.tag(TAG).e("API Scan Exception occurred ${e.message}")
            }
        }
    }

    private fun removeAlert() {
        if (wifiInputDialog != null) {
            wifiInputDialog!!.dismiss()
            wifiInputDialog = null
        }
    }

    private fun showErrorDialog(title: String) {
        runOnUiThread {
            val dialog = Dialog(this)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setCancelable(false)
            dialog.setContentView(R.layout.custom_wifi_provision_error_layout)

            val body = dialog.findViewById(R.id.tvBody) as TextView
            body.text = title

            val noBtn = dialog.findViewById(R.id.noBtn) as TextView
            noBtn.setOnClickListener {
                dialog.dismiss()
                this.finish()
            }

            dialog.apply {
                window?.setBackgroundDrawableResource(android.R.color.transparent)
                show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                this.finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val IP_ADDRESS = "192.168.10.10"
        private val TAG = Companion::class.java.simpleName

        private const val DIALOG_WIFI_PROV_INPUT_TAG = "WiFiProvDialogFragmentTAG"
        const val AP_SSID = "ssid"
        const val AP_PASSPHRASE = "passphrase"
        const val AP_SECURITY_TYPE = "security_type"

    }
}