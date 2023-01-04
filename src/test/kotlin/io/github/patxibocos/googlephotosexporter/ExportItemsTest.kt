package io.github.patxibocos.googlephotosexporter

import io.github.patxibocos.googlephotosexporter.exporters.Exporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import kotlin.test.Test

@ExtendWith(MockKExtension::class)
class ExportItemsTest {

    @Test
    fun `when no items on photos repository then nothing is uploaded`() = runTest {
        val googlePhotosRepository: GooglePhotosRepository = mockk {
            every { download(any()) } returns emptyFlow()
        }
        val exporter: Exporter = mockk {
            coEvery { this@mockk.get(any()) } returns null
        }
        val exportItems = ExportItems(googlePhotosRepository, exporter, null, "yyyy/MM/dd", "last-synced-item")

        exportItems(ItemType.values().toList())

        coVerify(exactly = 0) { exporter.upload(any(), any(), any(), any()) }
    }

    @Test
    fun `when many items on photos repository then every item is uploaded in order and last synced item is set`() =
        runTest {
            val googlePhotosRepository: GooglePhotosRepository = mockk {
                every { download(any()) } returns flowOf(
                    Item(byteArrayOf(), "id1", "item1.jpg", Instant.EPOCH),
                    Item(byteArrayOf(), "id2", "item2.jpg", Instant.EPOCH)
                )
            }
            val exporter: Exporter = mockk {
                coEvery { this@mockk.get(any()) } returns null
                coEvery { upload(any(), any(), any(), any()) } returns Unit
            }
            val exportItems = ExportItems(googlePhotosRepository, exporter, null, "yyyy/MM/dd", "last-synced-item")

            exportItems(ItemType.values().toList())

            coVerifyOrder {
                exporter.upload(byteArrayOf(), "item1.jpg", "1970/01/01/id1.jpg", false)
                exporter.upload(byteArrayOf(), "item2.jpg", "1970/01/01/id2.jpg", false)
                exporter.upload("id2".toByteArray(), "last-synced-item", "last-synced-item", true)
            }
        }

    fun `when emitting an item fails then collection stops and last successful item is set`() {
    }

    fun `when using an offset ID then only items after it are emitted`() {
    }

    fun `when passing an explicit offset ID then repository is not used to get last synced item`() {
    }
}
