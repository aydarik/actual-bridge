package de.gumerbaev.actual.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.util.UUID

data class ParsingRule(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var enabled: Boolean = true,
    var targetPackage: String = "*", // App package name or '*' for all
    var titleRegex: String = "",
    var textRegex: String = "",
    @SerializedName(value = "account", alternate = ["defaultAccount"])
    var account: String = "",
    var defaultType: String = ActualTransaction.TYPE_PAYMENT,
    // Group names or indices (1-based)
    var amountGroup: String = "amount",
    var payeeGroup: String = "payee",
    var accountGroup: String = "",
    var typeGroup: String = "",
    var dateGroup: String = ""
) : Serializable {

    companion object {
        fun createDefaultRules(): List<ParsingRule> {
            return listOf(
                ParsingRule(
                    id = "rule_default_1",
                    name = "Standard Card Payment (e.g., 'Paid $12.50 at Starbucks')",
                    enabled = true,
                    targetPackage = "*",
                    titleRegex = "",
                    textRegex = "(?i)(?:paid|spent|purchase of)\\s*[$€£¥]?\\s*(?<amount>[0-9]+(?:[.,][0-9]{2})?)\\s*(?:at|to|in)\\s*(?<payee>[^.,\\n]+)",
                    account = "Checking",
                    defaultType = ActualTransaction.TYPE_PAYMENT,
                    amountGroup = "amount",
                    payeeGroup = "payee"
                ),
                ParsingRule(
                    id = "rule_default_2",
                    name = "Direct Debit / Card transaction (e.g., 'Starbucks: 10.50 USD')",
                    enabled = true,
                    targetPackage = "*",
                    titleRegex = "",
                    textRegex = "(?i)(?<payee>[A-Za-z0-9\\s&'-]+?):\\s*[$€£¥]?\\s*(?<amount>[0-9]+(?:[.,][0-9]{2})?)(?:\\s*[A-Z]{3})?",
                    account = "Checking",
                    defaultType = ActualTransaction.TYPE_PAYMENT,
                    amountGroup = "amount",
                    payeeGroup = "payee"
                ),
                ParsingRule(
                    id = "rule_default_3",
                    name = "Income / Deposit (e.g., 'Received $150.00 from Employer')",
                    enabled = true,
                    targetPackage = "*",
                    titleRegex = "",
                    textRegex = "(?i)(?:received|deposit of|refund of)\\s*[$€£¥]?\\s*(?<amount>[0-9]+(?:[.,][0-9]{2})?)\\s*(?:from|by)\\s*(?<payee>[^.,\\n]+)",
                    account = "Checking",
                    defaultType = ActualTransaction.TYPE_DEPOSIT,
                    amountGroup = "amount",
                    payeeGroup = "payee"
                )
            )
        }
    }
}
