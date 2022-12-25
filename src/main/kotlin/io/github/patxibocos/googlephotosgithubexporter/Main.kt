package io.github.patxibocos.googlephotosgithubexporter

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    val appArgs = getAppArgs(args)
    val githubToken = System.getenv("GITHUB_TOKEN")
    val googlePhotosClientId = System.getenv("GOOGLE_PHOTOS_CLIENT_ID")
    val googlePhotosClientSecret = System.getenv("GOOGLE_PHOTOS_CLIENT_SECRET")
    val googlePhotosRefreshToken = System.getenv("GOOGLE_PHOTOS_REFRESH_TOKEN")
    val (itemTypes, maxChunkSize, prefixPath) = appArgs

    // Build clients
    val googlePhotosClient = googlePhotosHttpClient()
    val githubClient = githubHttpClient(githubToken)
    val photosClient = photosLibraryClient(googlePhotosClientId, googlePhotosClientSecret, googlePhotosRefreshToken)

    // Build repositories
    val exportRepository = when (val exporter = appArgs.exporter) {
        is Subcommands.Dropbox -> {
            TODO("Dropbox exporter not implemented yet")
        }

        is Subcommands.GitHub -> GitHubContentsRepository(
            githubClient,
            exporter.data().repoOwner,
            exporter.data().repoName,
            prefixPath
        )
    }.decorate(maxChunkSize)
    val googlePhotosRepository = GooglePhotosRepository(photosClient, googlePhotosClient)

    val exportItems = ExportItems(googlePhotosRepository, exportRepository)
    runBlocking {
        itemTypes.forEach { itemType ->
            launch {
                exportItems(itemType)
            }
        }
    }
}
