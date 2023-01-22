package io.github.patxibocos.googlephotosexporter.cli

import io.github.patxibocos.googlephotosexporter.ItemType
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.default
import kotlinx.cli.multiple

internal data class AppArgs(
    val itemTypes: List<ItemType>,
    val maxChunkSize: Int?,
    val prefixPath: String,
    val offsetId: String?,
    val datePathPattern: String,
    val syncFileName: String,
    val timeout: String?,
    val lastSyncedItem: String?,
    val requestTimeout: String?,
    val overrideContent: Boolean,
    val exporter: ExporterSubcommands<*>,
)

@OptIn(ExperimentalCli::class)
internal fun getAppArgs(args: Array<String>): AppArgs {
    val parser = ArgParser("google-photos-exporter")
    val itemTypes by parser.option(
        ArgType.Choice<ItemType>(),
        shortName = "it",
        description = "Item types to include",
    ).multiple().default(ItemType.values().toList())
    val maxChunkSize by parser.option(
        ArgType.Int,
        shortName = "mcs",
        description = "Max chunk size when uploading to GitHub",
    )
    val prefixPath by parser.option(
        ArgType.String,
        shortName = "pp",
        description = "Prefix path to use as parent path for content",
    ).default("")
    val offsetId by parser.option(
        ArgType.String,
        shortName = "oi",
        description = "ID of the item to use as offset (not included)",
    )
    val datePathPattern by parser.option(
        ArgType.String,
        shortName = "dpp",
        description = "LocalDate pattern to use for the path of the item",
    ).default("yyyy/MM/dd")
    val syncFileName by parser.option(
        ArgType.String,
        shortName = "sfn",
        description = "Name of the file where last successful item ID will be stored",
    ).default("last-synced-item")
    val timeout by parser.option(
        ArgType.String,
        shortName = "to",
        description = "Timeout for the runner",
    )
    val lastSyncedItem by parser.option(
        ArgType.String,
        shortName = "lsi",
        description = "ID of the last synced item",
    )
    val requestTimeout by parser.option(
        ArgType.String,
        shortName = "rto",
        description = "Timeout for the requests",
    )
    val overrideContent by parser.option(
        ArgType.Boolean,
        shortName = "oc",
        description = "Whether to override content",
    ).default(false)
    parser.subcommands(
        ExporterSubcommands.GitHub,
        ExporterSubcommands.Dropbox,
        ExporterSubcommands.Box,
        ExporterSubcommands.OneDrive,
    )
    val parserResult = parser.parse(args)
    val exporter = ExporterSubcommands.byName(parserResult.commandName)
    return AppArgs(
        itemTypes = itemTypes,
        maxChunkSize = maxChunkSize,
        prefixPath = prefixPath,
        offsetId = offsetId,
        datePathPattern = datePathPattern,
        syncFileName = syncFileName,
        timeout = timeout,
        lastSyncedItem = lastSyncedItem,
        requestTimeout = requestTimeout,
        overrideContent = overrideContent,
        exporter = exporter,
    )
}
