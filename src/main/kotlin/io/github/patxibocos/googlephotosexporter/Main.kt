package io.github.patxibocos.googlephotosexporter

import io.github.patxibocos.googlephotosexporter.cli.getAppArgs
import io.github.patxibocos.googlephotosexporter.exporters.Exporter
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val appArgs = getAppArgs(args)
    val (itemTypes, maxChunkSize, prefixPath, offsetId, datePathPattern, syncFileName) = appArgs

    val clientId = System.getenv("GOOGLE_PHOTOS_CLIENT_ID")
    val clientSecret = System.getenv("GOOGLE_PHOTOS_CLIENT_SECRET")
    val refreshToken = System.getenv("GOOGLE_PHOTOS_REFRESH_TOKEN")
    val googlePhotosClient = googlePhotosHttpClient(clientId, clientSecret, refreshToken)

    val exporter = Exporter.from(appArgs.exporter, prefixPath, maxChunkSize)
    val googlePhotosRepository = GooglePhotosRepository(googlePhotosClient, 3)

    val exportItems = ExportItems(googlePhotosRepository, exporter, offsetId, datePathPattern, syncFileName)
    runBlocking {
        val exitCode = exportItems(itemTypes)
        exitProcess(exitCode)
    }
}
