package io.github.patxibocos.googlephotosgithubexporter

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.multiple
import kotlinx.cli.required
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    val appArgs = getAppArgs(args)
    val githubToken = System.getenv("GITHUB_TOKEN")
    val googlePhotosClientId = System.getenv("GOOGLE_PHOTOS_CLIENT_ID")
    val googlePhotosClientSecret = System.getenv("GOOGLE_PHOTOS_CLIENT_SECRET")
    val googlePhotosRefreshToken = System.getenv("GOOGLE_PHOTOS_REFRESH_TOKEN")
    val (githubRepoOwner, githubRepoName) = appArgs

    // Build clients
    val googlePhotosClient = googlePhotosHttpClient()
    val githubClient = githubHttpClient(githubToken)
    val photosClient = photosLibraryClient(googlePhotosClientId, googlePhotosClientSecret, googlePhotosRefreshToken)

    // Build repositories
    val googlePhotosRepository = GooglePhotosRepository(photosClient, googlePhotosClient)
    val gitHubContentsRepository = GitHubContentsRepository(githubClient, githubRepoOwner, githubRepoName)

    val exportItems = ExportItems(googlePhotosRepository, gitHubContentsRepository)
    runBlocking {
        appArgs.itemTypes.forEach { itemType ->
            launch {
                exportItems(itemType)
            }
        }
    }
}

private data class AppArgs(
    val githubRepoOwner: String,
    val githubRepoName: String,
    val itemTypes: List<ItemType>
)

private fun getAppArgs(args: Array<String>): AppArgs {
    val parser = ArgParser("google-photos-github-exporter")
    val githubRepoOwner by parser.option(ArgType.String, shortName = "gro", description = "GitHub repository owner")
        .required()
    val githubRepoName by parser.option(ArgType.String, shortName = "grn", description = "GitHub repository name")
        .required()
    val itemTypes by parser.option(ArgType.Choice<ItemType>(), shortName = "it", description = "Item types to include")
        .multiple().default(ItemType.values().toList())
    parser.parse(args)
    return AppArgs(
        githubRepoOwner = githubRepoOwner,
        githubRepoName = githubRepoName,
        itemTypes = itemTypes
    )
}
