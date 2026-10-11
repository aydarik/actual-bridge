package de.gumerbaev.actual.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Keep
data class BackupData(
    @SerializedName("version")
    val version: Int = CURRENT_VERSION,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @SerializedName("appVersion")
    val appVersion: String? = null,

    @SerializedName("settings")
    val settings: AppSettingsBackup? = null,

    @SerializedName("transactions")
    val transactions: List<TransactionRecord>? = null
) : Serializable {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Keep
data class AppSettingsBackup(
    @SerializedName("serverUrl")
    val serverUrl: String = "",

    @SerializedName("apiToken")
    val apiToken: String = "",

    @SerializedName("attachLocation")
    val attachLocation: Boolean = false,

    @SerializedName("autoPopup")
    val autoPopup: Boolean = false,

    @SerializedName("autoSend")
    val autoSend: Boolean = false,

    @SerializedName("dismissOriginalNotification")
    val dismissOriginalNotification: Boolean = false,

    @SerializedName("parsingRules")
    val parsingRules: List<ParsingRule> = emptyList()
) : Serializable
