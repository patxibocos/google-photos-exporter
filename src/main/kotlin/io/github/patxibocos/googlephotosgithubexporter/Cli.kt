@file:OptIn(ExperimentalCli::class)

package io.github.patxibocos.googlephotosgithubexporter

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import kotlinx.cli.multiple

internal data class AppArgs(
    val itemTypes: List<ItemType>,
    val maxChunkSize: Int?,
    val prefixPath: String,
    val exporter: Subcommands<*>
)

internal fun getAppArgs(args: Array<String>): AppArgs {
    val parser = ArgParser("google-photos-exporter")
    val itemTypes by parser.option(ArgType.Choice<ItemType>(), shortName = "it", description = "Item types to include")
        .multiple().default(ItemType.values().toList())
    val maxChunkSize by parser.option(
        ArgType.Int,
        shortName = "mcs",
        description = "Max chunk size when uploading to GitHub"
    )
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

    object GitHub : Subcommand("github", "GitHub exporter"), Subcommands<Unit> {
        override fun execute() {}

        override fun data() = Unit
    }

    object Dropbox : Subcommand("dropbox", "Dropbox exporter"), Subcommands<Unit> {
        override fun execute() {}

        override fun data() = Unit
    }
}
