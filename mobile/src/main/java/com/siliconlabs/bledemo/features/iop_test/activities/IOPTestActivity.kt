/*
 * Bluegiga’s Bluetooth Smart Android SW for Bluegiga BLE modules
 * Contact: support@bluegiga.com.
 *
 * This is free software distributed under the terms of the MIT license reproduced below.
 *
 * Copyright (c) 2013, Bluegiga Technologies
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files ("Software")
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF
 * ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A  PARTICULAR PURPOSE.
 */
package com.siliconlabs.bledemo.features.iop_test.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.*
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.bluetooth.ble.GattCharacteristic
import com.siliconlabs.bledemo.bluetooth.ble.GattService
import com.siliconlabs.bledemo.bluetooth.ble.TimeoutGattCallback
import com.siliconlabs.bledemo.bluetooth.services.BluetoothService
import com.siliconlabs.bledemo.databinding.ActivityIopTestBinding
import com.siliconlabs.bledemo.databinding.DialogShareIopLogBinding
import com.siliconlabs.bledemo.features.iop_test.fragments.IOPTestFragment
import com.siliconlabs.bledemo.features.iop_test.fragments.IOPTestFragment.Companion.newInstance
import com.siliconlabs.bledemo.features.iop_test.models.*
import com.siliconlabs.bledemo.features.iop_test.models.Common.Companion.isSetProperty
import com.siliconlabs.bledemo.features.iop_test.models.IOPTest.Companion.createDataTest
import com.siliconlabs.bledemo.features.iop_test.models.IOPTest.Companion.getItemTestCaseInfo
import com.siliconlabs.bledemo.features.iop_test.models.IOPTest.Companion.getListItemChildrenTest
import com.siliconlabs.bledemo.features.iop_test.models.IOPTest.Companion.getSiliconLabsTestInfo
import com.siliconlabs.bledemo.features.iop_test.test_cases.ota.OtaFileManager
import com.siliconlabs.bledemo.features.iop_test.test_cases.ota.OtaFileSelectionDialog
import com.siliconlabs.bledemo.features.iop_test.test_cases.ota.OtaProgressDialog
import com.siliconlabs.bledemo.features.iop_test.utils.ErrorCodes
import com.siliconlabs.bledemo.features.scan.browser.dialogs.OtaLoadingDialog
import com.siliconlabs.bledemo.home_screen.dialogs.SelectDeviceDialog
import com.siliconlabs.bledemo.utils.AppUtil
import com.siliconlabs.bledemo.utils.BLEUtils.setNotificationForCharacteristic
import com.siliconlabs.bledemo.utils.Converters
import com.siliconlabs.bledemo.utils.CustomToastManager
import com.siliconlabs.bledemo.utils.Notifications
import com.siliconlabs.bledemo.utils.UuidConsts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.floor

@SuppressLint("LogNotTimber", "MissingPermission")
class IOPTestActivity : AppCompatActivity() {
    private val SAVE_LOG_REQUEST_CODE = 123
    private var isLogcatData: Boolean = false
    private var logs: String = ""

    private var reconnectTimer: Timer? = Timer()
    private var handler: Handler? = null

    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private lateinit var bluetoothAdapter: BluetoothAdapter

    private var isScanning = false
    private var isTestRunning = false
    private var isConnected = false
    private var isTestFinished = false
    private var readScannerStartTime = Any()

    private var mStartTimeScanner: Long = 0
    private var mStartTimeConnection: Long = 0
    private var mStartTimeDiscover: Long = 0
    private var mEndTimeDiscover: Long = 0

    private var mIndexStartChildrenTest = -1
    private var mIndexRunning = -1
    private var countReTest = 0
    private var iopPhase3IndexStartChildrenTest = -1
    private var iopPhase3BondingStep = 2
    private var iopPhase3ExtraDescriptor: BluetoothGattDescriptor? = null
    private var read_CCCD_value = ByteArray(1)
    var isCCCDPass = true
    private var testParametersService: BluetoothGattService? = null
    private var characteristicIOPPhase3Control: BluetoothGattCharacteristic? = null
    private var characteristicIOPPhase3Throughput: BluetoothGattCharacteristic? = null
    private var characteristicIOPPhase3ClientSupportedFeatures: BluetoothGattCharacteristic? = null
    private var characteristicIOPPhase3DatabaseHash: BluetoothGattCharacteristic? = null
    private var characteristicIOPPhase3IOPTestCaching: BluetoothGattCharacteristic? = null
    private var characteristicIOPPhase3IOPTestServiceChangedIndication: BluetoothGattCharacteristic? =
        null
    private var characteristicIOPPhase3ServiceChanged: BluetoothGattCharacteristic? = null
    private var characteristicIOPPhase3DeviceName: BluetoothGattCharacteristic? = null

    private var mBluetoothDevice: BluetoothDevice? = null
    private var mBluetoothService: BluetoothService? = null
    private var mBluetoothBinding: BluetoothService.Binding? = null
    private var mBluetoothGatt: BluetoothGatt? = null
    private var mListener: Listener? = null
    private var mBluetoothEnableDialog: Dialog? = null

    private val mListCharacteristics: MutableList<BluetoothGattCharacteristic> = ArrayList()
    private val characteristicsPhase3Security: MutableList<BluetoothGattCharacteristic> =
        ArrayList()

    private var isDisabled = false
    private val mtuValue = 255
    private var mStartTimeThroughput: Long = 0
    private var mEndTimeThroughput: Long = 0
    private var mByteNumReceived = 0
    private var mPDULength = 0
    private var mByteSpeed = 0
    private var mEndThroughputNotification = false

    private var otaProgressDialog: OtaProgressDialog? = null
    private var otaLoadingDialog: OtaLoadingDialog? = null
    private var otaFileSelectionDialog: OtaFileSelectionDialog? = null
    private var otaFileManager: OtaFileManager? = null

    /**
     * OTA Setup
     */
    private val delayNoResponse = 1

    private var reliable = true
    private var boolOTAbegin = false
    private var discoverTimeout = true
    private var ota_mode = false
    private var otaProcess = false
    private var disconnectGatt = false
    private var homekit = false
    private var otaMode = false

    private var mtu = 247
    private var currentRxPhy: Int? = null
    private var mtuDivisible = 0
    private var isServiceChangedIndication = 1
    private var isConnecting = false

    private val kBits = "%.2fkbit/s"

    private var kitDescriptor: BluetoothGattDescriptor? = null
    private var pack = 0
    private var otaTime: Long = 0

    private var mDeviceAddress: String? = null
    private var iopPhase3DatabaseHash: String? = null
    private var testCaseCount = 0

    private var shareMenuItem: MenuItem? = null
    private lateinit var binding: ActivityIopTestBinding

    /** Prevents showing the post-run bonding dialog more than once per test run. */
    private var bondingRemovedDialogShownThisRun = false

    /** User must acknowledge the Security-step briefing before the IOP Security flow runs. */
    private var iopSecurityIntroAcknowledged = false

    private var isInAppOTA = false
    private var ota_alreadyIn_Progress = false

    private val lock = Object()

