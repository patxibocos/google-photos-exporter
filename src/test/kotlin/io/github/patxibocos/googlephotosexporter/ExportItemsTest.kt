package io.github.patxibocos.googlephotosexporter

import io.github.patxibocos.googlephotosexporter.exporters.Exporter
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ExportItemsTest {

    fun `when no items on photos repository then nothing is uploaded`() {
        val googlePhotosRepository: GooglePhotosRepository = mockk()
        val exporter: Exporter = mockk()
        val exportItems = ExportItems(googlePhotosRepository, exporter, null, "yyyy/MM/dd", "last-synced-item")

        runBlocking { exportItems(ItemType.values().toList()) }
    }

    fun `when many items on photos repository then every item is uploaded in order and last synced item is set`() {
    }

    fun `when emitting an item fails then collection stops and last successful item is set`() {
    }

    fun `when using an offset ID then only items after it are emitted`() {
    }

    fun `when passing an explicit offset ID then repository is not used to get last synced item`() {
    }
}
