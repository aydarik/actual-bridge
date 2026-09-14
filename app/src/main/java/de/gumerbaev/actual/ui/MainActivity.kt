package de.gumerbaev.actual.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import de.gumerbaev.actual.R
import de.gumerbaev.actual.data.AppPreferences
import de.gumerbaev.actual.databinding.ActivityMainBinding
import de.gumerbaev.actual.databinding.DialogEditRuleBinding
import de.gumerbaev.actual.databinding.DialogTestParserBinding
import de.gumerbaev.actual.model.ActualTransaction
import de.gumerbaev.actual.model.ParsingRule
import de.gumerbaev.actual.model.TransactionRecord
import de.gumerbaev.actual.network.ActualApiClient
import de.gumerbaev.actual.parser.NotificationParser
import de.gumerbaev.actual.service.ActualNotificationListenerService
import de.gumerbaev.actual.ui.adapter.HistoryAdapter
import de.gumerbaev.actual.ui.adapter.RulesAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferences

    private lateinit var rulesAdapter: RulesAdapter
    private lateinit var historyAdapter: HistoryAdapter

    private val transactionUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadHistory()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences(this)

        setupUI()
        setupListeners()
        loadSettings()
        loadRules()
        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
        loadHistory()

        val filter = IntentFilter(ActualNotificationListenerService.ACTION_TRANSACTION_PARSED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(transactionUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(transactionUpdateReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(transactionUpdateReceiver)
        } catch (e: Exception) {
            // Ignored
        }
    }

    private fun setupUI() {
        // Rules RecyclerView
        rulesAdapter = RulesAdapter(
            rules = mutableListOf(),
            onToggleEnabled = { rule, isEnabled ->
                rule.enabled = isEnabled
                prefs.updateRule(rule)
            },
            onEdit = { rule ->
                showEditRuleDialog(rule)
            },
            onDelete = { rule ->
                AlertDialog.Builder(this)
                    .setTitle("Delete Rule")
                    .setMessage("Are you sure you want to delete '${rule.name}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        prefs.deleteRule(rule.id)
                        loadRules()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.rvRules.layoutManager = LinearLayoutManager(this)
        binding.rvRules.adapter = rulesAdapter

        // History RecyclerView
        historyAdapter = HistoryAdapter(
            records = mutableListOf(),
            onResend = { record ->
                val intent = Intent(this, ConfirmTransactionActivity::class.java).apply {
                    putExtra(ConfirmTransactionActivity.EXTRA_TRANSACTION, record.transaction)
                    putExtra(ConfirmTransactionActivity.EXTRA_RECORD_ID, record.id)
                }
                startActivity(intent)
            }
        )
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = historyAdapter
    }

    private fun setupListeners() {
        // Permissions
        binding.btnGrantNotification.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }

        binding.btnGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        // Save Settings
        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
        }

        // Test Connection
        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }

        // Add Rule
        binding.btnAddRule.setOnClickListener {
            showEditRuleDialog(null)
        }

        // Test Rules Dialog
        binding.btnTestRules.setOnClickListener {
            showTestParserDialog()
        }

        // Clear History
        binding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Transaction Log")
                .setMessage("Clear all logged transactions?")
                .setPositiveButton("Clear") { _, _ ->
                    prefs.clearHistory()
                    loadHistory()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updatePermissionStatuses() {
        val hasNotificationAccess = ActualNotificationListenerService.isNotificationServiceEnabled(this)
        if (hasNotificationAccess) {
            binding.tvNotificationStatus.text = "Active - Notifications monitored"
            binding.tvNotificationStatus.setTextColor(ContextCompat.getColor(this, R.color.secondary))
            binding.btnGrantNotification.text = "Granted"
            binding.btnGrantNotification.isEnabled = false
        } else {
            binding.tvNotificationStatus.text = "Disabled - Tap to enable in Settings"
            binding.tvNotificationStatus.setTextColor(ContextCompat.getColor(this, R.color.danger))
            binding.btnGrantNotification.text = "Grant"
            binding.btnGrantNotification.isEnabled = true
        }

        val hasOverlayAccess = Settings.canDrawOverlays(this)
        if (hasOverlayAccess) {
            binding.tvOverlayStatus.text = "Active - Popups will show over other apps"
            binding.tvOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.secondary))
            binding.btnGrantOverlay.text = "Granted"
            binding.btnGrantOverlay.isEnabled = false
        } else {
            binding.tvOverlayStatus.text = "Disabled - Tap to enable popup overlay"
            binding.tvOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.btnGrantOverlay.text = "Grant"
            binding.btnGrantOverlay.isEnabled = true
        }
    }

    private fun loadSettings() {
        binding.etServerUrl.setText(prefs.serverUrl)
        binding.etApiToken.setText(prefs.apiToken)
        binding.switchAttachLocation.isChecked = prefs.attachLocation
        binding.switchAutoPopup.isChecked = prefs.autoPopup
    }

    private fun saveSettings() {
        prefs.serverUrl = binding.etServerUrl.text?.toString()?.trim() ?: ""
        prefs.apiToken = binding.etApiToken.text?.toString()?.trim() ?: ""
        prefs.attachLocation = binding.switchAttachLocation.isChecked
        prefs.autoPopup = binding.switchAutoPopup.isChecked
    }

    private fun testConnection() {
        saveSettings()
        binding.btnTestConnection.isEnabled = false

        lifecycleScope.launch {
            val result = ActualApiClient.testConnection(prefs.serverUrl, prefs.apiToken)
            binding.btnTestConnection.isEnabled = true

            if (result.success) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Connection Successful! (HTTP ${result.httpCode})")
                    .setMessage("The server is reachable and accepted the test request.\n\nResponse:\n${result.responseBody}")
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Connection Failed")
                    .setMessage("Could not connect to ${prefs.serverUrl}.\n\nError:\n${result.errorMessage}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun loadRules() {
        val rules = prefs.getRules()
        rulesAdapter.updateData(rules)
        val activeCount = rules.count { it.enabled }
        binding.tvRulesCount.text = "$activeCount active of ${rules.size} rules"
    }

    private fun loadHistory() {
        val history = prefs.getHistory()
        historyAdapter.updateData(history)
        binding.tvEmptyHistory.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
        binding.rvHistory.visibility = if (history.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showEditRuleDialog(existingRule: ParsingRule?) {
        val dialogBinding = DialogEditRuleBinding.inflate(LayoutInflater.from(this))
        val isEditing = existingRule != null

        dialogBinding.tvDialogTitle.text = if (isEditing) "Edit Parsing Rule" else "New Parsing Rule"

        val types = listOf(ActualTransaction.TYPE_PAYMENT, ActualTransaction.TYPE_DEPOSIT)
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, types)
        dialogBinding.actvDefaultType.setAdapter(typeAdapter)

        if (existingRule != null) {
            dialogBinding.etRuleName.setText(existingRule.name)
            dialogBinding.etTargetPackage.setText(existingRule.targetPackage)
            dialogBinding.etTitleRegex.setText(existingRule.titleRegex)
            dialogBinding.etTextRegex.setText(existingRule.textRegex)
            dialogBinding.etAmountGroup.setText(existingRule.amountGroup)
            dialogBinding.etPayeeGroup.setText(existingRule.payeeGroup)
            dialogBinding.etDefaultAccount.setText(existingRule.account)
            dialogBinding.actvDefaultType.setText(existingRule.defaultType, false)
        } else {
            dialogBinding.etDefaultAccount.setText("")
            dialogBinding.actvDefaultType.setText(ActualTransaction.TYPE_PAYMENT, false)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancelRule.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnSaveRule.setOnClickListener {
            val name = dialogBinding.etRuleName.text?.toString()?.trim() ?: ""
            val textRegex = dialogBinding.etTextRegex.text?.toString()?.trim() ?: ""

            if (name.isBlank()) {
                dialogBinding.etRuleName.error = "Rule name is required"
                return@setOnClickListener
            }
            if (textRegex.isBlank()) {
                dialogBinding.etTextRegex.error = "Text regex is required"
                return@setOnClickListener
            }

            val ruleToSave = (existingRule ?: ParsingRule(name = name)).apply {
                this.name = name
                this.targetPackage = dialogBinding.etTargetPackage.text?.toString()?.trim() ?: "*"
                this.titleRegex = dialogBinding.etTitleRegex.text?.toString()?.trim() ?: ""
                this.textRegex = textRegex
                this.amountGroup = dialogBinding.etAmountGroup.text?.toString()?.trim() ?: "amount"
                this.payeeGroup = dialogBinding.etPayeeGroup.text?.toString()?.trim() ?: "payee"
                this.account = dialogBinding.etDefaultAccount.text?.toString()?.trim() ?: ""
                this.defaultType = dialogBinding.actvDefaultType.text?.toString()?.trim() ?: ActualTransaction.TYPE_PAYMENT
            }

            if (isEditing) {
                prefs.updateRule(ruleToSave)
            } else {
                prefs.addRule(ruleToSave)
            }

            loadRules()
            dialog.dismiss()
            Toast.makeText(this, "Rule saved", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showTestParserDialog() {
        val dialogBinding = DialogTestParserBinding.inflate(LayoutInflater.from(this))
        var lastParsedTransaction: ActualTransaction? = null

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnSampleCoffee.setOnClickListener {
            dialogBinding.etTestPackage.setText("com.google.android.apps.walletnfcrel")
            dialogBinding.etTestTitle.setText("Debit Card")
            dialogBinding.etTestText.setText("Paid $10.50 at Starbucks from Checking")
        }

        dialogBinding.btnSampleSalary.setOnClickListener {
            dialogBinding.etTestPackage.setText("com.bank.app")
            dialogBinding.etTestTitle.setText("Account Credit")
            dialogBinding.etTestText.setText("Received $1,250.00 from TechCorp")
        }

        dialogBinding.btnRunTest.setOnClickListener {
            val pkg = dialogBinding.etTestPackage.text?.toString()?.trim() ?: ""
            val title = dialogBinding.etTestTitle.text?.toString()?.trim() ?: ""
            val text = dialogBinding.etTestText.text?.toString()?.trim() ?: ""

            val rules = prefs.getRules()
            val result = NotificationParser.parse(
                packageName = pkg,
                title = title,
                text = text,
                rules = rules
            )

            dialogBinding.layoutTestResult.visibility = View.VISIBLE
            if (result.success && result.transaction != null) {
                lastParsedTransaction = result.transaction
                dialogBinding.tvTestStatus.text = "MATCHED: ${result.matchedRule?.name}"
                dialogBinding.tvTestStatus.setTextColor(ContextCompat.getColor(this, R.color.secondary))
                dialogBinding.tvTestJson.text = result.transaction.toJson()
                dialogBinding.btnSimulatePopup.visibility = View.VISIBLE
            } else {
                lastParsedTransaction = null
                dialogBinding.tvTestStatus.text = "NO MATCH: ${result.errorMessage}"
                dialogBinding.tvTestStatus.setTextColor(ContextCompat.getColor(this, R.color.danger))
                dialogBinding.tvTestJson.text = "Check package name or adjust regex patterns in Parsing Rules."
                dialogBinding.btnSimulatePopup.visibility = View.GONE
            }
        }

        dialogBinding.btnSimulatePopup.setOnClickListener {
            if (lastParsedTransaction != null) {
                dialog.dismiss()
                val intent = Intent(this, ConfirmTransactionActivity::class.java).apply {
                    putExtra(ConfirmTransactionActivity.EXTRA_TRANSACTION, lastParsedTransaction)
                }
                startActivity(intent)
            }
        }

        dialog.show()
    }
}
