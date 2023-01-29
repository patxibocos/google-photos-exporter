package io.github.patxibocos.googlephotosexporter

import io.github.patxibocos.googlephotosexporter.cli.getAppArgs
import io.github.patxibocos.googlephotosexporter.exporters.Exporter
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun main(args: Array<String>) {
    val appArgs = getAppArgs(args)
    val timeoutDuration = appArgs.timeout?.let(Duration::parse) ?: Duration.INFINITE
    val requestTimeoutDuration = appArgs.requestTimeout?.let(Duration::parse) ?: 5.minutes

    val clientId = System.getenv("GOOGLE_PHOTOS_CLIENT_ID")
    val clientSecret = System.getenv("GOOGLE_PHOTOS_CLIENT_SECRET")
    val refreshToken = System.getenv("GOOGLE_PHOTOS_REFRESH_TOKEN")
    val googlePhotosClient = googlePhotosHttpClient(clientId, clientSecret, refreshToken, requestTimeoutDuration)

    val exporter = Exporter.from(appArgs.exporter, appArgs.prefixPath, appArgs.maxChunkSize, requestTimeoutDuration)
    val googlePhotosRepository = GooglePhotosRepository(googlePhotosClient)

    val exportItems = ExportItems(googlePhotosRepository, exporter, appArgs.overrideContent)
    runBlocking {
        val exitCode = exportItems(
            offsetId = appArgs.offsetId,
            datePathPattern = appArgs.datePathPattern,
            syncFileName = appArgs.syncFileName,
            itemTypes = appArgs.itemTypes,
            timeout = timeoutDuration,
            lastSyncedItem = appArgs.lastSyncedItem,
        )
        exitProcess(exitCode)
    }
}
