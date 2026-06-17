package com.siliconlabs.bledemo.features.demo.matter_demo.controller

import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.ICDDeviceInfo

open class GenericChipDeviceListener : ChipDeviceController.CompletionListener {
    override fun onConnectDeviceComplete() {

    }

    override fun onStatusUpdate(status: Int) {
    }

    override fun onPairingComplete(errorCode: Long) {
    }

    override fun onPairingDeleted(errorCode: Long) {

    }

    override fun onCommissioningComplete(nodeId: Long, errorCode: Long) {

    }


    override fun onReadCommissioningInfo(
        vendorId: Int,
        productId: Int,
        wifiEndpointId: Int,
        threadEndpointId: Int
    ) {
    }

    override fun onCommissioningStatusUpdate(nodeId: Long, stage: String?, errorCode: Long) {

    }

    override fun onCommissioningStageStart(nodeId: Long, stage: String?) {

    }

    override fun onNotifyChipConnectionClosed() {
    }

    override fun onCloseBleComplete() {
    }

    override fun onError(error: Throwable?) {
    }

    override fun onOpCSRGenerationComplete(csr: ByteArray?) {
    }

    override fun onICDRegistrationInfoRequired() {
    }

    override fun onICDRegistrationComplete(errorCode: Long, icdDeviceInfo: ICDDeviceInfo) {
    }


}