package de.gumerbaev.actual.ui

import android.app.DatePickerDialog
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import de.gumerbaev.actual.R
import de.gumerbaev.actual.data.AppPreferences
import de.gumerbaev.actual.databinding.ActivityConfirmTransactionBinding
import de.gumerbaev.actual.model.ActualTransaction
import de.gumerbaev.actual.model.TransactionRecord
import de.gumerbaev.actual.network.ActualApiClient
import de.gumerbaev.actual.util.LocationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class ConfirmTransactionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRANSACTION = "extra_transaction"
        const val EXTRA_RECORD_ID = "extra_record_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    private lateinit var binding: ActivityConfirmTransactionBinding
    private lateinit var prefs: AppPreferences

    private var recordId: String? = null
    private var notificationId: Int = -1

    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            fetchLocation()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfirmTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences(this)

        val transaction = intent.getSerializableExtra(EXTRA_TRANSACTION) as? ActualTransaction
        recordId = intent.getStringExtra(EXTRA_RECORD_ID)
        notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        if (transaction == null) {
            Toast.makeText(this, "No transaction data provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupViews(transaction)
        setupListeners()
    }

    private fun setupViews(tx: ActualTransaction) {
        binding.etPayee.setText(tx.payee)
        binding.etAmount.setText(String.format(Locale.US, "%.2f", abs(tx.amount)))
        binding.etAccount.setText(tx.account)
        binding.etDate.setText(tx.date ?: ActualTransaction.todayFormatted())

        if (tx.type == ActualTransaction.TYPE_DEPOSIT) {
            binding.toggleTypeGroup.check(R.id.btnTypeDeposit)
        } else {
            binding.toggleTypeGroup.check(R.id.btnTypePayment)
        }

        currentLatitude = tx.latitude
        currentLongitude = tx.longitude
        updateLocationDisplay()
    }

    private fun setupListeners() {
        binding.root.setOnClickListener {
            dismissAndFinish()
        }

        binding.cardContainer.setOnClickListener {
            // Consume clicks inside the bottom sheet card to prevent dismissal
        }

        binding.btnClose.setOnClickListener {
            dismissAndFinish()
        }

        binding.btnDiscard.setOnClickListener {
            recordId?.let { id ->
                prefs.updateHistoryRecord(id, TransactionRecord.STATUS_DISCARDED)
            }
            cancelNotification()
            Toast.makeText(this, "Transaction discarded", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.etDate.setOnClickListener {
            showDatePicker()
        }

        binding.layoutDate.setEndIconOnClickListener {
            showDatePicker()
        }

        binding.btnLocationAction.setOnClickListener {
            if (currentLatitude != null && currentLongitude != null) {
                // Clear location
                currentLatitude = null
                currentLongitude = null
                updateLocationDisplay()
            } else {
                // Attach location
                if (LocationHelper.hasLocationPermission(this)) {
                    fetchLocation()
                } else {
                    requestLocationPermissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }
        }

        binding.btnSend.setOnClickListener {
            sendTransaction()
        }
    }

    private fun fetchLocation() {
        binding.tvLocationCoords.text = "Fetching current GPS coordinates..."
        lifecycleScope.launch {
            val loc = LocationHelper.getLastLocation(this@ConfirmTransactionActivity)
            if (loc != null) {
                currentLatitude = loc.latitude
                currentLongitude = loc.longitude
            } else {
                Toast.makeText(this@ConfirmTransactionActivity, "Could not obtain current location", Toast.LENGTH_SHORT).show()
            }
            updateLocationDisplay()
        }
    }

    private fun updateLocationDisplay() {
        if (currentLatitude != null && currentLongitude != null) {
            binding.tvLocationCoords.text = String.format(
                Locale.US,
                "Lat: %.4f, Lon: %.4f",
                currentLatitude,
                currentLongitude
            )
            binding.btnLocationAction.text = "Clear"
        } else {
            binding.tvLocationCoords.text = "Location: Not attached"
            binding.btnLocationAction.text = "Attach GPS"
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val parts = binding.etDate.text?.toString()?.split("-")
        if (parts != null && parts.size == 3) {
            parts[0].toIntOrNull()?.let { calendar.set(Calendar.YEAR, it) }
            parts[1].toIntOrNull()?.let { calendar.set(Calendar.MONTH, it - 1) }
            parts[2].toIntOrNull()?.let { calendar.set(Calendar.DAY_OF_MONTH, it) }
        }

        val dpd = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                binding.etDate.setText(sdf.format(selectedCalendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        dpd.show()
    }

    private fun sendTransaction() {
        val account = binding.etAccount.text?.toString()?.trim() ?: ""
        if (account.isBlank()) {
            binding.layoutAccount.error = "Account is required"
            return
        }
        binding.layoutAccount.error = null

        val amountStr = binding.etAmount.text?.toString()?.trim() ?: ""
        val parsedAmount = amountStr.toDoubleOrNull()
        if (parsedAmount == null || parsedAmount == 0.0) {
            binding.layoutAmount.error = "Valid amount is required"
            return
        }
        binding.layoutAmount.error = null

        val payee = binding.etPayee.text?.toString()?.trim().let {
            if (it.isNullOrBlank()) "Unknown" else it
        }

        val isDeposit = binding.toggleTypeGroup.checkedButtonId == R.id.btnTypeDeposit
        val type = if (isDeposit) ActualTransaction.TYPE_DEPOSIT else ActualTransaction.TYPE_PAYMENT

        // Negative value for deposit per example specification
        val finalAmount = if (isDeposit) -abs(parsedAmount) else abs(parsedAmount)

        val date = binding.etDate.text?.toString()?.trim().let {
            if (it.isNullOrBlank()) ActualTransaction.todayFormatted() else it
        }

        val finalTx = ActualTransaction(
            account = account,
            amount = finalAmount,
            payee = payee,
            type = type,
            date = date,
            latitude = currentLatitude,
            longitude = currentLongitude
        )

        // UI Loading state
        binding.btnSend.isEnabled = false
        binding.btnDiscard.isEnabled = false
        binding.progressSending.visibility = View.VISIBLE
        binding.tvStatusMessage.visibility = View.GONE

        lifecycleScope.launch {
            val serverUrl = prefs.serverUrl
            val apiToken = prefs.apiToken
            val result = ActualApiClient.sendTransaction(serverUrl, apiToken, finalTx)

            binding.progressSending.visibility = View.GONE
            binding.btnSend.isEnabled = true
            binding.btnDiscard.isEnabled = true

            if (result.success) {
                recordId?.let { id ->
                    prefs.updateHistoryRecord(
                        id,
                        TransactionRecord.STATUS_SENT,
                        result.httpCode,
                        "Success"
                    )
                }
                cancelNotification()

                binding.tvStatusMessage.setBackgroundResource(R.drawable.bg_status_success)
                binding.tvStatusMessage.setTextColor(getColor(R.color.secondary))
                binding.tvStatusMessage.text = "Successfully sent to Actual Budget!"
                binding.tvStatusMessage.visibility = View.VISIBLE

                Toast.makeText(this@ConfirmTransactionActivity, "Transaction posted successfully!", Toast.LENGTH_SHORT).show()
                delay(800)
                finish()
            } else {
                recordId?.let { id ->
                    prefs.updateHistoryRecord(
                        id,
                        TransactionRecord.STATUS_FAILED,
                        result.httpCode,
                        result.errorMessage
                    )
                }
                binding.tvStatusMessage.setBackgroundResource(R.drawable.bg_status_error)
                binding.tvStatusMessage.setTextColor(getColor(R.color.danger))
                binding.tvStatusMessage.text = "Send failed: ${result.errorMessage}"
                binding.tvStatusMessage.visibility = View.VISIBLE
            }
        }
    }

    private fun cancelNotification() {
        if (notificationId != -1) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notificationId)
        }
    }

    private fun dismissAndFinish() {
        finish()
    }
}
