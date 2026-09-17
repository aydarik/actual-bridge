package de.gumerbaev.actual.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import de.gumerbaev.actual.R
import de.gumerbaev.actual.databinding.FragmentSettingsBinding
import de.gumerbaev.actual.model.ParsingRule
import de.gumerbaev.actual.service.ActualNotificationListenerService
import de.gumerbaev.actual.ui.adapter.RulesAdapter
import de.gumerbaev.actual.util.LocationHelper

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var rulesAdapter: RulesAdapter
    private val mainActivity: MainActivity
        get() = requireActivity() as MainActivity

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

        setupRecyclerView()
        loadSettings()
        setupListeners()
        loadRules()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
    }

    private fun setupRecyclerView() {
        rulesAdapter = RulesAdapter(
            rules = mutableListOf(),
            onToggleEnabled = { rule, isEnabled ->
                rule.enabled = isEnabled
                mainActivity.prefs.updateRule(rule)
                countRules()
            },
            onEdit = { rule ->
                mainActivity.showEditRuleDialog(rule) { loadRules() }
            },
            onDelete = { rule ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Rule")
                    .setMessage("Are you sure you want to delete '${rule.name}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        mainActivity.prefs.deleteRule(rule.id)
                        loadRules()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.rvRules.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRules.adapter = rulesAdapter
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

        // Switches
        binding.switchAutoSend.setOnCheckedChangeListener { _, isChecked ->
            mainActivity.prefs.autoSend = isChecked
            updateAutoSendVisibility(isChecked)
        }

        binding.switchDismissNotification.setOnCheckedChangeListener { buttonView, isChecked ->
            mainActivity.prefs.dismissOriginalNotification = isChecked
            if (isChecked && !mainActivity.hasNotificationPermission()) {
                mainActivity.requestNotificationPermission(buttonView)
            }
        }

        binding.switchAutoPopup.setOnCheckedChangeListener { buttonView, isChecked ->
            mainActivity.prefs.autoPopup = isChecked
            if (isChecked && !mainActivity.hasNotificationPermission()) {
                mainActivity.requestNotificationPermission(buttonView)
            }
        }

        binding.switchAttachLocation.setOnCheckedChangeListener { _, isChecked ->
            mainActivity.prefs.attachLocation = isChecked
            if (isChecked) {
                if (!LocationHelper.hasLocationPermission(requireContext())) {
                    mainActivity.requestForegroundLocationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                } else if (!LocationHelper.hasBackgroundLocationPermission(requireContext())) {
                    mainActivity.requestBackgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
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

        binding.btnAddRule.setOnClickListener {
            mainActivity.showEditRuleDialog(null) { loadRules() }
        }

        binding.btnTestRules.setOnClickListener {
            mainActivity.showTestParserDialog()
        }
    }

    fun updatePermissionStatuses() {
        if (_binding == null) return

        val hasNotificationAccess = ActualNotificationListenerService.isNotificationServiceEnabled(requireContext())
        if (hasNotificationAccess) {
            binding.tvNotificationStatus.text = "Active - Notifications monitored"
            binding.tvNotificationStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.secondary))
            binding.btnGrantNotification.text = "Granted"
            binding.btnGrantNotification.isEnabled = false
        } else {
            binding.tvNotificationStatus.text = "Disabled - Tap to enable in Settings"
            binding.tvNotificationStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.danger))
            binding.btnGrantNotification.text = "Grant"
            binding.btnGrantNotification.isEnabled = true
        }

        val hasOverlayAccess = Settings.canDrawOverlays(requireContext())
        if (hasOverlayAccess) {
            binding.tvOverlayStatus.text = "Active - Popups will show over other apps"
            binding.tvOverlayStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.secondary))
            binding.btnGrantOverlay.text = "Granted"
            binding.btnGrantOverlay.isEnabled = false
        } else {
            binding.tvOverlayStatus.text = "Disabled - Tap to enable popup overlay"
            binding.tvOverlayStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            binding.btnGrantOverlay.text = "Grant"
            binding.btnGrantOverlay.isEnabled = true
        }
    }

    private fun loadSettings() {
        val prefs = mainActivity.prefs
        binding.etServerUrl.setText(prefs.serverUrl)
        binding.etApiToken.setText(prefs.apiToken)
        binding.switchAttachLocation.isChecked = prefs.attachLocation
        binding.switchAutoPopup.isChecked = prefs.autoPopup
        binding.switchAutoSend.isChecked = prefs.autoSend
        binding.switchDismissNotification.isChecked = prefs.dismissOriginalNotification
        updateAutoSendVisibility(prefs.autoSend)
    }

    private fun updateAutoSendVisibility(autoSendEnabled: Boolean) {
        if (autoSendEnabled) {
            binding.layoutAutoPopup.visibility = View.GONE
            binding.switchAutoPopup.isEnabled = false

            binding.layoutDismissNotification.visibility = View.VISIBLE
            binding.switchDismissNotification.isEnabled = true
        } else {
            binding.layoutAutoPopup.visibility = View.VISIBLE
            binding.switchAutoPopup.isEnabled = true

            binding.layoutDismissNotification.visibility = View.GONE
            binding.switchDismissNotification.isEnabled = false
        }
    }

    fun loadRules() {
        if (_binding == null) return
        val rules = mainActivity.prefs.getRules()
        rulesAdapter.updateData(rules)
        countRules(rules)
    }

    fun countRules(rules: List<ParsingRule>? = null) {
        if (_binding == null) return
        val rules = rules ?: mainActivity.prefs.getRules()
        val activeCount = rules.count { it.enabled }
        binding.tvRulesCount.text = "$activeCount active of ${rules.size} rules"
    }

    fun onLocationPermissionDenied() {
        if (_binding != null) {
            binding.switchAttachLocation.isChecked = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}