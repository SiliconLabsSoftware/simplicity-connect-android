package com.siliconlabs.bledemo.features.demo.energyharvesting

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.features.demo.energyharvesting.bluetooth.EnergyHarvestingBluetoothService
import com.siliconlabs.bledemo.features.demo.energyharvesting.data.PersistentVoltageLogger
import com.siliconlabs.bledemo.features.demo.energyharvesting.domain.data.FakeEnergyRepository
import com.siliconlabs.bledemo.features.demo.energyharvesting.domain.usecase.GetDeviceStatusUseCase
import com.siliconlabs.bledemo.features.demo.energyharvesting.presentation.EnergyViewModel
import com.siliconlabs.bledemo.features.demo.energyharvesting.presentation.EnergyVmFactory
import com.siliconlabs.bledemo.features.demo.energyharvesting.ui.EnergyScreen
import kotlinx.coroutines.launch
import kotlin.text.clear

class EnergyHarvestingActivity : ComponentActivity() {

    private var bleService: EnergyHarvestingBluetoothService? = null
    private var serviceBound = false

    private lateinit var voltageLogger: PersistentVoltageLogger


    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? EnergyHarvestingBluetoothService.LocalBinder
            bleService = localBinder?.getService()
            serviceBound = bleService != null
            if (serviceBound) {
                // Start is idempotent.
                bleService?.start()
                startCollectors()
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            bleService = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voltageLogger = PersistentVoltageLogger(applicationContext)
        // Bind (foreground start + bind) to ensure reliable instance acquisition.
        val intent = Intent(this, EnergyHarvestingBluetoothService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        val voltageLogger = PersistentVoltageLogger(applicationContext)
        val repo = FakeEnergyRepository()
        val vmFactory = EnergyVmFactory(
            getDeviceStatus = GetDeviceStatusUseCase(repo),
            voltageLogger = voltageLogger
        )
        val viewModel = ViewModelProvider(this, vmFactory)[EnergyViewModel::class.java]
        this.viewModel = viewModel

        // Proper edge-to-edge with blue system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val blue = ContextCompat.getColor(this, R.color.silabs_redtheme_status_bar_color)
        window.statusBarColor = blue


        // Set light icons for visibility on dark blue background
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                val uiState by viewModel.ui.collectAsState()
                val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

                Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                )
                    Column(modifier = Modifier.fillMaxSize()) {
                    // Fake status bar view with blue background
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(statusBarHeight)
                            .background(Color(blue))
                    )

                    // Matches XML layouts: 1dp grey line between status strip and toolbar (Wi‑Fi Throughput style)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colorResource(R.color.grey))
                    )

                    // Main content with scaffold
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.White,
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        "Energy Harvesting",
                                        fontSize = 20.sp,
                                        color = Color.White
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { voltageLogger.clear()
                                        finish() }) {
                                        Icon(
                                            painter = painterResource(R.drawable.matter_back),
                                            contentDescription = "Back",
                                            tint = Color.Unspecified
                                        )
                                    }
                                },
                                backgroundColor = Color(blue)
                            )
                        }
                    ) { innerPadding ->
                        Surface(color = Color.White) {
                            val scrollState = rememberScrollState()
                            EnergyScreen(
                                state = uiState,
                                modifier = Modifier
                                    .padding(innerPadding)
                                    .verticalScroll(scrollState)
                            )
                        }
                    }
                    }
                }
            }
        }
    }

    private lateinit var viewModel: EnergyViewModel

    private fun startCollectors() {
        val svc = bleService ?: return
        // Collect advertisement (optional debug)
        lifecycleScope.launch { svc.advData.collect { println("EH Adv Data -> $it") } }
        lifecycleScope.launch { svc.deviceAddresses.collect { addr -> println("EH Device Address -> $addr"); viewModel.onDeviceAddress(addr) } }
        lifecycleScope.launch { svc.deviceIdentifiers.collect { id -> println("EH Device Identifier -> $id"); viewModel.onDeviceIdentifier(id) } }
        lifecycleScope.launch { svc.emittedVoltages.collect { list -> list.forEach { mv -> viewModel.onNewVoltage(mv) } } }
        lifecycleScope.launch { svc.rssiUpdates.collect { rssi -> println("EH RSSI -> $rssi"); viewModel.onRssiUpdate(rssi) } }
        lifecycleScope.launch { svc.advIntervalMs.collect { interval -> println("EH Adv Interval -> ${interval}ms"); viewModel.onAdvInterval(interval.toDouble()) } }
        lifecycleScope.launch { svc.sampleEvents.collect { sample -> println("EH SampleEvent -> seq=${sample.sequence}, tsNanos=${sample.timestampNanos}, addr=${sample.deviceAddress}, id=${sample.deviceIdentifier}, voltageMv=${sample.voltageMv}, rssi=${sample.rssi}, advIntervalMs=${sample.advIntervalMs}") } }
    }

    override fun onDestroy() {
        if (serviceBound) {
            unbindService(connection)
            serviceBound = false
        }
        if (::voltageLogger.isInitialized) voltageLogger.clear()
        super.onDestroy()
    }
}
