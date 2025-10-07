package com.example.telegramtextapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class TextsRepository(
    private val client: OkHttpClient = defaultClient,
    private val json: Json = defaultJson
) {

    suspend fun fetchTexts(token: String): RemoteResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BuildConfig.TEXTS_API_BASE_URL.trimEnd('/') + "/texts")
            .get()
            .header("Authorization", "Bearer ${'$'}token")
            .build()

        val response = client.newCall(request).execute()

        response.use { res ->
            val rawBody = res.body?.string().orEmpty()
            if (res.isSuccessful) {
                if (rawBody.isBlank()) {
                    return@use RemoteResult.NetworkError("Empty response from server")
                }
                val parsed = json.decodeFromString(TextsResponse.serializer(), rawBody)
                val normalized = parsed.toDisplayTexts()
                return@use RemoteResult.Success(normalized)
            }

            val parsedError = runCatching {
                json.decodeFromString(ErrorResponse.serializer(), rawBody)
            }.getOrNull()

            return@use when (res.code) {
                401 -> RemoteResult.InvalidToken(
                    message = parsedError?.error ?: "Invalid token",
                    botLink = parsedError?.botLink
                )

                403 -> RemoteResult.SubscriptionInactive(
                    message = parsedError?.error ?: "Subscription inactive",
                    botLink = parsedError?.botLink,
                    subscription = parsedError?.subscription
                )

                else -> RemoteResult.NetworkError(
                    message = parsedError?.error ?: "Unexpected HTTP ${'$'}{res.code}"
                )
            }
        }
    }

    companion object {
        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder().build()
        }
        private val defaultJson: Json by lazy {
            Json { ignoreUnknownKeys = true }
        }
    }
}

sealed class RemoteResult {
    data class Success(val payload: FetchedTexts) : RemoteResult()
    data class SubscriptionInactive(
        val message: String,
        val botLink: String?,
        val subscription: SubscriptionPayload?
    ) : RemoteResult()

    data class InvalidToken(val message: String, val botLink: String?) : RemoteResult()
    data class NetworkError(val message: String) : RemoteResult()
}
