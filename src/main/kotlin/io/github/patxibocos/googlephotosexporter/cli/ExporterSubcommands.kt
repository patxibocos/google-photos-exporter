@file:OptIn(ExperimentalCli::class)

package io.github.patxibocos.googlephotosexporter.cli

import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand

internal sealed interface ExporterSubcommands<T> {
    fun data(): T
    val name: String

    companion object {
        fun byName(name: String): ExporterSubcommands<*> {
            return ExporterSubcommands::class.sealedSubclasses
                .firstOrNull { it.objectInstance?.name == name }
                ?.objectInstance
                ?: throw IllegalArgumentException("Unknown subcommand: $name")
        }
    }

    object GitHub : Subcommand("github", "GitHub exporter"), ExporterSubcommands<Unit> {
        override fun execute() {}

        override fun data() = Unit
    }

    object Dropbox : Subcommand("dropbox", "Dropbox exporter"), ExporterSubcommands<Unit> {
        override fun execute() {}

        override fun data() = Unit
    }

    object Box : Subcommand("box", "Box exporter"), ExporterSubcommands<Unit> {
        override fun execute() {}

        override fun data() = Unit
    }

    object OneDrive : Subcommand("onedrive", "OneDrive exporter"), ExporterSubcommands<Unit> {
        override fun execute() {}

        override fun data() = Unit
    }
}
