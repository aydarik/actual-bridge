package de.gumerbaev.actual.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Model matching the Actual Budget integration payload:
 * {
 *   "account": "Checking",
 *   "amount": 10.5,
 *   "payee": "Starbucks",
 *   "type": "payment",
 *   "date": "2026-07-01",
 *   "latitude": -37.8136,
 *   "longitude": 144.9631
 * }
 */
data class ActualTransaction(
    @SerializedName("account")
    val account: String,

    @SerializedName("amount")
    val amount: Double? = null,

    @SerializedName("payee")
    val payee: String? = null,

    @SerializedName("type")
    val type: String? = null,

    @SerializedName("date")
    val date: String? = null,

    @SerializedName("notes")
    val notes: String? = null,

    @SerializedName("latitude")
    val latitude: Double? = null,

    @SerializedName("longitude")
    val longitude: Double? = null
) : Serializable {

    companion object {
        const val TYPE_PAYMENT = "payment"
        const val TYPE_DEPOSIT = "deposit"

        fun todayFormatted(): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        }
    }

    /**
     * Converts to JSON string matching required field specifications.
     */
    fun toJson(): String {
        val map = linkedMapOf<String, Any>()
        map["account"] = account
        amount?.let { map["amount"] = it }
        payee?.let { map["payee"] = it }
        type?.let { map["type"] = it }
        date?.let { map["date"] = it }
        notes?.let { map["notes"] = it }
        if (latitude != null && longitude != null) {
            map["latitude"] = latitude
            map["longitude"] = longitude
        }
        return Gson().toJson(map)
    }
}
