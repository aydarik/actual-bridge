package de.gumerbaev.actual.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.gumerbaev.actual.model.ParsingRule
import de.gumerbaev.actual.model.TransactionRecord

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "actual_app_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_TOKEN = "api_token"
        private const val KEY_ATTACH_LOCATION = "attach_location"
        private const val KEY_AUTO_POPUP = "auto_popup"
        private const val KEY_PARSING_RULES = "parsing_rules"
        private const val KEY_TRANSACTION_HISTORY = "transaction_history"
        private const val MAX_HISTORY_SIZE = 100
    }

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "http://10.0.2.2:5006/transaction") ?: "http://10.0.2.2:5006/transaction"
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value.trim()).apply()

    var apiToken: String
        get() = prefs.getString(KEY_API_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_TOKEN, value.trim()).apply()

    var attachLocation: Boolean
        get() = prefs.getBoolean(KEY_ATTACH_LOCATION, false)
        set(value) = prefs.edit().putBoolean(KEY_ATTACH_LOCATION, value).apply()

    var autoPopup: Boolean
        get() = prefs.getBoolean(KEY_AUTO_POPUP, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_POPUP, value).apply()

    fun getRules(): List<ParsingRule> {
        val json = prefs.getString(KEY_PARSING_RULES, null)
        if (json.isNullOrBlank()) {
            val defaults = ParsingRule.createDefaultRules()
            saveRules(defaults)
            return defaults
        }
        return try {
            val type = object : TypeToken<List<ParsingRule>>() {}.type
            gson.fromJson(json, type) ?: ParsingRule.createDefaultRules()
        } catch (e: Exception) {
            ParsingRule.createDefaultRules()
        }
    }

    fun saveRules(rules: List<ParsingRule>) {
        val json = gson.toJson(rules)
        prefs.edit().putString(KEY_PARSING_RULES, json).apply()
    }

    fun addRule(rule: ParsingRule) {
        val rules = getRules().toMutableList()
        rules.add(0, rule)
        saveRules(rules)
    }

    fun updateRule(rule: ParsingRule) {
        val rules = getRules().toMutableList()
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index != -1) {
            rules[index] = rule
            saveRules(rules)
        }
    }

    fun deleteRule(ruleId: String) {
        val rules = getRules().toMutableList()
        rules.removeAll { it.id == ruleId }
        saveRules(rules)
    }

    fun getHistory(): List<TransactionRecord> {
        val json = prefs.getString(KEY_TRANSACTION_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<TransactionRecord>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addHistoryRecord(record: TransactionRecord) {
        val history = getHistory().toMutableList()
        history.add(0, record)
        if (history.size > MAX_HISTORY_SIZE) {
            history.subList(MAX_HISTORY_SIZE, history.size).clear()
        }
        val json = gson.toJson(history)
        prefs.edit().putString(KEY_TRANSACTION_HISTORY, json).apply()
    }

    fun updateHistoryRecord(recordId: String, status: String, httpCode: Int? = null, responseDetails: String? = null) {
        val history = getHistory().toMutableList()
        val index = history.indexOfFirst { it.id == recordId }
        if (index != -1) {
            val existing = history[index]
            existing.status = status
            existing.httpCode = httpCode
            existing.responseDetails = responseDetails
            val json = gson.toJson(history)
            prefs.edit().putString(KEY_TRANSACTION_HISTORY, json).apply()
        }
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_TRANSACTION_HISTORY).apply()
    }
}
