package de.gumerbaev.actual.ui

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import de.gumerbaev.actual.databinding.FragmentRulesBinding
import de.gumerbaev.actual.model.ParsingRule
import de.gumerbaev.actual.ui.adapter.RulesAdapter
import de.gumerbaev.actual.util.LocationHelper

class RulesFragment : Fragment() {

    private var _binding: FragmentRulesBinding? = null
    private val binding get() = _binding!!

    private lateinit var rulesAdapter: RulesAdapter
    private val mainActivity: MainActivity
        get() = requireActivity() as MainActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRulesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadAppSettings()
        setupListeners()
        loadRules()
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
        binding.btnAddRule.setOnClickListener {
            mainActivity.showEditRuleDialog(null) { loadRules() }
        }

        binding.btnTestRules.setOnClickListener {
            mainActivity.showTestParserDialog()
        }
    }

    private fun loadAppSettings() {
        val prefs = mainActivity.prefs
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
        val rulesList = rules ?: mainActivity.prefs.getRules()
        val activeCount = rulesList.count { it.enabled }
        binding.tvRulesCount.text = "$activeCount active of ${rulesList.size} rules"
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
