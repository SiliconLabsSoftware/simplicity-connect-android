package com.siliconlabs.bledemo.features.demo.awsiot.repository

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.features.configure.advertiser.services.MqttForegroundService
import com.siliconlabs.bledemo.utils.CustomToastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import timber.log.Timber
import java.io.InputStream
import java.security.KeyStore
import java.util.UUID
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

class MqttRepository(private val context: Context) {

    private lateinit var mqttClient: MqttAndroidClient
    private lateinit var endPoint: String
    private lateinit var sslContext: SSLContext

    // AWS IoT endpoint
//        MqttAndroidClient(context, "ssl://$AWS_END_POINT:8883", UUID.randomUUID().toString(),null,MqttAndroidClient.Ack.AUTO_ACK)
    private val _mqttMessageLiveData = MutableLiveData<String>()
    val mqttMessageLiveData: LiveData<String> = _mqttMessageLiveData
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var oldTopic = MutableLiveData<String>()
    var oldPublishTopic = MutableLiveData<String>()

    private val _connectionState = MutableLiveData<ConnectionResult>()
    val connectionState: LiveData<ConnectionResult> = _connectionState

    private fun emitConnection(state: ConnectionResult) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _connectionState.value = state
        } else {
            _connectionState.postValue(state)
        }
    }

    /**
     * Wipes the cached MQTT body so a new [mqttMessageLiveData] observer is not invoked with an
     * old retained message the moment [observe] registers (LiveData replays the latest value).
     */
    fun clearMqttMessageCacheForNewSubscription() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _mqttMessageLiveData.value = ""
        } else {
            _mqttMessageLiveData.postValue("")
        }
    }

    private var options = MqttConnectOptions().apply {
        isCleanSession = true
        isAutomaticReconnect = true // Manual retry logic for better control
        keepAliveInterval = 30
        connectionTimeout = 60
        maxRetryDelay = 60000L // Maximum retry delay of 30 seconds
        retryDelay = 1000L // Initial retry delay
        socketFactory = createSSLContext(context).socketFactory
        setWill("debug/status", "Client disconnected unexpectedly".toByteArray(), 1, true)
    }


    private var retryDelay = 1000L // Start with 1 second
    private val maxRetryDelay = 30000L // Cap retry at 30 seconds

    fun connect(subscribeTopic: String, endPoint: String, sslContext: SSLContext) {
        // Must run before any async connect callback so UI never receives Connected before Connecting
        // (postValue(Connecting) from IO was racing setValue(Connected) from onSuccess and hid the load bar).
        emitConnection(ConnectionResult.Connecting)

        // Show foreground notification when connecting
        this@MqttRepository.endPoint = endPoint
        this@MqttRepository.sslContext = sslContext
        mqttClient = MqttAndroidClient(
            context,
            "ssl://$endPoint:8883",
            UUID.randomUUID().toString(),
            null,
            MqttAndroidClient.Ack.AUTO_ACK
        )
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                context.startMqttForegroundService()
            }
        }
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                mqttClient.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        Timber.tag(TAG).e("Connection lost: ${cause?.message}")

                        ConnectionResult.Error("Connection Lost ${cause?.message}")
                        retryConnection()
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        _mqttMessageLiveData.postValue(message?.toString() ?: "")
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}


                })

                try {
                    mqttClient.connect(options, null, object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {

                            Timber.tag(TAG).d("Connected successfully")

                            emitConnection(ConnectionResult.Connected)
                            retryDelay = 1000L // Reset retry delay after success
                            oldTopic.value = subscribeTopic
                            subscribe(subscribeTopic)

                        }

                        override fun onFailure(
                            asyncActionToken: IMqttToken?,
                            exception: Throwable?
                        ) {
                            Timber.tag(TAG).e("Connection failed: ${exception?.message}")

                            emitConnection(
                                ConnectionResult.Error("Connection failed: ${exception?.message}")
                            )
                            //retryConnection()
                        }
                    })
                } catch (e: MqttException) {
                    Timber.tag(TAG).e("Exception in connect: ${e.message}")

                    emitConnection(
                        ConnectionResult.Error("Exception in connect: ${e.message}")
                    )
                    retryConnection()
                }
            }
        }
    }

    fun subscribe(topic: String) {
        emitConnection(ConnectionResult.SubscribeConnecting)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                mqttClient.subscribe(topic, 1, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Timber.tag(TAG).d("Subscribed to $topic successfully")

                        emitConnection(ConnectionResult.SubscribeConnected)
                        CustomToastManager.show(context, "Subscribed to $topic successfully", 5000)

                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {

                        Timber.tag(TAG).e("Subscription failed: ${exception?.message}")
                        //CustomToastManager.show(context,"Subscription failed: ${exception?.message}",5000)
                        emitConnection(
                            ConnectionResult.Error("Subscription failed: ${exception?.message}")
                        )
                    }
                })

                mqttClient.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        Timber.tag(TAG).e("Connection lost: ${cause?.message}")

                        emitConnection(
                            ConnectionResult.Error("Connection lost: ${cause?.message}")
                        )

                    }

                    override fun messageArrived(topic: String, message: MqttMessage) {
                        Timber.tag(TAG).d("Message received: ${message.toString()}")

                        // Post to LiveData on the Main Thread
                        CoroutineScope(Dispatchers.Main).launch {
                            _mqttMessageLiveData.postValue(message.toString())
                        }
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                        Timber.tag(TAG).d("Message delivery complete")
                        CustomToastManager.show(context, "Message delivery complete", 5000)

                    }
                })
            } catch (e: Exception) {
                Timber.tag(TAG).e("Error in subscription: ${e.message}")

                emitConnection(
                    ConnectionResult.Error("Error in subscription: ${e.message}")
                )
            }
        }
    }

    private fun retryConnection() {
        emitConnection(ConnectionResult.Connecting)
        coroutineScope.launch {
            delay(retryDelay)
            Timber.tag(TAG).d("Retrying connection in ${retryDelay / 1000} seconds...")

            CustomToastManager.show(
                context,
                "Retrying connection in ${retryDelay / 1000} seconds...",
                5000
            )
            retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay) // Exponential backoff
            connect(oldTopic.value.toString(), endPoint, sslContext)
        }
    }

    fun publish(topic: String, message: String) {
        oldPublishTopic.value = topic
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                if (mqttClient.isConnected) {
                    val mqttMessage = MqttMessage(message.toByteArray())
                    mqttClient.publish(topic, mqttMessage)
                    withContext(Dispatchers.Main) {
                        CustomToastManager.show(context, "Publish Successfully", 5000)
                    }


                } else {

                    Timber.tag(TAG).e("Publish failed: MQTT is not connected")
                    _connectionState.postValue(
                        ConnectionResult.Error("Publish failed: MQTT is not connected")
                    )
                }
            }
        }
    }

    fun disconnect() {
        //_connectionState.value = ConnectionResult.Disconnecting
        _connectionState.postValue(ConnectionResult.Disconnecting)
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (null != mqttClient && mqttClient.isConnected) {
                        try {

                            mqttClient.disconnect(null, object : IMqttActionListener {
                                override fun onSuccess(asyncActionToken: IMqttToken?) {
                                    _connectionState.value = ConnectionResult.Disconnected
                                    coroutineScope.launch {
                                        withContext(Dispatchers.Main) {
                                            /*CustomToastManager.show(
                                                context,
                                                "AWS IoT Disconnected Successfully!",
                                                3000
                                            )*/
                                        }
                                    }
                                }

                                override fun onFailure(
                                    asyncActionToken: IMqttToken?,
                                    exception: Throwable?
                                ) {
                                    Timber.tag(TAG).e("Disconnection failed: ${exception?.message}")
                                    _connectionState.value = ConnectionResult.DisconnectionError
                                }

                            })
                        } catch (e: MqttException) {
                            Timber.tag(TAG).e("MqttException during disconnect: ${e.message}")

                            _connectionState.value = ConnectionResult.DisconnectionError
                        }
                    } else {
                        _connectionState.value = ConnectionResult.DisconnectionError
                    }

                } catch (e: Exception) {
                    Timber.tag(TAG).e("Error during disconnect: ${e.message}")

                    coroutineScope.launch {
                        withContext(Dispatchers.Main) {
                            _connectionState.postValue(ConnectionResult.DisconnectionError)
                            _connectionState.postValue(ConnectionResult.Error("Error during disconnect: ${e.message}"))
                        }
                    }
                } finally {
                    // Stop the foreground service after disconnecting
                    context.stopService(Intent(context, MqttForegroundService::class.java))
                }
            }
        }
    }

    fun unregisterMQTTServices() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    mqttClient.disconnect()
                    mqttClient.unregisterResources()
                    mqttClient.close()
                    Timber.tag(TAG).d("MQTT resources unregistered successfully")
                } catch (e: Exception) {
                    Timber.tag(TAG).e("Error unregistering MQTT resources: ${e.message}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "MqttRepository"

        // const val AWS_END_POINT = "a2m21kovu9tcsh-ats.iot.us-east-2.amazonaws.com"


        fun createSSLContext(context: Context): SSLContext {
            val keyStore = KeyStore.getInstance("PKCS12")
            val password =
                "1234567890".toCharArray() // Use the same password you used to create the .p12 file

            val inputStream: InputStream =
                context.resources.openRawResource(R.raw.silabsawsiot) // Put server.p12 in res/raw
            keyStore.load(inputStream, password)

            val keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            keyManagerFactory.init(keyStore, password)

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(keyManagerFactory.keyManagers, null, null)

            return sslContext
        }
    }


    fun unsubscribeFromTopic() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                if (null != mqttClient) {
                    mqttClient.unsubscribe(oldTopic.value, null, object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            Timber.tag(TAG).d("Unsubscribed from topic: ${oldTopic.value}")
                        }

                        override fun onFailure(
                            asyncActionToken: IMqttToken?,
                            exception: Throwable?
                        ) {
                            Timber.tag(TAG).e("Unsubscribe failed: ${exception?.message}")
                        }
                    })
                }
            }
        }
    }

    fun Context.startMqttForegroundService() {
        val intent = Intent(this, MqttForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.startForegroundService(intent)
        } else {
            this.startService(intent)
        }
    }
}

sealed class ConnectionResult {
    object Connecting : ConnectionResult()
    object Connected : ConnectionResult()
    data class Error(val message: String, val throwable: Throwable? = null) : ConnectionResult()
    object SubscribeConnecting : ConnectionResult()
    object SubscribeConnected : ConnectionResult()
    object Disconnecting : ConnectionResult()
    object Disconnected : ConnectionResult()
    object DisconnectionError : ConnectionResult()
}