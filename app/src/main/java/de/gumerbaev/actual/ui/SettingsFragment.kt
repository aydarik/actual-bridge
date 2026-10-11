package de.gumerbaev.actual.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.gumerbaev.actual.BuildConfig
import de.gumerbaev.actual.R
import de.gumerbaev.actual.data.BackupManager
import de.gumerbaev.actual.databinding.DialogBackupBinding
import de.gumerbaev.actual.databinding.DialogRestoreBinding
import de.gumerbaev.actual.databinding.FragmentSettingsBinding
import de.gumerbaev.actual.model.BackupData
import de.gumerbaev.actual.network.UpdateChecker
import de.gumerbaev.actual.service.ActualNotificationListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val mainActivity: MainActivity
        get() = requireActivity() as MainActivity

    private val backupManager by lazy { BackupManager(mainActivity.prefs) }

    private var pendingBackupData: BackupData? = null

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { saveBackupToUri(it) }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { loadBackupFromUri(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadSettings()
        setupLanguageSelector()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
    }

    override fun onPause() {
        super.onPause()
        saveServerSettings()
    }

    private fun setupListeners() {
        // System Permissions
        binding.btnGrantNotification.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }

        binding.btnGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${requireContext().packageName}".toUri()
            )
            startActivity(intent)
        }

        // Action Buttons
        binding.btnTestConnection.setOnClickListener {
            binding.btnTestConnection.isEnabled = false
            val url = binding.etServerUrl.text?.toString()?.trim() ?: ""
            val token = binding.etApiToken.text?.toString()?.trim() ?: ""

            mainActivity.testConnection(url, token) {
                if (_binding != null) {
                    binding.btnTestConnection.isEnabled = true
                }
            }
        }

        // Backup & Restore
        binding.btnCreateBackup.setOnClickListener {
            showCreateBackupDialog()
        }

        binding.btnRestoreBackup.setOnClickListener {
            importBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
        }

        // Version & Updates
        binding.btnCheckUpdate.setOnClickListener {
            checkForAppUpdates()
        }

        binding.btnViewReleases.setOnClickListener {
            openUrl(UpdateChecker.RELEASES_WEB_URL)
        }
    }

    fun updatePermissionStatuses() {
        if (_binding == null) return

        val hasNotificationAccess = ActualNotificationListenerService.isNotificationServiceEnabled(requireContext())
        if (hasNotificationAccess) {
            binding.tvNotificationStatus.text = getString(R.string.settings_notif_active)
            binding.tvNotificationStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.secondary))
            binding.btnGrantNotification.text = getString(R.string.granted)
            binding.btnGrantNotification.isEnabled = false
        } else {
            binding.tvNotificationStatus.text = getString(R.string.settings_notif_disabled)
            binding.tvNotificationStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.danger))
            binding.btnGrantNotification.text = getString(R.string.grant)
            binding.btnGrantNotification.isEnabled = true
        }

        val hasOverlayAccess = Settings.canDrawOverlays(requireContext())
        if (hasOverlayAccess) {
            binding.tvOverlayStatus.text = getString(R.string.settings_overlay_active)
            binding.tvOverlayStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.secondary))
            binding.btnGrantOverlay.text = getString(R.string.granted)
            binding.btnGrantOverlay.isEnabled = false
        } else {
            binding.tvOverlayStatus.text = getString(R.string.settings_overlay_disabled)
            binding.tvOverlayStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            binding.btnGrantOverlay.text = getString(R.string.grant)
            binding.btnGrantOverlay.isEnabled = true
        }
    }

    private fun setupLanguageSelector() {
        val codes = resources.getStringArray(R.array.language_codes)
        val labels = listOf(
            getString(R.string.language_system),
            "English",
            "Deutsch",
            "Русский"
        )

        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_spinner_selected,
            labels
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerLanguage.adapter = adapter

        val current = AppCompatDelegate.getApplicationLocales()
        val currentCode = if (current.isEmpty) "" else current[0]?.language.orEmpty()
        val currentIndex = codes.indexOf(currentCode).takeIf { it >= 0 } ?: 0
        binding.spinnerLanguage.setSelection(currentIndex, false)

        binding.spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCode = codes[position]
                val activeLocales = AppCompatDelegate.getApplicationLocales()
                val activeCode = if (activeLocales.isEmpty) "" else activeLocales[0]?.language.orEmpty()
                if (selectedCode != activeCode) {
                    // An empty list restores the system language (English is the resource fallback).
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(selectedCode)
                    )
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    fun loadSettings() {
        val prefs = mainActivity.prefs
        binding.etServerUrl.setText(prefs.serverUrl)
        binding.etApiToken.setText(prefs.apiToken)
        binding.tvAppVersion.text = getString(R.string.settings_app_version, BuildConfig.VERSION_NAME)
        updateBackupStatus()
    }

    private fun updateBackupStatus() {
        if (_binding == null) return
        val lastBackup = mainActivity.prefs.lastBackupTimestamp
        if (lastBackup > 0L) {
            val formatted = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(lastBackup))
            binding.tvLastBackup.text = getString(R.string.settings_last_backup, formatted)
        } else {
            binding.tvLastBackup.text = getString(R.string.settings_no_backup_yet)
        }
    }

    private fun showCreateBackupDialog() {
        saveServerSettings()

        val dialogBinding = DialogBackupBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        val rulesCount = mainActivity.prefs.getRules().size
        val txCount = mainActivity.prefs.getHistory().size

        dialogBinding.tvBackupSettingsDesc.text = getString(R.string.backup_include_settings_desc, rulesCount)
        dialogBinding.tvBackupTransactionsDesc.text = getString(R.string.backup_include_transactions_desc, txCount)

        fun updateExportButtonState() {
            dialogBinding.btnConfirmBackup.isEnabled =
                dialogBinding.cbBackupSettings.isChecked || dialogBinding.cbBackupTransactions.isChecked
        }

        dialogBinding.cbBackupSettings.setOnCheckedChangeListener { _, _ -> updateExportButtonState() }
        dialogBinding.cbBackupTransactions.setOnCheckedChangeListener { _, _ -> updateExportButtonState() }
        updateExportButtonState()

        dialogBinding.btnCancelBackup.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirmBackup.setOnClickListener {
            val includeSettings = dialogBinding.cbBackupSettings.isChecked
            val includeTransactions = dialogBinding.cbBackupTransactions.isChecked

            pendingBackupData = backupManager.createBackup(
                includeSettings = includeSettings,
                includeTransactions = includeTransactions,
                appVersion = BuildConfig.VERSION_NAME
            )

            dialog.dismiss()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "actual_bridge_backup_$timestamp.json"
            exportBackupLauncher.launch(fileName)
        }

        dialog.show()
    }

    private fun saveBackupToUri(uri: Uri) {
        val dataToSave = pendingBackupData ?: backupManager.createBackup(
            includeSettings = true,
            includeTransactions = true,
            appVersion = BuildConfig.VERSION_NAME
        )
        pendingBackupData = null

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    backupManager.writeBackupToUri(requireContext().contentResolver, uri, dataToSave)
                }
                if (_binding == null) return@launch

                mainActivity.prefs.lastBackupTimestamp = System.currentTimeMillis()
                updateBackupStatus()
                Toast.makeText(requireContext(), R.string.backup_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                if (_binding == null) return@launch
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.restore_error_title)
                    .setMessage(getString(R.string.backup_error, e.localizedMessage ?: getString(R.string.unknown_error)))
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
        }
    }

    private fun loadBackupFromUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val backupData = try {
                withContext(Dispatchers.IO) {
                    backupManager.readBackupFromUri(requireContext().contentResolver, uri)
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.restore_error_title)
                    .setMessage(getString(R.string.restore_error_invalid_file, e.localizedMessage ?: getString(R.string.unknown_error)))
                    .setPositiveButton(R.string.ok, null)
                    .show()
                return@launch
            }

            if (_binding == null) return@launch
            showRestoreBackupDialog(backupData)
        }
    }

    private fun showRestoreBackupDialog(backupData: BackupData) {
        val dialogBinding = DialogRestoreBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        val formattedDate = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(backupData.timestamp))
        dialogBinding.tvRestoreCreated.text = getString(R.string.restore_info_created, formattedDate)
        if (!backupData.appVersion.isNullOrBlank()) {
            dialogBinding.tvRestoreVersion.visibility = View.VISIBLE
            dialogBinding.tvRestoreVersion.text = getString(R.string.restore_info_version, backupData.appVersion)
        } else {
            dialogBinding.tvRestoreVersion.visibility = View.GONE
        }

        val hasSettings = backupData.settings != null
        if (hasSettings) {
            dialogBinding.layoutRestoreSettings.visibility = View.VISIBLE
            dialogBinding.cbRestoreSettings.isChecked = true
            val rulesCount = backupData.settings.parsingRules.size
            dialogBinding.tvRestoreSettingsDesc.text = getString(R.string.restore_settings_desc, rulesCount)
        } else {
            dialogBinding.layoutRestoreSettings.visibility = View.GONE
        }

        val hasTransactions = !backupData.transactions.isNullOrEmpty()
        if (hasTransactions) {
            dialogBinding.layoutRestoreTransactions.visibility = View.VISIBLE
            dialogBinding.cbRestoreTransactions.isChecked = true
            val txCount = backupData.transactions.size
            dialogBinding.tvRestoreTransactionsDesc.text = getString(R.string.restore_transactions_desc, txCount)
            dialogBinding.layoutMergeTransactions.visibility = View.VISIBLE
            dialogBinding.cbMergeTransactions.isChecked = true
        } else {
            dialogBinding.layoutRestoreTransactions.visibility = View.GONE
        }

        fun updateRestoreButtonState() {
            val anyChecked = (hasSettings && dialogBinding.cbRestoreSettings.isChecked) ||
                    (hasTransactions && dialogBinding.cbRestoreTransactions.isChecked)
            dialogBinding.btnConfirmRestore.isEnabled = anyChecked
        }

        dialogBinding.cbRestoreSettings.setOnCheckedChangeListener { _, _ -> updateRestoreButtonState() }
        dialogBinding.cbRestoreTransactions.setOnCheckedChangeListener { _, isChecked ->
            dialogBinding.layoutMergeTransactions.visibility = if (isChecked) View.VISIBLE else View.GONE
            dialogBinding.cbMergeTransactions.isEnabled = isChecked
            updateRestoreButtonState()
        }

        updateRestoreButtonState()

        dialogBinding.btnCancelRestore.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirmRestore.setOnClickListener {
            val restoreSettings = hasSettings && dialogBinding.cbRestoreSettings.isChecked
            val restoreTransactions = hasTransactions && dialogBinding.cbRestoreTransactions.isChecked
            val merge = dialogBinding.cbMergeTransactions.isChecked

            viewLifecycleOwner.lifecycleScope.launch {
                val summary = withContext(Dispatchers.IO) {
                    backupManager.restoreBackup(
                        backupData = backupData,
                        restoreSettings = restoreSettings,
                        restoreTransactions = restoreTransactions,
                        mergeTransactions = merge
                    )
                }

                dialog.dismiss()
                if (_binding == null) return@launch

                mainActivity.notifyDataRestored()

                val lines = mutableListOf<String>()
                if (summary.settingsRestored) {
                    lines.add(getString(R.string.restore_summary_settings, summary.rulesCount))
                }
                if (summary.transactionsRestored) {
                    lines.add(getString(R.string.restore_summary_transactions, summary.transactionsCount))
                }

                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.restore_success_title)
                    .setMessage(getString(R.string.restore_success_msg, lines.joinToString("\n")))
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
        }

        dialog.show()
    }

    private fun checkForAppUpdates() {
        binding.btnCheckUpdate.isEnabled = false
        binding.btnCheckUpdate.text = getString(R.string.settings_checking_updates)
        binding.progressCheckUpdate.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val result = UpdateChecker.checkForUpdates(BuildConfig.VERSION_NAME)
            if (_binding == null) return@launch

            binding.progressCheckUpdate.visibility = View.GONE
            binding.btnCheckUpdate.isEnabled = true
            binding.btnCheckUpdate.text = getString(R.string.settings_check_update)

            handleUpdateResult(result)
        }
    }

    private fun handleUpdateResult(result: UpdateChecker.CheckResult) {
        val context = context ?: return
        when (result) {
            is UpdateChecker.CheckResult.NewUpdate -> {
                val messageBuilder = StringBuilder()
                messageBuilder.append(
                    getString(R.string.update_available_msg, result.currentVersion, result.release.tagName)
                )
                if (!result.release.body.isNullOrBlank()) {
                    messageBuilder.append(
                        getString(R.string.update_changelog_header, result.release.body.trim())
                    )
                }

                val hasApk = result.release.apkDownloadUrl != null
                AlertDialog.Builder(context)
                    .setTitle(R.string.update_available_title)
                    .setMessage(messageBuilder.toString())
                    .setPositiveButton(if (hasApk) R.string.update_download_apk else R.string.update_open_release) { _, _ ->
                        val targetUrl = result.release.apkDownloadUrl ?: result.release.htmlUrl.ifBlank { UpdateChecker.RELEASES_WEB_URL }
                        openUrl(targetUrl)
                    }
                    .setNeutralButton(R.string.settings_view_releases) { _, _ ->
                        openUrl(UpdateChecker.RELEASES_WEB_URL)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            is UpdateChecker.CheckResult.UpToDate -> {
                AlertDialog.Builder(context)
                    .setTitle(R.string.update_latest_title)
                    .setMessage(getString(R.string.update_latest_msg, result.currentVersion))
                    .setPositiveButton(R.string.ok, null)
                    .setNeutralButton(R.string.settings_view_releases) { _, _ ->
                        openUrl(UpdateChecker.RELEASES_WEB_URL)
                    }
                    .show()
            }
            is UpdateChecker.CheckResult.Error -> {
                AlertDialog.Builder(context)
                    .setTitle(R.string.update_check_failed_title)
                    .setMessage(getString(R.string.update_check_failed_msg, result.error.asString(context)))
                    .setPositiveButton(R.string.settings_view_releases) { _, _ ->
                        openUrl(UpdateChecker.RELEASES_WEB_URL)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                e.localizedMessage ?: getString(R.string.unknown_error),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun saveServerSettings() {
        if (_binding != null) {
            val url = binding.etServerUrl.text?.toString()?.trim() ?: ""
            val token = binding.etApiToken.text?.toString()?.trim() ?: ""
            if (url.isNotEmpty()) {
                mainActivity.prefs.serverUrl = url
            }
            mainActivity.prefs.apiToken = token
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}