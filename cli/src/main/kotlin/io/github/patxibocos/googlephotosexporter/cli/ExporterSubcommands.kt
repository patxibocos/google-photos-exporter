@file:OptIn(ExperimentalCli::class)

package io.github.patxibocos.googlephotosexporter.cli

import io.github.patxibocos.googlephotosexporter.exporters.Exporter
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand

internal sealed interface ExporterSubcommands<T> {
    fun data(): T
    val type: Exporter.ExporterType
    val name: String

    companion object {
        fun byName(name: String): ExporterSubcommands<*> {
            return when (name) {
                GitHub.name -> GitHub
                Dropbox.name -> Dropbox
                Box.name -> Box
                OneDrive.name -> OneDrive
                else -> throw IllegalArgumentException("Unknown exporter subcommand: $name")
            }
        }
    }

    object GitHub : Subcommand("github", "GitHub exporter"), ExporterSubcommands<Unit> {
        override fun execute() {}
        override fun data() = Unit
        override val type = Exporter.ExporterType.GITHUB
    }

    object Dropbox : Subcommand("dropbox", "Dropbox exporter"), ExporterSubcommands<Unit> {
        override fun execute() {}
        override fun data() = Unit
        override val type = Exporter.ExporterType.DROPBOX
    }

    object Box : Subcommand("box", "Box exporter"), ExporterSubcommands<Unit> {
        override fun execute() {}
        override fun data() = Unit
        override val type = Exporter.ExporterType.BOX
    }

    object OneDrive : Subcommand("onedrive", "OneDrive exporter"), ExporterSubcommands<Unit> {
        override fun execute() {}
        override fun data() = Unit
        override val type = Exporter.ExporterType.ONEDRIVE
    }
}
