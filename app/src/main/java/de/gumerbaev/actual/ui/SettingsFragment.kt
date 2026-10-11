package de.gumerbaev.actual.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import de.gumerbaev.actual.R
import de.gumerbaev.actual.databinding.FragmentSettingsBinding
import de.gumerbaev.actual.service.ActualNotificationListenerService

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

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

        loadSettings()
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