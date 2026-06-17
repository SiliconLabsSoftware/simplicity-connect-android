package com.siliconlabs.bledemo.features.demo.wifi_throughput.activities

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.Fragment
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.databinding.ActivityWifiThroughputBinding
import com.siliconlabs.bledemo.features.demo.wifi_throughput.fragments.WifiThroughPutDetailScreen
import com.siliconlabs.bledemo.features.demo.wifi_throughput.utils.ThroughputUtils
import com.siliconlabs.bledemo.utils.AppUtil
import com.siliconlabs.bledemo.utils.CustomToastManager
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WifiThroughputActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWifiThroughputBinding
    private val throughPutDemos = ThroughputUtils.WiFiThroughPutFeature.values()
    private val wifiThroughputTileFontFamily = FontFamily(Font(R.font.stolzl_regular))
    private val wifiThroughputDialogLabelFontFamily = FontFamily(Font(R.font.stolzl_bold))
    private lateinit var context: Context
    private var isConfirmCalled = mutableStateOf(false)
    var ipAddress by mutableStateOf("")
    var portNumber by mutableStateOf("")
    var userSelectedFeature: Int by mutableIntStateOf(0)
    private var isDownload = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context = this@WifiThroughputActivity
        binding = ActivityWifiThroughputBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Edge-to-edge & insets handling
        AppUtil.setEdgeToEdge(window, this)

        // Proper ActionBar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setHomeAsUpIndicator(R.drawable.matter_back)
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.wifi_title_Throughput)
        }
        findViewById<ComposeView>(R.id.my_composable).setContent {
            GridLayout(this, throughPutDemos)
            val isConfirm = remember { isConfirmCalled }
            if (isConfirm.value) {
                //FragmentContainer()
            }
        }
    }

    // Function to update the ActionBar title
    fun updateActionBarTitle(title: String) {
        supportActionBar?.title = title
    }

    // Function to reset the title back to the static one
    fun resetActionBarTitle() {
        supportActionBar?.title = getString(R.string.wifi_title_Throughput)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {

        resetActionBarTitle()
        val myFragment: WifiThroughPutDetailScreen? =
            supportFragmentManager.findFragmentByTag("tag") as WifiThroughPutDetailScreen?
        if (myFragment != null && myFragment.isVisible()) {
            supportFragmentManager.popBackStack()
        }
        super.onBackPressed()
    }

    private fun showThroughputDetailScreen(
        userSelectedOption: Int,
        ipAddress: String,
        portNumber: String
    ) {
        val mBundle = Bundle()
        mBundle.putString(
            ThroughputUtils.throughPutType,
            ThroughputUtils.getTitle(userSelectedOption, context)
        )
        mBundle.putString(ThroughputUtils.ipAddress, ipAddress)
        mBundle.putString(ThroughputUtils.portNumber, portNumber)
        val fragment = WifiThroughPutDetailScreen()
        showFragment(
            fragment,
            mBundle,
            fragment::class.java.simpleName
        )
    }

    private fun showFragment(
        fragment: Fragment, bundle: Bundle, tag: String? = null,
    ) {
        val fManager = supportFragmentManager
        val fTransaction = fManager.beginTransaction()
        fragment.setArguments(bundle)

        fTransaction.add(binding.throughputContainer, fragment, tag)
            .addToBackStack(null)
            .commit()

    }

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    fun GridLayout(
        context: Context,
        throughPutDemos: Array<ThroughputUtils.WiFiThroughPutFeature>
    ) {
        val dialogState: MutableState<Boolean> = remember {
            mutableStateOf(false)
        }
        val dialogHeaderTitle: MutableState<String> = remember { mutableStateOf("") }
        val dialogHeaderSubTitle: MutableState<String> = remember { mutableStateOf("") }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(R.color.range_test_content_background))
        ) {
            Image(
                painter = painterResource(R.drawable.modal_bg),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp),
                content = {
                    items(throughPutDemos.size) {
                        Card(
                            backgroundColor = colorResource(R.color.silabs_white),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            elevation = 4.dp,
                            onClick = {
                            dialogState.value = true
                            if (ThroughputUtils.getTitle(
                                    it,
                                    context
                                ) == ThroughputUtils.THROUGHPUT_TYPE_TCP_DOWNLOAD
                            ) {
                                dialogHeaderTitle.value = getString(R.string.wifi_throughput_configure_tcp_server)
                                dialogHeaderSubTitle.value =
                                    getString(R.string.tcp_download_sub_title)
                            } else if (ThroughputUtils.getTitle(
                                    it,
                                    context
                                ) == ThroughputUtils.THROUGHPUT_TYPE_TCP_UPLOAD
                            ) {
                                dialogHeaderTitle.value = getString(R.string.wifi_throughput_configure_tcp_client)
                                dialogHeaderSubTitle.value =
                                    getString(R.string.tcp_upload_sub_title)
                            } else if (ThroughputUtils.getTitle(
                                    it,
                                    context
                                ) == ThroughputUtils.THROUGHPUT_TYPE_UDP_DOWNLOAD
                            ) {
                                dialogHeaderTitle.value = getString(R.string.wifi_throughput_configure_udp_server)
                                dialogHeaderSubTitle.value =
                                    getString(R.string.dialog_udp_download_sub_title)
                            } else if (ThroughputUtils.getTitle(
                                    it,
                                    context
                                ) == ThroughputUtils.THROUGHPUT_TYPE_UDP_UPLOAD
                            ) {
                                dialogHeaderTitle.value = getString(R.string.wifi_throughput_configure_udp_client)
                                dialogHeaderSubTitle.value =
                                    getString(R.string.dialog_udp_upload_sub_title)
                            } else if (ThroughputUtils.getTitle(
                                    it,
                                    context
                                ) == ThroughputUtils.THROUGHPUT_TYPE_TLS_DOWNLOAD
                            ) {
                                dialogHeaderTitle.value = getString(R.string.wifi_throughput_configure_tls_server)
                                dialogHeaderSubTitle.value =
                                    getString(R.string.dialog_tls_download_sub_title)
                            } else if (ThroughputUtils.getTitle(
                                    it,
                                    context
                                ) == ThroughputUtils.THROUGHPUT_TYPE_TLS_UPLOAD
                            ) {
                                dialogHeaderTitle.value = getString(R.string.wifi_throughput_configure_tls_client)
                                dialogHeaderSubTitle.value =
                                    getString(R.string.dialog_sub_title_tls_upload)
                            }
                            userSelectedFeature = it
                            if (ThroughputUtils.isThroughPutTypeDownload(it)) {
                                isDownload.value = false
                                ipAddress = getLocalIpAddress(context)
                                portNumber = ""
                            } else {
                                isDownload.value = true
                                ipAddress =
                                    if (it == ThroughputUtils.WiFiThroughPutFeature.TLS_TX.ordinal) {
                                        getLocalIpAddress(context)
                                    } else {
                                        ""
                                    }
                                val clientTitle = ThroughputUtils.getTitle(it, context)
                                portNumber = when (clientTitle) {
                                    ThroughputUtils.THROUGHPUT_TYPE_TCP_UPLOAD,
                                    ThroughputUtils.THROUGHPUT_TYPE_UDP_UPLOAD,
                                    ThroughputUtils.THROUGHPUT_TYPE_TLS_UPLOAD -> ""
                                    else -> portNumber
                                }
                            }
                        }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = ThroughputUtils.getTitle(it, context),
                                fontWeight = FontWeight.Normal,
                                fontSize = 16.sp,
                                fontFamily = wifiThroughputTileFontFamily,
                                color = colorResource(R.color.silabs_primary_text),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }

                    }
                }
            }
            )
        }

        if (dialogState.value) {
            Dialog(
                onDismissRequest = ({
                    dialogState.value = false
                    isConfirmCalled.value = false
                }),
                properties = DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false,
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .wrapContentHeight(),
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = colorResource(R.color.silabs_white),
                        elevation = 6.dp,
                    ) {
                    val valueTextStyle = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(R.color.silabs_redtheme_primary_color),
                        fontFamily = wifiThroughputTileFontFamily,
                    )
                    val fieldShape = RoundedCornerShape(8.dp)
                    val throughputFieldDecoration = Modifier
                        .heightIn(min = 44.dp)
                        .background(
                            colorResource(R.color.silabs_white),
                            fieldShape,
                        )
                        .border(
                            width = 1.dp,
                            color = colorResource(R.color.silabs_divider),
                            shape = fieldShape,
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                    val resources = LocalContext.current.resources
                    val hintTextSizeSp =
                        resources.getDimension(R.dimen.text_size_S) /
                            resources.displayMetrics.scaledDensity
                    val hintTextStyle = TextStyle(
                        fontSize = hintTextSizeSp.sp,
                        fontWeight = FontWeight.Normal,
                        color = colorResource(R.color.silabs_dark_gray_text),
                        fontFamily = wifiThroughputTileFontFamily,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = dialogHeaderTitle.value,
                            fontFamily = wifiThroughputDialogLabelFontFamily,
                            fontSize = 20.sp,
                            color = colorResource(R.color.silabs_black),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = dialogHeaderSubTitle.value,
                            fontFamily = wifiThroughputTileFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp,
                            color = colorResource(R.color.silabs_dark_gray_text),
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 16.dp),
                        )

                        val isServerReceiveMode =
                            ThroughputUtils.isThroughPutTypeDownload(userSelectedFeature)
                        val isTlsClientConfigure =
                            userSelectedFeature ==
                                ThroughputUtils.WiFiThroughPutFeature.TLS_TX.ordinal
                        val ipFieldReadOnly =
                            isServerReceiveMode || isTlsClientConfigure
                        val ipHint = stringResource(R.string.wifi_throughput_hint_ip_address)
                        val portHint = stringResource(R.string.wifi_throughput_hint_server_port)
                        val showIpHint = ipAddress.isEmpty() && !ipFieldReadOnly
                        val showPortHint = portNumber.isEmpty()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.ip),
                                fontFamily = wifiThroughputTileFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                color = colorResource(R.color.silabs_primary_text),
                                modifier = Modifier.width(124.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .then(throughputFieldDecoration),
                            ) {
                                BasicTextField(
                                    value = ipAddress,
                                    onValueChange = { v ->
                                        if (v.length <= 15) {
                                            ipAddress = v
                                        }
                                    },
                                    readOnly = ipFieldReadOnly,
                                    textStyle = valueTextStyle,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { innerTextField ->
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            if (showIpHint) {
                                                Text(
                                                    text = ipHint,
                                                    style = hintTextStyle,
                                                )
                                            }
                                            innerTextField()
                                        }
                                    },
                                )
                            }
                        }

                        val maxChar = 4
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.port),
                                fontFamily = wifiThroughputTileFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                color = colorResource(R.color.silabs_primary_text),
                                modifier = Modifier.width(124.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .then(throughputFieldDecoration),
                            ) {
                                BasicTextField(
                                    value = portNumber,
                                    onValueChange = { v ->
                                        if (v.length <= maxChar) {
                                            portNumber = v
                                        }
                                    },
                                    textStyle = valueTextStyle,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { innerTextField ->
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            if (showPortHint) {
                                                Text(
                                                    text = portHint,
                                                    style = hintTextStyle,
                                                )
                                            }
                                            innerTextField()
                                        }
                                    },
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    dialogState.value = false
                                    isConfirmCalled.value = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    colorResource(R.color.silabs_redtheme_primary_color),
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = colorResource(R.color.silabs_redtheme_primary_color),
                                    backgroundColor = Color.White,
                                ),
                            ) {
                                Text(
                                    text = context.getString(R.string.matter_cancel),
                                    fontFamily = wifiThroughputTileFontFamily,
                                    fontSize = 16.sp,
                                )
                            }

                            Button(
                                onClick = {
                                    val isClientMode =
                                        !ThroughputUtils.isThroughPutTypeDownload(
                                            userSelectedFeature,
                                        )
                                    val isTlsClientTile =
                                        userSelectedFeature ==
                                            ThroughputUtils.WiFiThroughPutFeature.TLS_TX.ordinal
                                    if (isClientMode && ipAddress.isBlank() && !isTlsClientTile) {
                                        CustomToastManager.show(
                                            context,
                                            getString(R.string.enter_ip_address),
                                            5000,
                                        )
                                        return@Button
                                    }
                                    if (portNumber.length >= maxChar) {
                                        dialogState.value = false
                                        showThroughputDetailScreen(
                                            userSelectedFeature,
                                            ipAddress,
                                            portNumber
                                        )
                                        isConfirmCalled.value = true
                                    } else {
                                        CustomToastManager.show(
                                            context,
                                            getString(R.string.please_enter_valid_port_number),
                                            5000
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = colorResource(R.color.silabs_redtheme_primary_color),
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                            ) {
                                Text(
                                    text = if (
                                        userSelectedFeature ==
                                            ThroughputUtils.WiFiThroughPutFeature.TLS_TX.ordinal ||
                                        !isDownload.value
                                    ) {
                                        context.getString(R.string.dialog_start_server)
                                    } else {
                                        context.getString(R.string.wifi_throughput_start_client)
                                    },
                                    fontFamily = wifiThroughputTileFontFamily,
                                    fontSize = 16.sp,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }


    private fun getLocalIpAddress(context: Context): String {
        val wifiManager =
            (context.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        val wifiInfo = wifiManager.connectionInfo
        val ipInt = wifiInfo.ipAddress
        return InetAddress.getByAddress(
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ipInt).array()
        ).hostAddress
    }
}
