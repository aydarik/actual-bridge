package de.gumerbaev.actual.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import de.gumerbaev.actual.R
import de.gumerbaev.actual.data.AppPreferences
import de.gumerbaev.actual.model.ActualTransaction
import de.gumerbaev.actual.model.TransactionRecord
import de.gumerbaev.actual.parser.NotificationParser
import de.gumerbaev.actual.receiver.NotificationActionReceiver
import de.gumerbaev.actual.ui.ConfirmTransactionActivity
import de.gumerbaev.actual.util.LocationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class ActualNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var prefs: AppPreferences

    companion object {
        private const val TAG = "ActualNotifService"
        const val CHANNEL_TRANSACTIONS = "actual_budget_transactions"
        const val ACTION_TRANSACTION_PARSED = "de.gumerbaev.actual.ACTION_TRANSACTION_PARSED"

        private val notificationIdGenerator = AtomicInteger(1000)

        fun isNotificationServiceEnabled(context: Context): Boolean {
            val pkgName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            return flat != null && flat.contains(pkgName)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        createNotificationChannel()
        Log.d(TAG, "ActualNotificationListenerService created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkgName = sbn.packageName ?: return

        // Ignore our own notifications
        if (pkgName == packageName) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        val subText = extras.getCharSequence("android.subText")?.toString() ?: ""

        val effectiveText = when {
            bigText.isNotBlank() -> bigText
            text.isNotBlank() -> text
            else -> subText
        }

        if (title.isBlank() && effectiveText.isBlank()) return

        Log.d(TAG, "Checking notification from $pkgName: Title='$title' Text='$effectiveText'")

        val rules = prefs.getRules()
        val parseResult = NotificationParser.parse(
            packageName = pkgName,
            title = title,
            text = effectiveText,
            rules = rules
        )

        if (parseResult.success && parseResult.transaction != null) {
            Log.d(TAG, "Successfully parsed transaction: ${parseResult.transaction}")
            serviceScope.launch {
                handleParsedTransaction(pkgName, title, effectiveText, parseResult.transaction)
            }
        }
    }

    private suspend fun handleParsedTransaction(
        sourcePackage: String,
        sourceTitle: String,
        sourceText: String,
        transaction: ActualTransaction
    ) {
        var finalTransaction = transaction

        // Attach location if enabled and permitted
        if (prefs.attachLocation && LocationHelper.hasLocationPermission(this)) {
            val location = LocationHelper.getLastLocation(this)
            if (location != null) {
                finalTransaction = finalTransaction.copy(
                    latitude = location.latitude,
                    longitude = location.longitude
                )
            }
        }

        // Save record to history
        val record = TransactionRecord(
            sourcePackage = sourcePackage,
            sourceTitle = sourceTitle,
            sourceText = sourceText,
            transaction = finalTransaction,
            status = TransactionRecord.STATUS_PARSED
        )
        prefs.addHistoryRecord(record)

        // Notify local receivers (e.g. MainActivity if active)
        val broadcastIntent = Intent(ACTION_TRANSACTION_PARSED).apply {
            putExtra(NotificationActionReceiver.EXTRA_RECORD_ID, record.id)
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)

        val notificationId = notificationIdGenerator.incrementAndGet()

        // 1. Show interactive confirmation notification
        showConfirmationNotification(record, finalTransaction, notificationId)

        // 2. If overlay permission is granted and autoPopup is enabled, launch popup dialog directly!
        if (prefs.autoPopup && Settings.canDrawOverlays(this)) {
            try {
                val dialogIntent = Intent(this, ConfirmTransactionActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(ConfirmTransactionActivity.EXTRA_TRANSACTION, finalTransaction)
                    putExtra(ConfirmTransactionActivity.EXTRA_RECORD_ID, record.id)
                    putExtra(ConfirmTransactionActivity.EXTRA_NOTIFICATION_ID, notificationId)
                }
                startActivity(dialogIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not start overlay activity: ${e.message}")
            }
        }
    }

    private fun showConfirmationNotification(
        record: TransactionRecord,
        transaction: ActualTransaction,
        notificationId: Int
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to open confirmation dialog popup
        val reviewIntent = Intent(this, ConfirmTransactionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ConfirmTransactionActivity.EXTRA_TRANSACTION, transaction)
            putExtra(ConfirmTransactionActivity.EXTRA_RECORD_ID, record.id)
            putExtra(ConfirmTransactionActivity.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val reviewPendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            reviewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to 1-tap confirm and send
        val sendIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_CONFIRM_SEND
            putExtra(NotificationActionReceiver.EXTRA_RECORD_ID, record.id)
            putExtra(NotificationActionReceiver.EXTRA_TRANSACTION, transaction)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val sendPendingIntent = PendingIntent.getBroadcast(
            this,
            notificationId + 100000,
            sendIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to discard
        val discardIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISCARD
            putExtra(NotificationActionReceiver.EXTRA_RECORD_ID, record.id)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val discardPendingIntent = PendingIntent.getBroadcast(
            this,
            notificationId + 200000,
            discardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val amountDisplay = if (transaction.type == ActualTransaction.TYPE_DEPOSIT) {
            "+${kotlin.math.abs(transaction.amount)}"
        } else {
            "-${kotlin.math.abs(transaction.amount)}"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_TRANSACTIONS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Actual Budget: ${transaction.payee} ($amountDisplay)")
            .setContentText("Account: ${transaction.account} | Date: ${transaction.date ?: "Today"}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Payee: ${transaction.payee}\nAmount: $amountDisplay\nAccount: ${transaction.account}\nType: ${transaction.type}\nDate: ${transaction.date ?: "Today"}")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(reviewPendingIntent)
            .setFullScreenIntent(reviewPendingIntent, false)
            .addAction(android.R.drawable.ic_menu_send, "Send to Actual", sendPendingIntent)
            .addAction(android.R.drawable.ic_menu_edit, "Review / Edit", reviewPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Discard", discardPendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_TRANSACTIONS,
                "Actual Budget Transactions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for parsed transactions from notifications"
                enableVibration(true)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
