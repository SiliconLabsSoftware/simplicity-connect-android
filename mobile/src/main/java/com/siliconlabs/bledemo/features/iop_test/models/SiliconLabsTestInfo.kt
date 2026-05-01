package com.siliconlabs.bledemo.features.iop_test.models

import android.util.Log
import com.siliconlabs.bledemo.features.iop_test.activities.IOPTestActivity
import com.siliconlabs.bledemo.features.iop_test.utils.Utils
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

class SiliconLabsTestInfo(var fwName: String, var deviceMacAddress: String, val listItemTest: ArrayList<ItemTestCaseInfo>) {
    var connectionParameters: ConnectionParameters? = null
    var firmwareVersion: String = ""
    var firmwareAckVersion: String = ""
    var firmwareUnackVersion: String = ""
    var iopBoard: IopBoard = IopBoard.UNKNOWN
    private var phoneOs: String = Utils.getAndroidVersion()
    var phoneName: String = Utils.getDeviceName()
    var totalTestCase: Int
    var iopActivity = IOPTestActivity()
    init {
        this.totalTestCase = getTotalTestCase(listItemTest)
    }

    private fun getTotalTestCase(listItemTest: ArrayList<ItemTestCaseInfo>): Int {
        var count = 0
        for (item in listItemTest) {
            item.listChildrenItem?.let {
                count += it.size
            }
        }
        return count + 8
    }

    private fun getDate(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private fun logDataTest(): String {
        var printedTestCase7_5 = false
        return StringBuilder().apply {
            for (itemTest in listItemTest) {
                if (itemTest.listChildrenItem != null && itemTest.listChildrenItem?.size!! > 0) {
                    for (children in itemTest.listChildrenItem!!) {
                        append("\tTest case ")

                        when {
                            children.id >= 11 -> append(itemTest.idTest + 1)
                            itemTest.idTest == 8 -> append(7)
                            else -> append(itemTest.idTest)
                        }
                        append(".")

                        when {
                            children.id >= 11 -> append(children.id - 10)
                            itemTest.idTest < 6 -> append(children.id)
                            itemTest.idTest == 8 -> append(children.id + 1)
                            else -> append(children.id + 4)
                        }
                        append(",")

                        when {
                            children.statusChildrenTest -> append("Pass")
                            children.getValueErrorLog() == "N/A" -> append("")
                            else -> append(FAILED)
                        }

                        if (itemTest.idTest == 4) {
                            when {
                                children.id in 7..10 -> append("," + children.getValueErrorLog() + ".\n")
                                children.statusChildrenTest -> append(".\n")
                                else -> append(children.getValueErrorLog() + ".\n")
                            }
                        } else {
                            if (children.statusChildrenTest) {
                                append(".\n")
                            } else {
                                append("Fail" + ".\n")
                            }
                        }

                    }
                } else {
                    append("\tTest case ")

                    when {
                        itemTest.idTest < 5 -> append(itemTest.idTest)
                        itemTest.idTest == 7 -> append("7.1")
                        itemTest.idTest == 5 -> append("6.1")
                        itemTest.idTest == 6 -> append("6.2")
                        itemTest.idTest == 9 -> append("7.6")
                        else -> append(itemTest.idTest + 1)
                    }
                    append(",")

                    if (itemTest.idTest == 7 && itemTest.getValueStatusTest() == FAILED) {
                        append("Fail,")
                    } else {
                        append(itemTest.getValueStatusTest())
                    }

                    if (itemTest.idTest <= 3 && itemTest.getValueStatusTest() == FAILED) {
                        append(",")
                    }

                    if (itemTest.idTest == 7) {
                        append(itemTest.getThroughputPassedTestcase())
                    } else {
                        if (itemTest.idTest <= 3 && itemTest.getValueStatusTest() == FAILED) {
                            append(itemTest.getTimePassedTestCase())
                        }
                    }
                    append(".\n")
                }
                // Append test case 7.5 after test case 7.4
                if (itemTest.idTest == 8 && !printedTestCase7_5) {
                    if (iopActivity.isCCCDPass) {
                        append("\tTest case 7.5,Pass.\n")
                    } else {
                        append("\tTest case 7.5,Fail.\n")
                    }
                    printedTestCase7_5 = true // Set the flag to true after printing test case 7.5
                }

            }
        }.toString()
    }

    override fun toString(): String {
        return StringBuilder().apply {
            append("<timestamp>").append(getDate()).append("</timestamp>").append("\n")
            append("<phone_informations>").append("\n")
            append("\t").append("<phone_name>").append(phoneName).append("</phone_name>").append("\n")
            append("\t").append("<phone_os_version>").append(phoneOs).append("</phone_os_version>").append("\n")
            append("</phone_informations>").append("\n")
            append("<firmware_informations>").append("\n")
            append("\t").append("<firmware_original_version>").append(firmwareVersion).append("</firmware_original_version>").append("\n")
            append("\t").append("<firmware_ota_ack_version>").append(firmwareAckVersion).append("</firmware_ota_ack_version>").append("\n")
            append("\t").append("<firmware_ota_non_ack_version>").append(firmwareUnackVersion).append("</firmware_ota_non_ack_version>").append("\n")
            append("\t").append("<firmware_ic_name>").append(iopBoard.toString().replace("_", "")).append("</firmware_ic_name>").append("\n")
            append("\t").append("<firmware_name>").append(fwName).append("</firmware_name>").append("\n")
            append("</firmware_informations>").append("\n")
            append("<connection_parameters>").append("\n")
            append("\t").append("<mtu_size>").append(connectionParameters?.mtu).append("</mtu_size>").append("\n")
            append("\t").append("<pdu_size>").append(connectionParameters?.pdu).append("</pdu_size>").append("\n")
            append("\t").append("<interval>").append(connectionParameters?.interval).append("</interval>").append("\n")
            append("\t").append("<latency>").append(connectionParameters?.slaveLatency).append("</latency>").append("\n")
            append("\t").append("<supervision_timeout>").append(connectionParameters?.supervisionTimeout).append("</supervision_timeout>").append("\n")
            append("</connection_parameters>").append("\n")
            append("<test_results>").append("\n")
            append(logDataTest())
            append("</test_results>").append("\n")
            Timber.d(this.toString())
        }.toString()
    }

    companion object {
        private const val FAILED = "Failed"
    }
}