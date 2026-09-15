package de.gumerbaev.actual.model

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
    val amount: Double = 0.0,

    @SerializedName("payee")
    val payee: String = "Unknown",

    @SerializedName("type")
    val type: String = TYPE_PAYMENT,

    @SerializedName("date")
    val date: String? = null,

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
        val gson = com.google.gson.GsonBuilder()
            .setPrettyPrinting()
            .create()

        // Construct map without nulls for optional fields
        val map = linkedMapOf<String, Any>()
        map["account"] = account
        map["amount"] = amount
        map["payee"] = payee
        map["type"] = type
        date?.let { map["date"] = it }
        if (latitude != null && longitude != null) {
            map["latitude"] = latitude
            map["longitude"] = longitude
        }
        return gson.toJson(map)
    }
}
