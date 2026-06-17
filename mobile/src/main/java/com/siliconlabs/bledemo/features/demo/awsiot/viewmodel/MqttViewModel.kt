package com.siliconlabs.bledemo.features.demo.awsiot.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.siliconlabs.bledemo.features.demo.awsiot.repository.ConnectionResult
import com.siliconlabs.bledemo.features.demo.awsiot.repository.MqttRepository
import com.siliconlabs.bledemo.utils.CustomToastManager
import com.siliconlabs.bledemo.utils.Objects.isNetworkAvailable
import javax.net.ssl.SSLContext

class MqttViewModel(@SuppressLint("StaticFieldLeak") val context: Context) : ViewModel() {

    private val repository = MqttRepository(context)

    val mqttMessages: LiveData<String> = repository.mqttMessageLiveData  // Expose LiveData to UI

    val connectionResult:LiveData<ConnectionResult> = repository.connectionState
//    fun connect(subscribeTopic:String) {
//        if(isNetworkAvailable(context = context)){
//            repository.connect(subscribeTopic)
//        }else{
//            CustomToastManager.show(context,"Please check your internet connectivity",5000)
//        }
//    }

    fun connect(topic: String, endPoint: String, sslContext: SSLContext) =
        repository.connect(topic, endPoint, sslContext)

    /**
     * Clears the last MQTT payload so [mqttMessages.observe] does not synchronously replay a
     * retained/stale message and dismiss loading UI before real subscription traffic arrives.
     */
    fun prepareMqttObservationForNewSubscription() {
        repository.clearMqttMessageCacheForNewSubscription()
    }

    fun subscribe(topic: String) {
        repository.subscribe(topic)
    }

    fun disconnect(){
        repository.disconnect()
    }

    fun unsubscribeFromTopic(){
        repository.unsubscribeFromTopic()
    }

    fun publish(topic: String, message: String) {
        repository.publish(topic, message)
    }
}