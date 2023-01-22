package io.github.patxibocos.googlephotosexporter

import io.github.patxibocos.googlephotosexporter.cli.getAppArgs
import io.github.patxibocos.googlephotosexporter.exporters.Exporter
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun main(args: Array<String>) {
    val appArgs = getAppArgs(args)
    val (itemTypes, maxChunkSize, prefixPath, offsetId, datePathPattern, syncFileName, timeout, lastSyncedItem, requestTimeout) = appArgs
    val timeoutDuration = timeout?.let(Duration::parse) ?: Duration.INFINITE
    val requestTimeoutDuration = requestTimeout?.let(Duration::parse) ?: 5.minutes

    val clientId = System.getenv("GOOGLE_PHOTOS_CLIENT_ID")
    val clientSecret = System.getenv("GOOGLE_PHOTOS_CLIENT_SECRET")
    val refreshToken = System.getenv("GOOGLE_PHOTOS_REFRESH_TOKEN")
    val googlePhotosClient = googlePhotosHttpClient(clientId, clientSecret, refreshToken, requestTimeoutDuration)

    val exporter = Exporter.from(appArgs.exporter, prefixPath, maxChunkSize, requestTimeoutDuration)
    val googlePhotosRepository = GooglePhotosRepository(googlePhotosClient, 3)

    val exportItems = ExportItems(googlePhotosRepository, exporter)
    runBlocking {
        val exitCode = exportItems(
            offsetId = offsetId,
            datePathPattern = datePathPattern,
            syncFileName = syncFileName,
            itemTypes = itemTypes,
            timeout = timeoutDuration,
            lastSyncedItem = lastSyncedItem,
        )
        exitProcess(exitCode)
    }
}
