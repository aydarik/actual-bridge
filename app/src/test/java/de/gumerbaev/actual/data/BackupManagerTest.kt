package de.gumerbaev.actual.data

import de.gumerbaev.actual.model.ActualTransaction
import de.gumerbaev.actual.model.BackupData
import de.gumerbaev.actual.model.ParsingRule
import de.gumerbaev.actual.model.TransactionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupManagerTest {

    private val backupManager = BackupManager()

    private fun createSampleRule(): ParsingRule {
        return ParsingRule(
            id = "rule-1",
            name = "Test Bank Alert",
            enabled = true,
            targetPackage = "com.test.bank",
            titleRegex = "Alert",
            textRegex = "(?i)Paid \\$(?<amount>[0-9.]+) at (?<payee>.+)",
            account = "Checking",
            defaultType = ActualTransaction.TYPE_PAYMENT,
            amountGroup = "amount",
            payeeGroup = "payee"
        )
    }

    private fun createSampleTransaction(id: String, timestamp: Long, amount: Double): TransactionRecord {
        return TransactionRecord(
            id = id,
            timestamp = timestamp,
            sourcePackage = "com.test.bank",
            transaction = ActualTransaction(
                account = "Checking",
                amount = amount,
                payee = "Coffee Shop",
                type = ActualTransaction.TYPE_PAYMENT,
                date = "2026-10-11"
            ),
            status = TransactionRecord.STATUS_SENT
        )
    }

    @Test
    fun testCreateAndSerializeFullBackup() {
        val rules = listOf(createSampleRule())
        val transactions = listOf(createSampleTransaction("tx-1", 1000L, 4.50))

        val backupData = backupManager.createBackupData(
            serverUrl = "https://actual.example.com",
            apiToken = "test-token-123",
            attachLocation = true,
            autoPopup = false,
            autoSend = true,
            dismissOriginalNotification = true,
            rules = rules,
            transactions = transactions,
            includeSettings = true,
            includeTransactions = true,
            appVersion = "1.2.0"
        )

        assertEquals(BackupData.CURRENT_VERSION, backupData.version)
        assertEquals("1.2.0", backupData.appVersion)
        assertNotNull(backupData.settings)
        assertNotNull(backupData.transactions)
        assertEquals("https://actual.example.com", backupData.settings?.serverUrl)
        assertEquals("test-token-123", backupData.settings?.apiToken)
        assertTrue(backupData.settings?.attachLocation == true)
        assertTrue(backupData.settings?.autoSend == true)
        assertEquals(1, backupData.settings?.parsingRules?.size)
        assertEquals(1, backupData.transactions?.size)

        val json = backupManager.serializeBackup(backupData)
        assertTrue(json.contains("test-token-123"))
        assertTrue(json.contains("https://actual.example.com"))
        assertTrue(json.contains("Test Bank Alert"))
        assertTrue(json.contains("Coffee Shop"))

        val deserialized = backupManager.deserializeBackup(json)
        assertEquals(backupData.version, deserialized.version)
        assertEquals("1.2.0", deserialized.appVersion)
        assertEquals(backupData.settings?.serverUrl, deserialized.settings?.serverUrl)
        assertEquals(backupData.settings?.apiToken, deserialized.settings?.apiToken)
        assertEquals(1, deserialized.settings?.parsingRules?.size)
        assertEquals(1, deserialized.transactions?.size)
    }

    @Test
    fun testCreateBackupSettingsOnly() {
        val rules = listOf(createSampleRule())
        val backupData = backupManager.createBackupData(
            serverUrl = "https://actual.example.com",
            apiToken = "token",
            attachLocation = false,
            autoPopup = true,
            autoSend = false,
            dismissOriginalNotification = false,
            rules = rules,
            transactions = emptyList(),
            includeSettings = true,
            includeTransactions = false,
            appVersion = "1.0.0"
        )

        assertNotNull(backupData.settings)
        assertNull(backupData.transactions)
        assertEquals(1, backupData.settings?.parsingRules?.size)

        val json = backupManager.serializeBackup(backupData)
        val deserialized = backupManager.deserializeBackup(json)
        assertNotNull(deserialized.settings)
        assertNull(deserialized.transactions)
    }

    @Test
    fun testCreateBackupTransactionsOnly() {
        val transactions = listOf(createSampleTransaction("tx-1", 1000L, 10.0))
        val backupData = backupManager.createBackupData(
            serverUrl = "",
            apiToken = "",
            attachLocation = false,
            autoPopup = false,
            autoSend = false,
            dismissOriginalNotification = false,
            rules = emptyList(),
            transactions = transactions,
            includeSettings = false,
            includeTransactions = true,
            appVersion = "1.0.0"
        )

        assertNull(backupData.settings)
        assertNotNull(backupData.transactions)
        assertEquals(1, backupData.transactions?.size)

        val json = backupManager.serializeBackup(backupData)
        val deserialized = backupManager.deserializeBackup(json)
        assertNull(deserialized.settings)
        assertNotNull(deserialized.transactions)
        assertEquals(1, deserialized.transactions?.size)
    }

    @Test
    fun testCreateBackupThrowsWhenBothFalse() {
        try {
            backupManager.createBackupData(
                serverUrl = "",
                apiToken = "",
                attachLocation = false,
                autoPopup = false,
                autoSend = false,
                dismissOriginalNotification = false,
                rules = emptyList(),
                transactions = emptyList(),
                includeSettings = false,
                includeTransactions = false,
                appVersion = "1.0.0"
            )
            fail("Expected IllegalArgumentException when both settings and transactions are excluded")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("At least one item") == true)
        }
    }

    @Test
    fun testDeserializeThrowsOnEmptyOrMalformed() {
        try {
            backupManager.deserializeBackup("")
            fail("Expected exception on empty string")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("empty") == true)
        }

        try {
            backupManager.deserializeBackup("   ")
            fail("Expected exception on whitespace string")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("empty") == true)
        }

        try {
            backupManager.deserializeBackup("not json at all")
            fail("Expected exception on invalid json")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Malformed") == true)
        }

        try {
            backupManager.deserializeBackup("{\"version\": 1}")
            fail("Expected exception when neither settings nor transactions are present")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("neither settings nor transactions") == true)
        }
    }

    @Test
    fun testMergeTransactionsDeduplicationAndSorting() {
        val tx1 = createSampleTransaction("id-1", 100L, 10.0)
        val tx2 = createSampleTransaction("id-2", 200L, 20.0)
        val tx3 = createSampleTransaction("id-3", 300L, 30.0)
        val tx2Duplicate = createSampleTransaction("id-2", 200L, 20.0)

        val existing = listOf(tx2, tx1) // sorted 200, 100
        val incoming = listOf(tx3, tx2Duplicate) // 300 and duplicate 200

        val merged = BackupManager.mergeTransactions(existing, incoming, maxSize = 100)

        assertEquals(3, merged.size)
        assertEquals("id-3", merged[0].id)
        assertEquals(300L, merged[0].timestamp)
        assertEquals("id-2", merged[1].id)
        assertEquals(200L, merged[1].timestamp)
        assertEquals("id-1", merged[2].id)
        assertEquals(100L, merged[2].timestamp)
    }

    @Test
    fun testMergeTransactionsHonorsMaxSize() {
        val records = (1..10).map { i ->
            createSampleTransaction("id-$i", i * 1000L, i.toDouble())
        }

        val merged = BackupManager.mergeTransactions(emptyList(), records, maxSize = 5)

        assertEquals(5, merged.size)
        // Should retain the 5 newest (10, 9, 8, 7, 6)
        assertEquals("id-10", merged[0].id)
        assertEquals("id-6", merged[4].id)
    }

    @Test
    fun testStreamRoundTrip() {
        val rules = listOf(createSampleRule())
        val transactions = listOf(createSampleTransaction("tx-stream", 5000L, 99.99))
        val original = backupManager.createBackupData(
            serverUrl = "https://actual.test",
            apiToken = "secret",
            attachLocation = false,
            autoPopup = false,
            autoSend = false,
            dismissOriginalNotification = false,
            rules = rules,
            transactions = transactions,
            includeSettings = true,
            includeTransactions = true,
            appVersion = "1.0.0"
        )

        val outputStream = ByteArrayOutputStream()
        backupManager.writeBackup(outputStream, original)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val restored = backupManager.readBackup(inputStream)

        assertEquals(original.version, restored.version)
        assertEquals(original.settings?.serverUrl, restored.settings?.serverUrl)
        assertEquals(1, restored.transactions?.size)
        assertEquals("tx-stream", restored.transactions?.first()?.id)
    }

    @Test
    fun testBackwardCompatibilityWithUnknownFields() {
        val json = """
            {
                "version": 1,
                "timestamp": 1728664358000,
                "appVersion": "1.0.0",
                "futureField": "futureValue",
                "settings": {
                    "serverUrl": "https://server.com",
                    "apiToken": "tok",
                    "attachLocation": true,
                    "unknownSetting": 12345,
                    "parsingRules": []
                }
            }
        """.trimIndent()

        val backup = backupManager.deserializeBackup(json)
        assertNotNull(backup.settings)
        assertEquals("https://server.com", backup.settings?.serverUrl)
        assertTrue(backup.settings?.attachLocation == true)
    }
}
