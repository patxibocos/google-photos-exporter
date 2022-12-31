package io.github.patxibocos.googlephotosgithubexporter

import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val appArgs = getAppArgs(args)
    val (itemTypes, maxChunkSize, prefixPath, offsetId) = appArgs

    val googlePhotosClient = googlePhotosHttpClient()
    val photosClient = photosLibraryClient()

    val exportRepository = ExportRepository.forExporter(appArgs.exporter, prefixPath, maxChunkSize)
    val googlePhotosRepository = GooglePhotosRepository(photosClient, googlePhotosClient)

    val exportItems = ExportItems(googlePhotosRepository, exportRepository, offsetId)
    runBlocking {
        val exitCode = exportItems(itemTypes)
        exitProcess(exitCode)
    }
}
