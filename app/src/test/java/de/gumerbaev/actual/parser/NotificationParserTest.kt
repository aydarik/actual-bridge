package de.gumerbaev.actual.parser

import de.gumerbaev.actual.model.ActualTransaction
import de.gumerbaev.actual.model.ParsingRule
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NotificationParserTest {

    @Test
    fun testParsePaymentNotification() {
        val rules = ParsingRule.createDefaultRules()
        val result = NotificationParser.parse(
            packageName = "com.bank.app",
            title = "Debit Card Alert",
            text = "Paid $10.50 at Starbucks",
            rules = rules,
            fallbackAccount = "Checking"
        )

        assertTrue("Expected parsing to succeed", result.success)
        assertNotNull(result.transaction)
        assertEquals("Checking", result.transaction?.account)
        assertEquals(10.5, result.transaction?.amount ?: 0.0, 0.001)
        assertEquals("Starbucks", result.transaction?.payee)
        assertEquals(ActualTransaction.TYPE_PAYMENT, result.transaction?.type)
    }

    @Test
    fun testParseDepositNotification() {
        val rules = ParsingRule.createDefaultRules()
        val result = NotificationParser.parse(
            packageName = "com.bank.app",
            title = "Account Alert",
            text = "Received $150.00 from Employer",
            rules = rules,
            fallbackAccount = "Checking"
        )

        assertTrue("Expected parsing to succeed", result.success)
        assertNotNull(result.transaction)
        assertEquals("Checking", result.transaction?.account)
        // For deposit, negative value is expected
        assertEquals(-150.0, result.transaction?.amount ?: 0.0, 0.001)
        assertEquals("Employer", result.transaction?.payee)
        assertEquals(ActualTransaction.TYPE_DEPOSIT, result.transaction?.type)
    }

    @Test
    fun testParseMerchantFormat() {
        val rules = ParsingRule.createDefaultRules()
        val result = NotificationParser.parse(
            packageName = "com.google.android.apps.walletnfcrel",
            title = "Google Pay",
            text = "Starbucks: 10.50 USD",
            rules = rules,
            fallbackAccount = "Checking"
        )

        assertTrue("Expected parsing to succeed", result.success)
        assertNotNull(result.transaction)
        assertEquals(10.5, result.transaction?.amount ?: 0.0, 0.001)
        assertEquals("Starbucks", result.transaction?.payee)
    }

    @Test
    fun testTransactionJsonPayload() {
        val tx = ActualTransaction(
            account = "Checking",
            amount = 10.5,
            payee = "Starbucks",
            type = "payment",
            date = "2026-07-01",
            latitude = -37.8136,
            longitude = 144.9631
        )
        val jsonStr = tx.toJson()
        val obj = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject

        assertEquals("Checking", obj.get("account").asString)
        assertEquals(10.5, obj.get("amount").asDouble, 0.001)
        assertEquals("Starbucks", obj.get("payee").asString)
        assertEquals("payment", obj.get("type").asString)
        assertEquals("2026-07-01", obj.get("date").asString)
        assertEquals(-37.8136, obj.get("latitude").asDouble, 0.0001)
        assertEquals(144.9631, obj.get("longitude").asDouble, 0.0001)
    }

    @Test
    fun testEuropeanCommaAmountFormat() {
        val rules = ParsingRule.createDefaultRules()
        val result = NotificationParser.parse(
            packageName = "com.bank.app",
            title = "Notification",
            text = "Spent €24,99 at Supermarket",
            rules = rules,
            fallbackAccount = "Checking"
        )

        assertTrue("Expected parsing to succeed", result.success)
        assertNotNull(result.transaction)
        assertEquals(24.99, result.transaction?.amount ?: 0.0, 0.001)
        assertEquals("Supermarket", result.transaction?.payee)
    }

    @Test
    fun testCustomRuleWithAccountExtraction() {
        val customRule = ParsingRule(
            name = "Account Extractor",
            enabled = true,
            targetPackage = "*",
            textRegex = "(?i)Paid \\$(?<amount>[0-9.]+) at (?<payee>[^.,]+) from (?<account>[^.,]+)",
            amountGroup = "amount",
            payeeGroup = "payee",
            accountGroup = "account"
        )

        val result = NotificationParser.parseWithRule(
            packageName = "com.bank.app",
            title = "Alert",
            text = "Paid $45.50 at Target from Savings",
            rule = customRule
        )

        assertTrue(result.success)
        assertEquals("Savings", result.transaction?.account)
        assertEquals(45.50, result.transaction?.amount ?: 0.0, 0.001)
        assertEquals("Target", result.transaction?.payee)
    }

    @Test
    fun testTargetPackageFiltering() {
        val rule = ParsingRule(
            name = "Specific Bank Only",
            enabled = true,
            targetPackage = "com.mybank.app",
            textRegex = "(?i)Paid \\$(?<amount>[0-9.]+) at (?<payee>.+)"
        )

        val rejected = NotificationParser.parseWithRule(
            packageName = "com.other.app",
            title = "Alert",
            text = "Paid $10.00 at Store",
            rule = rule
        )
        assertFalse("Should reject non-matching package", rejected.success)

        val accepted = NotificationParser.parseWithRule(
            packageName = "com.mybank.app",
            title = "Alert",
            text = "Paid $10.00 at Store",
            rule = rule
        )
        assertTrue("Should accept matching package", accepted.success)
    }
}
