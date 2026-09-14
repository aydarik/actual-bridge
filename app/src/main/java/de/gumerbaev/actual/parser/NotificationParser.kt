package de.gumerbaev.actual.parser

import de.gumerbaev.actual.model.ActualTransaction
import de.gumerbaev.actual.model.ParsingRule
import java.util.regex.Matcher
import java.util.regex.Pattern

object NotificationParser {

    data class ParseResult(
        val success: Boolean,
        val transaction: ActualTransaction? = null,
        val matchedRule: ParsingRule? = null,
        val errorMessage: String? = null
    )

    /**
     * Attempts to parse an incoming notification against a list of rules.
     */
    fun parse(
        packageName: String,
        title: String,
        text: String,
        rules: List<ParsingRule>
    ): ParseResult {
        val enabledRules = rules.filter { it.enabled }
        if (enabledRules.isEmpty()) {
            return ParseResult(false, errorMessage = "No active parsing rules configured.")
        }

        for (rule in enabledRules) {
            val result = parseWithRule(packageName, title, text, rule)
            if (result.success) {
                return result
            }
        }

        return ParseResult(false, errorMessage = "Notification did not match any active rules.")
    }

    /**
     * Tests a single rule against a notification's package name, title, and text.
     */
    fun parseWithRule(
        packageName: String,
        title: String,
        text: String,
        rule: ParsingRule
    ): ParseResult {
        // 1. Package filter
        if (rule.targetPackage.isNotBlank() && rule.targetPackage.trim() != "*") {
            val targetPackages = rule.targetPackage.split(",").map { it.trim() }
            val matchesPackage = targetPackages.any { target ->
                target.equals(packageName, ignoreCase = true) ||
                        (target.endsWith("*") && packageName.startsWith(target.removeSuffix("*")))
            }
            if (!matchesPackage) {
                return ParseResult(false, errorMessage = "Package '$packageName' does not match rule target '${rule.targetPackage}'")
            }
        }

        // 2. Title filter (if specified)
        if (rule.titleRegex.isNotBlank()) {
            val titlePattern = try {
                Pattern.compile(rule.titleRegex, Pattern.CASE_INSENSITIVE)
            } catch (e: Exception) {
                return ParseResult(false, errorMessage = "Invalid title regex: ${e.message}")
            }
            if (!titlePattern.matcher(title).find()) {
                return ParseResult(false, errorMessage = "Title did not match pattern.")
            }
        }

        // 3. Text regex matching
        if (rule.textRegex.isBlank()) {
            return ParseResult(false, errorMessage = "Rule text regex is empty.")
        }

        val textPattern = try {
            Pattern.compile(rule.textRegex, Pattern.CASE_INSENSITIVE)
        } catch (e: Exception) {
            return ParseResult(false, errorMessage = "Invalid text regex: ${e.message}")
        }

        // Search text first; if not found, also try title + " " + text
        var matcher = textPattern.matcher(text)
        var found = matcher.find()

        if (!found && title.isNotBlank()) {
            val combined = "$title $text"
            val combinedMatcher = textPattern.matcher(combined)
            if (combinedMatcher.find()) {
                matcher = combinedMatcher
                found = true
            }
        }

        if (!found) {
            return ParseResult(false, errorMessage = "Notification text did not match rule regex.")
        }

        // 4. Extract Amount
        var amount = 0.0
        val rawAmount = extractGroup(matcher, rule.amountGroup)
        if (rawAmount != null) {
            amount = parseAmount(rawAmount)
        }

        // 5. Extract Payee
        var payee = extractGroup(matcher, rule.payeeGroup)?.trim()
        if (payee.isNullOrBlank()) {
            payee = if (title.isNotBlank() && title != packageName) title.trim() else "Unknown"
        }

        // Clean payee of trailing punctuation
        payee = payee.trimEnd('.', ',', ':', ';', '-', ' ')

        // 6. Extract Account
        var account = extractGroup(matcher, rule.accountGroup)?.trim()
        if (account.isNullOrBlank()) {
            account = rule.account.trim()
        }

        // 7. Extract Type ("payment" or "deposit")
        var type = extractGroup(matcher, rule.typeGroup)?.trim()?.lowercase()
        if (type.isNullOrBlank()) {
            type = rule.defaultType.lowercase()
        }
        if (type != ActualTransaction.TYPE_PAYMENT && type != ActualTransaction.TYPE_DEPOSIT) {
            type = if (type.contains("dep") || type.contains("inc") || type.contains("rec") || type.contains("refund")) {
                ActualTransaction.TYPE_DEPOSIT
            } else {
                ActualTransaction.TYPE_PAYMENT
            }
        }

        // Adjust amount sign: "negative value is a deposit"
        if (type == ActualTransaction.TYPE_DEPOSIT && amount > 0) {
            amount = -amount
        } else if (type == ActualTransaction.TYPE_PAYMENT && amount < 0) {
            amount = -amount // Ensure positive for payment
        }

        // 8. Extract Date
        var dateStr = extractGroup(matcher, rule.dateGroup)?.trim()
        if (dateStr.isNullOrBlank()) {
            dateStr = ActualTransaction.todayFormatted()
        }

        val transaction = ActualTransaction(
            account = account,
            amount = amount,
            payee = payee,
            type = type,
            date = dateStr,
            latitude = null,
            longitude = null
        )

        return ParseResult(
            success = true,
            transaction = transaction,
            matchedRule = rule
        )
    }

    private fun extractGroup(matcher: Matcher, groupIdentifier: String): String? {
        if (groupIdentifier.isBlank()) return null
        return try {
            // Try numeric index first
            val groupIndex = groupIdentifier.toIntOrNull()
            if (groupIndex != null) {
                if (groupIndex in 1..matcher.groupCount()) {
                    matcher.group(groupIndex)
                } else null
            } else {
                matcher.group(groupIdentifier)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseAmount(raw: String): Double {
        return try {
            // Remove currency symbols and non-numeric except dot, comma, minus
            var cleaned = raw.replace(Regex("[^0-9.,-]"), "").trim()
            if (cleaned.contains(",") && cleaned.contains(".")) {
                // Determine whether comma is decimal or thousand separator
                val lastComma = cleaned.lastIndexOf(',')
                val lastDot = cleaned.lastIndexOf('.')
                cleaned = if (lastComma > lastDot) {
                    // e.g. 1.250,50 -> comma is decimal
                    cleaned.replace(".", "").replace(',', '.')
                } else {
                    // e.g. 1,250.50 -> dot is decimal
                    cleaned.replace(",", "")
                }
            } else if (cleaned.contains(",")) {
                // If single comma and 2 digits after, likely decimal (e.g. 12,50)
                val parts = cleaned.split(",")
                if (parts.size == 2 && parts[1].length in 1..2) {
                    cleaned = cleaned.replace(',', '.')
                } else {
                    cleaned = cleaned.replace(",", "")
                }
            }
            cleaned.toDouble()
        } catch (e: Exception) {
            0.0
        }
    }
}
