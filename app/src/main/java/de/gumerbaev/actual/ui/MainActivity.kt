package de.gumerbaev.actual.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import de.gumerbaev.actual.R
import de.gumerbaev.actual.data.AppPreferences
import de.gumerbaev.actual.databinding.ActivityMainBinding
import de.gumerbaev.actual.databinding.DialogEditRuleBinding
import de.gumerbaev.actual.databinding.DialogTestParserBinding
import de.gumerbaev.actual.model.ActualTransaction
import de.gumerbaev.actual.model.ParsingRule
import de.gumerbaev.actual.network.ActualApiClient
import de.gumerbaev.actual.parser.NotificationParser
import de.gumerbaev.actual.service.ActualNotificationListenerService
import de.gumerbaev.actual.util.LocationHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    lateinit var prefs: AppPreferences
        private set

    private var pendingNotificationSwitch: CompoundButton? = null

    val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, getString(R.string.notif_permission_granted), Toast.LENGTH_SHORT).show()
        } else {
            pendingNotificationSwitch?.isChecked = false
            Toast.makeText(
                this,
                getString(R.string.notif_permission_required),
                Toast.LENGTH_SHORT
            ).show()
        }
        pendingNotificationSwitch = null
    }

    val requestBackgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, getString(R.string.location_permission_granted), Toast.LENGTH_SHORT).show()
        } else {
            notifyRulesFragmentLocationDenied()
            Toast.makeText(
                this,
                getString(R.string.bg_location_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val requestForegroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            if (!LocationHelper.hasBackgroundLocationPermission(this)) {
                requestBackgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        } else {
            notifyRulesFragmentLocationDenied()
            Toast.makeText(
                this,
                getString(R.string.location_permission_required),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val transactionUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            getTransactionsFragment()?.loadHistory()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences(this)

        setupViewPagerAndTabs()
    }

    override fun onResume() {
        super.onResume()
        getSettingsFragment()?.updatePermissionStatuses()
        getTransactionsFragment()?.loadHistory()

        val filter = IntentFilter(ActualNotificationListenerService.ACTION_TRANSACTION_PARSED)
        registerReceiver(transactionUpdateReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(transactionUpdateReceiver)
        } catch (_: Exception) {
            // Ignored
        }
    }

    private fun setupViewPagerAndTabs() {
        val adapter = MainPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_transactions)
                1 -> getString(R.string.tab_rules)
                2 -> getString(R.string.tab_settings)
                else -> ""
            }
        }.attach()

        // Conditional initial tab selection
        val hasServerUrl = prefs.serverUrl.isNotEmpty()
        val hasTransactions = prefs.getHistory().isNotEmpty()
        binding.viewPager.currentItem = if (hasTransactions) 0 else if (hasServerUrl) 1 else 2
    }

    // --- Helper Accessors for Active Fragments ---

    fun getTransactionsFragment(): TransactionsFragment? {
        return supportFragmentManager.findFragmentByTag("f0") as? TransactionsFragment
    }

    fun getRulesFragment(): RulesFragment? {
        return supportFragmentManager.findFragmentByTag("f1") as? RulesFragment
    }

    fun getSettingsFragment(): SettingsFragment? {
        return supportFragmentManager.findFragmentByTag("f2") as? SettingsFragment
    }

    fun notifyDataRestored() {
        getSettingsFragment()?.loadSettings()
        getRulesFragment()?.let {
            it.loadAppSettings()
            it.loadRules()
        }
        getTransactionsFragment()?.loadHistory()
    }

    private fun notifyRulesFragmentLocationDenied() {
        getRulesFragment()?.onLocationPermissionDenied()
    }

    // --- Business & Navigation Logic Callbacks ---

    fun hasNotificationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestNotificationPermission(switchView: CompoundButton) {
        pendingNotificationSwitch = switchView
        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun testConnection(serverUrl: String, apiToken: String, onComplete: () -> Unit) {
        prefs.serverUrl = serverUrl
        prefs.apiToken = apiToken

        lifecycleScope.launch {
            val result = ActualApiClient.testConnection(prefs.serverUrl, prefs.apiToken)
            onComplete()

            if (result.success || result.httpCode == 400) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(getString(R.string.connection_success_title))
                    .setMessage(getString(R.string.connection_success_message))
                    .setPositiveButton(R.string.ok, null)
                    .show()
            } else {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(getString(R.string.connection_failed_title))
                    .setMessage(getString(R.string.connection_failed_message, prefs.serverUrl, result.error?.asString(this@MainActivity) ?: getString(R.string.unknown_error)))
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
        }
    }

    fun showEditRuleDialog(existingRule: ParsingRule?, onRuleSaved: () -> Unit) {
        val dialogBinding = DialogEditRuleBinding.inflate(LayoutInflater.from(this))
        val isEditing = existingRule != null

        dialogBinding.tvDialogTitle.text = getString(if (isEditing) R.string.edit_rule_title else R.string.new_rule_title)

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
            dialogBinding.etAccount.setText(existingRule.account)
            dialogBinding.actvDefaultType.setText(existingRule.defaultType, false)
        } else {
            dialogBinding.etAccount.setText("")
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
                dialogBinding.etRuleName.error = getString(R.string.rule_name_required)
                return@setOnClickListener
            }
            if (textRegex.isBlank()) {
                dialogBinding.etTextRegex.error = getString(R.string.text_regex_required)
                return@setOnClickListener
            }

            val ruleToSave = (existingRule ?: ParsingRule(name = name)).apply {
                this.name = name
                this.targetPackage = dialogBinding.etTargetPackage.text?.toString()?.trim() ?: "*"
                this.titleRegex = dialogBinding.etTitleRegex.text?.toString()?.trim() ?: ""
                this.textRegex = textRegex
                this.amountGroup = dialogBinding.etAmountGroup.text?.toString()?.trim() ?: "amount"
                this.payeeGroup = dialogBinding.etPayeeGroup.text?.toString()?.trim() ?: "payee"
                this.account = dialogBinding.etAccount.text?.toString()?.trim() ?: ""
                this.defaultType = dialogBinding.actvDefaultType.text?.toString()?.trim() ?: ActualTransaction.TYPE_PAYMENT
            }

            if (isEditing) {
                prefs.updateRule(ruleToSave)
            } else {
                prefs.addRule(ruleToSave)
            }

            onRuleSaved()
            dialog.dismiss()
            Toast.makeText(this, getString(R.string.rule_saved), Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    @SuppressLint("SetTextI18n")
    fun showTestParserDialog() {
        val dialogBinding = DialogTestParserBinding.inflate(LayoutInflater.from(this))
        var lastParsedTransaction: ActualTransaction? = null

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnSampleCoffee.setOnClickListener {
            dialogBinding.etTestPackage.setText("com.bank.app")
            dialogBinding.etTestTitle.setText("Debit Card")
            dialogBinding.etTestText.setText("Paid $10.50 at Starbucks")
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
                dialogBinding.tvTestStatus.text = getString(R.string.test_matched, result.matchedRule?.name)
                dialogBinding.tvTestStatus.setTextColor(ContextCompat.getColor(this, R.color.secondary))
                dialogBinding.tvTestJson.text = result.transaction.toJson()
                dialogBinding.btnSimulatePopup.visibility = View.VISIBLE
            } else {
                lastParsedTransaction = null
                dialogBinding.tvTestStatus.text = getString(R.string.test_no_match, result.error?.asString(this@MainActivity).orEmpty())
                dialogBinding.tvTestStatus.setTextColor(ContextCompat.getColor(this, R.color.danger))
                dialogBinding.tvTestJson.text = getString(R.string.test_no_match_hint)
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

    private class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> TransactionsFragment()
                1 -> RulesFragment()
                2 -> SettingsFragment()
                else -> throw IllegalArgumentException("Invalid position: $position")
            }
        }
    }
}