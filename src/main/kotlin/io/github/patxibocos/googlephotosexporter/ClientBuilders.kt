package io.github.patxibocos.googlephotosexporter

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TokenInfo(@SerialName("access_token") val accessToken: String)

private fun httpClientForOAuth(
    refreshUrl: String,
    clientId: String,
    clientSecret: String,
    grantType: String,
    refreshToken: String?,
    vararg extraParams: Pair<String, String>
): HttpClient {
    val bearerTokenStorage = mutableListOf<BearerTokens>()
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                }
            )
        }
    }
    return client.config {
        install(Auth) {
            bearer {
                loadTokens {
                    // Sending this dummy value for Dropbox (because when sending an empty value the API doesn't return a 401)
                    BearerTokens("sl.", refreshToken ?: "")
                }
                refreshTokens {
                    val response = client.submitForm(
                        url = refreshUrl,
                        formParameters = Parameters.build {
                            append("grant_type", grantType)
                            append("client_id", clientId)
                            append("client_secret", clientSecret)
                            refreshToken?.let { append("refresh_token", it) }
                            extraParams.forEach { (key, value) ->
                                append(key, value)
                            }
                        }
                    )
                    val refreshTokenInfo = response.body<TokenInfo>()
                    bearerTokenStorage.add(BearerTokens(refreshTokenInfo.accessToken, refreshToken ?: ""))
                    bearerTokenStorage.last()
                }
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
        }
    }
}

internal fun googlePhotosHttpClient(clientId: String, clientSecret: String, refreshToken: String): HttpClient =
    httpClientForOAuth(
        "https://accounts.google.com/o/oauth2/token",
        clientId,
        clientSecret,
        "refresh_token",
        refreshToken
    )

internal fun githubHttpClient(accessToken: String): HttpClient {
    return HttpClient(CIO) {
        install(Auth) {
            bearer {
                loadTokens {
                    BearerTokens(accessToken, "")
                }
            }
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                }
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
        }
    }
}

internal fun dropboxHttpClient(appKey: String, appSecret: String, refreshToken: String): HttpClient =
    httpClientForOAuth("https://api.dropbox.com/oauth2/token", appKey, appSecret, "refresh_token", refreshToken)

internal fun boxHttpClient(boxClientId: String, boxClientSecret: String, boxUserId: String): HttpClient =
    httpClientForOAuth(
        "https://api.box.com/oauth2/token",
        boxClientId,
        boxClientSecret,
        "client_credentials",
        null,
        "box_subject_type" to "user",
        "box_subject_id" to boxUserId
    )

internal fun oneDriveHttpClient(clientId: String, clientSecret: String, refreshToken: String): HttpClient =
    httpClientForOAuth(
        "https://login.live.com/oauth20_token.srf",
        clientId,
        clientSecret,
        "refresh_token",
        refreshToken
    )
