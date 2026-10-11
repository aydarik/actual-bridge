package de.gumerbaev.actual.model

import java.io.Serializable
import java.util.UUID

data class ParsingRule(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var enabled: Boolean = true,
    var targetPackage: String = "*", // App package name or '*' for all
    var titleRegex: String = "",
    var textRegex: String = "",
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
        fun createDefaultRules(
            paymentRuleName: String = "Standard Card Payment (e.g., 'Paid $12.50 at Starbucks')",
            depositRuleName: String = "Income / Deposit (e.g., 'Received $150.00 from Employer')"
        ): List<ParsingRule> {
            return listOf(
                ParsingRule(
                    id = UUID.randomUUID().toString(),
                    name = paymentRuleName,
                    enabled = true,
                    targetPackage = "*",
                    titleRegex = "",
                    textRegex = "(?i)(?:paid|spent|purchase of)\\s*[$€£¥]?\\s*(?<amount>[0-9][0-9.,\\s]*[0-9]|[0-9]+)\\s*[$€£¥]?\\s*(?:at|to|in)\\s*(?<payee>[^.,\\n]+)",
                    account = "Checking",
                    defaultType = ActualTransaction.TYPE_PAYMENT,
                    amountGroup = "amount",
                    payeeGroup = "payee"
                ),
                ParsingRule(
                    id = UUID.randomUUID().toString(),
                    name = depositRuleName,
                    enabled = true,
                    targetPackage = "*",
                    titleRegex = "",
                    textRegex = "(?i)(?:received|deposit of|refund of)\\s*[$€£¥]?\\s*(?<amount>[0-9][0-9.,\\s]*[0-9]|[0-9]+)\\s*[$€£¥]?\\s*(?:from|by)\\s*(?<payee>[^.,\\n]+)",
                    account = "Checking",
                    defaultType = ActualTransaction.TYPE_DEPOSIT,
                    amountGroup = "amount",
                    payeeGroup = "payee"
                )
            )
        }
    }
}
