package de.gumerbaev.actual.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
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
        // The adapter ignores filtering so the full list stays visible after a selection.
        binding.actvLanguage.setAdapter(
            object : ArrayAdapter<String>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                labels
            ) {
                override fun getFilter(): Filter = object : Filter() {
                    override fun performFiltering(constraint: CharSequence?) =
                        FilterResults().apply {
                            values = labels
                            count = labels.size
                        }

                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        notifyDataSetChanged()
                    }
                }
            }
        )

        val current = AppCompatDelegate.getApplicationLocales()
        val currentCode = if (current.isEmpty) "" else current[0]?.language.orEmpty()
        val currentIndex = codes.indexOf(currentCode).takeIf { it >= 0 } ?: 0
        binding.actvLanguage.setText(labels[currentIndex], false)

        binding.actvLanguage.setOnItemClickListener { _, _, position, _ ->
            if (position == currentIndex) return@setOnItemClickListener
            // An empty list restores the system language (English is the resource fallback).
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(codes[position])
            )
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