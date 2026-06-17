package com.siliconlabs.bledemo.features.demo.wifi_throughput.utils

import android.content.Context
import com.siliconlabs.bledemo.R
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit


object ThroughputUtils {

    enum class WiFiThroughPutFeature(id: Int) {
        TCP_RX(1),
        TCP_TX(2),
        UDP_RX(3),
        UDP_TX(4),
        TLS_RX(5),
        TLS_TX(6)
    }

    var throughPutType : String = "throughputType"
    var ipAddress : String = "ipAddress"
    var portNumber : String = "portNumber"

    const val THROUGHPUT_TYPE_TCP_UPLOAD =  "TCP Upload"
    const val THROUGHPUT_TYPE_UDP_UPLOAD =  "UDP Upload"
    const val THROUGHPUT_TYPE_TLS_UPLOAD =  "TLS Upload"
    const val THROUGHPUT_TYPE_TCP_DOWNLOAD =  "TCP Download"
    const val THROUGHPUT_TYPE_UDP_DOWNLOAD =  "UDP Download"
    const val THROUGHPUT_TYPE_TLS_DOWNLOAD =  "TLS Download"


    var port: Int = 5005
    var running: Boolean = false
    var socket: DatagramSocket? = null

    fun getTitle(key : Int, context: Context) : String{
        return when(key){
            0 ->  context.getString(R.string.tcp_receive_title)
            1 ->  context.getString(R.string.tcp_send_title)
            2 ->  context.getString(R.string.udp_receive_title)
            3 ->  context.getString(R.string.udp_send_title)
            4 ->  context.getString(R.string.tls_receive_title)
            5 ->  context.getString(R.string.tls_send_title)
            else ->  "Nothing"
        }
    }

    /** Grid ordinal: 0 TCP_RX, 1 TCP_TX, 2 UDP_RX, 3 UDP_TX, 4 TLS_RX, 5 TLS_TX */
    fun isThroughPutTypeDownload(key : Int) : Boolean{
        return when(key){
            0, 2, 4 -> true  // receive / download (this device is server)
            1, 3, 5 -> false // send / upload (this device is client — user enters peer IP)
            else -> false
        }
    }

    fun sendEvent() {
        val BYTES_TO_SEND = 536870912.0 //317470020// Example value, set your actual byte count
        val UDP_BUFFER_SIZE = 1470 // Example buffer size, set accordingly
        val TEST_TIMEOUT: Long = 10000 // Timeout in milliseconds
        val dataBuffer = ByteArray(UDP_BUFFER_SIZE) // Example data buffer
        val serverAddress: InetAddress
        var socket: DatagramSocket? = null
        var totalBytesSent = 0
        var sentBytes: Int
        var fail = 0
        var pass = 0
        val SEVER_IP = "192.168.1.194"
        val buffer = ByteArray(1470)
        for (i in buffer.indices) {
            buffer[i] = ('A'.code + (i % 26)).toByte()
        }
        val startTime = System.nanoTime()

        try {
            serverAddress = InetAddress.getByName(SEVER_IP) // Replace with actual server address
            socket = DatagramSocket()
            socket.soTimeout = TEST_TIMEOUT.toInt()
            val start = System.currentTimeMillis()

            while (totalBytesSent < BYTES_TO_SEND) {
                println("Total bytes sent so far : $totalBytesSent")
                val packet = DatagramPacket(
                    buffer,
                    buffer.size,
                    serverAddress,
                    5005
                ) // Replace 9876 with actual server port
                socket.send(packet)
                val now = System.currentTimeMillis()

                if ((now - start) > TEST_TIMEOUT) {
                    println(
                        """
                        Time Out: ${now - start}
                        """.trimIndent()
                    )
                    break
                }
                sentBytes = packet.length
                if (sentBytes < 0) {
                    fail++
                } else {
                    pass++
                }
                if (sentBytes > 0) {
                    totalBytesSent += sentBytes
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (socket != null && !socket.isClosed) {
                socket.close()
            }
        }
        val endTime = System.nanoTime()
        val timeTakenInSeconds = TimeUnit.NANOSECONDS.toSeconds(endTime - startTime)
        val bandwidthInMbps = (totalBytesSent * 8.0) / timeTakenInSeconds / 1_000_000
        println("Bandwidth: $bandwidthInMbps Mbps")
        /*println("Total Bytes Sent: $totalBytesSent")
        println("Pass: $pass")
        println("Fail: $fail")*/
    }

    fun receiveUDPData() {
        val LISTENING_PORT = 5005 // Replace with actual listening port
        val TEST_TIMEOUT = longArrayOf(60000) // Timeout in milliseconds
        val hasDataReceived = booleanArrayOf(false)
        val bytesRead = longArrayOf(0)
        val start: Long
        val now: Long
        var serverSocket: DatagramSocket? = null
        try {
            serverSocket = DatagramSocket()
            println(
                """
                
                Socket ID: ${serverSocket.localPort}
                """.trimIndent()
            )
            //System.out.println("\nListening on Local Port " + LISTENING_PORT);
            start = System.currentTimeMillis()
            serverSocket.reuseAddress = true
            if (!serverSocket.isBound) serverSocket.bind(InetSocketAddress(5005))

            val finalServerSocket: DatagramSocket = serverSocket
            val receiverThread = Thread {
                val receiveBuffer =
                    ByteArray(1470) // Adjust buffer size as needed
                while (!hasDataReceived[0]) {
                    try {
                        val receivePacket =
                            DatagramPacket(receiveBuffer, receiveBuffer.size)
                        finalServerSocket.receive(receivePacket)
                        bytesRead[0] += receivePacket.length.toLong()
                        hasDataReceived[0] =
                            true // Assuming we stop after first receive for simplicity
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
            receiverThread.start()
            receiverThread.join(TEST_TIMEOUT[0])
            now = System.currentTimeMillis()
            println("\nUDP_RX Async Throughput test finished")
            println(
                """
                
                Total bytes received: ${bytesRead[0]}
                """.trimIndent()
            )
            measureAndPrintThroughput(bytesRead[0], (now - start))
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            /*if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }*/
        }
    }

    private fun measureAndPrintThroughput(totalBytesReceived: Long, duration: Long) {
        val throughput = (totalBytesReceived / duration.toDouble()) * 1000 // bytes per second
        println("Throughput: $throughput bytes/sec")
    }

    fun receiUDP() {
        running = true
        var totalBytesReceived =0L

        Thread {
            try {
                var now: Long
                socket = DatagramSocket(port)
                val start = System.currentTimeMillis()

                println("UDP Server started on port $port")

                val buffer = ByteArray(1470)
                while (running) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket!!.receive(packet)
                    totalBytesReceived += packet.length
                    println(
                        "Total packets receieved is : $totalBytesReceived"
                    )
                    val data =
                        String(packet.data, 0, packet.length)
                   /* println(
                        "Received: " + data + " from " + packet.address.hostAddress
                    )*/
                    now = System.currentTimeMillis()

                    measureAndPrintThroughput(packet.length.toLong(), (now - start))
                    // Process the received data and send a response if needed
                    val response = "Echo: $data"
                    val responsePacket = DatagramPacket(
                        response.toByteArray(),
                        response.toByteArray().size,
                        packet.address,
                        packet.port
                    )
                    socket!!.send(responsePacket)
                }
            } catch (e: IOException) {
                System.err.println("Error: " + e.message)
            } finally {
                if (socket != null) {
                    running = false
                    socket!!.close()
                }
                println("UDP Server stopped")
            }
        }.start()
    }
}