package com.siliconlabs.bledemo.features.demo.connected_lighting.fragments

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.siliconlabs.bledemo.features.demo.connected_lighting.activities.ConnectedLightingActivity
import com.siliconlabs.bledemo.features.demo.connected_lighting.presenters.ConnectedLightingPresenter
import com.siliconlabs.bledemo.features.demo.connected_lighting.models.TriggerSource
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.bluetooth.ble.GattService
import com.siliconlabs.bledemo.databinding.FragmentLightBinding

class ConnectedLightingFragment : Fragment(), ConnectedLightingPresenter.View {
    lateinit var presenter: ConnectedLightingPresenter
    private lateinit var binding: FragmentLightBinding
    private var disconnectedDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentLightBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presenter = ConnectedLightingPresenter(this, activity as ConnectedLightingActivity)

        binding.btnLightbulb.setOnClickListener {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            presenter.onLightClicked()
        }
    }

    override fun showLightState(
        lightOn: Boolean,
        triggerSource: TriggerSource,
        gattService: GattService?
    ) {
        println("ConnectedLightingScreen showLightState: $lightOn, $triggerSource, $gattService")

        val isDmpZigbee = gattService?.name == "TheDMP" &&
                (triggerSource == TriggerSource.ZIGBEE ||
                        triggerSource == TriggerSource.UNKNOWN ||
                        triggerSource == TriggerSource.BUTTON
                        || triggerSource == TriggerSource.BLUETOOTH)
        val (imageRes, textRes) = when {
            // Special case: force locked icon/status when UNKNOWN + TheDMP + lightOff
            isDmpZigbee && !lightOn && triggerSource == TriggerSource.UNKNOWN ->
                R.drawable.door_unlock to R.string.matter_unlock_status
            isDmpZigbee && lightOn -> R.drawable.door_lock to R.string.matter_locked_status
            isDmpZigbee && !lightOn -> R.drawable.door_unlock to R.string.matter_unlock_status
            lightOn -> R.drawable.light_on to R.string.light_demo_on
            else -> R.drawable.light_off to R.string.light_demo_off
        }
        println("ConnectedLightingScreen showLightState: lightOn=$lightOn, trigger=$triggerSource, gatt=${gattService?.name}")
        binding.btnLightbulb.setImageResource(imageRes)
        binding.tvLightStatus.setText(textRes)
        updateToggleHint(isDmpZigbee)
        applyAwsSourceUi(triggerSource, gattService)
    }

    override fun showLightStateWithSource(
        lightOn: Boolean,
        source: TriggerSource,
        gattService: GattService?
    ) {
        when (source) {
            TriggerSource.AWS -> {
                if (lightOn) {
                    //RECEIVED STATE FROM AWS AS 01
                    binding.btnLightbulb.setImageResource(R.drawable.door_lock)
                    binding.tvLightStatus.setText(R.string.smart_lock_status_asw_lock)
                } else {
                    //RECEIVED STATE FROM AWS AS 00
                    binding.btnLightbulb.setImageResource(R.drawable.door_unlock)
                    binding.tvLightStatus.setText(R.string.smart_lock_status_asw_unlock)
                }
                updateToggleHint(true)
            }
            else -> {
                showLightState(lightOn, source, gattService)
            }
        }
        applyAwsSourceUi(source, gattService)
    }

    private fun applyAwsSourceUi(source: TriggerSource?, gattService: GattService?) {
        if (!isAdded || isRemoving || isDetached) return
        if (gattService?.name == "TheDMP" && source == TriggerSource.ZIGBEE) {
            binding.ivLightChangeSourceLogo.visibility = View.VISIBLE
            binding.ivLightChangeSourceLogo.setImageResource(R.drawable.ic_aws_iot_icon)
            binding.tvLightChangeSourceName.text = getString(R.string.smart_lock_demo_dialog_btn_aws)
        }
    }

    override fun showTriggerSourceDetails(source: TriggerSource?, gattService: GattService?) {
        println("ConnectedLightingScreen gattService: $gattService, source: $source")
        if (isAdded && !isDetached && !isRemoving) {
            if (shouldForceAwsSourceUi(source, gattService)) {
                applyAwsSourceUi(source, gattService)
                return
            }
            if (source == TriggerSource.UNKNOWN && gattService?.name == "TheDMP") {
                binding.ivLightChangeSourceLogo.visibility = View.VISIBLE
                binding.ivLightChangeSourceLogo.setImageResource(R.drawable.ic_aws_iot_icon)
                binding.tvLightChangeSourceName.text = getString(R.string.smart_lock_demo_dialog_btn_aws)
            }

            val iconId = source?.iconId ?: android.R.color.transparent
            if (iconId == android.R.color.transparent || iconId == 0) {
                binding.ivLightChangeSourceLogo.visibility = View.GONE
            } else {
                binding.ivLightChangeSourceLogo.setImageResource(iconId)
                binding.ivLightChangeSourceLogo.visibility = View.VISIBLE
            }
            source?.let { binding.tvLightChangeSourceName.text = getText(it.textResId) }
        }
    }
    private fun shouldForceAwsSourceUi(
        source: TriggerSource?,
        gattService: GattService?
    ) = gattService?.name == "TheDMP" && source == TriggerSource.ZIGBEE
    override fun showTriggerSourceAddress(sourceAddress: String, source: TriggerSource?) {
        println("Trigger source address: $sourceAddress, $source")
        binding.tvLightChangeSource
        if (TriggerSource.UNKNOWN == source || sourceAddress == "00:00:00:00:00:00:00:00") {
            binding.tvLightChangeSource.text = getString(R.string.light_demo_changed_source_unknown)
        } else {
            binding.tvLightChangeSource.text = StringBuffer(sourceAddress).reverse().toString()
        }
    }

    override fun showDeviceDisconnectedDialog() {
        // Avoid scheduling if fragment/activity already going away
        val act = activity ?: return
        if (!isAdded || isRemoving || isDetached || act.isFinishing || act.isDestroyed) return

        act.runOnUiThread {
            // Re-check after posting to UI thread
            val currentAct = activity
            if (currentAct == null ||
                !isAdded ||
                isRemoving ||
                isDetached ||
                currentAct.isFinishing ||
                currentAct.isDestroyed ||
                !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) return@runOnUiThread

            // Dismiss any existing dialog first
            disconnectedDialog?.setOnDismissListener(null)
            disconnectedDialog?.dismiss()
            disconnectedDialog = null

            disconnectedDialog = AlertDialog.Builder(currentAct)
                .setMessage(R.string.light_demo_connection_lost)
                .setPositiveButton(R.string.light_demo_connection_lost_button) { dialog, _ ->
                    dialog.dismiss()
                }
                .setOnDismissListener {
                    disconnectedDialog = null
                    // Ensure we only call if still added
                    if (isAdded && !isRemoving && !isDetached) {
                        presenter.leaveDemo()
                    }
                }
                .create()

            try {
                disconnectedDialog?.show()
            } catch (_: Exception) {
                // Activity window gone; prevent leak
                disconnectedDialog = null
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Dismiss dialog to prevent window leak
        disconnectedDialog?.dismiss()
        disconnectedDialog = null
    }

    private fun updateToggleHint(isLockUi: Boolean) {
        binding.tvLightBulbHint.setText(
            if (isLockUi) R.string.light_demo_toggle_hint_lock
            else R.string.light_demo_toggle_hint_light
        )
    }
}