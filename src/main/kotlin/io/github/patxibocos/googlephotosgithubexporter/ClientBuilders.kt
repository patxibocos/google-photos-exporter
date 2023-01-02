package io.github.patxibocos.googlephotosgithubexporter

import com.box.sdk.BoxAPIConnection
import com.box.sdk.BoxCCGAPIConnection
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.DbxClientV2
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.OAuth2CredentialsWithRefresh
import com.google.photos.library.v1.PhotosLibraryClient
import com.google.photos.library.v1.PhotosLibrarySettings
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

internal fun photosLibraryClient(): PhotosLibraryClient {
    fun getNewAccessToken(): String {
        val clientId = System.getenv("GOOGLE_PHOTOS_CLIENT_ID")
        val clientSecret = System.getenv("GOOGLE_PHOTOS_CLIENT_SECRET")
        val refreshToken = System.getenv("GOOGLE_PHOTOS_REFRESH_TOKEN")
        val scopes = listOf("https://www.googleapis.com/auth/photoslibrary.readonly")
        val tokenResponse =
            GoogleRefreshTokenRequest(
                NetHttpTransport(),
                GsonFactory(),
                refreshToken,
                clientId,
                clientSecret
            ).setScopes(
                scopes
            ).setGrantType("refresh_token").execute()
        return tokenResponse.accessToken
    }

    val settings: PhotosLibrarySettings = PhotosLibrarySettings.newBuilder()
        .setCredentialsProvider(
            FixedCredentialsProvider.create(
                OAuth2CredentialsWithRefresh.newBuilder().setRefreshHandler { AccessToken(getNewAccessToken(), null) }
                    .build()
            )
        )
        .build()
    return PhotosLibraryClient.initialize(settings)
}

internal fun googlePhotosHttpClient(): HttpClient {
    return HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
        }
    }
}

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

internal fun oneDriveHttpClient(clientId: String, clientSecret: String, refreshToken: String): HttpClient {
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
                        url = "https://login.live.com/oauth20_token.srf",
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
