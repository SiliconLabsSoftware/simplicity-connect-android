package com.siliconlabs.bledemo.features.demo.matter_demo.dishwasher_demo.view

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.GetConnectedDeviceCallbackJni
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.bluetooth.beacon_utils.eddystone.Constants.SCAN_TIMER
import com.siliconlabs.bledemo.databinding.FragmentMatterDishwasherBinding
import com.siliconlabs.bledemo.features.demo.matter_demo.activities.MatterDemoActivity
import com.siliconlabs.bledemo.features.demo.matter_demo.dishwasher_demo.utils.DishWasherEnumConstants
import com.siliconlabs.bledemo.features.demo.matter_demo.dishwasher_demo.model.DishWasherModel
import com.siliconlabs.bledemo.features.demo.matter_demo.dishwasher_demo.viewmodel.DishwasherViewModel
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterLightFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterLightFragment.Companion.ARG_DEVICE_MODEL
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterLightFragment.Companion.INIT
import com.siliconlabs.bledemo.features.demo.matter_demo.model.MatterScannedResultModel
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.ChipClient
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.CustomProgressDialog
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.FragmentUtils
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.MessageDialogFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.SharedPrefsUtils
import com.siliconlabs.bledemo.utils.CustomToastManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@AndroidEntryPoint
class MatterDishwasherFragment : Fragment() {
    private var progress: Int = 0
    private lateinit var dialog: MessageDialogFragment
    private val dialogTag = "MessageDialog"
    private val deviceController: ChipDeviceController
        get() = ChipClient.getDeviceController(requireContext())
    private lateinit var mPrefs: SharedPreferences

