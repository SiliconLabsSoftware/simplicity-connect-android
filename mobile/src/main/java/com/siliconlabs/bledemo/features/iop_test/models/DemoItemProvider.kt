package com.siliconlabs.bledemo.features.iop_test.models


class DemoItemProvider {
    companion object {
        private fun getDataTest(deviceName: String, deviceMacAddress: String): ArrayList<ItemTestCaseInfo> {
            val displayName = deviceMacAddress.takeIf { it.isNotBlank() } ?: deviceName
            return ArrayList<ItemTestCaseInfo>().apply {
                add(ItemTestCaseInfo(1, "Scan",
                        "Scanning for device \"$displayName\".",
                        null, Common.IOP3_TC_STATUS_WAITING))
                add(ItemTestCaseInfo(2, "Connect", "Connect to device.", null, Common.IOP3_TC_STATUS_WAITING))
                add(ItemTestCaseInfo(3, "GATT Discovery", "Discover the GATT database.", null, Common.IOP3_TC_STATUS_WAITING))
                add(ItemTestCaseInfo(4, "GATT Operations", "Perform GATT operations (read, write, write without response, indication, notification) with various lengths.", dataChildrenTest(), Common.IOP3_TC_STATUS_WAITING))
                add(ItemTestCaseInfo(5, "OTA-DFU with ACK", "Over-the-air Device Firmware Update with acknowledgment.", null, Common.IOP3_TC_STATUS_WAITING))
                add(ItemTestCaseInfo(6, "OTA-DFU without ACK", "Over-the-air Device Firmware Update without acknowledgment.", null, Common.IOP3_TC_STATUS_WAITING))
                add(ItemTestCaseInfo(7, "Throughput", "Measure Throughput speed using GATT notification.", null, Common.IOP3_TC_STATUS_WAITING))
                add(ItemTestCaseInfo(8, "Security", "Test security features: Just works pairing, Authenticated pairing and Bonding.", dataChildrenSecurity(), Common.IOP3_TC_STATUS_WAITING))
                add(ItemTestCaseInfo(9, "Privacy", "Test bluetooth address encryption using resolvable private addresses.",null, Common.IOP3_TC_STATUS_WAITING))

                /* This test should remain hidden for now due to investigation being underway. It may return later.
                add(ItemTestCaseInfo(9, "GATT Caching", "GATT Caching.", dataChildrenCaching(), Common.IOP3_TC_STATUS_WAITING))
                 */
            }
        }

        fun createDataSiliconLabsTest(fwName: String, deviceMacAddress: String): SiliconLabsTestInfo {
            return SiliconLabsTestInfo(fwName, deviceMacAddress, getDataTest(fwName, deviceMacAddress))
        }

        private fun dataChildrenTest(): ArrayList<ChildrenItemTestInfo> {
            return ArrayList<ChildrenItemTestInfo>().apply {
                add(ChildrenItemTestInfo(1, "", "IOP Test Read Only Length 1", "Read"))
                add(ChildrenItemTestInfo(2, "", "IOP Test Read Only Length 255", "Read"))
                add(ChildrenItemTestInfo(3, "", "IOP Test Write Only Length 1", "Write"))
                add(ChildrenItemTestInfo(4, "", "IOP Test Write Only length 255", "Write"))
                add(ChildrenItemTestInfo(5, "", "IOP Test Write Without Response Length 1", " Write Without Response"))
                add(ChildrenItemTestInfo(6, "", "IOP Test Write Without Response Length 255", " Write Without Response"))
                add(ChildrenItemTestInfo(7, "", "IOP Test Notify length 1", "Notify"))
                add(ChildrenItemTestInfo(8, "", "IOP Test Notify length MTU - 3", "Notify"))
                add(ChildrenItemTestInfo(9, "", "IOP Test Indicate Length 1", "Indicate"))
                add(ChildrenItemTestInfo(10, "", "IOP Test Indicate length MTU - 3", "Indicate"))
                add(ChildrenItemTestInfo(11, "Characteristic", "IOP Test Length 1", "Read,Write"))
                add(ChildrenItemTestInfo(12, "Characteristic", "IOP Test Length 255", "Read,Write"))
                add(ChildrenItemTestInfo(13, "Characteristic", "IOP Test Length Variable 4", "Read,Write"))
                add(ChildrenItemTestInfo(14, "Characteristic", "IOP Test Const Length 1", "Read,Write"))
                add(ChildrenItemTestInfo(15, "Characteristic", "IOP Test Const Length 255", "Read,Write"))
                add(ChildrenItemTestInfo(16, "Characteristic", "IOP Test User Len 1", "Read,Write"))
                add(ChildrenItemTestInfo(17, "Characteristic", "IOP Test User Len 255", "Read,Write"))
                add(ChildrenItemTestInfo(18, "Characteristic", "IOP Test User Len Variable 4", "Read,Write"))
            }
        }

        fun dataChildrenOTA(): ArrayList<ChildrenItemTestInfo> {
            return ArrayList<ChildrenItemTestInfo>().apply {
                add(ChildrenItemTestInfo(1, "", "OTA update-Acknowledged write", "Write"))
                add(ChildrenItemTestInfo(2, "", "OTA update-Unacknowledged write", "Write"))
            }
        }

/* These test cases should remain hidden for now due to investigation being underway. They may return later.

        private fun dataChildrenCaching(): ArrayList<ChildrenItemTestInfo> {
            return ArrayList<ChildrenItemTestInfo>().apply {
                add(ChildrenItemTestInfo(1, "", "IOP Test GATT Caching runtime", ""))
                add(ChildrenItemTestInfo(2, "", "IOP Test Service change indications", ""))
            }
        }
*/

        private fun dataChildrenSecurity(): ArrayList<ChildrenItemTestInfo> {
            return ArrayList<ChildrenItemTestInfo>().apply {
                add(ChildrenItemTestInfo(1, "", "IOP Test Security-Pairing", ""))
                add(ChildrenItemTestInfo(2, "", "IOP Test Security-Authentication", ""))
                add(ChildrenItemTestInfo(3, "", "IOP Test Security-Bonding", ""))
            }
        }
    }
}