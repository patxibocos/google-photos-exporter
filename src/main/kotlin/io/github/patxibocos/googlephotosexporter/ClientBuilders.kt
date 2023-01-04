package io.github.patxibocos.googlephotosexporter

import com.box.sdk.BoxAPIConnection
import com.box.sdk.BoxCCGAPIConnection
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.DbxClientV2
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
    refreshToken: String
): HttpClient {
    val bearerTokenStorage = mutableListOf<BearerTokens>()
    return HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                }
            )
        }
        install(Auth) {
            bearer {
                loadTokens {
                    BearerTokens("", refreshToken)
                }
                refreshTokens {
                    val refreshTokenInfo: TokenInfo = client.submitForm(
                        url = refreshUrl,
                        formParameters = Parameters.build {
                            append("grant_type", "refresh_token")
                            append("client_id", clientId)
                            append("client_secret", clientSecret)
                            append("refresh_token", oldTokens?.refreshToken ?: "")
                        }
                    ) { markAsRefreshTokenRequest() }.body()
                    bearerTokenStorage.add(BearerTokens(refreshTokenInfo.accessToken, oldTokens?.refreshToken!!))
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
    httpClientForOAuth("https://accounts.google.com/o/oauth2/token", clientId, clientSecret, refreshToken)

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

internal fun dropboxClient(appKey: String, appSecret: String, refreshToken: String): DbxClientV2 {
    val config = DbxRequestConfig.newBuilder("google-photos-exporter").build()
    val credentials = DbxCredential(
        "",
        Long.MAX_VALUE,
        refreshToken,
        appKey,
        appSecret
    )
    return DbxClientV2(config, credentials).apply {
        // Forcing refresh as we are initially passing an empty token
        this.refreshAccessToken()
    }
}

internal fun boxClient(boxClientId: String, boxClientSecret: String, boxUserId: String): BoxAPIConnection {
    return BoxCCGAPIConnection.userConnection(boxClientId, boxClientSecret, boxUserId)
}

internal fun boxHttpClient(): HttpClient {
    return HttpClient(CIO)
}

internal fun oneDriveHttpClient(clientId: String, clientSecret: String, refreshToken: String): HttpClient =
    httpClientForOAuth("https://login.live.com/oauth20_token.srf", clientId, clientSecret, refreshToken)