    private val mBondStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
            val prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
            val msg =
                "Bond state change: state " + printBondState(state) + ", previous state " + printBondState(
                    prevState
                )
            Log.d(TAG, msg)
            if (state == BluetoothDevice.BOND_BONDED && prevState == BluetoothDevice.BOND_BONDING) {
                handler?.postDelayed({
                    if (getSiliconLabsTestInfo().listItemTest[POSITION_TEST_IOP3_SECURITY].getStatusTest() == Common.IOP3_TC_STATUS_PROCESSING
                        || getSiliconLabsTestInfo().listItemTest[POSITION_TEST_IOP3_LE_PRIVACY].getStatusTest() == Common.IOP3_TC_STATUS_PROCESSING
                    /*  || getSiliconLabsTestInfo().listItemTest[POSITION_TEST_IOP3_CACHING]
                              .getStatusTest() == Common.IOP3_TC_STATUS_PROCESSING*/) {
                        if (mBluetoothGatt != null) {
                            mListCharacteristics.clear()
                            characteristicsPhase3Security.clear()
                            Log.d(TAG, "discovering services with 1600ms delay")
                            Log.d(TAG, "isConnected: $isConnected")
                            val result = mBluetoothGatt!!.discoverServices()
                            if (!result) {
                                Log.e(TAG, "discoverServices failed to start")
                            }
                        } else {
                            retryIOP3Failed(mIndexRunning, ++countReTest / 2)
                        }
                    }
                }, 1600)
            } else if (state == BluetoothDevice.BOND_BONDING) {
                /* Toast.makeText(
                     this@IOPTestActivity,
                     R.string.iop_test_toast_bonding,
                     Toast.LENGTH_LONG
                 ).show()*/
                CustomToastManager.show(
                    this@IOPTestActivity, getString(R.string.iop_test_toast_bonding), 5000
                )
            }
        }

        private fun printBondState(state: Int): String {
            return when (state) {
                BluetoothDevice.BOND_NONE -> "BOND_NONE"
                BluetoothDevice.BOND_BONDING -> "BOND_BONDING"
                BluetoothDevice.BOND_BONDED -> "BOND_BONDED"
                else -> state.toString()
            }
        }
    }
    private val mPairRequestReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1)
            val msg = "Pairing request, variant: " + printVariant(variant)
            Log.d(TAG, msg)
        }

        private fun printVariant(variant: Int): String {
            if (variant == BluetoothDevice.PAIRING_VARIANT_PIN) {
                /*Toast.makeText(
                    this@IOPTestActivity,
                    R.string.iop_test_toast_press_passkey,
                    Toast.LENGTH_LONG
                ).show()*/
                CustomToastManager.show(
                    this@IOPTestActivity, getString(R.string.iop_test_toast_press_passkey), 5000
                )
                return "PAIRING_VARIANT_PIN"
            }
            return variant.toString()
        }
    }
    private val bluetoothAdapterStateChangeListener: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    Log.d(
                        TAG,
                        "BluetoothAdapter.ACTION_STATE_CHANGED mIndexRunning: $mIndexRunning,state: $state"
                    )
                    when (state) {
                        BluetoothAdapter.STATE_OFF -> {
                            disconnectGatt(mBluetoothGatt)
                            handler?.removeCallbacksAndMessages(null)
                            finish()
                        }
                    }
                }
            }
        }

    /**
     * Update data test failed by item
     */
    private fun updateDataTestFailed(index: Int) {
        Log.d(TAG, "updateDataTestFailed $index")
        when (index) {
            POSITION_TEST_SCANNER -> {
                scanLeDevice(false)
                getSiliconLabsTestInfo().listItemTest[index].setStatusTest(Common.IOP3_TC_STATUS_FAILED)
            }

            POSITION_TEST_CONNECTION, POSITION_TEST_DISCOVER_SERVICE, POSITION_TEST_SERVICE -> disconnectGatt(
                mBluetoothGatt
            )

            else -> {
            }
        }
        Log.d(TAG, "listItemTest.size " + getSiliconLabsTestInfo().listItemTest.size)

        for (i in index until getSiliconLabsTestInfo().listItemTest.size) {
            getSiliconLabsTestInfo().listItemTest[i].setStatusTest(Common.IOP3_TC_STATUS_FAILED)
        }
        Log.d(TAG, "POSITION_TEST_SCANNER " + POSITION_TEST_SCANNER)
        if (index >= POSITION_TEST_SCANNER) {
            isTestRunning = false
            isTestFinished = true
            if (isConnected) {
                isConnected = false
                mBluetoothGatt?.disconnect()
            }
        }
        runOnUiThread { updateUIFooter(isTestRunning) }
        mListener?.updateUi()
    }

    /**
     * Start test by item
     */
    private fun startItemTest(item: Int) {
        if (item == POSITION_TEST_IOP3_SECURITY && !iopSecurityIntroAcknowledged) {
            // finishItemTest may call this from a GATT binder thread; dialogs require a main Looper.
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                mListener?.scrollViewToPosition(item)
                AlertDialog.Builder(this@IOPTestActivity)
                    .setTitle(R.string.iop_test_security_pairing_info_title)
                    .setMessage(R.string.iop_test_security_pairing_info_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.button_ok) { dialog, _ ->
                        dialog.dismiss()
                        iopSecurityIntroAcknowledged = true
                        startItemTest(POSITION_TEST_IOP3_SECURITY)
                    }
                    .show()
            }
            return
        }

        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                mListener?.scrollViewToPosition(item)
            }
        }


        mIndexRunning = item
        isTestRunning = true
        isTestFinished = false
        isConnecting = false
        Log.d(TAG, "startItemTest: setStatusTest PROCESSING, item: $item")
        getSiliconLabsTestInfo().listItemTest[item].setStatusTest(Common.IOP3_TC_STATUS_PROCESSING)
        handler?.postDelayed({
            Log.d(TAG, "startItemTest with 1000ms delay of test case")
            when (item) {
                POSITION_TEST_SCANNER -> {
                    Log.d(TAG, "scanLeDevice at POSITION_TEST_SCANNER")
                    scanLeDevice(true)
                }

                POSITION_TEST_CONNECTION -> connectToDevice(mBluetoothDevice)
                POSITION_TEST_DISCOVER_SERVICE -> mBluetoothGatt?.requestMtu(247)
                POSITION_TEST_SERVICE -> runChildrenTestCase(mIndexStartChildrenTest)
                POSITION_TEST_IOP3_THROUGHPUT -> {
                    Log.d(TAG, "POSITION_TEST_IOP3_THROUGHPUT")
                    iopPhase3RunTestCaseThroughput(1)
                }

                POSITION_TEST_IOP3_LE_PRIVACY -> {
                    Log.d(TAG, "POSITION_TEST_IOP3_LE_PRIVACY")
                    iopPhase3RunTestCaseLEPrivacy(1)
                }

                POSITION_TEST_IOP3_SECURITY -> {
                    Log.d(TAG, "POSITION_TEST_IOP3_SECURITY")
                    iopPhase3IndexStartChildrenTest = 0
                    iopPhase3RunTestCaseSecurity(iopPhase3IndexStartChildrenTest, 1)
                }


                /*
                POSITION_TEST_IOP3_CACHING -> {
                    isServiceChangedIndication = 1
                    iopPhase3RunTestCaseCaching(0)
                }
                */
                POSITION_TEST_IOP3_OTA_ACK -> {
                    iopPhase3IndexStartChildrenTest = 0
                    startOtaTestCase(iopPhase3IndexStartChildrenTest)
                }

                POSITION_TEST_IOP3_OTA_WITHOUT_ACK -> {
                    iopPhase3IndexStartChildrenTest = 1
                    if (mBluetoothGatt?.getService(ota_service)
                            ?.getCharacteristic(ota_data) != null
                    ) {
                        isInAppOTA = true
                    } else {
                        isInAppOTA = false
                    }
                    startOtaTestCase(iopPhase3IndexStartChildrenTest)
                }

                else -> {
                }
            }
        }, 1000)

        showDetailInformationTest(testCaseCount++, false)
        mListener?.updateUi()
    }

    /**
     * Finish test by item
     */
    private fun finishItemTest(item: Int, itemTestCaseInfo: ItemTestCaseInfo) {
        Log.d(TAG, "finishItemTest, test item: $item")
        when (item) {
            POSITION_TEST_SCANNER -> {
                itemTestCaseInfo.timeStart = mStartTimeScanner
                itemTestCaseInfo.setTimeEnd(System.currentTimeMillis())
                Log.d(
                    TAG,
                    "finishItemTest: scanLeDevice startTime: " + itemTestCaseInfo.getTimeEnd()
                )
                if (itemTestCaseInfo.getStatusTest() == Common.IOP3_TC_STATUS_FAILED) {
                    countReTest++
                    if (countReTest < 7) {
                        itemTestCaseInfo.setStatusTest(-1)
                        startItemTest(item)
                        return
                    }
                } else {
                    countReTest = 0
                }
            }

            POSITION_TEST_CONNECTION -> {
                itemTestCaseInfo.timeStart = mStartTimeConnection
                itemTestCaseInfo.setTimeEnd(System.currentTimeMillis())
                Log.d(
                    TAG,
                    "finishItemTest: POSITION_TEST_CONNECTION time " + (System.currentTimeMillis() - mStartTimeConnection)
                )
                if (itemTestCaseInfo.getStatusTest() == Common.IOP3_TC_STATUS_FAILED) {
                    countReTest++
                    if (countReTest < 7) {
                        if (isConnected) {
                            isConnected = false
                            mBluetoothGatt?.disconnect()
                        }
                        itemTestCaseInfo.setStatusTest(Common.IOP3_TC_STATUS_PROCESSING)
                        handler?.postDelayed({ startItemTest(item) }, 3000)
                    } else {
                        countReTest = 0
                    }
                } else {
                    countReTest = 0
                }
            }

            POSITION_TEST_DISCOVER_SERVICE -> {
                itemTestCaseInfo.timeStart = mStartTimeDiscover
                itemTestCaseInfo.setTimeEnd(mEndTimeDiscover)
                Log.d(
                    TAG,
                    "finishItemTest: POSITION_TEST_DISCOVER_SERVICE " + (mEndTimeDiscover - mStartTimeDiscover) + "ms"
                )
                countReTest = 0
            }

            POSITION_TEST_SERVICE -> {
                getItemTestCaseInfo(POSITION_TEST_SERVICE).checkStatusItemService()
                Log.d(TAG, "finishItemTest: POSITION_TEST_SERVICE")
                countReTest = 0
            }

            POSITION_TEST_IOP3_THROUGHPUT -> {
                val throughputAcceptable = calculateAcceptableThroughput()
                itemTestCaseInfo.setThroughputBytePerSec(mByteSpeed, throughputAcceptable)
                Log.d(
                    TAG,
                    "finishItemTest: POSITION_TEST_IOP3_THROUGHPUT mByteSpeed $mByteSpeed throughputAcceptable $throughputAcceptable"
                )
                countReTest = 0
            }

            POSITION_TEST_IOP3_LE_PRIVACY -> {
                getItemTestCaseInfo(POSITION_TEST_IOP3_LE_PRIVACY).checkStatusItemService()
                Log.d(TAG, "finishItemTest: POSITION_TEST_IOP3_LE_PRIVACY")
                countReTest = 0
                isTestRunning = false
                isTestFinished = true
            }

            POSITION_TEST_IOP3_SECURITY -> {
                getItemTestCaseInfo(POSITION_TEST_IOP3_SECURITY).checkStatusItemService()
                Log.d(TAG, "finishItemTest: POSITION_TEST_IOP3_SECURITY")
                countReTest = 0
                //  isTestRunning = false
                //  isTestFinished = true
                mBluetoothService?.isNotificationEnabled = true
            }


            /*
            POSITION_TEST_IOP3_CACHING -> {
                isTestRunning = false
                isTestFinished = true
            }
            */
            POSITION_TEST_IOP3_OTA_ACK, POSITION_TEST_IOP3_OTA_WITHOUT_ACK -> countReTest = 0
            else -> {
            }
        }
        if (item != POSITION_TEST_IOP3_LE_PRIVACY && countReTest == 0) {
            startItemTest(item + 1)
        }
        /*if (item != POSITION_TEST_IOP3_SECURITY && countReTest == 0) {
          startItemTest(item + 1)
      }*/



        runOnUiThread { updateUIFooter(isTestRunning) }
        mListener?.updateUi()
    }

    private fun calculateAcceptableThroughput(): Int {
        val notLegacyFw =
            getSiliconLabsTestInfo().firmwareVersion.split('.')[0].let { it != "" && it.toInt() >= 6 }
        val knownPhy = currentRxPhy in arrayOf(BluetoothDevice.PHY_LE_1M, BluetoothDevice.PHY_LE_2M)


        return if (notLegacyFw && knownPhy) {
            try {
                Log.d(
                    TAG,
                    "finishItemTest: POSITION_TEST_IOP3_THROUGHPUT calculating acceptable throughput (new method)"
                )
                val connectionParameters = getSiliconLabsTestInfo().connectionParameters!!
                val pdu = connectionParameters.pdu
                Log.d(TAG, "pdu : " + pdu)
                val tPacketMicroseconds: Int = when (currentRxPhy) {
                    BluetoothDevice.PHY_LE_1M -> (8 * pdu) + 492
                    else -> (4 * pdu) + 396
                }
                val connectionIntervalMs: Double =
                    getSiliconLabsTestInfo().connectionParameters!!.interval
                val connectionIntervalMicroseconds = connectionIntervalMs * 1000
                val connectionIntervalSeconds = connectionIntervalMs / 1000
                Log.d(TAG, "connectionIntervalMs " + connectionIntervalMs)
                val numPacket = floor(connectionIntervalMicroseconds / tPacketMicroseconds)
                val sizeEffective = mtu - 3
                val fragmentationCount = ceil(mtu.toFloat() / (pdu - 4))
                val velExpected =
                    numPacket * sizeEffective / fragmentationCount / connectionIntervalSeconds
                (0.5 * velExpected).toInt()
            } catch (e: NullPointerException) {
                Log.d(
                    TAG,
                    "finishItemTest: POSITION_TEST_IOP3_THROUGHPUT Failed to calculate acceptable throughput"
                )
                0
            }
        } else {

            Log.d(
                TAG,
                "finishItemTest: POSITION_TEST_IOP3_THROUGHPUT calculating acceptable throughput (legacy method)"
            )
            (mPDULength) * 4 * 1000 * 65 / 1500
        }
    }

    private fun runnable(gatt: BluetoothGatt) {
        Thread {
            getServicesInfo(gatt) //SHOW SERVICES
        }.start()
    }

    /**
     * Update data test after listening bluetooth gatt callback
     */
    private fun updateDataTest(
        characteristic: BluetoothGattCharacteristic?,
        type: Int,
        status: Int
    ) {
        if (characteristic != null) {
            for (itemChildrenTest: ChildrenItemTestInfo in getListChildrenItemTestCase(
                POSITION_TEST_SERVICE
            )!!) {
                if (itemChildrenTest.characteristic?.uuid.toString() == characteristic.uuid.toString()) {
                    itemChildrenTest.statusRunTest = 1 // 0 running, 1 finish test
                    itemChildrenTest.valueMtu = mtu
                    if (itemChildrenTest.characteristic?.uuid.toString() == CommonUUID.Characteristic.NOTIFICATION_LENGTH_1.toString() || itemChildrenTest.characteristic?.uuid.toString() == CommonUUID.Characteristic.INDICATE_LENGTH_1.toString() || itemChildrenTest.characteristic?.uuid.toString() == CommonUUID.Characteristic.NOTIFICATION_LENGTH_MTU_3.toString() || itemChildrenTest.characteristic?.uuid.toString() == CommonUUID.Characteristic.INDICATE_LENGTH_MTU_3.toString()) {
                        itemChildrenTest.endTimeTest = System.currentTimeMillis()
                    }
                    when (type) {
                        0 -> itemChildrenTest.statusRead = status
                        1 -> itemChildrenTest.statusWrite = status
                        else -> {
                        }
                    }
                    itemChildrenTest.setDataAndCompareResult(characteristic)
                }
            }
            for (itemChildrenTest: ChildrenItemTestInfo in getListChildrenItemTestCase(
                POSITION_TEST_IOP3_SECURITY
            )!!) {
                if (itemChildrenTest.characteristic?.uuid.toString() == characteristic.uuid.toString()) {
                    itemChildrenTest.statusRunTest = 1 // 0 running, 1 finish test
                    when (type) {
                        0 -> itemChildrenTest.statusRead = status
                        1 -> itemChildrenTest.statusWrite = status
                        else -> {
                        }
                    }
                    itemChildrenTest.setDataAndCompareResult(characteristic)
                }
            }



            if (mIndexRunning == POSITION_TEST_IOP3_OTA_ACK || mIndexRunning == POSITION_TEST_IOP3_OTA_WITHOUT_ACK) {
                if (!otaProcess) {
                    testParametersService?.let { readCharacteristic(it.characteristics[0]) }
                    if (characteristicIOPPhase3DeviceName?.uuid.toString() == characteristic.uuid.toString()) {
                        if (isOtaNameCorrect(characteristic.getStringValue(0))) {
                            checkIOP3OTA(mIndexRunning, Common.IOP3_TC_STATUS_PASS)
                        } else {
                            checkIOP3OTA(mIndexRunning, Common.IOP3_TC_STATUS_FAILED)
                        }
                        handler?.postDelayed(
                            {
                                finishItemTest(
                                    mIndexRunning,
                                    getSiliconLabsTestInfo().listItemTest[mIndexRunning]
                                )
                            },
                            100
                        ) /* Read testParametersService characteristic before starting next test case. */
                    }
                }
            } else if (characteristicIOPPhase3Control?.uuid.toString() == characteristic.uuid.toString()) {
                if (getSiliconLabsTestInfo().listItemTest[POSITION_TEST_IOP3_THROUGHPUT].getStatusTest() == Common.IOP3_TC_STATUS_PROCESSING) {
                    iopPhase3RunTestCaseThroughput(0)
                } /*else if (getSiliconLabsTestInfo().listItemTest[POSITION_TEST_IOP3_LE_PRIVACY].getStatusTest() == Common.IOP3_TC_STATUS_PROCESSING) {
                    iopPhase3RunTestCaseLEPrivacy(0)
                    finishItemTest(
                        POSITION_TEST_IOP3_LE_PRIVACY,
                        getSiliconLabsTestInfo().listItemTest[POSITION_TEST_IOP3_LE_PRIVACY]
                    )
                }*/ /* else if (getSiliconLabsTestInfo().listItemTest[POSITION_TEST_IOP3_CACHING]
                                .getStatusTest() == Common.IOP3_TC_STATUS_PROCESSING) {
                    if (isServiceChangedIndication == 0) {
                        handler?.postDelayed({
                            val securityItem = getListChildrenItemTestCase(POSITION_TEST_IOP3_SECURITY)!![0]
                            readCharacteristic(securityItem.characteristic)
                        }, 2000)
                    }
                } */
            } else if (characteristicIOPPhase3Throughput?.uuid.toString() == characteristic.uuid.toString()) {
                mByteNumReceived += characteristic.value.size
                mPDULength = characteristic.value.size
            } else if (mIndexRunning == POSITION_TEST_IOP3_CACHING) {
                if (isServiceChangedIndication == 1) {
                    if (characteristicIOPPhase3IOPTestServiceChangedIndication != null) {
                        if (characteristicIOPPhase3IOPTestServiceChangedIndication?.uuid.toString() == characteristic.uuid.toString()) {
                            val itemChildrenTest =
                                getListChildrenItemTestCase(POSITION_TEST_IOP3_CACHING)!![isServiceChangedIndication]
                            itemChildrenTest.statusRead = status
                            itemChildrenTest.statusRunTest = 1
                            itemChildrenTest.setDataAndCompareResult((itemChildrenTest.characteristic)!!)
                        }
                    }
                } else {
                    if (characteristicIOPPhase3DatabaseHash?.uuid.toString() == characteristic.uuid.toString()) {
                        if (iopPhase3DatabaseHash != null) {
                            val itemTestInfo =
                                getListChildrenItemTestCase(POSITION_TEST_IOP3_CACHING)!![0]
                            if (!itemTestInfo.compareValueCharacteristic(
                                    iopPhase3DatabaseHash!!,
                                    Converters.getHexValue(characteristic.value)
                                )
                            ) {
                                Log.d(TAG, "iopPhase3DatabaseHash has changed")
                                mListCharacteristics.clear()
                                characteristicsPhase3Security.clear()
                                refreshServices()
                            }
                        } else {
                            setNotificationForCharacteristic(
                                characteristicIOPPhase3ServiceChanged,
                                Notifications.DISABLED
                            )
                        }
                        iopPhase3DatabaseHash = Converters.getHexValue(characteristic.value)
                        Log.d(TAG, "characteristicIOPPhase3DatabaseHash: $iopPhase3DatabaseHash")
                    } else if (characteristicIOPPhase3ClientSupportedFeatures?.uuid.toString() == characteristic.uuid.toString()) {
                        readCharacteristic(characteristicIOPPhase3DatabaseHash)
                    } else if (characteristicIOPPhase3IOPTestCaching != null) {
                        if (characteristicIOPPhase3IOPTestCaching?.uuid.toString() == characteristic.uuid.toString()) {
                            val itemChildrenTest =
                                getListChildrenItemTestCase(POSITION_TEST_IOP3_CACHING)!![isServiceChangedIndication]
                            itemChildrenTest.statusRead = status
                            itemChildrenTest.statusRunTest = 1
                            itemChildrenTest.setDataAndCompareResult((itemChildrenTest.characteristic)!!)
                            handler?.removeCallbacks(iopCachingRunnable)
                            if (itemChildrenTest.statusChildrenTest) {
                                getItemTestCaseInfo(POSITION_TEST_IOP3_CACHING).setStatusTest(Common.IOP3_TC_STATUS_PASS)
                            }
                            finishItemTest(
                                POSITION_TEST_IOP3_CACHING,
                                getSiliconLabsTestInfo().listItemTest[POSITION_TEST_IOP3_CACHING]
                            )
                        }
                    }
                }
            }

        }
    }

    /**
     * DISCONNECTS AND CONNECTS WITH THE SELECTED DELAY
     */
    fun reconnect(delaytoconnect: Long) {
        if (!isInAppOTA) {
            mBluetoothDevice = mBluetoothGatt?.device
            if (mBluetoothService?.isGattConnected()!!) {
                mBluetoothService?.clearConnectedGatt()
            }

            mBluetoothGatt?.disconnect()
            reconnectTimer?.schedule(object : TimerTask() {
                override fun run() {
                    mBluetoothGatt?.close()
                    mBluetoothBinding?.unbind()
                }
            }, 400)

            reconnectTimer?.schedule(object : TimerTask() {
                override fun run() {
                    Log.d(TAG, "Attempting connection...")
                    mBluetoothBinding = object : BluetoothService.Binding(applicationContext) {
                        override fun onBound(service: BluetoothService?) {
                            service?.isNotificationEnabled = false
                            mBluetoothGatt = mBluetoothDevice?.connectGatt(
                                applicationContext,
                                false,
                                gattCallback,
                                BluetoothDevice.TRANSPORT_LE
                            )
                        }
                    }
                    mBluetoothBinding?.bind()
                }
            }, delaytoconnect)
        }
    }

    /**
     * DISCONNECT GATT GENTLY AND CLEAN GLOBAL VARIABLES
     */
    private fun disconnectGatt(gatt: BluetoothGatt?) {
        if (!isInAppOTA) {
            val disconnectTimer = Timer()
            boolOTAbegin = false
            otaProcess = false
            disconnectGatt = true
            if (gatt != null && gatt.device != null) {
                val btGatt: BluetoothGatt = gatt
                disconnectTimer.schedule(object : TimerTask() {
                    override fun run() {
                        //Getting bluetoothDevice to FetchUUID
                        btGatt.device?.let {
                            mBluetoothDevice = btGatt.device
                        }
                        btGatt.disconnect()
                        mBluetoothService?.clearConnectedGatt()
                    }
                }, 200)
                disconnectTimer.schedule(object : TimerTask() {
                    override fun run() {
                        mBluetoothDevice?.fetchUuidsWithSdp()
                    }
                }, 300)
            } else {
                finish()
            }
        }
    }

    /**
     * CLEANS USER INTERFACE AND FINISH ACTIVITY
     */
    fun exit(gatt: BluetoothGatt?) {
        gatt?.close()
        mBluetoothService?.connectedGatt?.close()

        mBluetoothBinding?.unbind()
        disconnectGatt = false

        handler?.postDelayed({
            if (otaLoadingDialog?.isShowing() == true) {
                otaLoadingDialog?.dismiss()
            }
            if (otaProgressDialog != null && otaProgressDialog?.isShowing!!) {
                otaProgressDialog?.dismiss()
            }
        }, 1000)
    }

    private var onConnectionSuccess: (() -> Unit)? = null
    private var onConnectionFailure: (() -> Unit)? = null


    private fun connectToDevice(
        bluetoothDevice: BluetoothDevice?,
        onConnectionSuccess: (() -> Unit)? = null,
        onConnectionFailure: (() -> Unit)? = null
    ) {
        Log.d(TAG, "connectToDevice() with param called with: bluetoothDevice = $bluetoothDevice")
        mStartTimeConnection = System.currentTimeMillis()

        // Save the callback functions
        this.onConnectionSuccess = onConnectionSuccess
        this.onConnectionFailure = onConnectionFailure

        Log.d(TAG, "connectToDevice() with param, postDelayed connectionRunnable")
        handler?.postDelayed({
            // Execute the connection logic after the delay
            mBluetoothBinding = object : BluetoothService.Binding(applicationContext) {
                override fun onBound(service: BluetoothService?) {
                    mBluetoothService = service
                    service?.isNotificationEnabled = false
                    if(!isTestFinished){
                        service?.connectGatt(bluetoothDevice!!, false, gattCallback)
                        mBluetoothGatt = service?.connectedGatt!!
                    }
                }
            }
            mBluetoothBinding?.bind()
        }, 15000) // 15 seconds delay
    }


    private fun connectToDevice(bluetoothDevice: BluetoothDevice?) {
        Log.d(TAG, "connectToDevice() called with: bluetoothDevice = $bluetoothDevice")
        mStartTimeConnection = System.currentTimeMillis()

        Log.d(TAG, "connectToDevice(), postDelayed connectionRunnable")
        handler?.postDelayed(connectionRunnable, CONNECTION_PERIOD)
        mBluetoothBinding = object : BluetoothService.Binding(applicationContext) {
            override fun onBound(service: BluetoothService?) {
                mBluetoothService = service
                service?.isNotificationEnabled = false
                service?.connectGatt(bluetoothDevice!!, false, gattCallback)
                mBluetoothGatt = service?.connectedGatt!!
            }
        }
        mBluetoothBinding?.bind()
    }


    private fun saveLogcatFile() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"  // MIME type of the file you want to save
            putExtra(Intent.EXTRA_TITLE, getPathOfLogFile())
        }
        startActivityForResult(intent, SAVE_LOG_REQUEST_CODE)
    }


    private fun startTest() {
        isTestRunning = true
        isTestFinished = false
        bondingRemovedDialogShownThisRun = false

        // Drop pending work from any previous run (delays, retries, scan timeout).
        handler?.removeCallbacksAndMessages(null)
        reconnectTimer?.cancel()
        reconnectTimer = Timer()

        resetFunctionTest()
        removeBondIfBondedForDeviceUnderTest()
        startItemTest(POSITION_TEST_SCANNER)

        updateUIFooter(isTestRunning)
    }

    /**
     * Update Ui footer
     */
    private fun updateUIFooter(isRunning: Boolean) {
        if (!isRunning) {
            binding.btnStartAndStopTest.apply {
                text = getString(R.string.button_run_test)
                isEnabled = true
                backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        this@IOPTestActivity,
                        R.color.silabs_blue
                    )
                )
            }
            if (isTestFinished) {
                shareMenuItem?.isVisible = true
                mBluetoothDevice?.let { removeBond(it) }
                releaseIopBluetoothResources()
                showBondingRemovedDialogIfNeeded()
            }
        } else {
            binding.btnStartAndStopTest?.apply {
                text = getString(R.string.button_waiting)
                backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        this@IOPTestActivity,
                        R.color.silabs_inactive_light
                    )
                )
                isEnabled = false
            }
            shareMenuItem?.isVisible = false
        }
    }

    private fun removeBond(device: BluetoothDevice): Boolean {
        return try {
            return device::class.java.getMethod("removeBond").invoke(device) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clears an existing pairing so the IOP run can perform a fresh bonding flow.
     * Uses the last known device and, if set, the address in the system bonded list.
     */
    private fun removeBondIfBondedForDeviceUnderTest() {
        removeBondIfBonded(mBluetoothDevice)
        mDeviceAddress?.let { addr ->
            bluetoothAdapter.bondedDevices
                .find { it.address.equals(addr, ignoreCase = true) }
                ?.let { removeBondIfBonded(it) }
        }
    }

    private fun removeBondIfBonded(device: BluetoothDevice?): Boolean {
        if (device == null || device.bondState != BluetoothDevice.BOND_BONDED) {
            return false
        }
        val invoked = removeBond(device)
        Log.d(TAG, "removeBondIfBonded: invoked=$invoked address=${device.address}")
        return invoked
    }

    private fun showBondingRemovedDialogIfNeeded() {
        if (bondingRemovedDialogShownThisRun || isFinishing) {
            return
        }
        bondingRemovedDialogShownThisRun = true
        AlertDialog.Builder(this)
            .setMessage(R.string.iop_test_bonding_removed_dialog_message)
            .setPositiveButton(R.string.button_done) { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    /**
     * Show information device test and progress test item
     */
    private fun showDetailInformationTest(item: Int, isInformation: Boolean) {
        val siliconlabsTestInfo = getSiliconLabsTestInfo()
        runOnUiThread {
            if (isInformation || item == POSITION_TEST_CONNECTION) {
                binding.tvFwName.text =
                    getString(R.string.iop_test_label_fw_name, siliconlabsTestInfo.fwName)
                binding.tvDeviceName.text =
                    getString(R.string.iop_test_label_device_name, siliconlabsTestInfo.phoneName)
            }
            val total = siliconlabsTestInfo.totalTestCase.toString()
            if (item == POSITION_TEST_SERVICE) {
                testCaseCount = item + mIndexStartChildrenTest + 1
            }
            Log.d(TAG, "The number of test case $testCaseCount")
            binding.tvProgress.text = getString(
                R.string.iop_test_label_progress_count_test,
                testCaseCount.toString(),
                total
            )
        }
    }

    /**
     * Reset list item test and start over
     */
    private fun resetFunctionTest() {
        if (isScanning) {
            scanLeDevice(false)
        }

        reliable = true
        testCaseCount = 0
        mIndexRunning = -1
        mIndexStartChildrenTest = -1
        countReTest = 0
        iopPhase3IndexStartChildrenTest = -1
        iopPhase3BondingStep = 2
        iopSecurityIntroAcknowledged = false
        iopPhase3ExtraDescriptor = null
        iopPhase3DatabaseHash = null

        readScannerStartTime = true
        mStartTimeScanner = 0
        mStartTimeConnection = 0
        mStartTimeDiscover = 0
        mEndTimeDiscover = 0
        mStartTimeThroughput = 0
        mEndTimeThroughput = 0
        mByteNumReceived = 0
        mPDULength = 0
        mByteSpeed = 0
        mEndThroughputNotification = false

        read_CCCD_value = ByteArray(1)
        isCCCDPass = true

        mListCharacteristics.clear()
        characteristicsPhase3Security.clear()

        testParametersService = null
        characteristicIOPPhase3Control = null
        characteristicIOPPhase3Throughput = null
        characteristicIOPPhase3ClientSupportedFeatures = null
        characteristicIOPPhase3DatabaseHash = null
        characteristicIOPPhase3IOPTestCaching = null
        characteristicIOPPhase3IOPTestServiceChangedIndication = null
        characteristicIOPPhase3ServiceChanged = null
        characteristicIOPPhase3DeviceName = null
        kitDescriptor = null

        boolOTAbegin = false
        discoverTimeout = true
        ota_mode = false
        otaProcess = false
        disconnectGatt = false
        homekit = false
        otaMode = false
        isInAppOTA = false
        ota_alreadyIn_Progress = false
        otaTime = 0
        pack = 0

        mtu = 247
        currentRxPhy = null
        mtuDivisible = 0
        isServiceChangedIndication = 1
        isConnecting = false

        isDisabled = false

        onConnectionSuccess = null
        onConnectionFailure = null

        if (isConnected) {
            isConnected = false
            mBluetoothGatt?.disconnect()
        }
        resetData()

        // Same labels/progress baseline as when the screen first loads.
        showDetailInformationTest(POSITION_TEST_SCANNER, true)
    }

    private fun resetData() {
        val info = getSiliconLabsTestInfo()
        createDataTest(info.fwName, info.deviceMacAddress)
        mListener?.updateUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("onDestroy", "called")
        if (isScanning) {
            scanLeDevice(false)
            Log.d("onDestroy", "scanLeDevice(false)")
        }

        handler?.removeCallbacksAndMessages(null)
        reconnectTimer = null
        handler = null

        mBluetoothService?.clearConnectedGatt()
        mBluetoothService?.isNotificationEnabled = true
        unregisterBroadcastReceivers()
        releaseIopBluetoothResources()
    }

    private fun unregisterBroadcastReceivers() {
        unregisterReceiver(mBondStateReceiver)
        unregisterReceiver(mPairRequestReceiver)
        unregisterReceiver(bluetoothAdapterStateChangeListener)
    }

    fun setListener(listener: Listener) {
        mListener = listener
    }

    override fun onBackPressed() {
        if (isTestRunning) {
            showDialogConfirmStopTest()
        } else {
            disconnectGatt(mBluetoothGatt)
            super.onBackPressed()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIopTestBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        AppUtil.setEdgeToEdge(window, this)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.title_Interoperability_Test)
        }
        checkIfBluetoothIsSupported()

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        handler = Handler(Looper.getMainLooper())

        addChildrenView()
        showDetailInformationTest(POSITION_TEST_SCANNER, true)
        checkBluetoothExtendedSettings()
        registerBroadcastReceivers()
        handleClickEvents()
        startLogCapture()
        applyGestureNavigationMargin()
    }

    private fun applyGestureNavigationMargin() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.llIopFooter) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val gestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures())
            val bottomInset = maxOf(nav.bottom, gestures.bottom)
            val lp = v.layoutParams as ViewGroup.MarginLayoutParams
            val extraPaddingDp = 16
            lp.bottomMargin = if (bottomInset > 0)
                bottomInset + (extraPaddingDp * resources.displayMetrics.density).toInt()
            else 0
            v.layoutParams = lp
            insets
        }
        binding.btnStartAndStopTest.post { ViewCompat.requestApplyInsets(binding.btnStartAndStopTest) }
    }

    private fun startLogCapture() {
        lifecycleScope.launch(Dispatchers.IO) {
            captureContinuousLogcat("IOPTest").collect { logLine ->
                withContext(Dispatchers.Main) {
                    logs += logLine + "\n"
                }
            }
        }

    }

    fun captureContinuousLogcat(tag: String): Flow<String> = flow {
        val process = Runtime.getRuntime().exec("logcat -s $tag")
        val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))

        bufferedReader.useLines { lines ->
            lines.forEach { line ->
                emit(line)
            }
        }
    }

    private fun checkIfBluetoothIsSupported() {
        if (BluetoothAdapter.getDefaultAdapter() == null) {
            /*Toast.makeText(
                this,
                R.string.iop_test_toast_bluetooth_not_supported,
                Toast.LENGTH_SHORT
            ).show()*/
            CustomToastManager.show(
                this@IOPTestActivity,
                getString(R.string.iop_test_toast_bluetooth_not_supported),
                5000
            )
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_iop_test, menu)
        shareMenuItem = menu?.get(0)?.also {
            it.isVisible = false
        }
        return true
    }

    private fun showCustomDialog() {
        val dialog = Dialog(this)
        val dialogBinding: DialogShareIopLogBinding =
            DialogShareIopLogBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        dialogBinding.tvTestLog.setOnClickListener {
            isLogcatData = false
            //saveLogFile()
            saveLogcatFile()
            dialog.dismiss()
        }

        dialogBinding.tvAppLog.setOnClickListener {
            isLogcatData = true
            saveLogcatFile()
            dialog.dismiss()
        }

        dialogBinding.shareLogCancel.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.iop_share -> {
                showCustomDialog()
                true
            }

            android.R.id.home -> {
                val dialog =
                    supportFragmentManager.findFragmentByTag("select_device_tag") as? SelectDeviceDialog
                if (dialog != null && dialog.isVisible) {
                    dialog.dismiss()
                }
                onBackPressed()
                true
            }

            else -> false
        }
    }

    private fun checkBluetoothExtendedSettings() {
        if (bluetoothAdapter.isLe2MPhySupported) {
            Log.d(TAG, "2M PHY supported!")
        }
        if (bluetoothAdapter.isLeExtendedAdvertisingSupported) {
            Log.d(TAG, "LE Extended Advertising supported!")
        }
        val maxDataLength = bluetoothAdapter.leMaximumAdvertisingDataLength
        Log.d(TAG, "maxDataLength $maxDataLength")
    }

    private fun registerBroadcastReceivers() {
        registerReceiver(
            mBondStateReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            RECEIVER_EXPORTED
        )
        registerReceiver(
            mPairRequestReceiver, IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST),
            RECEIVER_EXPORTED
        )
        registerReceiver(
            bluetoothAdapterStateChangeListener,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            RECEIVER_EXPORTED
        )
    }

    private fun handleClickEvents() {
        binding.btnStartAndStopTest.setOnClickListener {
            startTest()
        }
    }

    /**
     * Scan Device
     */
    private fun scanLeDevice(enable: Boolean) {
        if (enable) {
            if (!isScanning) {
                isScanning = true
                handler?.postDelayed(scanRunnable, SCAN_PERIOD)
                readScannerStartTime = true
                Log.d(TAG, "Scanner Fw name: " + getSiliconLabsTestInfo().fwName)
                if (bluetoothAdapter.isEnabled) {
                    bluetoothLeScanner.startScan(scanCallback)
                }
            } else {
                Log.d(TAG, "Scan has already started")
            }
        } else {
            stopScanner()
            handler?.removeCallbacks(scanRunnable)
        }
    }

    private val scanRunnable: Runnable = Runnable {
        if (isScanning) {
            stopScanner()
            Log.d(TAG, "scanRunnable updateDataTestFailed")
            updateDataTestFailed(mIndexRunning)
        }
    }
    private val connectionRunnable: Runnable = Runnable {
        if (!isConnected) {
            Log.d(TAG, "updateDataTestFailed connectionRunnable")
            if (!otaProcess) {
                countReTest++
                retryIOP3Failed(mIndexRunning, countReTest)
            }
        }
    }

    /**
     * Stop Scan device
     */
    private fun stopScanner() {
        isScanning = false
        if (bluetoothAdapter.isEnabled) {
            bluetoothLeScanner.stopScan(scanCallback)
        }
    }

    /**
     * Dialog Confirm stop test
     */
    private fun showDialogConfirmStopTest() {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.iop_test_message_stop_test))
            .setPositiveButton(getString(R.string.button_ok)) { dialog, _ ->
                dialog.dismiss()
                mBluetoothService?.isNotificationEnabled = true
                disconnectGatt(mBluetoothGatt)
                finish()
            }.setNegativeButton(getString(R.string.button_cancel)) { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    /**
     * Add Fragment have not yet list item test.
     */
    private fun addChildrenView() {
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.container, newInstance(), IOPTestFragment::class.java.name)
            disallowAddToBackStack()
        }.commit()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        when (requestCode) {
            BLUETOOTH_SETTINGS_REQUEST_CODE -> {
                if (!bluetoothAdapter.isEnabled && mBluetoothEnableDialog != null) {
                    mBluetoothEnableDialog?.show()
                }
            }

            GBL_FILE_CHOICE_REQUEST_CODE -> {
                intent?.data?.let {
                    otaFileManager?.readFilename(it)
                    otaFileSelectionDialog?.changeFileName(otaFileManager?.otaFilename)
                    if (otaFileManager?.hasCorrectFileExtension() == true) {
                        otaFileManager?.readFile(it)
                        otaFileSelectionDialog?.enableUploadButton()
                    } else {
                        /*Toast.makeText(this, getString(R.string.incorrect_file), Toast.LENGTH_SHORT)
                            .show()*/
                        CustomToastManager.show(
                            this@IOPTestActivity, getString(R.string.incorrect_file), 5000
                        )
                    }
                } ?: /*Toast.makeText(
                    this,
                    getString(R.string.chosen_file_not_found),
                    Toast.LENGTH_SHORT
                ).show()*/
                CustomToastManager.show(
                    this@IOPTestActivity, getString(R.string.chosen_file_not_found), 5000
                )
            }

            SAVE_LOG_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    intent?.data?.let { uri ->
                        Executors.newSingleThreadExecutor().execute {
                            val file = saveDataLogcatDataToFile(uri)
                            handler?.post {
                                shareLogDataTestByEmail(uri)
                            }
                        }
                    } ?: run {
                        /*Toast.makeText(
                            this,
                            getString(R.string.chosen_file_not_found),
                            Toast.LENGTH_SHORT
                        ).show()*/
                        CustomToastManager.show(
                            this@IOPTestActivity, getString(R.string.chosen_file_not_found), 5000
                        )
                    }
                }
            }
        }
    }

    /**
     * Save log data into File
     */
    private fun saveDataLogcatDataToFile(uri: Uri): File {
        val contentResolver = applicationContext.contentResolver
        val outputStream = contentResolver.openOutputStream(uri)
        outputStream?.use { fOut ->
            OutputStreamWriter(fOut).use { myOutWriter ->
                if (isLogcatData) {
                    myOutWriter.write(logs + "\n" + getDataLog())
                } else {
                    myOutWriter.write(getDataLog())
                }


            }
        } ?: throw IOException("Failed to open output stream")

        return File(uri.path ?: throw IOException("File path not available from URI"))


    }

    /**
     * Enable indicate by characteristics
     */
    private fun setIndicationProperty(
        characteristic: BluetoothGattCharacteristic?,
        indicate: Notifications
    ) {
        setNotificationForCharacteristic(
            mBluetoothGatt!!,
            characteristic,
            UuidConsts.CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR,
            indicate
        )
    }

    /**
     * Enable notification by characteristics
     */
    private fun setNotificationForCharacteristic(
        characteristic: BluetoothGattCharacteristic?,
        notifications: Notifications
    ) {
        setNotificationForCharacteristic(
            mBluetoothGatt!!,
            characteristic,
            UuidConsts.CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR,
            notifications
        )
    }


    /**
     * Write down the value in characteristics and wait for response
     */
    private fun writeValueToCharacteristic(
        characteristic: BluetoothGattCharacteristic?,
        value: String?,
        byteValues: ByteArray?,
        callback: (Boolean) -> Unit // Callback parameter
    ) {
        var newValue: ByteArray? = null
        if (byteValues != null) {
            newValue = byteValues
        } else if (!TextUtils.isEmpty(value)) {
            newValue = Converters.hexToByteArray(value!!)
        }
        if (isSetProperty(Common.PropertyType.WRITE, characteristic!!.properties)) {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else if (isSetProperty(
                Common.PropertyType.WRITE_NO_RESPONSE,
                characteristic.properties
            )
        ) {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        characteristic.value = newValue
        Log.d(TAG, "writeValueToCharacteristic " + characteristic.uuid.toString())

        // Perform the write operation asynchronously
        val success = mBluetoothGatt?.writeCharacteristic(characteristic) ?: false

        // Invoke the callback with the write operation result
        callback(success)
    }

    /**
     * Write down the value in characteristics
     */
    private fun writeValueToCharacteristic(
        characteristic: BluetoothGattCharacteristic?,
        value: String?,
        byteValues: ByteArray?
    ) {
        if (characteristic == null) {
            Timber.tag(TAG).e("writeValueToCharacteristic called with null characteristic")
            return
        }
        var newValue: ByteArray? = null
        if (byteValues != null) {
            newValue = byteValues
        } else if (!TextUtils.isEmpty(value)) {
            newValue = Converters.hexToByteArray(value!!)
        }
        if (isSetProperty(Common.PropertyType.WRITE, characteristic!!.properties)) {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else if (isSetProperty(
                Common.PropertyType.WRITE_NO_RESPONSE,
                characteristic.properties
            )
        ) {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        characteristic.value = newValue
        Log.d(TAG, "writeValueToCharacteristic " + characteristic.uuid.toString())
        if (!mBluetoothGatt!!.writeCharacteristic(characteristic)) {
            Log.e(
                TAG,
                String.format(
                    "ERROR: writeCharacteristic failed for characteristic: %s",
                    characteristic.uuid
                )
            )
        }
    }

    /**
     * Create values of write characteristics
     */
    private fun writeHexForMaxLengthByte(value: String, length: Int): String {
        return StringBuilder().apply {
            for (i in 0 until length) {
                append(value)
            }
        }.toString()
    }

    /**
     * Read values by characteristic
     */
    private fun readCharacteristic(characteristic: BluetoothGattCharacteristic?) {
        mBluetoothGatt?.readCharacteristic(characteristic)
    }

    /**
     * Read all service
     */
    private fun getServicesInfo(gatt: BluetoothGatt) {
        mEndTimeDiscover = System.currentTimeMillis()
        val gattServices = gatt.services
        Log.d(TAG, "getServicesInfo(), Services count: " + gattServices.size)
        var count = 0
        for (gattService: BluetoothGattService in gattServices) {
            val serviceUUID = gattService.uuid.toString()
            Log.d(TAG, "getServicesInfo(), add Characteristic of ServiceUUID $serviceUUID")

            when (serviceUUID) {
                CommonUUID.Service.UUID_GENERIC_ATTRIBUTE.toString() -> {
                    val gattCharacteristics = gattService.characteristics
                    for (item: BluetoothGattCharacteristic in gattCharacteristics) {
                        Log.d(TAG, "mBluetoothGattServiceGenericAttribute " + item.uuid.toString())
                        when {
                            CommonUUID.Characteristic.IOP_TEST_PHASE3_SERVICE_CHANGED.toString() == item.uuid.toString() -> {
                                characteristicIOPPhase3ServiceChanged = item
                            }

                            CommonUUID.Characteristic.IOP_TEST_PHASE3_CLIENT_SUPPORT_FEATURES.toString() == item.uuid.toString() -> {
                                characteristicIOPPhase3ClientSupportedFeatures = item
                            }

                            CommonUUID.Characteristic.IOP_TEST_PHASE3_DATABASE_HASH.toString() == item.uuid.toString() -> {
                                characteristicIOPPhase3DatabaseHash = item
                            }
                        }
                    }
                }

                CommonUUID.Service.UUID_GENERIC_ACCESS.toString() -> {
                    val gattCharacteristics = gattService.characteristics
                    for (item: BluetoothGattCharacteristic in gattCharacteristics) {
                        if (CommonUUID.Characteristic.IOP_DEVICE_NAME.toString() == item.uuid.toString()) {
                            Log.d(TAG, "characteristicIOPPhase3DeviceName " + item.uuid.toString())
                            characteristicIOPPhase3DeviceName = item
                        }
                    }
                }

                CommonUUID.Service.TEST_PARAMETERS.toString() -> {
                    count++
                    testParametersService = gattService
                }

                CommonUUID.Service.UUID_PROPERTIES_SERVICE.toString(),
                CommonUUID.Service.UUID_CHARACTERISTICS_SERVICE.toString() -> {
                    count++
                    val gattCharacteristics = gattService.characteristics
                    for (item: BluetoothGattCharacteristic in gattCharacteristics) {
                        mListCharacteristics.add(item)
                    }
                }

                CommonUUID.Service.UUID_PHASE3_SERVICE.toString() -> {
                    Log.d(TAG, "mBluetoothGattServicePhase3 " + gattService.uuid.toString())
                    val gattCharacteristicsIOPPhase3 = gattService.characteristics
                    for (item: BluetoothGattCharacteristic in gattCharacteristicsIOPPhase3) {
                        val charUUID = item.uuid.toString()
                        when (charUUID) {
                            CommonUUID.Characteristic.IOP_TEST_PHASE3_CONTROL.toString() -> {
                                characteristicIOPPhase3Control = item
                            }

                            CommonUUID.Characteristic.IOP_TEST_THROUGHPUT.toString() -> {
                                characteristicIOPPhase3Throughput = item
                            }

                            CommonUUID.Characteristic.IOP_TEST_GATT_CATCHING.toString() -> {
                                characteristicIOPPhase3IOPTestCaching = item
                                Log.d(
                                    TAG,
                                    "characteristicIOPPhase3IOPTestCaching " + characteristicIOPPhase3IOPTestCaching?.uuid.toString()
                                )
                            }

                            CommonUUID.Characteristic.IOP_TEST_SERVICE_CHANGED_INDICATION.toString() -> {
                                characteristicIOPPhase3IOPTestServiceChangedIndication = item
                                Log.d(
                                    TAG,
                                    "characteristicIOPPhase3IOPTestServiceChangedIndication " + characteristicIOPPhase3IOPTestServiceChangedIndication?.uuid.toString()
                                )
                            }

                            CommonUUID.Characteristic.IOP_TEST_SECURITY_PAIRING.toString(),
                            CommonUUID.Characteristic.IOP_TEST_SECURITY_AUTHENTICATION.toString(),
                            CommonUUID.Characteristic.IOP_TEST_SECURITY_BONDING.toString() -> {
                                Log.d(TAG, "characteristicsPhase3Security $charUUID")
                                characteristicsPhase3Security.add(item)
                            }
                        }
                    }
                }

                CommonUUID.Service.UUID_BLE_OTA.toString() -> {
                    val gattCharacteristics = gattService.characteristics
                    for (gattCharacteristic: BluetoothGattCharacteristic in gattCharacteristics) {
                        val characteristicUUID = gattCharacteristic.uuid.toString()
                        Log.i(
                            TAG,
                            "onServicesDiscovered Characteristic UUID " + characteristicUUID + " - Properties: " + gattCharacteristic.properties
                        )
                        if (gattCharacteristic.uuid.toString() == ota_control.toString()) {
                            if (gattCharacteristics.contains(
                                    mBluetoothGatt?.getService(ota_service)
                                        ?.getCharacteristic(ota_data)
                                )
                            ) {
                                if (!gattServices.contains(
                                        mBluetoothGatt?.getService(
                                            homekit_service
                                        )
                                    )
                                ) {
                                    Log.i(TAG, "onServicesDiscovered Device in DFU Mode")
                                    if (mIndexRunning == 4 || mIndexRunning == 5) {
                                        mBluetoothGatt?.requestMtu(247)
                                    }
                                } else {
                                    Log.i(TAG, "onServicesDiscovered OTA_Control found")
                                    val gattDescriptors = gattCharacteristic.descriptors
                                    for (gattDescriptor: BluetoothGattDescriptor in gattDescriptors) {
                                        val descriptor = gattDescriptor.uuid.toString()
                                        if (gattDescriptor.uuid.toString() == homekit_descriptor.toString()) {
                                            kitDescriptor = gattDescriptor
                                            Log.i(TAG, "descriptor, UUID: $descriptor")
                                            val stable = byteArrayOf(0x00.toByte(), 0x00.toByte())
                                            homeKitOTAControl(stable)
                                            homekit = true
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (i in getListChildrenItemTestCase(POSITION_TEST_SERVICE)!!.indices) {
            for (j in i until mListCharacteristics.size) {
                getListChildrenItemTestCase(POSITION_TEST_SERVICE)!![i].characteristic =
                    mListCharacteristics[j]
                break
            }
        }
        for (i in getListChildrenItemTestCase(POSITION_TEST_IOP3_SECURITY)!!.indices) {
            for (j in i until characteristicsPhase3Security.size) {
                getListChildrenItemTestCase(POSITION_TEST_IOP3_SECURITY)!![i].characteristic =
                    characteristicsPhase3Security[j]
                break
            }
        }



        when (mIndexRunning) {
            POSITION_TEST_DISCOVER_SERVICE -> {
                testParametersService?.let {
                    handler?.postDelayed({ readCharacteristic(it.characteristics[0]) }, 5000)
                }
            }

            POSITION_TEST_IOP3_OTA_WITHOUT_ACK,
            POSITION_TEST_IOP3_OTA_ACK -> {
                if (!otaProcess) {
                    handler?.postDelayed({
                        Log.d(TAG, "read device name")
                        readCharacteristic(characteristicIOPPhase3DeviceName)
                    }, 5000)
                }
            }

            POSITION_TEST_IOP3_THROUGHPUT -> {
                mEndThroughputNotification = false
                finishItemTest(
                    POSITION_TEST_IOP3_THROUGHPUT,
                    getSiliconLabsTestInfo().listItemTest[POSITION_TEST_IOP3_THROUGHPUT]
                )
            }

            POSITION_TEST_IOP3_LE_PRIVACY -> {
                return
                /* finishItemTest(
                     POSITION_TEST_IOP3_LE_PRIVACY,
                     getSiliconLabsTestInfo().listItemTest[POSITION_TEST_IOP3_LE_PRIVACY]
                 )*/
            }

            POSITION_TEST_IOP3_SECURITY -> {
                if (iopPhase3BondingStep == 2) {
                    iopPhase3RunTestCaseSecurity(iopPhase3IndexStartChildrenTest, 0)
                } else {
                    iopPhase3RunTestCaseBonding(6)
                }
                // iopPhase3RunTestCaseSecurity(iopPhase3IndexStartChildrenTest, 0)
            }

            POSITION_TEST_IOP3_CACHING -> {
                if (characteristicIOPPhase3IOPTestCaching != null) {
                    getListChildrenItemTestCase(POSITION_TEST_IOP3_CACHING)!![0].characteristic =
                        characteristicIOPPhase3IOPTestCaching
                    readCharacteristic(characteristicIOPPhase3IOPTestCaching)
                } else if (characteristicIOPPhase3IOPTestServiceChangedIndication != null) {
                    getListChildrenItemTestCase(POSITION_TEST_IOP3_CACHING)!![1].characteristic =
                        characteristicIOPPhase3IOPTestServiceChangedIndication
                    readCharacteristic(characteristicIOPPhase3IOPTestServiceChangedIndication)
                }
            }
        }
    }

    private fun getModelNumberStringCharacteristic(): BluetoothGattCharacteristic? {
        val gattService = mBluetoothGatt?.getService(GattService.DeviceInformation.number)
        return gattService?.getCharacteristic(GattCharacteristic.ModelNumberString.uuid)
    }

    private fun readFirmwareVersion(payload: ByteArray) {
        when (mIndexRunning) {
            POSITION_TEST_DISCOVER_SERVICE -> {
                getSiliconLabsTestInfo().firmwareVersion = parseFirmwareVersion(payload)
            }

            POSITION_TEST_IOP3_OTA_ACK -> {
                getSiliconLabsTestInfo().firmwareAckVersion = parseFirmwareVersion(payload)
            }

            POSITION_TEST_IOP3_OTA_WITHOUT_ACK -> {
                getSiliconLabsTestInfo().firmwareUnackVersion = parseFirmwareVersion(payload)
            }

            else -> {}
        }
        readCharacteristic(testParametersService?.characteristics?.get(1))
    }

    private fun getFirmwareVersion(): String {
        for (fwString in listOf(
            getSiliconLabsTestInfo().firmwareUnackVersion,
            getSiliconLabsTestInfo().firmwareAckVersion
        )) {
            if (fwString != "") {
                return fwString
            }
        }
        return getSiliconLabsTestInfo().firmwareVersion
    }

    private fun readConnectionParameters(payload: ByteArray) {
        var payloadIndex = 0
        when (getFirmwareVersion()) {
            "3.2.1", "3.2.2", "3.2.3", "3.2.4" -> {
                getSiliconLabsTestInfo().iopBoard = IopBoard.fromBoardCode(payload[0])
                payloadIndex = 2
            }

            else -> readCharacteristic(getModelNumberStringCharacteristic())
        }

        getSiliconLabsTestInfo().connectionParameters = ConnectionParameters(
            mtu = Converters.calculateDecimalValue(
                payload.copyOfRange(payloadIndex, payloadIndex + 2), isBigEndian = false
            ),
            pdu = Converters.calculateDecimalValue(
                payload.copyOfRange(payloadIndex + 2, payloadIndex + 4), isBigEndian = false
            ),
            interval = Converters.calculateDecimalValue(
                payload.copyOfRange(payloadIndex + 4, payloadIndex + 6), isBigEndian = false
            ).toDouble()
                .times(1.25), // Conversion of sent int representation to actual double value in ms
            slaveLatency = Converters.calculateDecimalValue(
                payload.copyOfRange(payloadIndex + 6, payloadIndex + 8), isBigEndian = false
            ),
            supervisionTimeout = Converters.calculateDecimalValue(
                payload.copyOfRange(payloadIndex + 8, payloadIndex + 10), isBigEndian = false
            )
                .times(10), // Conversion of sent int representation to actual value in ms
        )
        if (mIndexRunning == POSITION_TEST_DISCOVER_SERVICE) {
            mIndexStartChildrenTest = 0
            finishItemTest(
                POSITION_TEST_DISCOVER_SERVICE,
                getSiliconLabsTestInfo().listItemTest[POSITION_TEST_DISCOVER_SERVICE]
            )
            Log.d(TAG, "convertValuesParameters(), finishItemTest(POSITION_TEST_DISCOVER_SERVICE)")
        }
    }

    private fun parseFirmwareVersion(payload: ByteArray): String {
        return if (payload.size < 8) "3.2.1" /* Old, incompatible method for numbering versions. */
        else StringBuilder().apply {
            append(Converters.calculateDecimalValue(payload.copyOfRange(0, 2), isBigEndian = false))
            append(".")
            append(Converters.calculateDecimalValue(payload.copyOfRange(2, 4), isBigEndian = false))
            append(".")
            append(Converters.calculateDecimalValue(payload.copyOfRange(4, 6), isBigEndian = false))
        }.toString()
    }

    /**
     * Get list item children test by index test
     */
    private fun getListChildrenItemTestCase(index: Int): List<ChildrenItemTestInfo>? {
        return getListItemChildrenTest(index)
    }

    /**
     * Set time start and End item notification and indicate
     */
    private fun setTimeStart(gattCharacteristic: BluetoothGattCharacteristic?) {
        for (item: ChildrenItemTestInfo in getListChildrenItemTestCase(POSITION_TEST_SERVICE)!!) {
            if (item.characteristic?.uuid == gattCharacteristic!!.uuid) {
                item.startTimeTest = System.currentTimeMillis()
            }
        }
    }

    /**
     * Get Path of log file in ExternalStorage
     */
    private fun getPathOfLogFile(): String {
        val boardName = getSiliconLabsTestInfo().iopBoard.icName.text
        val pathFile = getSiliconLabsTestInfo().phoneName
        pathFile.replace(" ", "")
        if (isLogcatData) {
            return "logcat" + "_" + getDate("EEE MMM dd HH_mm_ss z yyyy") + ".txt"

        } else {
            return pathFile + "_" + boardName + "_" + getDate("yyyy_MM_dd_HH_mm_ss") + ".txt"
        }
    }


    /**
     * Get Date currently
     */
    private fun getDate(format: String): String {
        return SimpleDateFormat(format, Locale.getDefault()).format(Date())
    }

    /**
     * Get log data by object SiliconlabsInformation
     */
    private fun getDataLog(): String {
        return getSiliconLabsTestInfo().toString()
    }

    private fun shareLogDataTestByEmail(uri: Uri) {
        try {
            val message = "Please check the attachment to get the log file."
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                if (isLogcatData) {
                    putExtra(Intent.EXTRA_SUBJECT, "[Silabs] Application Debug log")
                } else {
                    putExtra(Intent.EXTRA_SUBJECT, "[Silabs] Test log")
                }

                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Exception in shareLogDataTestByEmail(): $e")
        }
    }


    /**
     * Perform the test by item test case
     */
    private fun runChildrenTestCase(index: Int) {
        showDetailInformationTest(POSITION_TEST_SERVICE, false)

        val childrenItem = getListChildrenItemTestCase(POSITION_TEST_SERVICE)!![index]
        val uuids = CommonUUID.Characteristic.values()
        val cUuid = childrenItem.characteristic?.uuid.toString()
        if (TextUtils.isEmpty(cUuid)) {
            childrenItem.statusRunTest = 0 // 0 running, 1 finish test
            if (mIndexStartChildrenTest <= 17) {
                mIndexStartChildrenTest += 1
                runChildrenTestCase(mIndexStartChildrenTest)
            } else {
                return
            }
        }
        var matchChar = -1
        for (i in uuids.indices) {
            if (cUuid.equals(uuids[i].toString(), ignoreCase = true)) {
                matchChar = uuids[i].id
                Log.d(
                    TAG,
                    "Run TestCase [" + (matchChar + 3) + "] at characteristic " + uuids[i].toString()
                )
                break
            }
        }
        when (matchChar) {
            CommonUUID.ID_READ_ONLY_LENGTH_1, CommonUUID.ID_READ_ONLY_LENGTH_255 -> readCharacteristic(
                childrenItem.characteristic
            )

            CommonUUID.ID_WRITE_ONLY_LENGTH_1, CommonUUID.ID_WRITE_WITHOUT_RESPONSE_LENGTH_1 -> writeValueToCharacteristic(
                childrenItem.characteristic,
                writeHexForMaxLengthByte(Common.WRITE_VALUE_0, Common.WRITE_LENGTH_1),
                null
            )

            CommonUUID.ID_WRITE_ONLY_LENGTH_255, CommonUUID.ID_WRITE_WITHOUT_RESPONSE_LENGTH_255 -> writeValueToCharacteristic(
                childrenItem.characteristic,
                writeHexForMaxLengthByte(Common.WRITE_VALUE_0, Common.WRITE_LENGTH_255),
                null
            )

            CommonUUID.ID_NOTIFICATION_LENGTH_1, CommonUUID.ID_NOTIFICATION_LENGTH_MTU_3 -> {
                setTimeStart(childrenItem.characteristic)
                setNotificationForCharacteristic(childrenItem.characteristic, Notifications.NOTIFY)
            }

            CommonUUID.ID_INDICATE_LENGTH_1, CommonUUID.ID_INDICATE_LENGTH_MTU_3 -> {
                setTimeStart(childrenItem.characteristic)
                setIndicationProperty(childrenItem.characteristic, Notifications.INDICATE)
            }

            CommonUUID.ID_IOP_TEST_LENGTH_1, CommonUUID.ID_IOP_TEST_USER_LEN_1 -> if (!childrenItem.isWriteCharacteristic) {
                writeValueToCharacteristic(
                    childrenItem.characteristic,
                    writeHexForMaxLengthByte(Common.WRITE_VALUE_55, Common.WRITE_LENGTH_1),
                    null
                )
            } else {
                readCharacteristic(childrenItem.characteristic)
            }

            CommonUUID.ID_IOP_TEST_LENGTH_255, CommonUUID.ID_IOP_TEST_USER_LEN_255 -> if (!childrenItem.isWriteCharacteristic) {
                writeValueToCharacteristic(
                    childrenItem.characteristic,
                    null,
                    Converters.decToByteArray(createDataTestCaseLength255(mtuValue))
                )
            } else {
                readCharacteristic(childrenItem.characteristic)
            }

            CommonUUID.ID_IOP_TEST_LENGTH_VARIABLE_4, CommonUUID.ID_IOP_TEST_USER_LEN_VARIABLE_4 ->                 // write length 1
                if (childrenItem.getLstValueItemTest()
                        .isEmpty() && !childrenItem.isWriteCharacteristic
                ) {
                    writeValueToCharacteristic(
                        childrenItem.characteristic,
                        writeHexForMaxLengthByte(Common.WRITE_VALUE_55, Common.WRITE_LENGTH_1),
                        null
                    )
                } else if (childrenItem.getLstValueItemTest()
                        .isEmpty() && childrenItem.isWriteCharacteristic
                ) {
                    childrenItem.isWriteCharacteristic = false
                    childrenItem.statusWrite = -1
                    readCharacteristic(childrenItem.characteristic)
                } else if (childrenItem.getLstValueItemTest().size == 1 && !childrenItem.isWriteCharacteristic) {
                    childrenItem.statusRead = -1
                    childrenItem.isReadCharacteristic = false
                    writeValueToCharacteristic(
                        childrenItem.characteristic,
                        writeHexForMaxLengthByte(Common.WRITE_VALUE_66, Common.WRITE_LENGTH_4),
                        null
                    )
                } else if (childrenItem.getLstValueItemTest().size == 1 && childrenItem.isWriteCharacteristic) {
                    readCharacteristic(childrenItem.characteristic)
                }

            CommonUUID.ID_IOP_TEST_CONST_LENGTH_1 -> if (!childrenItem.isReadCharacteristic) {
                readCharacteristic(childrenItem.characteristic)
            } else {
                writeValueToCharacteristic(
                    childrenItem.characteristic,
                    writeHexForMaxLengthByte(Common.WRITE_VALUE_55, Common.WRITE_LENGTH_1),
                    null
                )
            }

            CommonUUID.ID_IOP_TEST_CONST_LENGTH_255 -> if (!childrenItem.isReadCharacteristic) {
                readCharacteristic(childrenItem.characteristic)
            } else {
                writeValueToCharacteristic(
                    childrenItem.characteristic,
                    null,
                    Converters.decToByteArray(createDataTestCaseLength255(mtuValue))
                )
            }

            else -> {
            }
        }
    }

    /**
     * Return value write to characteristics
     */
    private fun createDataTestCaseLength255(len: Int): String {
        val builder = StringBuilder()
        for (i in 0 until len) {
            builder.append(i).append(" ")
        }
        return builder.toString()
    }

    /**
     * Check status item test and the next item test
     */
    private fun checkNextTestCase(characteristic: BluetoothGattCharacteristic?, type: Int) {
        if (characteristic != null) {
            for (itemChildrenTest: ChildrenItemTestInfo in getListChildrenItemTestCase(
                POSITION_TEST_SERVICE
            )!!) {
                if (itemChildrenTest.characteristic?.uuid.toString() == characteristic.uuid.toString()) {
                    if (type == 1) {
                        itemChildrenTest.isReadCharacteristic = true
                    } else if (type == 2) {
                        itemChildrenTest.isWriteCharacteristic = true
                    }
                    if (CommonUUID.Characteristic.IOP_TEST_LENGTH_1.toString() == characteristic.uuid.toString() || CommonUUID.Characteristic.IOP_TEST_LENGTH_255.toString() == characteristic.uuid.toString() || CommonUUID.Characteristic.IOP_TEST_CONST_LENGTH_1.toString() == characteristic.uuid.toString() || CommonUUID.Characteristic.IOP_TEST_CONST_LENGTH_255.toString() == characteristic.uuid.toString() || CommonUUID.Characteristic.IOP_TEST_USER_LEN_1.toString() == characteristic.uuid.toString() || CommonUUID.Characteristic.IOP_TEST_USER_LEN_255.toString() == characteristic.uuid.toString()) {
                        mIndexStartChildrenTest =
                            if (itemChildrenTest.isReadCharacteristic && itemChildrenTest.isWriteCharacteristic) {
                                getListChildrenItemTestCase(POSITION_TEST_SERVICE)!!.indexOf(
                                    itemChildrenTest
                                ) + 1
                            } else {
                                getListChildrenItemTestCase(POSITION_TEST_SERVICE)!!.indexOf(
                                    itemChildrenTest
                                )
                            }
                    } else if (CommonUUID.Characteristic.NOTIFICATION_LENGTH_1.toString() == characteristic.uuid.toString()) {
                        if (isDisabled) {
                            mIndexStartChildrenTest =
                                getListChildrenItemTestCase(POSITION_TEST_SERVICE)!!.indexOf(
                                    itemChildrenTest
                                )
                            isDisabled = false
                        } else {
                            if (itemChildrenTest.statusChildrenTest) {
                                countReTest = 0
                                mIndexStartChildrenTest =
                                    getListChildrenItemTestCase(POSITION_TEST_SERVICE)!!.indexOf(
                                        itemChildrenTest
                                    ) + 1
                                isDisabled = false
                            } else {
                                countReTest++
                                if (countReTest > 5) {
                                    mIndexStartChildrenTest =
                                        getListChildrenItemTestCase(POSITION_TEST_SERVICE)!!.indexOf(
                                            itemChildrenTest
                                        ) + 1
                                    isDisabled = false
                                    countReTest = 0
                                } else {
                                    isDisabled = true
                                    setNotificationForCharacteristic(
                                        characteristic,
                                        Notifications.DISABLED
                                    )
                                    return
                                }
                            }
                        }
                    } else if (CommonUUID.Characteristic.INDICATE_LENGTH_1.toString() == characteristic.uuid.toString()) {
                        if (isDisabled) {
                            mIndexStartChildrenTest =
                                getListChildrenItemTestCase(POSITION_TEST_SERVICE)!!.indexOf(
                                    itemChildrenTest
                                )
                            isDisabled = false
                        } else {
                            if (itemChildrenTest.statusChildrenTest) {
                                countReTest = 0
                                mIndexStartChildrenTest =
                                    getListChildrenItemTestCase(POSITION_TEST_SERVICE)!!.indexOf(
                                        itemChildrenTest
                                    ) + 1
                                isDisabled = false
                            } else {
                                countReTest++
                                if (countReTest > 5) {
                                    countReTest = 0
                                    mIndexStartChildrenTest =
                                        getListChildrenItemTestCase(POSITION_TEST_SERVICE)!!.indexOf(
                                            itemChildrenTest
                                        ) + 1
                                    isDisabled = false
                                } else {
                                    isDisabled = true
                                    setIndicationProperty(characteristic, Notifications.DISABLED)
                                    return
                                }
                            }
                        }
                    } else if (CommonUUID.Characteristic.IOP_TEST_LENGTH_VARIABLE_4.toString() == characteristic.uuid.toString() || CommonUUID.Characteristic.IOP_TEST_USER_LEN_VARIABLE_4.toString() == characteristic.uuid.toString()) {
                        if (itemChildrenTest.getLstValueItemTest().size > 1 && itemChildrenTest.isReadCharacteristic) {
                            mIndexStartChildrenTest =
                                getListChildrenItemTestCase(POSITION_TEST_SERVICE)!!.indexOf(
                                    itemChildrenTest
                                ) + 1
                            if ((CommonUUID.Characteristic.IOP_TEST_USER_LEN_VARIABLE_4.toString() == characteristic.uuid.toString())) {
                                finishItemTest(
                                    POSITION_TEST_SERVICE,
                                    getItemTestCaseInfo(POSITION_TEST_SERVICE)
                                )
                            }
                        } else {
                            mIndexStartChildrenTest =
                                getListChildrenItemTestCase(POSITION_TEST_SERVICE)!!.indexOf(
                                    itemChildrenTest
                                )
                        }
                    } else {
                        mIndexStartChildrenTest =
                            getListChildrenItemTestCase(POSITION_TEST_SERVICE)!!.indexOf(
                                itemChildrenTest
                            ) + 1
                    }
                    break
                }
            }
            if (mIndexRunning == POSITION_TEST_IOP3_SECURITY) {
                for (itemChildrenTest: ChildrenItemTestInfo in getListChildrenItemTestCase(
                    POSITION_TEST_IOP3_SECURITY
                )!!) {
                    if (itemChildrenTest.characteristic?.uuid.toString() == characteristic.uuid.toString()) {
                        handler?.removeCallbacks(iopSecurityRunnable)
                        if (type == 1) {
                            itemChildrenTest.isReadCharacteristic = true
                        }
                        if (CommonUUID.Characteristic.IOP_TEST_SECURITY_BONDING.toString() != (characteristic.uuid.toString())) {
                            iopPhase3IndexStartChildrenTest++
                            Log.d(
                                TAG,
                                "iopPhase3IndexStartChildrenTest $iopPhase3IndexStartChildrenTest"
                            )
                            showDetailInformationTest(testCaseCount++, false)
                            iopPhase3RunTestCaseSecurity(iopPhase3IndexStartChildrenTest, 1)
                        } /*else {
                            finishItemTest(POSITION_TEST_IOP3_SECURITY, getSiliconLabsTestInfo().listItemTest[POSITION_TEST_IOP3_SECURITY])
                        }*/
                        else {
                            Log.d("checkNextTestCase", "iopPhase3RunTestCaseBonding")
                            if (iopPhase3BondingStep < 8) {
                                iopPhase3RunTestCaseBonding(iopPhase3BondingStep + 1)
                            }

                            showDetailInformationTest(testCaseCount++, false)
                        }
                    }
                }
            } else if (mIndexRunning == POSITION_TEST_IOP3_CACHING) {
                if (characteristicIOPPhase3IOPTestServiceChangedIndication != null) {
                    if ((characteristicIOPPhase3IOPTestServiceChangedIndication?.uuid.toString() == characteristic.uuid.toString())) {
                        handler?.removeCallbacks(iopCachingRunnable)

                        isServiceChangedIndication = 0
                        showDetailInformationTest(testCaseCount++, false)
                        iopPhase3RunTestCaseCaching(0)
                    }
                }
            }

        }
        if (mIndexStartChildrenTest <= 17) {
            runChildrenTestCase(mIndexStartChildrenTest)
        }
    }

    private var nrTries = 0
    private fun retryCommand(characteristic: BluetoothGattCharacteristic) {
        nrTries++
        if (nrTries <= 5) {
            handler?.postDelayed({
                Log.d(TAG, "retryCommand(), read characteristic" + characteristic.uuid.toString())
                readCharacteristic(characteristic)
            }, 1000)
        }
    }

    /**
     * Create values for control characteristic
     */
    private fun iopPhase3WriteControlBytes(
        throughput: Int,
        security: Int,
        caching: Int
    ): ByteArray {
        val hex = ByteArray(3)
        hex[0] = throughput.toByte()
        hex[1] = security.toByte()
        hex[2] = caching.toByte()
        return hex
    }

    private fun iopPhase3RunTestCaseBonding(step: Int) {
        Log.d("iopPhase3RunTestCaseBonding", "step $step")
        iopPhase3BondingStep = step
        val CCCD_value = ByteArray(1)
        val securityItem = getListChildrenItemTestCase(POSITION_TEST_IOP3_SECURITY)!![2]
        Log.d(
            "iopPhase3RunTestCaseBonding",
            "uuid " + UUID.fromString(securityItem.characteristic!!.uuid.toString())
        )
        iopPhase3ExtraDescriptor =
            securityItem.characteristic!!.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        when (step) {
            // write CCCD to different value from the default
            3 -> {
                Log.d(TAG, "CASE 3: iopPhase3RunTestCaseBonding" + "Write value 0x00 to CCCD")
                CCCD_value[0] = 1
                iopPhase3ExtraDescriptor?.setValue(CCCD_value)
                mBluetoothGatt?.writeDescriptor(iopPhase3ExtraDescriptor)

                mBluetoothGatt?.readDescriptor(iopPhase3ExtraDescriptor)
                read_CCCD_value = iopPhase3ExtraDescriptor?.getValue()!!.copyOf()
            }

            // disconnect
            4 -> {
                Log.d(TAG, "CASE 4: iopPhase3RunTestCaseBonding" + "disconnect")

                disconnectGatt(mBluetoothGatt)
            }

            6 -> {
                mBluetoothGatt?.readDescriptor(iopPhase3ExtraDescriptor)
                Log.d(
                    TAG,
                    "CASE 6: iopPhase3RunTestCaseBonding" + "read CCCD:" + read_CCCD_value[0]
                )
                // Mobile read CCCD. (Pass: if the CCCD value is the same as the value written before bond ).
                if (read_CCCD_value[0].toInt() == 1) {
                    isCCCDPass = true
                } else {
                    isCCCDPass = false
                    updateDataTestFailed(POSITION_TEST_IOP3_SECURITY)

                    finishItemTest(
                        POSITION_TEST_IOP3_SECURITY,
                        getSiliconLabsTestInfo().listItemTest[POSITION_TEST_IOP3_SECURITY]
                    )
                }
            }

            // Mobile write value 0xBB (0xBB or 0x00) to CCCD. (Pass: if the CCCD value was written in last step).
            7 -> {
                Log.d(TAG, "CASE 7: iopPhase3RunTestCaseBonding" + "Write value 0x00 to CCCD")
                CCCD_value[0] = 0
                iopPhase3ExtraDescriptor?.setValue(CCCD_value)
                mBluetoothGatt?.writeDescriptor(iopPhase3ExtraDescriptor)
            }

            8 -> {
                mBluetoothGatt?.readDescriptor(iopPhase3ExtraDescriptor)
                read_CCCD_value = iopPhase3ExtraDescriptor?.getValue()!!.copyOf()
                Log.d(
                    TAG,
                    "CASE 8: iopPhase3RunTestCaseBonding" + "read CCCD:" + read_CCCD_value[0]
                )
                // Mobile read CCCD. (Pass: if the CCCD value is the same as the value written before bond ).
                if (read_CCCD_value[0].toInt() == 0) {
                    isCCCDPass = true
                } else {
                    isCCCDPass = false
                    updateDataTestFailed(POSITION_TEST_IOP3_SECURITY)
                }
                finishItemTest(
                    POSITION_TEST_IOP3_SECURITY,
                    getSiliconLabsTestInfo().listItemTest[POSITION_TEST_IOP3_SECURITY]
                )
            }

            else -> {
            }
        }
    }

    private fun iopPhase3RunTestCaseSecurity(index: Int, isControl: Int) {
        iopPhase3IndexStartChildrenTest = index
        isConnecting = false
        val securityItem = getListChildrenItemTestCase(POSITION_TEST_IOP3_SECURITY)!![index]
        val cUuid = securityItem.characteristic?.uuid.toString()
        val uuids = CommonUUID.Characteristic.values()
        var matchChar = -1
        for (i in uuids.indices) {
            if (cUuid.equals(uuids[i].toString(), ignoreCase = true)) {
                matchChar = uuids[i].id
                Log.d(
                    TAG,
                    "Run TestCase [7." + (matchChar - CommonUUID.ID_IOP_TEST_PHASE3_THROUGHPUT) + "] at Characteristic " + uuids[i].toString()
                )
                break
            }
        }
        when (matchChar) {
            CommonUUID.ID_IOP_TEST_PHASE3_SECURITY_PAIRING,
            CommonUUID.ID_IOP_TEST_PHASE3_SECURITY_AUTHENTICATION,
            CommonUUID.ID_IOP_TEST_PHASE3_SECURITY_BONDING ->
                if (isControl == 1) {
                    if (iopPhase3BondingStep == 2) {
                        countReTest = 0
                        handler?.postDelayed(iopSecurityRunnable, 60000)
                        writeValueToCharacteristic(
                            characteristicIOPPhase3Control,
                            null,
                            iopPhase3WriteControlBytes(
                                0,
                                matchChar - CommonUUID.ID_IOP_TEST_PHASE3_CONTROL - 1,
                                0
                            )
                        )
                        Log.d(TAG, "Set Control Characteristic for Security")
                    } else {
                        //Mobile read CCCD
                        iopPhase3RunTestCaseBonding(6)
                    }
                } else if (!securityItem.isReadCharacteristic) {
                    Log.d(TAG, "Read Security Characteristic $cUuid")
                    readCharacteristic(securityItem.characteristic)
                }

            else -> {
            }
        }
    }


    private fun iopPhase3RunTestCaseThroughput(isControl: Int) {
        Log.d(
            TAG,
            "Run TestCase [6] at Characteristic " + characteristicIOPPhase3Throughput?.uuid.toString()
        )
        if (isControl == 1) {
            writeValueToCharacteristic(
                characteristicIOPPhase3Control,
                null,
                iopPhase3WriteControlBytes(1, 0, 0)
            )
            Log.d(TAG, "Set Control Characteristic for Throughput")
        } else {
            Log.d(TAG, "set Notification enable for Throughput")
            mEndThroughputNotification = false
            setNotificationForCharacteristic(
                characteristicIOPPhase3Throughput,
                Notifications.NOTIFY
            )
            mStartTimeThroughput = System.currentTimeMillis()
            handler?.postDelayed({
                mEndTimeThroughput = System.currentTimeMillis()
                mByteSpeed =
                    ((mByteNumReceived * 1000) / (mEndTimeThroughput - mStartTimeThroughput)).toInt()
                Log.d(TAG, "set Notification disable for throughput")
                Log.d(TAG, "Throughput is $mByteSpeed Bytes/sec")
                try {
                    setNotificationForCharacteristic(
                        characteristicIOPPhase3Throughput,
                        Notifications.DISABLED
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error Notification disable for throughput")
                }
                mEndThroughputNotification = true
            }, 5000)
        }
    }

    private fun iopPhase3RunTestCaseLEPrivacy(isControl: Int) {
        Log.d(
            TAG,
            "Run TestCase [8] at Characteristic " + characteristicIOPPhase3Throughput?.uuid.toString()
        )

        if (isControl == 1) {
            connectToDevice(mBluetoothDevice)

            writeValueToCharacteristic(
                characteristicIOPPhase3Control,
                null,
                iopPhase3WriteControlBytes(0, 4, 0)
            ) { success ->
                if (success) {
                    handler?.postDelayed({
                        Log.d(TAG, "Write operation successful")

                        connectToDevice(
                            mBluetoothDevice,
                            onConnectionSuccess = {
                                // Code to execute after successful connection
                                handler?.postDelayed({
                                    if (isConnected) {
                                        Log.d(TAG, "Connection successful")
                                        // Perform further actions here

                                        Log.d(TAG, "Set Control Characteristic for LE Privacy")
                                        if (isServiceChangedIndication == 1) {
                                            try {
                                                val connectionState = mBluetoothDevice?.bondState
                                                Log.d(TAG, "" + connectionState)


                                                when (connectionState) {
                                                    BluetoothDevice.BOND_BONDED -> {
                                                        // Device is bonded (connected)
                                                        Log.d(TAG, "Device is bonded (connected)")
                                                        setNotificationForCharacteristic(
                                                            characteristicIOPPhase3ServiceChanged,
                                                            Notifications.INDICATE
                                                        )
                                                        getItemTestCaseInfo(
                                                            POSITION_TEST_IOP3_LE_PRIVACY
                                                        ).setStatusTest(Common.IOP3_TC_STATUS_PASS)

                                                    }

                                                    BluetoothDevice.BOND_BONDING -> {
                                                        // Device is in the process of bonding (connecting)
                                                        // Handle this state if needed
                                                        Log.d(
                                                            TAG,
                                                            "Device is in the process of bonding (connecting)"
                                                        )
                                                    }

                                                    BluetoothDevice.BOND_NONE -> {
                                                        // Device is not bonded (not connected)
                                                        // Handle this state if needed
                                                        Log.d(
                                                            TAG,
                                                            "Device is not bonded (not connected)"
                                                        )
                                                        Log.d(
                                                            TAG,
                                                            "Connection is not trusted, not bonded"
                                                        )
                                                        updateDataTestFailed(
                                                            POSITION_TEST_IOP3_LE_PRIVACY
                                                        )
                                                        //  handler?.removeCallbacks(iopLEPrivacyRunnable)
                                                    }
                                                }

                                            } catch (e: Exception) {
                                                Log.d(TAG, "exception $e")
                                                updateDataTestFailed(POSITION_TEST_IOP3_LE_PRIVACY)
                                            }

                                        } else {
                                            Log.d(
                                                TAG,
                                                "isServiceChangedIndication $isServiceChangedIndication"
                                            )
                                            updateDataTestFailed(POSITION_TEST_IOP3_LE_PRIVACY)
                                        }
                                    }
                                }, 15000)


                            },
                            onConnectionFailure = {
                                // Code to handle connection failure
                                Log.e(TAG, "Connection failed")
                                updateDataTestFailed(POSITION_TEST_IOP3_LE_PRIVACY)
                                // Perform actions to handle the failure
                            }
                        )
                    }, CONNECTION_PERIOD)


                } else {
                    // Write operation failed, handle accordingly
                    Log.e(TAG, "Write operation failed")
                }
            }

        } else {
            Log.d(TAG, "is control $isControl")
        }
    }

    private fun iopPhase3RunTestCaseCaching(isControl: Int) {
        Log.d(TAG, "Run TestCase [8]")
        val itemChildrenTest: ChildrenItemTestInfo
        isConnecting = false
        if (isControl == 1) {
            if (isServiceChangedIndication == 1) {
                writeValueToCharacteristic(
                    characteristicIOPPhase3Control,
                    null,
                    iopPhase3WriteControlBytes(0, 0, 1)
                )
                Log.d(TAG, "Set Control Characteristic for Service Changed Indications")
            } else {
                writeValueToCharacteristic(
                    characteristicIOPPhase3Control,
                    null,
                    iopPhase3WriteControlBytes(0, 0, 2)
                )
                Log.d(TAG, "Set Control Characteristic for GATT Caching")
            }
        } else {
            itemChildrenTest =
                getListChildrenItemTestCase(POSITION_TEST_IOP3_CACHING)!![isServiceChangedIndication]
            itemChildrenTest.statusRunTest = 0
            itemChildrenTest.startTimeTest = System.currentTimeMillis()
            handler?.postDelayed(iopCachingRunnable, 90000)
            if (isServiceChangedIndication == 1) {
                val bonded = mBluetoothGatt?.device?.bondState == BluetoothDevice.BOND_BONDED
                if (bonded) {
                    Log.d(TAG, "Subscribe Indication for Service Changed")
                    setNotificationForCharacteristic(
                        characteristicIOPPhase3ServiceChanged,
                        Notifications.INDICATE
                    )
                } else {
                    Log.d(TAG, "Connection is not trusted, not bonded")
                    iopCachingFailed()
                    handler?.removeCallbacks(iopCachingRunnable)
                }
            } else {
                Log.d(TAG, "Set on Client Supported Features")
                writeValueToCharacteristic(
                    characteristicIOPPhase3ClientSupportedFeatures,
                    "01",
                    null
                )
            }
        }
    }


    private val iopCachingRunnable = Runnable {
        Log.d(TAG, "iopCachingRunnable")
        iopCachingFailed()
    }

    private fun iopCachingFailed() {
        val itemChildrenTest =
            getListChildrenItemTestCase(POSITION_TEST_IOP3_CACHING)!![isServiceChangedIndication]
        itemChildrenTest.statusRunTest = 1
        itemChildrenTest.statusChildrenTest = false
        itemChildrenTest.endTimeTest = System.currentTimeMillis()
        getSiliconLabsTestInfo().listItemTest[POSITION_TEST_IOP3_CACHING].setStatusTest(Common.IOP3_TC_STATUS_FAILED)
        finishItemTest(
            POSITION_TEST_IOP3_CACHING,
            getSiliconLabsTestInfo().listItemTest[POSITION_TEST_IOP3_CACHING]
        )
    }

    private fun retryIOP3Failed(index: Int, mCountReTest: Int) {
        if (index == POSITION_TEST_SERVICE) {
            updateDataTestFailed(index)
        } else if (index == POSITION_TEST_CONNECTION) {
            Log.d(TAG, "retryIOP3Failed index $index")
            handler?.postDelayed({
                finishItemTest(
                    POSITION_TEST_CONNECTION,
                    getSiliconLabsTestInfo().listItemTest[POSITION_TEST_CONNECTION]
                )
            }, 1000)
        } else {
            if (mCountReTest < 7) {
                isConnecting = false
                reconnect(2000)
            } else {
                updateDataTestFailed(index)
            }
        }
    }

    private fun checkIOP3OTA(index: Int, status: Int) {
        if (!otaProcess) {
            if (index == POSITION_TEST_IOP3_OTA_ACK || index == POSITION_TEST_IOP3_OTA_WITHOUT_ACK) {
                Log.d(TAG, "checkIOP3OTA $index, setStatusRunTest finish test")
                Log.d(TAG, "setStatusTest $status")
                val itemTestCaseInfo = getSiliconLabsTestInfo().listItemTest[index]
                itemTestCaseInfo.setStatusTest(status)
            }
        }
    }

    private val iopSecurityRunnable: Runnable = Runnable {
        Log.d(TAG, "iopSecurityRunnable $iopPhase3IndexStartChildrenTest")
        val itemChildrenTest =
            getListChildrenItemTestCase(POSITION_TEST_IOP3_SECURITY)!![iopPhase3IndexStartChildrenTest]
        itemChildrenTest.statusRunTest = 1 // 0 running, 1 finish test
        Log.d(
            TAG,
            "iopSecurityRunnable $iopPhase3IndexStartChildrenTest, setStatusRunTest finish test"
        )
        itemChildrenTest.statusChildrenTest = false
        Log.d(TAG, "setStatusChildrenTest false")
        val itemTestCaseInfo = getSiliconLabsTestInfo().listItemTest[POSITION_TEST_IOP3_SECURITY]
        itemTestCaseInfo.setStatusTest(Common.IOP3_TC_STATUS_FAILED)
        updateDataTestFailed(POSITION_TEST_IOP3_SECURITY)
    }

    private fun startOtaTestCase(index: Int) {
        Log.d(TAG, "startOtaTestCase $index")
        initOtaProgressDialog()
        otaLoadingDialog = OtaLoadingDialog(getString(R.string.iop_test_label_resetting))

        if (index == 1) {
            reliable = false
        }
        otaFileManager = OtaFileManager(this)

        when (getSiliconLabsTestInfo().firmwareVersion) {
            "3.2.1", "3.2.2", "3.2.3", "3.2.4" -> {
                otaFileManager
                    ?.apply { uploadMode = OtaFileManager.UploadMode.AUTO }
                    ?.also {
                        it.findGblFile(
                            getSiliconLabsTestInfo().firmwareVersion,
                            getSiliconLabsTestInfo().iopBoard,
                            reliable
                        )
                    }
                startOtaProcess()
            }

            else -> {
                otaFileManager?.uploadMode = OtaFileManager.UploadMode.USER
                otaFileSelectionDialog =
                    OtaFileSelectionDialog(listener = fileSelectionListener).also {
                        it.show(supportFragmentManager, "ota_file_selection_dialog")
                    }
            }
        }

    }

    private val fileSelectionListener = object : OtaFileSelectionDialog.FileSelectionListener {
        override fun onSelectFileButtonClicked() {
            Intent(Intent.ACTION_GET_CONTENT)
                .apply { type = "*/*" }
                .also {
                    startActivityForResult(
                        Intent.createChooser(
                            it,
                            getString(R.string.ota_choose_file)
                        ), GBL_FILE_CHOICE_REQUEST_CODE
                    )
                }
        }

        override fun onOtaButtonClicked() {
            otaFileManager?.otaFile?.let {
                otaFileSelectionDialog?.dismiss()
                startOtaProcess()
            } ?: if (otaFileManager?.otaFilename != null) {
                /*Toast.makeText(
                    this@IOPTestActivity,
                    getString(R.string.incorrect_file),
                    Toast.LENGTH_SHORT
                ).show()*/
                CustomToastManager.show(
                    this@IOPTestActivity, getString(R.string.incorrect_file), 5000
                )
            } else {
                /*Toast.makeText(
                    this@IOPTestActivity,
                    getString(R.string.no_file_chosen),
                    Toast.LENGTH_SHORT
                ).show()*/
                CustomToastManager.show(
                    this@IOPTestActivity, getString(R.string.no_file_chosen), 5000
                )
            }
        }

        override fun onCancelButtonClicked() {
            otaFileSelectionDialog?.dismiss()
            otaFileSelectionDialog = null
            isInAppOTA = false
            checkIOP3OTA(mIndexRunning, Common.IOP3_TC_STATUS_FAILED)
            finishItemTest(mIndexRunning, getSiliconLabsTestInfo().listItemTest[mIndexRunning])
        }
    }

    private fun startOtaProcess() {
        otaMode = true
        otaProcess = true
        ota_mode = false
        boolOTAbegin = true

        runOnUiThread {
            Log.d(TAG, OTA_BEGIN)
            dfuMode(OTA_BEGIN)
        }
    }

    private fun isOtaNameCorrect(deviceName: String): Boolean {
        return when {
            otaFileManager?.uploadMode == OtaFileManager.UploadMode.AUTO && mIndexRunning == POSITION_TEST_IOP3_OTA_ACK -> {
                deviceName.equals(OTA_DEVICE_NAME_AUTO_ACK, ignoreCase = true)
            }

            otaFileManager?.uploadMode == OtaFileManager.UploadMode.AUTO && mIndexRunning == POSITION_TEST_IOP3_OTA_WITHOUT_ACK -> {
                deviceName.equals(OTA_DEVICE_NAME_AUTO_UNACK, ignoreCase = true)
            }

            otaFileManager?.uploadMode == OtaFileManager.UploadMode.USER && mIndexRunning == POSITION_TEST_IOP3_OTA_ACK -> {
                deviceName.equals(OTA_DEVICE_NAME_USER_ACK, ignoreCase = true)
            }

            otaFileManager?.uploadMode == OtaFileManager.UploadMode.USER && mIndexRunning == POSITION_TEST_IOP3_OTA_WITHOUT_ACK -> {
                deviceName.equals(OTA_DEVICE_NAME_USER_UNACK, ignoreCase = true)
            }

            else -> false

        }
    }

    /**
     * OTA STATE MACHINE
     */
    @Synchronized
    private fun dfuMode(step: String) {
        when (step) {
            "INIT" -> dfuMode(OTA_BEGIN)
            "OTABEGIN" -> if (ota_mode) {
                //START OTA PROCESS -> gattCallback -> OnCharacteristicWrite
                Log.d("OTA_BEGIN", "true")
                handler?.postDelayed(WRITE_OTA_CONTROL_ZERO, 200)
            } else {
                //PUT DEVICE IN DFUMODE -> gattCallback -> OnCharacteristicWrite
                if (homekit) {
                    mBluetoothGatt?.readDescriptor(kitDescriptor)
                } else {
                    Log.d("DFU_MODE", "true")
                    handler?.postDelayed(WRITE_OTA_CONTROL_ZERO, 200)
                }
            }

            "OTAUPLOAD" -> {
                Log.d(OTA_UPLOAD, "Called")
                //Check Services
                val mBluetoothGattService = mBluetoothGatt?.getService(ota_service)
                if (mBluetoothGattService != null) {
                    val charac =
                        mBluetoothGatt?.getService(ota_service)?.getCharacteristic(ota_data)
                    if (charac != null) {
                        charac.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        Log.d("Instance ID", "" + charac.instanceId)

                        pack = 0

                        //Set info into UI OTA Progress
                        runOnUiThread {
                            otaProgressDialog?.setProgressInfo(
                                otaFileManager?.otaFilename,
                                otaFileManager?.otaFile?.size
                            )
                            animateLoading()
                        }
                        //Start OTA_data Upload in another thread
                        val otaUpload = Thread {
                            if (reliable) {
                                otaWriteDataReliable()
                            } else {
                                writeOtaData(otaFileManager?.otaFile)
                            }
                        }
                        otaUpload.start()
                    }
                }
            }

            "OTAEND" -> {
                Log.d(TAG, "OTAEND Called")
                ota_alreadyIn_Progress = false
                isInAppOTA = false
                handler?.postDelayed({ writeOtaControl(0x03.toByte()) }, 1000)
            }

            "DISCONNECTION" -> {
                otaProcess = false
                boolOTAbegin = false
                isInAppOTA = false
                disconnectGatt(mBluetoothGatt)
            }

            else -> {
            }
        }
    }

    private val DFU_OTA_UPLOAD: Runnable = Runnable {
        dfuMode(OTA_UPLOAD)
    }

    private fun initOtaProgressDialog() {
        otaProcess = false
        otaProgressDialog = OtaProgressDialog(this)

        otaProgressDialog?.btnOtaEnd?.setOnClickListener {
            otaProgressDialog?.dismiss()
            ota_mode = false
            otaMode = false
            dfuMode(OTA_DISCONNECTION)
            handler?.postDelayed({ scanLeDevice(true) }, 1000)
        }
    }

    private fun showOtaProgressDialog() {
        otaProgressDialog?.show()
        dfuMode(OTA_BEGIN)
    }

    private fun showOtaProgressDialogForRainier() {
        otaProgressDialog?.show()
    }

    private fun showOtaLoadingDialog() {
        otaLoadingDialog?.show(supportFragmentManager, "ota_loading_support")
        animateLoading()
    }

    /**
     * CREATES BAR PROGRESS ANIMATION IN LOADING AND OTA PROGRESS DIALOG
     */
    private fun animateLoading() {
        if (otaProgressDialog?.uploadImage != null) {
            otaProgressDialog?.uploadImage?.visibility = View.GONE
            otaLoadingDialog?.toggleLoadingSpinner(isVisible = false)
            if (otaLoadingDialog?.isShowing() == true) {
                otaLoadingDialog?.toggleLoadingSpinner(isVisible = true)
            }
            if (otaProgressDialog?.isShowing!!) {
                otaProgressDialog?.uploadImage?.visibility = View.VISIBLE
            }
        }
    }

    /**
     * USED TO CLEAN CACHE AND REDISCOVER SERVICES
     */
    private fun refreshServices() {
        if (!isInAppOTA) {
            if (mBluetoothGatt != null && mBluetoothGatt?.device != null) {
                refreshDeviceCache()
                mBluetoothGatt?.discoverServices()
            } else if (mBluetoothService != null && mBluetoothService?.connectedGatt != null) {
                refreshDeviceCache()
                mBluetoothService?.connectedGatt?.discoverServices()
            }
        }
    }

    /**
     * CALLS A METHOD TO CLEAN DEVICE SERVICES
     */
    private fun refreshDeviceCache(): Boolean {
        try {
            Log.d(TAG, "refreshDevice Called")
            val localMethod = mBluetoothGatt?.javaClass?.getMethod("refresh")
            if (localMethod != null) {
                val bool: Boolean = localMethod.invoke(mBluetoothGatt, *arrayOfNulls(0)) as Boolean
                Log.d(TAG, "refreshDevice bool: $bool")
                return bool
            }
        } catch (localException: Exception) {
            Log.e(TAG, "refreshDevice An exception occurred while refreshing device")
        }
        return false
    }

    private val WRITE_OTA_CONTROL_ZERO: Runnable = Runnable {
        writeOtaControl(0x00.toByte())
    }

    /**
     * (RUNNABLE) CHECKS OTA BEGIN BOX AND STARTS
     */
    private val checkBeginRunnable: Runnable = Runnable {
        otaProgressDialog?.chrono?.base = SystemClock.elapsedRealtime()
        otaProgressDialog?.chrono?.start()
    }

    fun onceAgain() {
        writeOtaControl(0x00.toByte())
    }

    /**
     * WRITES BYTE TO OTA CONTROL CHARACTERISTIC
     */
    private fun writeOtaControl(ctrl: Byte): Boolean {
        Log.e("writeOtaControl", "Called")
        if (mBluetoothGatt?.getService(ota_service) != null) {
            val charac = mBluetoothGatt?.getService(ota_service)?.getCharacteristic(ota_control)
            if (charac != null) {
                Log.d("Instance ID", "" + charac.instanceId)
                charac.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                Log.d("charac_properties", "" + charac.properties)
                val control = ByteArray(1)
                control[0] = ctrl
                charac.value = control
                mBluetoothGatt?.writeCharacteristic(charac)
                return true
            } else {
                Log.d(TAG, "characteristic null")
            }
        } else {
            Log.d("service", "null")
        }
        return false
    }

    /**
     * WRITES EBL/GBL FILES TO OTA_DATA CHARACTERISTIC
     */
    @Synchronized
    fun otaWriteDataReliable() {
        if (pack == 0) {
            //SET MTU_divisible by 4
            var minus = 0
            do {
                mtuDivisible = mtu - 3 - minus
                minus++
            } while (mtuDivisible % 4 != 0)
        }
        val writearray: ByteArray
        val pgss: Float
        if (pack + mtuDivisible > otaFileManager?.otaFile!!.size - 1) {
            //SET last by 4
            var plus = 0
            var last = otaFileManager?.otaFile!!.size - pack
            do {
                last += plus
                plus++
            } while (last % 4 != 0)
            writearray = ByteArray(last)
            var j = 0
            for (i in pack until pack + last) {
                if (otaFileManager?.otaFile!!.size - 1 < i) {
                    writearray[j] = 0xFF.toByte()
                } else {
                    writearray[j] = otaFileManager?.otaFile!![i]
                }
                j++
            }
            pgss = ((pack + last).toFloat() / (otaFileManager?.otaFile!!.size - 1)) * 100
            Log.d(
                "characte",
                "last: " + pack + " / " + (pack + last) + " : " + Converters.getHexValue(writearray)
            )
        } else {
            var j = 0
            writearray = ByteArray(mtuDivisible)
            for (i in pack until pack + mtuDivisible) {
                writearray[j] = otaFileManager?.otaFile!![i]
                j++
            }
            pgss = ((pack + mtuDivisible).toFloat() / (otaFileManager?.otaFile!!.size - 1)) * 100
            Log.d(
                "characte",
                "pack: " + pack + " / " + (pack + mtuDivisible) + " : " + Converters.getHexValue(
                    writearray
                )
            )
        }
        val charac = mBluetoothGatt?.getService(ota_service)?.getCharacteristic(ota_data)
        charac?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        charac?.value = writearray
        mBluetoothGatt?.writeCharacteristic(charac)
        val waitingTime = (System.currentTimeMillis() - otaTime)
        val bitrate = 8 * pack.toFloat() / waitingTime
        if (pack > 0) {
            handler?.post {
                runOnUiThread {
                    otaProgressDialog?.progressBar?.progress = pgss.toInt()
                    val datarate = String.format(Locale.US, kBits, bitrate)
                    otaProgressDialog?.dataRate?.text = datarate
                    otaProgressDialog?.dataSize?.text =
                        getString(R.string.iop_test_n_percent, pgss.toInt())
                }
            }
        } else {
            otaTime = System.currentTimeMillis()
        }
    }

    /**
     * White with NO RESPONSE
     */
    @Synchronized
    fun writeOtaData(dataThread: ByteArray?) {
        try {
            val value = ByteArray(mtu - 3)
            val start = System.nanoTime()
            var j = 0
            for (i in dataThread!!.indices) {
                value[j] = dataThread[i]
                j++
                if (j >= mtu - 3 || i >= (dataThread.size - 1)) {
                    var wait = System.nanoTime()
                    val charac =
                        mBluetoothGatt?.getService(ota_service)?.getCharacteristic(ota_data)
                    charac?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    val progress = ((i + 1).toFloat() / dataThread.size) * 100
                    val bitrate =
                        (((i + 1) * (8.0)).toFloat() / (((wait - start) / 1000000.0).toFloat()))
                    if (j < mtu - 3) {
                        val end = ByteArray(j)
                        System.arraycopy(value, 0, end, 0, j)
                        Log.d(
                            "Progress",
                            "sent " + (i + 1) + " / " + dataThread.size + " - " + String.format(
                                "%.1f",
                                progress
                            ) + " % - " + String.format(
                                kBits,
                                bitrate
                            ) + " - " + Converters.getHexValue(end)
                        )
                        runOnUiThread {
                            otaProgressDialog?.dataSize?.text =
                                getString(R.string.iop_test_n_percent, progress.toInt())
                            otaProgressDialog?.progressBar?.progress = progress.toInt()
                        }
                        charac?.value = end
                    } else {
                        j = 0
                        Log.d(
                            "Progress",
                            "sent " + (i + 1) + " / " + dataThread.size + " - " + String.format(
                                "%.1f",
                                progress
                            ) + " % - " + String.format(
                                kBits,
                                bitrate
                            ) + " - " + Converters.getHexValue(value)
                        )
                        runOnUiThread {
                            otaProgressDialog?.dataSize?.text =
                                getString(R.string.iop_test_n_percent, progress.toInt())
                            otaProgressDialog?.progressBar?.progress = progress.toInt()
                        }
                        charac?.value = value
                    }
                    if (mBluetoothGatt!!.writeCharacteristic(charac)) {
                        runOnUiThread {
                            val datarate = String.format(Locale.US, kBits, bitrate)
                            otaProgressDialog?.dataRate?.text = datarate
                        }
                        while ((System.nanoTime() - wait) / 1000000.0 < delayNoResponse) {
                        }
                    } else {
                        do {
                            while ((System.nanoTime() - wait) / 1000000.0 < delayNoResponse) {
                            }
                            wait = System.nanoTime()
                            runOnUiThread {
                                val datarate = String.format(Locale.US, kBits, bitrate)
                                otaProgressDialog?.dataRate?.text = datarate
                            }
                        } while (!mBluetoothGatt!!.writeCharacteristic(charac))
                    }
                }
            }
            handler?.post {
                runOnUiThread {
                    otaProgressDialog?.chrono?.stop()
                    otaProgressDialog?.uploadImage?.clearAnimation()
                    otaProgressDialog?.uploadImage?.visibility = View.INVISIBLE
                }
            }
            val end = System.currentTimeMillis()
            val time = (end - start) / 1000L.toFloat()
            Log.d("OTA Time - ", "" + time + "s")
            dfuMode(OTA_END)
        } catch (e: NullPointerException) {
            e.printStackTrace()
        }
    }

    /**
     * WRITES OTA CONTROL FOR HOMEKIT DEVICES
     */
    private fun homeKitOTAControl(instanceID: ByteArray) {
        //WRITE CHARACTERISTIC FOR HOMEKIT
        val value = byteArrayOf(
            0x00,
            0x02,
            0xee.toByte(),
            instanceID[0],
            instanceID[1],
            0x03,
            0x00,
            0x01,
            0x01,
            0x01
        )
        writeGenericCharacteristic(ota_service, ota_control, value)
        Log.d(TAG, "characteristic writting: " + Converters.getHexValue(value))
    }

    /**
     * WRITES BYTE ARRAY TO A GENERIC CHARACTERISTIC
     */
    private fun writeGenericCharacteristic(
        service: UUID?,
        characteristic: UUID?,
        value: ByteArray?
    ): Boolean {
        if (mBluetoothGatt != null) {
            val bluetoothGattCharacteristic =
                mBluetoothGatt?.getService(service)?.getCharacteristic(characteristic)
            Log.d(TAG, "characteristic exists")
            if (bluetoothGattCharacteristic != null) {
                bluetoothGattCharacteristic.value = value
                bluetoothGattCharacteristic.writeType =
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                mBluetoothGatt?.writeCharacteristic(bluetoothGattCharacteristic)
                Log.d(TAG, "characteristic written")
            } else {
                Log.d(TAG, "characteristic null")
                return false
            }
        } else {
            Log.d("bluetoothGatt", "null")
            return false
        }
        return true
    }

    private val gattCallback: TimeoutGattCallback = object : TimeoutGattCallback() {
        override fun onTimeout() {
            super.onTimeout()
            Log.d(TAG, "updateDataTestFailed onTimeout()")
            updateDataTestFailed(mIndexRunning)
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            mBluetoothGatt?.let {
                if (mBluetoothGatt?.device?.address != gatt.device.address) {
                    return
                }
            }
            super.onConnectionStateChange(gatt, status, newState)
            Log.d(TAG, "onConnectionStateChange status $status - newState $newState")
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    Log.d(TAG, "onConnectionStateChange connected")
                    handler?.removeCallbacks(connectionRunnable)
                    isConnected = true
                    isTestFinished = false
                    if (getSiliconLabsTestInfo().listItemTest[POSITION_TEST_CONNECTION].getStatusTest() == Common.IOP3_TC_STATUS_PROCESSING) {
                        Log.d(TAG, "onConnectionStateChange POSITION_TEST_CONNECTION")
                        finishItemTest(
                            POSITION_TEST_CONNECTION,
                            getSiliconLabsTestInfo().listItemTest[POSITION_TEST_CONNECTION]
                        )
                    } else {
                        if (!otaProcess) {
                            Log.d(
                                TAG,
                                "onConnectionStateChange connected mIndexRunning $mIndexRunning"
                            )
                            mBluetoothGatt?.requestMtu(247)
                            handler?.postDelayed({
                                if (isConnected && mIndexRunning == 8) {
                                    Log.d(
                                        TAG,
                                        "onConnectionStateChange connected mIndexRunningis 8"
                                    )
                                    isTestRunning = false
                                    isTestFinished = true

                                    getItemTestCaseInfo(POSITION_TEST_IOP3_LE_PRIVACY).setStatusTest(
                                        Common.IOP3_TC_STATUS_PASS
                                    )
                                    runOnUiThread { updateUIFooter(isTestRunning) }
                                    mListener?.updateUi()
                                }
                            }, CONNECTION_PERIOD)

                        } else { //After OTA process started
                            //get information
                            Log.d(TAG, "Device: " + gatt.device)
                            Log.d(TAG, "Device Name: " + gatt.device.name)
                            if (gatt.services.isEmpty()) {
                                handler?.postDelayed({
                                    mBluetoothGatt =
                                        null //It's going to be equal gatt in Discover Services Callback...
                                    Log.d(
                                        TAG,
                                        "onConnected, start Services Discovery: " + gatt.discoverServices()
                                    )
                                }, 250)

                                discoverTimeout = true
                                val timeout = Runnable {
                                    handler?.postDelayed({
                                        if (discoverTimeout) {
                                            disconnectGatt(gatt)
                                            runOnUiThread {
                                                /*Toast.makeText(
                                                    baseContext,
                                                    "DISCOVER SERVICES TIMEOUT",
                                                    Toast.LENGTH_LONG
                                                ).show()*/
                                                CustomToastManager.show(
                                                    this@IOPTestActivity,
                                                    "DISCOVER SERVICES TIMEOUT",
                                                    5000
                                                )
                                            }
                                        }
                                    }, 25000)
                                }
                                Thread(timeout).start()
                            }
                        }
                    }
                }

                BluetoothGatt.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected IOP Test device: " + System.currentTimeMillis())
                    isConnected = false
                    discoverTimeout = false
                    exit(mBluetoothGatt)
                    if (status != 0 && otaMode) {
                        if (status == 133) {
                            //reconnect after 30 seconds
                            handler?.postDelayed({
                                Log.d(TAG, "onConnectionStateChange OTA status $status")
                                retryIOP3Failed(mIndexRunning, countReTest++)
                            }, 30000)
                        }
                    } else {
                        if (!otaProcess && !isTestFinished) {
                            Log.d(
                                TAG,
                                "onConnectionStateChange ota_process $otaProcess,isConnecting $isConnecting"
                            )
                            if (mIndexRunning > POSITION_TEST_IOP3_OTA_WITHOUT_ACK || mIndexRunning < POSITION_TEST_IOP3_OTA_ACK) {
                                if (!isConnecting) {
                                    isConnecting = true
                                    handler?.postDelayed({
                                        Log.d(TAG, "onConnectionStateChange re-connecting")
                                        retryIOP3Failed(mIndexRunning, countReTest++)
                                    }, 5000)
                                }
                            } else {
                                if (status == 133 || status == 8) {
                                    if (!isConnecting) {
                                        isConnecting = true
                                        handler?.postDelayed({
                                            Log.d(TAG, "onConnectionStateChange status: $status")
                                            retryIOP3Failed(mIndexRunning, countReTest++)
                                        }, 30000)
                                    }
                                }
                            }
                        } else {
                            if (status == 133) {
                                handler?.postDelayed({
                                    Log.d(TAG, "onConnectionStateChange OTA status: $status")
                                    if(!isTestFinished) {
                                        retryIOP3Failed(mIndexRunning, countReTest++)
                                    }
                                }, 30000)
                            }
                        }
                        if (disconnectGatt) {
                            exit(gatt)
                        }
                        if (gatt.services.isEmpty()) {
                            exit(gatt)
                        }
                        if (!boolOTAbegin && !otaProcess) {
                            exit(gatt)
                        }
                    }
                }

                BluetoothGatt.STATE_CONNECTING -> Log.d(
                    TAG,
                    "onConnectionStateChange Connecting..."
                )

                else -> {
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            Log.d(TAG, "onServicesDiscovered(), status " + Integer.toHexString(status))
            if (mBluetoothGatt != gatt) {
                mBluetoothGatt = gatt
                handler?.postDelayed({
                    refreshServices()
                    mBluetoothGatt?.readPhy()
                }, 2000)
            } else {
                discoverTimeout = false
                if (status != 0) {
                    runOnUiThread {
                        /*Toast.makeText(
                            baseContext,
                            ErrorCodes.getErrorName(status),
                            Toast.LENGTH_LONG
                        ).show()*/
                        CustomToastManager.show(
                            this@IOPTestActivity, ErrorCodes.getErrorName(status), 5000
                        )
                        updateDataTestFailed(mIndexRunning)
                    }
                    handler?.postDelayed({
                        disconnectGatt(gatt)
                    }, 2000)
                } else {
                    runnable(gatt)

                    val otaServiceCheck = gatt.getService(ota_service) != null
                    if (otaServiceCheck) {
                        val otaDataCheck =
                            gatt.getService(ota_service).getCharacteristic(ota_data) != null
                        if (otaDataCheck) {
                            val homeKitCheck = gatt.getService(homekit_service) != null
                            if (!homeKitCheck) {
                                ota_mode = true
                                ota_alreadyIn_Progress = true
                            }
                        } else {
                            if (boolOTAbegin) {
                                onceAgain()
                            }
                        }
                    }
                    try {
                        val otaDataCheck =
                            gatt.getService(ota_service).getCharacteristic(ota_data) == null
                        if (!otaDataCheck) {
                            ota_alreadyIn_Progress = false
                        }
                    } catch (e: Exception) {
                        ota_alreadyIn_Progress = false
                    }
                    //IF DFU_MODE, LAUNCH OTA SETUP AUTOMATICALLY
                    if (ota_mode && boolOTAbegin && !ota_alreadyIn_Progress) {
                        handler?.postDelayed({
                            runOnUiThread {
                                if (otaLoadingDialog?.isShowing() == true) {
                                    otaLoadingDialog?.toggleLoadingSpinner(isVisible = false)
                                    otaLoadingDialog?.dismiss()
                                }
                                showOtaProgressDialog()
                            }
                        }, 1000)
                    }
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            Log.d(
                TAG,
                "onCharacteristicRead: " + characteristic.uuid.toString() + " status " + status
            )

            if (characteristic.uuid == GattCharacteristic.ModelNumberString.uuid) {
                getSiliconLabsTestInfo().iopBoard =
                    IopBoard.fromBoardString(characteristic.getStringValue(0))
            }

            if (characteristic.service.uuid.toString() == CommonUUID.Service.TEST_PARAMETERS.toString()) {
                if (status == 0) {
                    when (characteristic.uuid.toString()) {
                        CommonUUID.Characteristic.FIRMWARE_VERSION.toString() ->
                            readFirmwareVersion(characteristic.value)

                        CommonUUID.Characteristic.CONNECTION_PARAMETERS.toString() ->
                            readConnectionParameters(characteristic.value)

                        else -> {}
                    }
                } else {
                    nrTries = 0
                    retryCommand(characteristic)
                }
            } else {
                if (status == 18) {
                    if (mIndexRunning == POSITION_TEST_IOP3_CACHING) {
                        if (isServiceChangedIndication == 0) {
                            readCharacteristic(characteristicIOPPhase3DatabaseHash)
                            return
                        }
                    }
                }
                updateDataTest(characteristic, 0, status)
                checkNextTestCase(characteristic, 1)
                // type 1: CharacteristicRead, 2:CharacteristicWrite
                if (mBluetoothGatt?.getService(ota_service) != null) {
                    if (characteristic === mBluetoothGatt?.getService(ota_service)
                            ?.getCharacteristic(ota_control)
                    ) {
                        val value = characteristic.value
                        if (value[2] == 0x05.toByte()) {
                            Log.e("homekit_descriptor", "Insecure Connection")
                            runOnUiThread {
                                /*Toast.makeText(
                                    this@IOPTestActivity,
                                    "Error: Not a Homekit Secure Connection",
                                    Toast.LENGTH_SHORT
                                ).show()*/
                                CustomToastManager.show(
                                    this@IOPTestActivity,
                                    "Error: Not a Homekit Secure Connection",
                                    5000
                                )
                            }
                        } else if (value[2] == 0x04.toByte()) {
                            Log.e("homekit_descriptor", "Wrong Address")
                        } else if (value[2] == 0x00.toByte()) {
                            Log.e("homekit_descriptor", "Entering in DFU_Mode...")
                            if (ota_mode && otaProcess) {
                                Log.e(OTA_UPLOAD, "Sent")
                                handler?.removeCallbacks(DFU_OTA_UPLOAD)
                                handler?.postDelayed(DFU_OTA_UPLOAD, 500)
                            } else if (!ota_mode && otaProcess) {
                                runOnUiThread { showOtaLoadingDialog() }
                                handler?.postDelayed({
                                    reconnect(4000)
                                }, 200)
                            }
                        }
                    }
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            Log.d(TAG, "onCharacteristicWrite: " + characteristic.uuid.toString())
            Log.d(TAG, "onCharacteristicWrite: $status")
            if (!otaProcess) {
                updateDataTest(characteristic, 1, status)
                checkNextTestCase(characteristic, 2)
            } else {
                if (characteristic.uuid == ota_control) { //OTA Control Callback Handling
                    if (characteristic.value.size == 1) {
                        if (characteristic.value[0] == 0x00.toByte()) {
                            Log.e(
                                "Callback",
                                "Control " + Converters.getHexValue(characteristic.value) + "status: " + status
                            )
                            if (ota_mode && otaProcess) {
                                Log.e(OTA_UPLOAD, "Sent")
                                runOnUiThread(checkBeginRunnable)
                                handler?.removeCallbacks(DFU_OTA_UPLOAD)
                                handler?.postDelayed(DFU_OTA_UPLOAD, 500)
                            } else if (!ota_mode && otaProcess) {
                                runOnUiThread { showOtaLoadingDialog() }
                                if (mBluetoothGatt?.getService(ota_service)
                                        ?.getCharacteristic(ota_data) == null
                                ) {
                                    handler?.post {
                                        reconnect(4000)
                                    }
                                } else {
                                    if (!ota_alreadyIn_Progress) {
                                        ota_alreadyIn_Progress = true
                                        runOnUiThread(checkBeginRunnable)
                                        handler?.removeCallbacks(DFU_OTA_UPLOAD)
                                        handler?.postDelayed(DFU_OTA_UPLOAD, 500)
                                        handler?.postDelayed({
                                            runOnUiThread {
                                                otaLoadingDialog?.toggleLoadingSpinner(isVisible = false)
                                                otaLoadingDialog?.dismiss()
                                                if (!otaProgressDialog!!.isShowing) {
                                                    showOtaProgressDialogForRainier()
                                                }
                                            }
                                        }, 1000)
                                    }
                                }
                            }
                        }
                        if (characteristic.value[0] == 0x03.toByte()) {
                            if (otaProcess) {
                                Log.e(
                                    "Callback",
                                    "Control " + Converters.getHexValue(characteristic.value) + "status: " + status
                                )
                                runOnUiThread { otaProgressDialog?.btnOtaEnd?.isEnabled = true }
                                boolOTAbegin = false
                            }
                        }
                    } else {
                        Log.i(
                            "OTA_Control",
                            "Received: " + Converters.getHexValue(characteristic.value)
                        )
                        if (characteristic.value[0].toInt() == 0x00 && characteristic.value[1].toInt() == 0x02) {
                            Log.i("HomeKit", "Reading OTA_Control...")
                            mBluetoothGatt?.readCharacteristic(characteristic)
                        }
                    }
                }
                if (characteristic.uuid == ota_data) {   //OTA Data Callback Handling
                    if (reliable) {
                        if (otaProgressDialog?.isShowing!!) {
                            pack += mtuDivisible
                            if (pack <= otaFileManager?.otaFile?.size!! - 1) {
                                otaWriteDataReliable()
                            } else if (pack > otaFileManager?.otaFile?.size!! - 1) {
                                handler?.post {
                                    runOnUiThread {
                                        otaProgressDialog?.chrono?.stop()
                                        otaProgressDialog?.uploadImage?.clearAnimation()
                                        otaProgressDialog?.uploadImage?.visibility = View.INVISIBLE
                                    }
                                }
                                handler?.postDelayed({
                                    dfuMode(OTA_END)
                                }, 5000)
                            }
                        } else {
                            pack += mtuDivisible
                            if (pack <= otaFileManager?.otaFile?.size!! - 1) {
                                otaWriteDataReliable()
                            } else if (pack > otaFileManager?.otaFile?.size!! - 1) {
                                handler?.post {
                                    runOnUiThread {
                                        otaProgressDialog?.chrono?.stop()
                                        otaProgressDialog?.uploadImage?.clearAnimation()
                                        otaProgressDialog?.uploadImage?.visibility = View.INVISIBLE
                                    }
                                }
                                handler?.postDelayed({
                                    dfuMode(OTA_END)
                                }, 5000)
                            }
                        }
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            Log.d(
                TAG,
                "onCharacteristicChanged: " + characteristic.uuid.toString() + " len:" + characteristic.value.size
            )
            updateDataTest(characteristic, -1, -1)
            checkNextTestCase(characteristic, 0)
            // type 1: CharacteristicRead, 2:CharacteristicWrite, 0:Notify
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorRead(gatt, descriptor, status)
            Log.d(TAG, "onDescriptorRead mIndexStartChildrenTest: $mIndexStartChildrenTest")
            if (descriptor.uuid.toString() == homekit_descriptor.toString()) {
                val value = ByteArray(2)
                value[0] = 0xF2.toByte()
                value[1] = 0xFF.toByte()
                if (descriptor.value[0] == value[0] && descriptor.value[1] == value[1]) {
                    Log.i("descriptor", "getValue " + Converters.getHexValue(descriptor.value))
                }
            }
            if (iopPhase3ExtraDescriptor != null) {
                if (descriptor.uuid.toString() == iopPhase3ExtraDescriptor!!.uuid.toString()) {
                    Log.d(
                        "iopPhase3RunTestCaseBonding",
                        "Descriptor getValue " + Converters.getHexValue(descriptor.value)
                    )
                    if (iopPhase3BondingStep < 8) {
                        iopPhase3RunTestCaseBonding(iopPhase3BondingStep + 1)
                    }
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            Log.d(TAG, "onDescriptorWrite status $status")
            if (mIndexStartChildrenTest <= 17 && isDisabled) {
                Log.e(TAG, "onDescriptorWrite mIndexStartChildrenTest: $mIndexStartChildrenTest")
                runChildrenTestCase(mIndexStartChildrenTest)
            } else if (mEndThroughputNotification) {
                mEndThroughputNotification = false
                if (status == 0) {
                    finishItemTest(
                        POSITION_TEST_IOP3_THROUGHPUT,
                        getSiliconLabsTestInfo().listItemTest[POSITION_TEST_IOP3_THROUGHPUT]
                    )
                } else if (getSiliconLabsTestInfo().listItemTest[POSITION_TEST_IOP3_LE_PRIVACY]
                        .getStatusTest() == Common.IOP3_TC_STATUS_PROCESSING
                ) {
                    iopPhase3RunTestCaseLEPrivacy(1)
                }
                /*} else if (getSiliconLabsTestInfo().listItemTest[POSITION_TEST_IOP3_CACHING]
                .getStatusTest() == Common.IOP3_TC_STATUS_PROCESSING) {
                    iopPhase3RunTestCaseCaching(1) */
            } else if (getSiliconLabsTestInfo().listItemTest[POSITION_TEST_IOP3_LE_PRIVACY]
                    .getStatusTest() == Common.IOP3_TC_STATUS_PROCESSING
            ) {
                iopPhase3RunTestCaseLEPrivacy(1)
            } else if (iopPhase3ExtraDescriptor != null) {
                if (descriptor.uuid.toString() == iopPhase3ExtraDescriptor!!.uuid.toString()) {
                    if (iopPhase3BondingStep < 8) {
                        iopPhase3RunTestCaseBonding(iopPhase3BondingStep + 1)
                    }
                }
            } else {
                Log.d(TAG, "do nothing")
            }
        }

        override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
            super.onReliableWriteCompleted(gatt, status)
            Log.d(TAG, "onReliableWriteCompleted: ")
        }

        override fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyUpdate(gatt, txPhy, rxPhy, status)
            currentRxPhy = rxPhy
        }

        override fun onPhyRead(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyRead(gatt, txPhy, rxPhy, status) // TODO read PHY before starting throughput?
            Log.d(TAG, "read PHY before starting throughput")
            currentRxPhy = rxPhy
            Log.d(TAG, "phy : " + currentRxPhy.toString())
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            Log.d(TAG, "onMtuChanged: $mtu")
            this@IOPTestActivity.mtu = mtu
            mStartTimeDiscover = System.currentTimeMillis()
            Log.d(TAG, "setPreferredPhy 1M")
            mBluetoothGatt?.setPreferredPhy(
                BluetoothDevice.PHY_LE_1M_MASK,
                BluetoothDevice.PHY_LE_1M_MASK,
                BluetoothDevice.PHY_OPTION_NO_PREFERRED
            )
            if (mIndexRunning == POSITION_TEST_DISCOVER_SERVICE || mIndexRunning == POSITION_TEST_IOP3_THROUGHPUT || mIndexRunning == POSITION_TEST_IOP3_LE_PRIVACY || (mIndexRunning == POSITION_TEST_IOP3_SECURITY && iopPhase3BondingStep > 2)) {
                mListCharacteristics.clear()
                characteristicsPhase3Security.clear()
                characteristicIOPPhase3Control = null
                refreshServices()
            } else if ((mIndexRunning == POSITION_TEST_IOP3_OTA_WITHOUT_ACK
                        || mIndexRunning == POSITION_TEST_IOP3_OTA_ACK)
                && !otaProcess
            ) {
                Log.d(TAG, "rediscover OTA")
                handler?.postDelayed({
                    mListCharacteristics.clear()
                    characteristicsPhase3Security.clear()
                    characteristicIOPPhase3Control = null
                    refreshServices()
                }, 20000)
            }
            if (otaProcess) {
                mBluetoothGatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            }
        }

    }


    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            Timber.d("Device scanned. Name = ${device.name}, address = ${device.address}")

            synchronized(lock) {
                if (readScannerStartTime as Boolean) {
                    readScannerStartTime = false
                    mStartTimeScanner = System.currentTimeMillis()
                    Log.d("scanLeDevice", "startTime: $mStartTimeScanner")
                }
            }

            when (mIndexRunning) {
                POSITION_TEST_SCANNER -> {
                    if (device.name.equals(getSiliconLabsTestInfo().fwName, ignoreCase = true)) {
                        mBluetoothDevice = device
                        mDeviceAddress = device.address
                        scanLeDevice(false)
                        if (device.bondState == BluetoothDevice.BOND_BONDED) {
                            removeBond(device)
                            handler?.postDelayed(
                                {
                                    finishItemTest(
                                        POSITION_TEST_SCANNER,
                                        getSiliconLabsTestInfo().listItemTest[POSITION_TEST_SCANNER]
                                    )
                                },
                                BOND_REMOVE_DELAY_MS
                            )
                        } else {
                            finishItemTest(
                                POSITION_TEST_SCANNER,
                                getSiliconLabsTestInfo().listItemTest[POSITION_TEST_SCANNER]
                            )
                        }
                    } else return
                }

                POSITION_TEST_IOP3_OTA_ACK,
                POSITION_TEST_IOP3_OTA_WITHOUT_ACK -> {
                    if (device.address == mDeviceAddress) {
                        mBluetoothDevice = device
                        scanLeDevice(false)
                        handler?.postDelayed({
                            connectToDevice(mBluetoothDevice)
                        }, 15000)
                    } else return
                }

                POSITION_TEST_IOP3_LE_PRIVACY,
                POSITION_TEST_IOP3_SECURITY,
                POSITION_TEST_IOP3_CACHING -> {
                    if (device.address == mDeviceAddress) {
                        Timber.d("Manufacturer: ${Build.MANUFACTURER}")
                        mBluetoothDevice = device
                        scanLeDevice(false)
                        connectToDevice(mBluetoothDevice)
                    } else return
                }
            }
        }
    }

    interface Listener {
        fun updateUi()
        fun scrollViewToPosition(position: Int)
    }

    companion object {
        private const val FOLDER_NAME = "SiliconLabs_App"
        private const val TAG = "IOPTest"

        private val ota_service = UUID.fromString("1d14d6ee-fd63-4fa1-bfa4-8f47b42119f0")
        private val ota_data = UUID.fromString("984227f3-34fc-4045-a5d0-2c581f81a153")
        private val ota_control = UUID.fromString("f7bf3564-fb6d-4e53-88a4-5e37e0326063")
        private val homekit_descriptor = UUID.fromString("dc46f0fe-81d2-4616-b5d9-6abdd796939a")
        private val homekit_service = UUID.fromString("0000003e-0000-1000-8000-0026bb765291")

        private const val OTA_BEGIN = "OTABEGIN"
        private const val OTA_UPLOAD = "OTAUPLOAD"
        private const val OTA_END = "OTAEND"
        private const val OTA_DISCONNECTION = "DISCONNECTION"

        private const val OTA_DEVICE_NAME_AUTO_ACK = "IOP Test Update"
        private const val OTA_DEVICE_NAME_AUTO_UNACK = "IOP Test"
        private const val OTA_DEVICE_NAME_USER_ACK = "IOP_Test_2"
        private const val OTA_DEVICE_NAME_USER_UNACK = "IOP_Test_1"

        private const val POSITION_TEST_SCANNER = 0
        private const val POSITION_TEST_CONNECTION = 1
        private const val POSITION_TEST_DISCOVER_SERVICE = 2
        private const val POSITION_TEST_SERVICE = 3

        /* Add IoP Test for P3 */
        private const val POSITION_TEST_IOP3_THROUGHPUT = 6
        private const val POSITION_TEST_IOP3_SECURITY = 7
        private const val POSITION_TEST_IOP3_CACHING = 9
        private const val POSITION_TEST_IOP3_LE_PRIVACY = 8
        private const val POSITION_TEST_IOP3_OTA_ACK = 4
        private const val POSITION_TEST_IOP3_OTA_WITHOUT_ACK = 5

        private const val SCAN_PERIOD: Long = 20000
        /** Delay after [BluetoothDevice.removeBond] before continuing the test so bond clears. */
        private const val BOND_REMOVE_DELAY_MS = 1500L
        private const val CONNECTION_PERIOD: Long = 10000
        private const val BLUETOOTH_SETTINGS_REQUEST_CODE = 100

        const val GBL_FILE_CHOICE_REQUEST_CODE = 201

        fun startActivity(context: Context) {
            val intent = Intent(context, IOPTestActivity::class.java)
            startActivity(context, intent, null)
        }
    }

    private fun releaseIopBluetoothResources() {
        mBluetoothService?.unregisterGattCallback()
        try {
            mBluetoothGatt?.disconnect()
        } catch (_: Exception) {
        }
        mBluetoothService?.clearConnectedGatt()
        try {
            mBluetoothGatt?.close()
        } catch (_: Exception) {
        }
        mBluetoothGatt = null
        isConnected = false
        mBluetoothBinding?.unbind()
        mBluetoothBinding = null
    }
}
