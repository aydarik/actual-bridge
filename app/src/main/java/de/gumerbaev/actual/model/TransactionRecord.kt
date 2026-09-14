package de.gumerbaev.actual.model

import java.io.Serializable
import java.util.UUID

data class TransactionRecord(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val sourcePackage: String,
    val sourceTitle: String,
    val sourceText: String,
    val transaction: ActualTransaction,
    var status: String = STATUS_PARSED,
    var httpCode: Int? = null,
    var responseDetails: String? = null
) : Serializable {

    companion object {
        const val STATUS_PARSED = "PARSED"
        const val STATUS_SENT = "SENT"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_DISCARDED = "DISCARDED"
    }
}
