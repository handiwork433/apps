package com.example.telegramtextapp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TextsPayload(
    val title: String = "",
    val subtitle: String = "",
    val body: String = ""
)

@Serializable
data class TextsResponse(
    val data: TextsPayload = TextsPayload(),
    @SerialName("last_updated") val lastUpdated: String? = null,
    val subscription: SubscriptionPayload? = null
)

data class DisplayTexts(
    val title: String,
    val subtitle: String,
    val body: String
)

fun TextsResponse.toDisplayTexts(): FetchedTexts = FetchedTexts(
    texts = DisplayTexts(
        title = data.title,
        subtitle = data.subtitle,
        body = data.body
    ),
    lastUpdated = lastUpdated,
    subscription = subscription
)

data class FetchedTexts(
    val texts: DisplayTexts,
    val lastUpdated: String?,
    val subscription: SubscriptionPayload?
)

@Serializable
data class SubscriptionPayload(
    val active: Boolean = false,
    @SerialName("expires_at") val expiresAt: String? = null
)

@Serializable
data class ErrorResponse(
    val error: String? = null,
    @SerialName("bot_link") val botLink: String? = null,
    val subscription: SubscriptionPayload? = null
)
