package de.gumerbaev.actual.network

import de.gumerbaev.actual.model.ActualTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object ActualApiClient {

    data class ApiResult(
        val success: Boolean,
        val httpCode: Int = 0,
        val responseBody: String? = null,
        val errorMessage: String? = null
    )

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Sends the transaction to the specified server URL.
     */
    suspend fun sendTransaction(
        serverUrl: String,
        apiToken: String?,
        transaction: ActualTransaction
    ): ApiResult = withContext(Dispatchers.IO) {
        val jsonPayload = transaction.toJson()
        return@withContext postJson(serverUrl, apiToken, jsonPayload)
    }

    /**
     * Tests connectivity to the server URL.
     */
    suspend fun testConnection(
        serverUrl: String,
        apiToken: String?
    ): ApiResult = withContext(Dispatchers.IO) {
        val testTransaction = ActualTransaction(
            account = "Test Account",
            amount = 0.0,
            payee = "Connection Test",
            type = ActualTransaction.TYPE_PAYMENT,
            date = ActualTransaction.todayFormatted()
        )
        return@withContext postJson(serverUrl, apiToken, testTransaction.toJson())
    }

    private fun postJson(
        serverUrl: String,
        apiToken: String?,
        json: String
    ): ApiResult {
        if (serverUrl.isBlank()) {
            return ApiResult(false, errorMessage = "Server URL cannot be empty.")
        }

        val requestBody = json.toRequestBody(JSON_MEDIA_TYPE)
        val requestBuilder = Request.Builder()
            .url(serverUrl)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")

        if (!apiToken.isNullOrBlank()) {
            val token = apiToken.trim()
            if (token.startsWith("Bearer ", ignoreCase = true)) {
                requestBuilder.addHeader("Authorization", token)
            } else {
                requestBuilder.addHeader("Authorization", "Bearer $token")
                requestBuilder.addHeader("x-api-key", token)
            }
        }

        return try {
            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body.string()
                val isSuccess = response.isSuccessful // 2xx status codes

                ApiResult(
                    success = isSuccess,
                    httpCode = code,
                    responseBody = body,
                    errorMessage = if (!isSuccess) "Server returned HTTP $code: $body" else null
                )
            }
        } catch (e: Exception) {
            ApiResult(
                success = false,
                errorMessage = e.localizedMessage ?: e.javaClass.simpleName
            )
        }
    }
}
