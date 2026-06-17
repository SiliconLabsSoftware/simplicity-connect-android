package com.siliconlabs.bledemo.features.demo.health_thermometer.models

import android.bluetooth.BluetoothGattCharacteristic
import com.siliconlabs.bledemo.R
import java.text.SimpleDateFormat
import java.util.*

class TemperatureReading(val type: Type, val temperature: Double, private val readingTime: Long) {
    enum class Type(var normalizedMin: Float, var normalizedMax: Float) {
        CELSIUS(0f, 50f),
        FAHRENHEIT(32f, 122f);

        fun getRange(): Float {
            return normalizedMax - normalizedMin
        }
    }

    enum class HtmType(val nameResId: Int) {
        UNKNOWN(R.string.unknown),
        ARMPIT(R.string.therm_type_armpit),
        BODY(R.string.therm_type_body),
        EAR(R.string.therm_type_ear),
        FINGER(R.string.therm_type_finger),
        GI_TRACT(R.string.therm_type_gi_tract),
        MOUTH(R.string.therm_type_mouth),
        RECTUM(R.string.therm_type_rectum),
        TOE(R.string.therm_type_toe),
        TYMPANUM(R.string.therm_type_tympanum);
    }

    var htmType: HtmType? = null
    var normalizedTemperature = 0.0

    init {
        normalizedTemperature = when {
            temperature > type.normalizedMax -> type.normalizedMax.toDouble()
            temperature < type.normalizedMin -> type.normalizedMin.toDouble()
            else -> temperature
        }
    }

    fun getFormattedTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(readingTime))
    }

    fun getTemperature(fetchType: Type): Double {
        return when (fetchType) {
            type -> temperature
            Type.CELSIUS -> fahrenheitToCelsius(temperature)
            else -> celsiusToFahrenheit(temperature)
        }
    }

    companion object {
        fun fromCharacteristic(characteristic: BluetoothGattCharacteristic): TemperatureReading {
            val flags = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
            val type = if (flags and 0x01 > 0) Type.FAHRENHEIT else Type.CELSIUS
            val temperatureFloat = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 1)
            val time: Long
            if (flags and 0x02 > 0) {
                val year = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 5)
                val month = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 7)
                val day = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 8)
                val hour = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 9)
                val min = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 10)
                val sec = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 11)
                val cal = Calendar.getInstance()
                cal[Calendar.YEAR] = year
                cal[Calendar.MONTH] = month
                cal[Calendar.DAY_OF_MONTH] = day
                cal[Calendar.HOUR_OF_DAY] = hour
                cal[Calendar.MINUTE] = min
                cal[Calendar.SECOND] = sec
                cal[Calendar.MILLISECOND] = 0
                time = cal.timeInMillis
            } else {
                time = System.currentTimeMillis()
            }
            return TemperatureReading(type, temperatureFloat.toDouble(), time)
        }

        fun getSampleReading(): TemperatureReading {
            val time = System.currentTimeMillis() - (Math.random() * 6000000).toLong()
            var temp = (Math.random() * Type.FAHRENHEIT.getRange()).toFloat() + Type.FAHRENHEIT.normalizedMin
            if (time % 3 == 0L) {
                temp = Type.FAHRENHEIT.normalizedMax - 15 + (Math.random() * 50).toFloat()
            }
            return TemperatureReading(Type.FAHRENHEIT, temp.toDouble(), time)
        }

        fun fahrenheitToCelsius(fTemp: Double): Double {
            return (fTemp - 32.0f) * 5.0f / 9.0f
        }

        fun celsiusToFahrenheit(cTemp: Double): Double {
            return cTemp * 9.0f / 5.0f + 32.0f
        }
    }
}