    private lateinit var scope: CoroutineScope
    lateinit var binding: FragmentMatterDishwasherBinding
    private var deviceId: Long = INIT
    private var endpointId: Int = DISHWASHER_OPT_ENDPOINT
    private lateinit var model: MatterScannedResultModel
    private var customProgressDialog: CustomProgressDialog? = null
    private var timer: CountDownTimer? = null
    private var timeLeftInMillis: Long = INITIAL_TIME_LEFT_MILLIS_LONG // 10 minutes in milliseconds
    private var isTimerRunning = false
    private var isPaused = false
    private var currentTotalEnergy: Float = DISHWASHER_ZERO_ENERGY_KWH
    private var currentInCycleEnergy: Float = DISHWASHER_ZERO_ENERGY_KWH
    private var cycleCounter: Int? = 0
    private var mCurrentCycleState: Boolean = true
    var mInCurrentCycleGlobalState = DISHWASHER_ZERO_ENERGY_KWH
    private lateinit var dishwasherPref: SharedPreferences
    private val viewModel: DishwasherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mPrefs = requireContext().getSharedPreferences(
            MatterDemoActivity.MATTER_PREF, AppCompatActivity.MODE_PRIVATE
        )
        dishwasherPref = requireContext().getSharedPreferences(
            DISHWASHER_PREF, AppCompatActivity.MODE_PRIVATE
        )
        model = requireArguments().getParcelable(ARG_DEVICE_MODEL)!!
        deviceId = model.deviceId
        Timber.tag(TAG).e("deviceID: $model")
        viewModel.setDeviceId(deviceId)
        showMatterProgressDialog(getString(R.string.matter_device_status))
        CoroutineScope(Dispatchers.IO).launch {
            val resultInfo = checkForDeviceStatus()
            withContext(Dispatchers.Main) {
                if (resultInfo) {
                    println("Operation was successful")
                    removeProgress()
                }
            }
        }
    }

    private suspend fun checkForDeviceStatus(): Boolean {
        return withContext(Dispatchers.Default) {
            // Simulate a time-consuming task

            deviceController.getConnectedDevicePointer(
                deviceId,
                object : GetConnectedDeviceCallbackJni.GetConnectedDeviceCallback {
                    override fun onDeviceConnected(devicePointer: Long) {
                        println("----DevicePointer:$devicePointer")
                        model.isDeviceOnline = true
                        SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, true)
                    }

                    override fun onConnectionFailure(nodeId: Long, error: java.lang.Exception?) {
                        model.isDeviceOnline = false
                        removeProgress()
                        println("----nodeId:$nodeId")
                        println("----error:${error!!.message}")
                        SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, false)
                        showMessageDialog()

                    }
                })

            delay(SCAN_TIMER * COUNTDOWN_INTERVAL)
            true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = FragmentMatterDishwasherBinding.inflate(inflater, container, false)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            binding.btnOn.isEnabled = true
            binding.btnOff.isEnabled = false
            binding.btnPause.isEnabled = false
            requireActivity().runOnUiThread {
                imageDishwasherOff()
            }
        }
        return binding.root
    }

    private fun showMessageDialog() {

        try {
            if (isAdded && !requireActivity().isFinishing) {
                requireActivity().runOnUiThread {
                    if (!MessageDialogFragment.isDialogShowing()) {
                        dialog = MessageDialogFragment()
                        dialog.setMessage(getString(R.string.matter_device_offline_text))
                        dialog.setOnDismissListener {
                            removeProgress()
                            if (requireActivity().supportFragmentManager.backStackEntryCount > 0) {
                                requireActivity().supportFragmentManager.popBackStack()
                            } else {
                                FragmentUtils.getHost(
                                    this@MatterDishwasherFragment, CallBackHandler::class.java
                                ).onBackHandler()
                            }
                        }
                        val transaction: FragmentTransaction =
                            requireActivity().supportFragmentManager.beginTransaction()

                        dialog.show(transaction, dialogTag)
                    }
                }
            } else {
                Timber.e("device offline")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e("device offline device offline ${e.message}")
        }

    }

    private fun removeProgress() {
        if (customProgressDialog?.isShowing == true) {
            customProgressDialog?.dismiss()
        }
    }

    private fun showMatterProgressDialog(message: String) {
        customProgressDialog = CustomProgressDialog(requireContext())
        customProgressDialog!!.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        customProgressDialog!!.setMessage(message)
        customProgressDialog!!.setCanceledOnTouchOutside(false)
        customProgressDialog!!.show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scope = viewLifecycleOwner.lifecycleScope
        updateCompletedCycleTextCounter()
        binding.btnOn.text = requireContext().getString(R.string.matter_dishwasher_on)
        binding.btnOff.text = requireContext().getString(R.string.matter_dishwasher_off)
        binding.btnPause.text = requireContext().getString(R.string.matter_dishwasher_pause)
        (activity as MatterDemoActivity).hideQRScanner()

        binding.btnOn.setOnClickListener {
            DishWasherModel.dishwasherCurrentRunningState = DishWasherEnumConstants.STARTED
            viewModel.saveAppliedCycleStates(DishWasherEnumConstants.STARTED)
            binding.tvCurrentCycleEnergyComp.text = "$currentInCycleEnergy kWh"
            binding.btnOff.isEnabled = true
            binding.btnPause.isEnabled = true
            binding.btnOn.isEnabled = false
            viewModel.readEnergy(context)
            viewModel.turnOnDishwasher(requireContext(),endpointId)
            scope.launch {
                startDishwasherTimer()
            }
        }
        binding.btnOff.setOnClickListener {
            currentInCycleEnergy = DISHWASHER_ZERO_ENERGY_KWH
            binding.tvCurrentCycleEnergyComp.text = DISHWASHER_ZERO_ENERGY_DISPLAY
            viewModel.saveInCurrentCycleEnergyConsumed(DISHWASHER_ZERO_ENERGY_KWH)
            DishWasherModel.dishwasherCurrentRunningState = DishWasherEnumConstants.STOPPED
            viewModel.saveAppliedCycleStates(DishWasherEnumConstants.STOPPED)
            cycleCounter = cycleCounter!! + 1
            updateCompletedCycleTextCounter()
            binding.btnOn.isEnabled = true
            binding.btnOff.isEnabled = false
            binding.btnPause.isEnabled = false
            mCurrentCycleState = false
            viewModel.readEnergy(context)
            viewModel.turnOffDishwasher(requireContext(),endpointId)
            scope.launch {
                stopDishwasherTimer()
            }
            mInCurrentCycleGlobalState = DISHWASHER_ZERO_ENERGY_KWH
            cycleCounter?.let {
                viewModel.saveCompletedCycleCount(it)
            }
        }
        binding.btnPause.setOnClickListener {
            println("btnPauseClickLisnetState${isPaused}")
            binding.btnOn.isEnabled = false
            binding.btnOff.isEnabled = true
            binding.btnPause.isEnabled = true
            if (isPaused) {
                //resume dishwasher operations
                viewModel.resumeDishwasher(requireContext(),endpointId)
                scope.launch {
                    resumeDishwasherTimer()
                }

            } else {
                //pause dishwasher operations
                viewModel.pauseDishwasher(requireContext(),endpointId)
                scope.launch {
                    pauseDishwasherTimer()
                }

            }
            isPaused = !isPaused
            if (isPaused) {
                DishWasherModel.dishwasherCurrentRunningState = DishWasherEnumConstants.PAUSED
                viewModel.saveAppliedCycleStates(DishWasherEnumConstants.PAUSED)
            } else {
                DishWasherModel.dishwasherCurrentRunningState = DishWasherEnumConstants.RESUMED
                viewModel.saveAppliedCycleStates(DishWasherEnumConstants.RESUMED)
            }


        }
        updateCountDownTimerText()
        binding.matterProgressBar.max = 100
        binding.matterProgressBar.rotation = 180F
        //back handling
        requireActivity().onBackPressedDispatcher.addCallback(
            requireActivity(), onBackPressedCallback
        )
        consumeDishwasherViewModelObserverValues()
    }

    @SuppressLint("DefaultLocale")
    private fun consumeDishwasherViewModelObserverValues() {
        try {
            if (isAdded) {
                viewModel.getCompletedCycleCount().observe(viewLifecycleOwner, Observer {
                    if (-1 != it) {
                        cycleCounter =
                            it
                        updateCompletedCycleTextCounter()
                    }
                })
                println("timeLeftInMillSecOnResume ${timeLeftInMillis}")

                viewModel.getAppliedCycleStates().observe(viewLifecycleOwner, Observer {
                    if (it != null) {

                        println("consumedState ${it}")
                        when (it) {

                            DishWasherEnumConstants.STARTED -> {
                                dishwasherStartCycle()
                            }

                            DishWasherEnumConstants.STOPPED -> {
                                dishwasherStopCycle()
                            }

                            DishWasherEnumConstants.RESUMED -> {
                                dishwasherResumeCycle()
                            }

                            DishWasherEnumConstants.PAUSED -> {
                                dishwasherPauseCycle()

                            }

                            else -> {

                                binding.btnOn.isEnabled = true
                                binding.btnOff.isEnabled = false
                                binding.btnPause.isEnabled = false
                                requireActivity().runOnUiThread {
                                    imageDishwasherOff()
                                }


                            }

                        }
                    }
                })
            }
        } catch (e: Exception) {
            println("MatterDishwasherFragment ${e.message}")
            Timber.tag(TAG).e("Error :${e}")
        }

        //read energyValue from Dishwasher for Success State
        viewModel.energyMeasurement.observe(viewLifecycleOwner, Observer { dishWasherEnergyValue->
            if (isAdded) {
                    val energyValue = dishWasherEnergyValue?.energy
                    if (null != energyValue) {
                        val str = (energyValue?.toDouble())?.div(MEGAWATTS_TO_KILOWATTS_FACTOR)
                        val formattedEnergyValue = String.format("%.3f", str)
                        if (formattedEnergyValue.isNotEmpty() || formattedEnergyValue.isNotBlank()) {
                            currentTotalEnergy = formattedEnergyValue.toFloat()
                            if (mInCurrentCycleGlobalState == DISHWASHER_ZERO_ENERGY_KWH) {
                                mInCurrentCycleGlobalState = formattedEnergyValue.toFloat()
                            }
                            //currentInCycleEnergy = currentTotalEnergy - currentInCycleEnergy
                            if(cycleCounter!! <= 0){
                                currentInCycleEnergy =
                                    currentTotalEnergy
                            }else{
                                currentInCycleEnergy =
                                    currentTotalEnergy - mInCurrentCycleGlobalState
                            }

                            //in current cycle
                            if (mCurrentCycleState) {
                                mCurrentCycleState = false
                                binding.tvCurrentCycleEnergyComp.text = "${String.format("%.3f", currentInCycleEnergy)} kWh"
                            } else {
                                if (cycleCounter!! <= 0) {
                                    // when the completed cycle is zero that is during the first cycle, in current cycle
                                    //will be the Total energy coming from the firmware
                                    binding.tvCurrentCycleEnergyComp.text = "${String.format("%.3f", currentTotalEnergy)} kWh"

                                    viewModel.saveInCurrentCycleEnergyConsumed(String.format("%.3f", currentTotalEnergy).toFloat())
                                } else {

                                    if(DishWasherModel.dishwasherCurrentRunningState != DishWasherEnumConstants.STOPPED) {
                                        binding.tvCurrentCycleEnergyComp.text = "${String.format("%.3f", currentInCycleEnergy)} kWh"

                                        viewModel.saveInCurrentCycleEnergyConsumed(String.format("%.3f", currentInCycleEnergy).toFloat())
                                    }

                                }
                                if(DishWasherModel.dishwasherCurrentRunningState == DishWasherEnumConstants.STOPPED) {
                                    binding.tvCurrentCycleEnergyComp.text = DISHWASHER_ZERO_ENERGY_DISPLAY


                                }
                            }


                            // in Total
                            if (cycleCounter!! <= 0) {
                                binding.tvTotalEnergyConsumption.text = "$formattedEnergyValue kWh"

                                viewModel.saveTotalEnergyConsumption(formattedEnergyValue.toFloat())
                            } else {
                                binding.tvTotalEnergyConsumption.text = "$formattedEnergyValue kWh"
                                viewModel.saveTotalEnergyConsumption(formattedEnergyValue.toFloat())
                            }
                            // average energy per cycle
                            val averageCurrentEnergyPerCycle =
                                currentTotalEnergy.div(cycleCounter!!)
                            if (averageCurrentEnergyPerCycle.isInfinite()) {
                                binding.tvAverageEnergyPerCycle.text = DISHWASHER_ZERO_ENERGY_DISPLAY
                            }
                            if (cycleCounter!! <= 0) {
                                binding.tvAverageEnergyPerCycle.text = "$formattedEnergyValue kWh"
                                viewModel.saveAverageEnergyPerCycle(formattedEnergyValue.toFloat())
                            } else {

                                binding.tvAverageEnergyPerCycle.text = "${String.format("%.3f", averageCurrentEnergyPerCycle)} kWh"
                                viewModel.saveAverageEnergyPerCycle(String.format("%.3f", averageCurrentEnergyPerCycle)
                                    .toFloat())

                            }
                        } else {
                            resetDishwasherToZeroState()
                        }
                    } else {
                        resetDishwasherToZeroState()
                    }

                }


        })
        //read energyValue from Dishwasher for Error State
        viewModel.error.observe(viewLifecycleOwner, Observer { error->
            if (error != null) {
                println("Error :${error.message}")
                Timber.tag(TAG).e("Error :${error.message}")
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        /*Toast.makeText(
                            requireContext(),
                            "Error Occurred:${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()*/
                        CustomToastManager.show(
                            requireContext(),
                            "Error Occurred:${error.message}",
                            5000
                        )
                    }
                }

            }
        })

        viewModel.energyMeasurementInResumeOrPauseState.observe(viewLifecycleOwner, Observer {
            if (isAdded && !requireActivity().isFinishing) {
                println("insideOnReadEnergy")
                val energyValue = it?.energy
                println("energyValue ${energyValue}")
                if (null != energyValue) {
                    val str = (energyValue?.toDouble())?.div(MEGAWATTS_TO_KILOWATTS_FACTOR)
                    val formattedEnergyValue = String.format("%.3f", str)
                    if (formattedEnergyValue.isNotEmpty() || formattedEnergyValue.isNotBlank()) {
                        var previousCycle = DISHWASHER_ZERO_ENERGY_KWH
                        viewModel.getInCurrentCycleEnergyConsumed().observe(viewLifecycleOwner,
                            Observer {
                                previousCycle =
                                    it
                            })
                        viewModel.getTotalEnergyConsumption()
                            .observe(viewLifecycleOwner) { totalEnergy ->
                                currentTotalEnergy = totalEnergy
                            }
                        currentInCycleEnergy =
                            (formattedEnergyValue.toFloat() - currentTotalEnergy) + previousCycle

                        //in current cycle
                        var completedCount = 0
                        viewModel.getCompletedCycleCount().observe(viewLifecycleOwner,
                            Observer {
                                completedCount = it
                            })
                        if (completedCount <= 0) {
                            // when the completed cycle is zero that is during the first cycle, in current cycle
                            //will be the Total energy coming from the firmware
                            println("completed 1st Cycle ${currentTotalEnergy}")
                            binding.tvCurrentCycleEnergyComp.text = "${String.format("%.3f", currentTotalEnergy)} kWh"
                            println("currentInCycleEnergyInDishWasher2 ${currentInCycleEnergy}")

                            viewModel.saveInCurrentCycleEnergyConsumed(
                                String.format(
                                    "%.3f",
                                    currentTotalEnergy
                                ).toFloat()
                            )
                        } else {
                            println("currentInCycleEnergyInDishWasher3 ${currentInCycleEnergy}")
                            if(DishWasherModel.dishwasherCurrentRunningState != DishWasherEnumConstants.STOPPED){
                                binding.tvCurrentCycleEnergyComp.text = "${String.format("%.3f", currentInCycleEnergy)} kWh"

                                viewModel.saveInCurrentCycleEnergyConsumed(
                                    String.format(
                                        "%.3f",
                                        currentInCycleEnergy
                                    ).toFloat()
                                )
                            }

                            if(DishWasherModel.dishwasherCurrentRunningState == DishWasherEnumConstants.STOPPED) {
                                binding.tvCurrentCycleEnergyComp.text = DISHWASHER_ZERO_ENERGY_DISPLAY
                            }

                        }

                        // in Total
                        if (cycleCounter!! <= 0) {
                            binding.tvTotalEnergyConsumption.text = "$formattedEnergyValue kWh"

                            viewModel.saveTotalEnergyConsumption(formattedEnergyValue.toFloat())
                        } else {
                            binding.tvTotalEnergyConsumption.text = "$formattedEnergyValue kWh"

                            binding.tvAverageEnergyPerCycle.text = "${String.format("%.3f", formattedEnergyValue.toFloat().div(cycleCounter!!))} kWh"

                            viewModel.saveTotalEnergyConsumption(formattedEnergyValue.toFloat())
                        }
                        // average energy per cycle
                        val averageCurrentEnergyPerCycle =
                            currentTotalEnergy.div(cycleCounter!!)
                        if (averageCurrentEnergyPerCycle.isInfinite()) {
                            binding.tvAverageEnergyPerCycle.text = DISHWASHER_ZERO_ENERGY_DISPLAY
                        }
                        if (cycleCounter!! <= 0) {
                            binding.tvAverageEnergyPerCycle.text = "$formattedEnergyValue kWh"

                            viewModel.saveAverageEnergyPerCycle(formattedEnergyValue.toFloat())
                        } else {
                            binding.tvAverageEnergyPerCycle.text = "${String.format("%.3f", averageCurrentEnergyPerCycle)} kWh"



                            viewModel.saveAverageEnergyPerCycle(
                                String.format("%.3f", averageCurrentEnergyPerCycle)
                                    .toFloat()
                            )
                        }
                    } else {
                        resetDishwasherToZeroState()
                    }
                } else {
                    resetDishwasherToZeroState()
                }

            }

        })
        viewModel.errorInResumeOrPauseState.observe(viewLifecycleOwner, Observer {
            if (it != null) {
                println("Error :${it.message}")
                Timber.tag(TAG).e("Error :${it.message}")
                if (isAdded && !requireActivity().isFinishing) {
                    /*Toast.makeText(
                        requireContext(),
                        "Error Occurred:${it.message}",
                        Toast.LENGTH_SHORT
                    ).show()*/
                    CustomToastManager.show(
                        requireContext(),
                        "Error Occurred:${it.message}",
                        5000
                    )
                }


            }
        })

        //Pause DishwasherSuccess
        viewModel.pauseDishwasherSuccess.observe(viewLifecycleOwner, Observer {
            imageDishwasherPause()
        })
        //Pause DishwasherError
        viewModel.pauseDishwasherError.observe(viewLifecycleOwner, Observer {
            Timber.tag(TAG).e("onError : $it")
        })


        //resume Dishwasher Success
        viewModel.resumeDishwasherSuccess.observe(viewLifecycleOwner, Observer {
            if (isAdded) {
                imageDishwasherResume()
                    var state : DishWasherEnumConstants = DishWasherEnumConstants.EMPTY
                    viewModel.getAppliedCycleStates().observe(viewLifecycleOwner, Observer {
                        if (it != null) {
                            state = it
                        }
                    })
                    if (state == DishWasherEnumConstants.RESUMED) {
                        var completedCount = 0
                        viewModel.getCompletedCycleCount().observe(viewLifecycleOwner, Observer {
                            completedCount = it
                        })
                        if (completedCount <= 0) {
                            viewModel.getInCurrentCycleEnergyConsumed()
                                .observe(viewLifecycleOwner,
                                    Observer {
                                        currentTotalEnergy =
                                            it
                                    })

                            println("justDishwasherInResume $currentTotalEnergy")

                        } else {

                            viewModel.getTotalEnergyConsumption().observe(viewLifecycleOwner) { totalEnergy ->
                                currentTotalEnergy = totalEnergy
                            }

                        }
                        scope.launch {
                            if (isAdded) {
                                scope.launch(Dispatchers.Main) {
                                    viewModel.getCompletedCycleProgressBar().observe(viewLifecycleOwner,
                                        Observer {
                                            progress =
                                                it
                                            updateMatterProgressBar()
                                        })

                                }
                            }

                            viewModel.readEnergyWhenInPauseOrResumeState(context)

                        }
                    }

                }

        })
        //resume Dishwasher Error
        viewModel.resumeDishwasherError.observe(viewLifecycleOwner, Observer {
            Timber.tag(TAG).e("onError : $it")
        })

        viewModel.stopDishwasherSuccess.observe(viewLifecycleOwner, Observer {
            imageDishwasherOff()
        })

        viewModel.stopDishwasherError.observe(viewLifecycleOwner, Observer {
            Timber.tag(TAG).e("onError : $it")
        })

        viewModel.startDishwasherSuccess.observe(viewLifecycleOwner, Observer {
            imageDishwasherOn()
        })
        viewModel.startDishwasherError.observe(viewLifecycleOwner, Observer {
            Timber.tag(TAG).e("onError : $it")
        })

    }


    private fun showAlertDialogForOtherOperations() {
        val dialog = AlertDialog.Builder(requireContext()).setTitle(getString(R.string.alert))
            .setMessage(getString(R.string.matter_dishwasher_navigating_back))
            .setPositiveButton("OK") { _, _ ->
                // Explicitly pause and navigate back
                DishWasherModel.dishwasherCurrentRunningState = DishWasherEnumConstants.PAUSED
                viewModel.saveAppliedCycleStates(DishWasherEnumConstants.PAUSED)
                binding.btnPause.performClick()
                requireActivity().supportFragmentManager.popBackStack()
            }.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.create()

        dialog.show()
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (isAdded) {
                if (requireActivity().supportFragmentManager.backStackEntryCount > 0) {
                    handleDishwasherBackNavigationUI()
                } else {
                    FragmentUtils.getHost(
                        this@MatterDishwasherFragment,
                        MatterLightFragment.CallBackHandler::class.java
                    ).onBackHandler()
                }
            }
        }
    }

    fun handleDishwasherBackNavigationUI() {
        IS_BACK_BUTTON_CLICKED = true
        if (DishWasherModel.dishwasherCurrentRunningState == DishWasherEnumConstants.STARTED || DishWasherModel.dishwasherCurrentRunningState == DishWasherEnumConstants.RESUMED) {
            showAlertDialogForOtherOperations()
        } else {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    fun showAlertDialogForPausedState() {
        val dialog = AlertDialog.Builder(requireContext()).setTitle(getString(R.string.alert))
            .setMessage(getString(R.string.matter_dishwasher_navigating_back))
            .setPositiveButton("OK") { _, _ ->
                // Navigate back without explicitly pausing
                DishWasherModel.dishwasherCurrentRunningState = DishWasherEnumConstants.PAUSED
                viewModel.saveAppliedCycleStates(DishWasherEnumConstants.PAUSED)
                requireActivity().supportFragmentManager.popBackStack()
            }.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.create()

        dialog.show()
    }


    private fun updateCompletedCycleTextCounter() {
        val htmlString =
            "<font color='#7A7878'>${context?.getString(R.string.matter_completed_cycle)}${" "}</font><b><font color='#000000'>$cycleCounter</font></b>"
        // Set the HTML string to the TextView
        binding.tvCompletedCycleText.text = Html.fromHtml(htmlString, Html.FROM_HTML_MODE_LEGACY)
    }

    private fun resumeDishwasherTimer() {
        isTimerRunning = true
        timer = object : CountDownTimer(timeLeftInMillis, COUNTDOWN_INTERVAL) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateCountDownTimerText()
                updateMatterProgressBar()
                viewModel.saveTimeLeftFormatted(timeLeftInMillis)
            }

            override fun onFinish() {
                updateCompletedCycleTextCounter()
                binding.btnOff.performClick()
                viewModel.saveTimeLeftFormatted(timeLeftInMillis)
            }
        }.start()

        binding.btnPause.text = getString(R.string.matter_dishwasher_pause)

    }


    private fun imageDishwasherResume() {
        if (isAdded) {
            requireActivity().runOnUiThread {
                binding.btnMatterDeviceState.setImageResource(R.drawable.dishwasher_state_on)
                binding.btnMatterDeviceState.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.silabs_redtheme_primary_color),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                binding.txtClusterStatus.text = requireContext().getString(R.string.blinky_tb_on)
            }
        }
    }

    private fun updateCountDownTimerText() {
        val minutes = (timeLeftInMillis / COUNTDOWN_INTERVAL) / 60
        val seconds = (timeLeftInMillis / COUNTDOWN_INTERVAL) % 60

        val timeLeftFormatted = String.format("%02d:%02d", minutes, seconds)
        val htmlString =
            "<font color='#7A7878'>${context?.getString(R.string.matter_remaining_time)}${" "}</font><b><font color='#000000'>$timeLeftFormatted</font></b><font color='#7A7878'> ${
                context?.getString(R.string.matter_min)
            }</font>"
        // Set the HTML string to the TextView
        binding.progressMatterText.text = Html.fromHtml(htmlString, Html.FROM_HTML_MODE_LEGACY)

    }

    private fun pauseDishwasherTimer() {
        if (!isTimerRunning) return

        timer?.cancel()
        isTimerRunning = false
        //isPaused = true
        binding.btnPause.text = getString(R.string.matter_resume)
    }

    private fun stopDishwasherTimer() {
        timer?.cancel()
        timeLeftInMillis = INITIAL_TIME_LEFT_MILLIS_LONG // Reset to 10 minutes
        updateCountDownTimerText()
        binding.matterProgressBar.progress = 0
        isTimerRunning = false
    }

    private fun startDishwasherTimer() {
        if (isTimerRunning) return

        timer = object : CountDownTimer(timeLeftInMillis, COUNTDOWN_INTERVAL) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateCountDownTimerText()
                updateMatterProgressBar()
                viewModel.saveTimeLeftFormatted(timeLeftInMillis)
            }

            override fun onFinish() {
                updateCompletedCycleTextCounter()
                binding.btnOff.performClick()
                viewModel.saveTimeLeftFormatted(timeLeftInMillis)
            }
        }.start()

        isTimerRunning = true
    }

    private fun updateMatterProgressBar() {
        progress = ((INITIAL_TIME_LEFT_MILLIS - timeLeftInMillis).toFloat() / INITIAL_TIME_LEFT_MILLIS * 100).toInt()
        viewModel.saveCompletedCycleProgressBar(progress)
        binding.matterProgressBar.progress = progress.toInt()
    }


    private fun imageDishwasherPause() {
        if (isAdded) {
            requireActivity().runOnUiThread {
                binding.btnMatterDeviceState.setImageResource(R.drawable.ic_dishwasher_icon_stateoff)
                binding.btnMatterDeviceState.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.silabs_redtheme_primary_color),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                binding.txtClusterStatus.text =
                    requireContext().getString(R.string.matter_dishwasher_pause)
            }
        }
    }

    private fun imageDishwasherOff() {
        if (isAdded) {
            requireActivity().runOnUiThread {
                binding.btnMatterDeviceState.setImageResource(R.drawable.ic_dishwasher_icon_stateoff)
                binding.btnMatterDeviceState.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.silabs_dark_gray_icon),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                binding.txtClusterStatus.text = requireContext().getString(R.string.blinky_tb_off)
            }
        }
    }

    private fun imageDishwasherOn() {
        if (isAdded) {
            requireActivity().runOnUiThread {
                binding.btnMatterDeviceState.setImageResource(R.drawable.dishwasher_state_on)
                binding.btnMatterDeviceState.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.silabs_redtheme_primary_color),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                binding.txtClusterStatus.text = requireContext().getString(R.string.blinky_tb_on)
            }
        }
    }

    interface CallBackHandler {
        fun onBackHandler()
    }

    companion object {
        private val TAG = Companion::class.java.simpleName.toString()
        const val DISHWASHER_PREF = "DISHWASHER_PREF"
        fun newInstance(): MatterDishwasherFragment = MatterDishwasherFragment()

        const val DISHWASHER_OPT_ENDPOINT = 1
        const val DISHWASHER_ELECTRICAL_POWER_MEASUREMENT_ENDPOINT = 2
        const val TIME_OUT = 900
        var IS_BACK_BUTTON_CLICKED = false

        const val DISHWASHER_ZERO_ENERGY_DISPLAY = "0.000 kWh"
        const val DISHWASHER_ZERO_ENERGY_KWH = 0.000F
        const val MEGAWATTS_TO_KILOWATTS_FACTOR = 1_000_000
        private const val INITIAL_TIME_LEFT_MILLIS = 600000
        private const val INITIAL_TIME_LEFT_MILLIS_LONG = 600000L
        const val COUNTDOWN_INTERVAL = 1000L
    }


    override fun onResume() {
        super.onResume()
        if (IS_BACK_BUTTON_CLICKED) {
            IS_BACK_BUTTON_CLICKED = false
            //whenever the dishwasher is in pause or resume state,Call the firmware again
            //for throwing the reading again
            viewModel.readEnergyWhenInPauseOrResumeState(context)
        }
    }

    private fun restoreRemainingDishwasherTime() {
        var timeLeft = 0L
        viewModel.getTimeLeftFormatted().observe(viewLifecycleOwner, Observer {
            timeLeft = it
        })


        if (timeLeft != 0L) {
            timeLeftInMillis = timeLeft
            updateCountDownTimerText()
        }
    }

    private fun dishwasherPauseCycle() {
        // Retrieve the time left value from SharedPreferences
        scope.launch(Dispatchers.Main) {
            binding.btnPause.text = getString(R.string.matter_resume)
            binding.btnOn.isEnabled = false
            binding.btnOff.isEnabled = true
            binding.btnPause.isEnabled = true
            restoreRemainingDishwasherTime()
            viewModel.getCompletedCycleProgressBar().observe(viewLifecycleOwner, Observer {
                progress = it
                updateMatterProgressBar()

            })

            println("In OnPause() State")
            isPaused = true
            imageDishwasherPause()


            viewModel.getTotalEnergyConsumption().observe(viewLifecycleOwner) { totalEnergy ->
                // Use totalEnergy here
                binding.tvTotalEnergyConsumption.text = "$totalEnergy kWh"
            }

            viewModel.getAverageEnergyPerCycle().observe(viewLifecycleOwner, Observer {
                binding.tvAverageEnergyPerCycle.text = "$it kWh"
            })

            viewModel.getInCurrentCycleEnergyConsumed().observe(viewLifecycleOwner, Observer {
                binding.tvCurrentCycleEnergyComp.text = "$it kWh"


            })

            println("isPausedState ${isPaused}")
        }

    }

    private fun dishwasherResumeCycle() {
        // Retrieve the time left value from SharedPreferences
        scope.launch(Dispatchers.Main) {
            binding.btnPause.text = getString(R.string.matter_dishwasher_pause)
            binding.btnOn.isEnabled = false
            binding.btnOff.isEnabled = true
            binding.btnPause.isEnabled = true
            restoreRemainingDishwasherTime()
            isPaused = false
            viewModel.getTotalEnergyConsumption().observe(viewLifecycleOwner) { totalEnergy ->
                // Use totalEnergy here
                binding.tvTotalEnergyConsumption.text = "$totalEnergy kWh"
            }

            viewModel.getAverageEnergyPerCycle().observe(viewLifecycleOwner, Observer {
                binding.tvAverageEnergyPerCycle.text = "$it kWh"


            })

            viewModel.getInCurrentCycleEnergyConsumed().observe(viewLifecycleOwner, Observer {
                binding.tvCurrentCycleEnergyComp.text = "$it kWh"
            })

            println("In OnResume() State")
            imageDishwasherResume()
        }


    }

    private fun dishwasherStopCycle() {
        scope.launch(Dispatchers.Main) {
            isPaused = false
            binding.btnOn.isEnabled = true
            binding.btnOff.isEnabled = false
            binding.btnPause.isEnabled = false
            imageDishwasherOff()
            stopDishwasherTimer()
            viewModel.getTotalEnergyConsumption().observe(viewLifecycleOwner) { totalEnergy ->
                // Use totalEnergy here
                binding.tvTotalEnergyConsumption.text ="$totalEnergy kWh"
            }

            viewModel.getAverageEnergyPerCycle().observe(viewLifecycleOwner, Observer {
                binding.tvAverageEnergyPerCycle.text = "$it kWh"
            })
            viewModel.getInCurrentCycleEnergyConsumed().observe(viewLifecycleOwner, Observer {
                binding.tvCurrentCycleEnergyComp.text = "$it kWh"
            })

        }

    }

    private fun dishwasherStartCycle() {
        scope.launch(Dispatchers.Main) {
            isPaused = false
            binding.btnOff.isEnabled = true
            binding.btnPause.isEnabled = true
            binding.btnOn.isEnabled = false
            resetDishwasherToZeroState()
            imageDishwasherOn()
        }

    }

    private fun resetDishwasherToZeroState() {
        binding.tvTotalEnergyConsumption.text = DISHWASHER_ZERO_ENERGY_DISPLAY
        binding.tvAverageEnergyPerCycle.text = DISHWASHER_ZERO_ENERGY_DISPLAY
        binding.tvCurrentCycleEnergyComp.text = DISHWASHER_ZERO_ENERGY_DISPLAY
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (timer != null) {
            timer?.cancel()
        }
        if (scope != null) {
            scope.cancel()
        }
    }


}