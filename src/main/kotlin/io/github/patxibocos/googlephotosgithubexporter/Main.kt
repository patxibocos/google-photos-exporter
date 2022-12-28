package io.github.patxibocos.googlephotosgithubexporter

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    val appArgs = getAppArgs(args)
    val googlePhotosClientId = System.getenv("GOOGLE_PHOTOS_CLIENT_ID")
    val googlePhotosClientSecret = System.getenv("GOOGLE_PHOTOS_CLIENT_SECRET")
    val googlePhotosRefreshToken = System.getenv("GOOGLE_PHOTOS_REFRESH_TOKEN")
    val (itemTypes, maxChunkSize, prefixPath) = appArgs

    // Build clients
    val googlePhotosClient = googlePhotosHttpClient()
    val photosClient = photosLibraryClient(googlePhotosClientId, googlePhotosClientSecret, googlePhotosRefreshToken)

    // Build repositories
    val exportRepository = when (val exporter = appArgs.exporter) {
        is Subcommands.Dropbox -> {
            val dropboxRefreshToken = System.getenv("DROPBOX_REFRESH_TOKEN")
            val dropboxAppKey = System.getenv("DROPBOX_APP_KEY")
            val dropboxAppSecret = System.getenv("DROPBOX_APP_SECRET")
            val dropboxClient = dropboxClient(dropboxAppKey, dropboxAppSecret, dropboxRefreshToken)
            DropboxRepository(dropboxClient, prefixPath)
        }

        is Subcommands.GitHub -> {
            val githubAccessToken = System.getenv("GITHUB_ACCESS_TOKEN")
            val client = githubHttpClient(githubAccessToken)
            val githubRepositoryOwner = System.getenv("GITHUB_REPOSITORY_OWNER")
            val githubRepositoryName = System.getenv("GITHUB_REPOSITORY_NAME")
            GitHubRepository(
                client,
                githubRepositoryOwner,
                githubRepositoryName,
                prefixPath
            )
        }
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
