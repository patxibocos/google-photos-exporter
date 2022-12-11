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
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

private fun photosLibraryClient(clientId: String, clientSecret: String, refreshToken: String): PhotosLibraryClient {
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

fun main(args: Array<String>) {
    val appArgs = getAppArgs(args)
    val (githubToken, githubRepoOwner, githubRepoName, googlePhotosClientId, googlePhotosClientSecret, googlePhotosRefreshToken) = appArgs
    val googlePhotosClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
        }
    }
    val githubClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                }
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
        }
    }

    val photosClient = photosLibraryClient(googlePhotosClientId, googlePhotosClientSecret, googlePhotosRefreshToken)
    val googlePhotosRepository = GooglePhotosRepository(photosClient, googlePhotosClient)
    val gitHubContentsRepository = GitHubContentsRepository(githubToken, githubClient, githubRepoOwner, githubRepoName)

    val exportPhotos = ExportPhotos(googlePhotosRepository, gitHubContentsRepository)
    runBlocking {
        exportPhotos()
    }
}

private data class AppArgs(
    val githubToken: String,
    val githubRepoOwner: String,
    val githubRepoName: String,
    val googlePhotosClientId: String,
    val googlePhotosClientSecret: String,
    val googlePhotosRefreshToken: String,
)

private fun getAppArgs(args: Array<String>): AppArgs {
    val parser = ArgParser("google-photos-github-exporter")
    val githubToken by parser.option(ArgType.String, shortName = "gt", description = "GitHub token").required()
    val githubRepoOwner by parser.option(ArgType.String, shortName = "gro", description = "GitHub repository owner")
        .required()
    val githubRepoName by parser.option(ArgType.String, shortName = "grn", description = "GitHub repository name")
        .required()
    val googlePhotosClientId by parser.option(
        ArgType.String,
        shortName = "gpci",
        description = "Google Photos client ID"
    ).required()
    val googlePhotosClientSecret by parser.option(
        ArgType.String,
        shortName = "gpcs",
        description = "Google Photos client secret"
    ).required()
    val googlePhotosRefreshToken by parser.option(
        ArgType.String,
        shortName = "gprt",
        description = "Google Photos refresh token"
    ).required()
    parser.parse(args)
    return AppArgs(
        githubToken = githubToken,
        githubRepoOwner = githubRepoOwner,
        githubRepoName = githubRepoName,
        googlePhotosClientId = googlePhotosClientId,
        googlePhotosClientSecret = googlePhotosClientSecret,
        googlePhotosRefreshToken = googlePhotosRefreshToken,
    )
}
