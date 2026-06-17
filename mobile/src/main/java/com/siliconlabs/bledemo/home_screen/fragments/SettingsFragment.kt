package com.siliconlabs.bledemo.home_screen.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.siliconlabs.bledemo.BuildConfig
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.databinding.FragmentSettingsBinding
import com.siliconlabs.bledemo.home_screen.dialogs.DeviceInformationDialog
import com.siliconlabs.bledemo.home_screen.dialogs.ScanTimeoutHelpDialog
import com.siliconlabs.bledemo.home_screen.utils.SettingsStorage

class SettingsFragment : Fragment() {

    private lateinit var _binding: FragmentSettingsBinding
    private var settingsStorage: SettingsStorage? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater)
        return _binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.action_settings)
        context?.let { settingsStorage = SettingsStorage(it) }
        initTextsAndLinks()
        setupUiListeners()
    }

    private fun initTextsAndLinks() {
        _binding.apply {
            val scanTimeoutAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.scan_timeout_options,
                R.layout.spinner_scan_timeout_text_item
            ).also { adapter ->
                adapter.setDropDownViewResource(R.layout.spinner_scan_timeout_dropdown_item)
            }
            spinnerScanTimeoutSetting.adapter = scanTimeoutAdapter
            spinnerScanTimeoutSetting.setSelection(getScanTimeoutSelection())
            btnReportIssue.linkToWebpage(LINK_REPORT_ISSUE)

            silabsProductsWireless.linkToWebpage(LINK_MORE_INFO)
            silabsSupport.linkToWebpage(LINK_SUPPORT)
            githubSiliconlabsEfrconnect.linkToWebpage(LINK_SOURCECODE)
            docsSilabsBluetoothLatest.linkToWebpage(LINK_DOCUMENTATION)
            docsSilabsReleaseNotes.linkToWebpage(LINK_RELEASE_NOTES)
            usersGuideEfrconnect.linkToWebpage(LINK_USERS_GUIDE)
            helpTextPlaystore.linkToWebpage(LINK_GOOGLE_PLAY_STORE)

            dialogHelpVersionText.text = getString(R.string.version_text, BuildConfig.VERSION_NAME)
        }
    }

    private fun setupUiListeners() {
        _binding.apply {
            scanTimeoutHelpIcon.setOnClickListener {
                ScanTimeoutHelpDialog().show(childFragmentManager, "scan_timeout_help_dialog")
            }

            btnDeviceInfo.setOnClickListener {
                DeviceInformationDialog().show(childFragmentManager, "Device_Information_Dialog")
            }
            spinnerScanTimeoutSetting.onItemSelectedListener = onScanSettingSelected
        }
    }

    private fun View.linkToWebpage(url: String) {
        setOnClickListener {
            val uri = Uri.parse("https://$url")
            val launchBrowser = Intent(Intent.ACTION_VIEW, uri)
            startActivity(launchBrowser)
        }
    }

    private fun getScanTimeoutSelection(): Int {
        return when (settingsStorage?.loadScanSetting()) {
            SettingsStorage.SCAN_SETTING_SECONDS -> 0
            SettingsStorage.SCAN_SETTING_MINUTE -> 1
            SettingsStorage.SCAN_SETTING_TWO_MINUTES -> 2
            SettingsStorage.SCAN_SETTING_FIVE_MINUTES -> 3
            SettingsStorage.SCAN_SETTING_TEN_MINUTES -> 4
            SettingsStorage.SCAN_SETTING_INFINITE -> 5
            else -> 5
        }
    }

    private fun onScanSettingChanged(selectedPosition: Int) {
        settingsStorage?.saveScanSetting(
            when (selectedPosition) {
                0 -> SettingsStorage.SCAN_SETTING_SECONDS
                1 -> SettingsStorage.SCAN_SETTING_MINUTE
                2 -> SettingsStorage.SCAN_SETTING_TWO_MINUTES
                3 -> SettingsStorage.SCAN_SETTING_FIVE_MINUTES
                4 -> SettingsStorage.SCAN_SETTING_TEN_MINUTES
                5 -> SettingsStorage.SCAN_SETTING_INFINITE
                else -> {
                    SettingsStorage.SCAN_SETTING_INFINITE
                }
            }
        )
    }

    private val onScanSettingSelected = object : OnItemSelectedListener {
        override fun onItemSelected(
            parentView: AdapterView<*>,
            selectedItemView: View?,
            position: Int,
            id: Long
        ) {
            onScanSettingChanged(position)
        }

        override fun onNothingSelected(parentView: AdapterView<*>) { /* Nothing to do here */
        }
    }

    companion object {
        private const val LINK_REPORT_ISSUE =
            "github.com/SiliconLabs/SimplicityConnect-android/issues"
        private const val LINK_MORE_INFO = "silabs.com/products/wireless"
        private const val LINK_SOURCECODE = "github.com/SiliconLabs/SimplicityConnect-android"
        private const val LINK_USERS_GUIDE = "docs.silabs.com/mobile-apps/latest/mobile-apps-start/"
        private const val LINK_SUPPORT = "silabs.com/support"
        private const val LINK_RELEASE_NOTES =
            "docs.silabs.com/mobile-apps/latest/mobile-apps-release-notes/"
        private const val LINK_DOCUMENTATION = "docs.silabs.com/bluetooth/latest"
        private const val LINK_GOOGLE_PLAY_STORE =
            "play.google.com/store/apps/developer?id=Silicon+Laboratories"
    }

}