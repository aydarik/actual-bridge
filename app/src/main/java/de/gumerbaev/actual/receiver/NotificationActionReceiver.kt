package de.gumerbaev.actual.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import de.gumerbaev.actual.data.AppPreferences
import de.gumerbaev.actual.model.ActualTransaction
import de.gumerbaev.actual.model.TransactionRecord
import de.gumerbaev.actual.network.ActualApiClient
import de.gumerbaev.actual.service.ActualNotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CONFIRM_SEND = "de.gumerbaev.actual.ACTION_CONFIRM_SEND"
        const val ACTION_DISCARD = "de.gumerbaev.actual.ACTION_DISCARD"

        const val EXTRA_RECORD_ID = "extra_record_id"
        const val EXTRA_TRANSACTION = "extra_transaction"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val recordId = intent.getStringExtra(EXTRA_RECORD_ID)
        val prefs = AppPreferences(context)

        when (intent.action) {
            ACTION_DISCARD -> {
                if (notificationId != -1) {
                    notificationManager.cancel(notificationId)
                }
                if (recordId != null) {
                    prefs.updateHistoryRecord(recordId, TransactionRecord.STATUS_DISCARDED)
                }
                Toast.makeText(context, "Transaction discarded", Toast.LENGTH_SHORT).show()
            }

            ACTION_CONFIRM_SEND -> {
                val transaction = intent.getSerializableExtra(EXTRA_TRANSACTION, ActualTransaction::class.java) ?: return
                val pendingResult = goAsync()

                CoroutineScope(Dispatchers.IO).launch {
                    val serverUrl = prefs.serverUrl
                    val apiToken = prefs.apiToken
                    val result = ActualApiClient.sendTransaction(serverUrl, apiToken, transaction)

                    withContext(Dispatchers.Main) {
                        try {
                            if (result.success) {
                                if (recordId != null) {
                                    prefs.updateHistoryRecord(
                                        recordId,
                                        TransactionRecord.STATUS_SENT,
                                        result.httpCode,
                                        "Success: ${result.responseBody}"
                                    )
                                }
                                val sentNotification = NotificationCompat.Builder(
                                    context,
                                    ActualNotificationListenerService.CHANNEL_TRANSACTIONS
                                )
                                    .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                                    .setContentTitle("Transaction Sent to Actual")
                                    .setContentText("${transaction.payee} (${transaction.amount}) sent to ${transaction.account}")
                                    .setAutoCancel(true)
                                    .setTimeoutAfter(4000)
                                    .build()

                                notificationManager.notify(notificationId, sentNotification)
                                Toast.makeText(context, "Transaction sent successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                if (recordId != null) {
                                    prefs.updateHistoryRecord(
                                        recordId,
                                        TransactionRecord.STATUS_FAILED,
                                        result.httpCode,
                                        result.errorMessage
                                    )
                                }
                                val errorNotification = NotificationCompat.Builder(
                                    context,
                                    ActualNotificationListenerService.CHANNEL_TRANSACTIONS
                                )
                                    .setSmallIcon(android.R.drawable.stat_notify_error)
                                    .setContentTitle("Failed to send transaction")
                                    .setContentText(result.errorMessage ?: "Unknown error")
                                    .setAutoCancel(true)
                                    .build()

                                notificationManager.notify(notificationId, errorNotification)
                                Toast.makeText(context, "Send failed: ${result.errorMessage}", Toast.LENGTH_LONG).show()
                            }
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }
}
