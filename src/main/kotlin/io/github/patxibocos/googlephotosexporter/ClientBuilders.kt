package io.github.patxibocos.googlephotosexporter

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration

@Serializable
data class TokenInfo(@SerialName("access_token") val accessToken: String)

private fun httpClientForOAuth(
    refreshUrl: String,
    clientId: String,
    clientSecret: String,
    grantType: String,
    refreshToken: String?,
    requestTimeout: Duration,
    vararg extraParams: Pair<String, String>,
): HttpClient {
    val bearerTokenStorage = mutableListOf<BearerTokens>()
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                },
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
                        },
                    )
                    val refreshTokenInfo = response.body<TokenInfo>()
                    bearerTokenStorage.add(BearerTokens(refreshTokenInfo.accessToken, refreshToken ?: ""))
                    bearerTokenStorage.last()
                }
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = requestTimeout.inWholeMilliseconds
        }
    }
}

internal fun googlePhotosHttpClient(
    clientId: String,
    clientSecret: String,
    refreshToken: String,
    requestTimeout: Duration,
): HttpClient =
    httpClientForOAuth(
        "https://accounts.google.com/o/oauth2/token",
        clientId,
        clientSecret,
        "refresh_token",
        refreshToken,
        requestTimeout,
    )

internal fun githubHttpClient(accessToken: String, requestTimeout: Duration): HttpClient {
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
                },
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = requestTimeout.inWholeMilliseconds
        }
    }
}

internal fun dropboxHttpClient(
    appKey: String,
    appSecret: String,
    refreshToken: String,
    requestTimeout: Duration,
): HttpClient =
    httpClientForOAuth(
        "https://api.dropbox.com/oauth2/token",
        appKey,
        appSecret,
        "refresh_token",
        refreshToken,
        requestTimeout,
    )

internal fun boxHttpClient(
    boxClientId: String,
    boxClientSecret: String,
    boxUserId: String,
    requestTimeout: Duration,
): HttpClient =
    httpClientForOAuth(
        "https://api.box.com/oauth2/token",
        boxClientId,
        boxClientSecret,
        "client_credentials",
        null,
        requestTimeout,
        "box_subject_type" to "user",
        "box_subject_id" to boxUserId,
    )

internal fun oneDriveHttpClient(
    clientId: String,
    clientSecret: String,
    refreshToken: String,
    requestTimeout: Duration,
): HttpClient =
    httpClientForOAuth(
        "https://login.live.com/oauth20_token.srf",
        clientId,
        clientSecret,
        "refresh_token",
        refreshToken,
        requestTimeout,
    )

suspend fun HttpClient.requestWithRetry(
    urlString: String,
    method: HttpMethod,
    dontRetryFor: List<HttpStatusCode> = emptyList(),
    maxRetries: Int = 3,
    block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse {
    val response = try {
        request(HttpRequestBuilder().apply { url(urlString); block(); this.method = method })
    } catch (_: HttpRequestTimeoutException) {
        if (maxRetries > 0) {
            return requestWithRetry(urlString, method, dontRetryFor, maxRetries - 1, block)
        } else {
            throw Exception("Request $method $urlString failed with timeout")
        }
    }
    val shouldRetry = !response.status.isSuccess() && !dontRetryFor.contains(response.status)
    if (shouldRetry) {
        if (maxRetries > 0) {
            return requestWithRetry(urlString, method, dontRetryFor, maxRetries - 1, block)
        } else {
            throw DownloadError("Request failed (status ${response.status}): ${response.body<String>()}")
        }
    }
    return response
}

internal class DownloadError(m: String) : Exception(m)
