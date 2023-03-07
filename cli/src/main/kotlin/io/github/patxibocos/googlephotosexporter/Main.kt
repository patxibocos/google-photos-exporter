package io.github.patxibocos.googlephotosexporter

import io.github.patxibocos.googlephotosexporter.cli.AppArgs
import io.github.patxibocos.googlephotosexporter.cli.getAppArgs
import io.github.patxibocos.googlephotosexporter.exporters.Exporter
import io.github.patxibocos.googlephotosexporter.exporters.exporter
import kotlinx.coroutines.runBlocking
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
        val offsetId = appArgs.offsetId ?: exporter.get(appArgs.syncFileName)?.toString(Charsets.UTF_8)?.trim()
        var lastSyncedItem: String? = null
        exportItems(
            offsetId = offsetId,
            datePathPattern = appArgs.datePathPattern,
            itemTypes = appArgs.itemTypes,
            timeout = timeoutDuration,
        ).collect { event ->
            when (event) {
                ExportEvent.DownloadFailed -> {}
                ExportEvent.ExportCompleted -> {
                    lastSyncedItem?.let {
                        exporter.upload(it.toByteArray(), appArgs.syncFileName, appArgs.syncFileName, true)
                    }
                }

                ExportEvent.ExportStarted -> {}
                is ExportEvent.ItemDownloaded -> {}
                is ExportEvent.ItemUploaded -> {
                    lastSyncedItem = event.item.id
                }

                is ExportEvent.ItemsCollected -> {}
                ExportEvent.UploadFailed -> {}
            }
        }
    }
}
