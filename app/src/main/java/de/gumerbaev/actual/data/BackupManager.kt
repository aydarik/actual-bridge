package de.gumerbaev.actual.data

import android.content.ContentResolver
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import de.gumerbaev.actual.model.AppSettingsBackup
import de.gumerbaev.actual.model.BackupData
import de.gumerbaev.actual.model.ParsingRule
import de.gumerbaev.actual.model.TransactionRecord
import java.io.InputStream
import java.io.OutputStream

class BackupManager(
    private val prefs: AppPreferences? = null,
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
) {

    companion object {
        fun mergeTransactions(
            existing: List<TransactionRecord>,
            incoming: List<TransactionRecord>,
            maxSize: Int = AppPreferences.MAX_HISTORY_SIZE
        ): List<TransactionRecord> {
            val existingIds = existing.map { it.id }.toSet()
            val newUnique = incoming.filter { it.id !in existingIds }
            val combined = (existing + newUnique).sortedByDescending { it.timestamp }
            return if (combined.size > maxSize) combined.take(maxSize) else combined
        }
    }

    fun createBackup(
        includeSettings: Boolean,
        includeTransactions: Boolean,
        appVersion: String
    ): BackupData {
        val currentPrefs = prefs ?: throw IllegalStateException("AppPreferences is required to create backup")
        return createBackupData(
            serverUrl = currentPrefs.serverUrl,
            apiToken = currentPrefs.apiToken,
            attachLocation = currentPrefs.attachLocation,
            autoPopup = currentPrefs.autoPopup,
            autoSend = currentPrefs.autoSend,
            dismissOriginalNotification = currentPrefs.dismissOriginalNotification,
            rules = currentPrefs.getRules(),
            transactions = currentPrefs.getHistory(),
            includeSettings = includeSettings,
            includeTransactions = includeTransactions,
            appVersion = appVersion
        )
    }

    fun createBackupData(
        serverUrl: String,
        apiToken: String,
        attachLocation: Boolean,
        autoPopup: Boolean,
        autoSend: Boolean,
        dismissOriginalNotification: Boolean,
        rules: List<ParsingRule>,
        transactions: List<TransactionRecord>,
        includeSettings: Boolean,
        includeTransactions: Boolean,
        appVersion: String
    ): BackupData {
        if (!includeSettings && !includeTransactions) {
            throw IllegalArgumentException("At least one item (settings or transactions) must be selected")
        }

        val settingsBackup = if (includeSettings) {
            AppSettingsBackup(
                serverUrl = serverUrl,
                apiToken = apiToken,
                attachLocation = attachLocation,
                autoPopup = autoPopup,
                autoSend = autoSend,
                dismissOriginalNotification = dismissOriginalNotification,
                parsingRules = rules
            )
        } else null

        val transactionsBackup = if (includeTransactions) {
            transactions
        } else null

        return BackupData(
            version = BackupData.CURRENT_VERSION,
            timestamp = System.currentTimeMillis(),
            appVersion = appVersion,
            settings = settingsBackup,
            transactions = transactionsBackup
        )
    }

    fun serializeBackup(backupData: BackupData): String {
        return gson.toJson(backupData)
    }

    fun deserializeBackup(json: String): BackupData {
        if (json.isBlank()) {
            throw IllegalArgumentException("Backup content is empty")
        }
        val data = try {
            gson.fromJson(json, BackupData::class.java)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed backup file", e)
        } ?: throw IllegalArgumentException("Invalid backup format")

        if (data.settings == null && data.transactions == null) {
            throw IllegalArgumentException("Backup contains neither settings nor transactions")
        }

        return data
    }

    fun writeBackup(outputStream: OutputStream, backupData: BackupData) {
        val json = serializeBackup(backupData)
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(json)
            writer.flush()
        }
    }

    fun readBackup(inputStream: InputStream): BackupData {
        val json = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return deserializeBackup(json)
    }

    fun writeBackupToUri(contentResolver: ContentResolver, uri: Uri, backupData: BackupData) {
        val outputStream = contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Could not open output stream for URI: $uri")
        outputStream.use { stream ->
            writeBackup(stream, backupData)
        }
    }

    fun readBackupFromUri(contentResolver: ContentResolver, uri: Uri): BackupData {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not open input stream for URI: $uri")
        return inputStream.use { stream ->
            readBackup(stream)
        }
    }

    data class RestoreSummary(
        val settingsRestored: Boolean,
        val rulesCount: Int,
        val transactionsRestored: Boolean,
        val transactionsCount: Int
    )

    fun restoreBackup(
        backupData: BackupData,
        restoreSettings: Boolean,
        restoreTransactions: Boolean,
        mergeTransactions: Boolean
    ): RestoreSummary {
        val currentPrefs = prefs ?: throw IllegalStateException("AppPreferences is required to restore backup")
        var rulesCount = 0
        var transactionsCount = 0

        if (restoreSettings && backupData.settings != null) {
            currentPrefs.restoreSettings(backupData.settings)
            rulesCount = backupData.settings.parsingRules.size
        }

        if (restoreTransactions && backupData.transactions != null) {
            currentPrefs.restoreTransactions(backupData.transactions, merge = mergeTransactions)
            transactionsCount = backupData.transactions.size
        }

        return RestoreSummary(
            settingsRestored = restoreSettings && backupData.settings != null,
            rulesCount = rulesCount,
            transactionsRestored = restoreTransactions && backupData.transactions != null,
            transactionsCount = transactionsCount
        )
    }
}
