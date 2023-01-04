package io.github.patxibocos.googlephotosexporter

import io.github.patxibocos.googlephotosexporter.cli.getAppArgs
import io.github.patxibocos.googlephotosexporter.exporters.Exporter
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val appArgs = getAppArgs(args)
    val (itemTypes, maxChunkSize, prefixPath, offsetId, datePathPattern, syncFileName) = appArgs

    val googlePhotosClient = googlePhotosHttpClient()
    val photosClient = photosLibraryClient()

    val exporter = Exporter.from(appArgs.exporter, prefixPath, maxChunkSize)
    val googlePhotosRepository = GooglePhotosRepository(photosClient, googlePhotosClient)

    val exportItems = ExportItems(googlePhotosRepository, exporter, offsetId, datePathPattern, syncFileName)
    runBlocking {
        val exitCode = exportItems(itemTypes)
        exitProcess(exitCode)
    }
}
