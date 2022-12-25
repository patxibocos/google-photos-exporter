@file:OptIn(ExperimentalCli::class)

package io.github.patxibocos.googlephotosgithubexporter

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
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
            maxChunkSize,
            prefixPath
        )
    }
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

private data class AppArgs(
    val itemTypes: List<ItemType>,
    val maxChunkSize: Int,
    val prefixPath: String,
    val exporter: Subcommands<*>
)

data class GitHubArgs(val repoOwner: String, val repoName: String)

sealed interface Subcommands<T> {
    fun data(): T
    val name: String

    companion object {
        fun byName(name: String): Subcommands<*> {
            return Subcommands::class.sealedSubclasses
                .firstOrNull { it.objectInstance?.name == name }
                ?.objectInstance
                ?: throw IllegalArgumentException("Unknown subcommand: $name")
        }
    }

    object GitHub : Subcommand("github", "GitHub exporter"), Subcommands<GitHubArgs> {

        private val repoOwner by option(ArgType.String, shortName = "ro", description = "Repository owner")
            .required()
        private val repoName by option(ArgType.String, shortName = "rn", description = "Repository name")
            .required()

        override fun execute() {}

        override fun data() = GitHubArgs(repoOwner, repoName)
    }

    object Dropbox : Subcommand("dropbox", "Dropbox exporter"), Subcommands<Unit> {
        override fun execute() {}

        override fun data() = Unit
    }
}

private fun getAppArgs(args: Array<String>): AppArgs {
    val parser = ArgParser("google-photos-github-exporter")
    val itemTypes by parser.option(ArgType.Choice<ItemType>(), shortName = "it", description = "Item types to include")
        .multiple().default(ItemType.values().toList())
    val maxChunkSize by parser.option(
        ArgType.Int,
        shortName = "mcs",
        description = "Max chunk size when uploading to GitHub"
    ).default(25)
    val prefixPath by parser.option(
        ArgType.String,
        shortName = "pp",
        description = "Prefix path to use as parent path for content"
    ).default("")
    parser.subcommands(Subcommands.GitHub, Subcommands.Dropbox)
    val parserResult = parser.parse(args)
    val exporter = Subcommands.byName(parserResult.commandName)
    return AppArgs(
        itemTypes = itemTypes,
        maxChunkSize = maxChunkSize,
        prefixPath = prefixPath,
        exporter = exporter
    )
}
