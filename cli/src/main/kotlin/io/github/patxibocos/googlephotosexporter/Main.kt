package io.github.patxibocos.googlephotosexporter

import io.github.patxibocos.googlephotosexporter.cli.AppArgs
import io.github.patxibocos.googlephotosexporter.cli.getAppArgs
import io.github.patxibocos.googlephotosexporter.exporters.Exporter
import io.github.patxibocos.googlephotosexporter.exporters.exporter
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private fun buildExporter(appArgs: AppArgs, requestTimeoutDuration: Duration): Exporter {
    return exporter(appArgs.prefixPath, appArgs.maxChunkSize, requestTimeoutDuration) {
        when (appArgs.exporter.type) {
            Exporter.ExporterType.BOX -> box(
                boxClientId = System.getenv("BOX_CLIENT_ID"),
                boxClientSecret = System.getenv("BOX_CLIENT_SECRET"),
                boxUserId = System.getenv("BOX_USER_ID"),
            )

            Exporter.ExporterType.DROPBOX -> dropbox(
                dropboxRefreshToken = System.getenv("DROPBOX_REFRESH_TOKEN"),
                dropboxAppKey = System.getenv("DROPBOX_APP_KEY"),
                dropboxAppSecret = System.getenv("DROPBOX_APP_SECRET"),
            )

            Exporter.ExporterType.GITHUB -> github(
                githubAccessToken = System.getenv("GITHUB_ACCESS_TOKEN"),
                githubRepositoryOwner = System.getenv("GITHUB_REPOSITORY_OWNER"),
                githubRepositoryName = System.getenv("GITHUB_REPOSITORY_NAME"),
            )

            Exporter.ExporterType.ONEDRIVE -> onedrive(
                oneDriveClientId = System.getenv("ONEDRIVE_CLIENT_ID"),
                oneDriveClientSecret = System.getenv("ONEDRIVE_CLIENT_SECRET"),
                oneDriveRefreshToken = System.getenv("ONEDRIVE_REFRESH_TOKEN"),
            )
        }
    }
}

private fun buildGooglePhotosRepository(requestTimeoutDuration: Duration): GooglePhotosRepository {
    val clientId = System.getenv("GOOGLE_PHOTOS_CLIENT_ID")
    val clientSecret = System.getenv("GOOGLE_PHOTOS_CLIENT_SECRET")
    val refreshToken = System.getenv("GOOGLE_PHOTOS_REFRESH_TOKEN")
    val googlePhotosClient = googlePhotosHttpClient(clientId, clientSecret, refreshToken, requestTimeoutDuration)
    return GooglePhotosRepository(googlePhotosClient)
}

fun main(args: Array<String>) {
    val appArgs = getAppArgs(args)
    val timeoutDuration = appArgs.timeout?.let(Duration::parse) ?: Duration.INFINITE
    val requestTimeoutDuration = appArgs.requestTimeout?.let(Duration::parse) ?: 5.minutes

    val exporter = buildExporter(appArgs, requestTimeoutDuration)
    val googlePhotosRepository = buildGooglePhotosRepository(requestTimeoutDuration)

    val exportItems = ExportItems(googlePhotosRepository, exporter, appArgs.overrideContent)
    runBlocking {
        val offsetSync = appArgs.offsetId?.let { Sync(it, Instant.MIN) } ?: exporter.get(appArgs.syncFileName)
            ?.toString(Charsets.UTF_8)?.trim()?.let { Json.decodeFromString(it) }
        var lastCompletedSync: Sync? = null
        exportItems(
            offsetSync = offsetSync,
            datePathPattern = appArgs.datePathPattern,
            itemTypes = appArgs.itemTypes,
            timeout = timeoutDuration,
        ).collect { event ->
            when (event) {
                ExportEvent.DownloadFailed -> {}
                ExportEvent.ExportCompleted -> {
                    lastCompletedSync?.let {
                        exporter.upload(
                            data = Json.encodeToString(it).toByteArray(),
                            name = appArgs.syncFileName,
                            filePath = appArgs.syncFileName,
                            overrideContent = true,
                        )
                    }
                }

                ExportEvent.ExportStarted -> {}
                is ExportEvent.ItemDownloaded -> {}
                is ExportEvent.ItemUploaded -> {
                    lastCompletedSync = Sync(event.item.id, event.item.creationTime)
                }

                is ExportEvent.ItemsCollected -> {}
                ExportEvent.UploadFailed -> {}
            }
        }
    }
}
