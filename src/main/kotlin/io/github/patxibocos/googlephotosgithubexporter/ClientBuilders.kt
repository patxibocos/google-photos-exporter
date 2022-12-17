package io.github.patxibocos.googlephotosgithubexporter

import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.OAuth2Credentials
import com.google.photos.library.v1.PhotosLibraryClient
import com.google.photos.library.v1.PhotosLibrarySettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal fun photosLibraryClient(clientId: String, clientSecret: String, refreshToken: String): PhotosLibraryClient {
    fun getNewAccessToken(): String {
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

    val accessToken = getNewAccessToken()
    val settings: PhotosLibrarySettings = PhotosLibrarySettings.newBuilder()
        .setCredentialsProvider(
            FixedCredentialsProvider.create(
                OAuth2Credentials.newBuilder().setAccessToken(AccessToken(accessToken, null)).build()
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

internal fun githubHttpClient(token: String): HttpClient {
    return HttpClient(CIO) {
        install(Auth) {
            bearer {
                loadTokens {
                    BearerTokens(token, "")
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
