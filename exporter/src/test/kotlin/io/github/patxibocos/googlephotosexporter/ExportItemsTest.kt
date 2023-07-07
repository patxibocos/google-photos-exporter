package io.github.patxibocos.googlephotosexporter

import io.github.patxibocos.googlephotosexporter.exporters.Exporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import kotlin.test.Test
import kotlin.time.Duration

@ExtendWith(MockKExtension::class)
class ExportItemsTest {

    @MockK
    private lateinit var exporter: Exporter

    @MockK
    private lateinit var googlePhotosRepository: GooglePhotosRepository

    @Test
    fun `when no items on photos repository then nothing is uploaded`() = runTest {
        every { googlePhotosRepository.download(ItemType.entries, null, any()) } returns emptyFlow()
        coEvery { exporter.get(any()) } returns null
        val exportItems = ExportItems(googlePhotosRepository, exporter, false)

        exportItems(
            offsetId = null,
            datePathPattern = "yyyy/MM/dd",
            itemTypes = ItemType.entries,
            timeout = Duration.INFINITE,
        ).collect()

        coVerify(exactly = 0) { exporter.upload(any(), any(), any(), any()) }
    }

    @Test
    fun `when many items on photos repository then every item is uploaded in order`() =
        runTest {
            every { googlePhotosRepository.download(ItemType.entries, null, any()) } returns flowOf(
                Item(byteArrayOf(), "id1", "item1.jpg", Instant.EPOCH),
                Item(byteArrayOf(), "id2", "item2.jpg", Instant.EPOCH),
            )
            coEvery { exporter.get(any()) } returns null
            coEvery { exporter.upload(any(), any(), any(), any()) } returns Unit
            val exportItems = ExportItems(googlePhotosRepository, exporter, false)

            exportItems(
                offsetId = null,
                datePathPattern = "yyyy/MM/dd",
                itemTypes = ItemType.entries,
                timeout = Duration.INFINITE,
            ).collect()

            coVerifyOrder {
                exporter.upload(byteArrayOf(), "item1.jpg", "1970/01/01/id1.jpg", false)
                exporter.upload(byteArrayOf(), "item2.jpg", "1970/01/01/id2.jpg", false)
            }
        }

    @Test
    fun `when emitting an item fails then collection stops`() = runTest {
        every { googlePhotosRepository.download(ItemType.entries, null, any()) } returns flow {
            emit(Item(byteArrayOf(), "id1", "item1.jpg", Instant.EPOCH))
            throw Exception()
        }
        coEvery { exporter.get(any()) } returns null
        coEvery { exporter.upload(any(), any(), any(), any()) } returns Unit
        val exportItems = ExportItems(googlePhotosRepository, exporter, false)

        exportItems(
            offsetId = null,
            datePathPattern = "yyyy/MM/dd",
            itemTypes = ItemType.entries,
            timeout = Duration.INFINITE,
        ).collect()

        coVerifyOrder {
            exporter.upload(byteArrayOf(), "item1.jpg", "1970/01/01/id1.jpg", false)
        }
    }

    @Test
    fun `when passing an explicit offset ID then it is used as offset`() = runTest {
        every { googlePhotosRepository.download(ItemType.entries, "last-item-id", any()) } returns emptyFlow()
        val exportItems =
            ExportItems(googlePhotosRepository, exporter, false)

        exportItems(
            offsetId = "last-item-id",
            datePathPattern = "yyyy/MM/dd",
            itemTypes = ItemType.entries,
            timeout = Duration.INFINITE,
        ).collect()
    }
}
