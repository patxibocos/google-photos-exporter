package io.github.patxibocos.googlephotosexporter

import io.github.patxibocos.googlephotosexporter.exporters.Exporter
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.slf4j.Logger
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class ExportItems(
    private val googlePhotosRepository: GooglePhotosRepository,
    private val exporter: Exporter,
    private val offsetId: String?,
    private val datePathPattern: String,
    private val syncFileName: String,
    private val logger: Logger = KotlinLogging.logger {},
) {
    private fun pathForItem(item: Item): String {
        val date = item.creationTime.atOffset(ZoneOffset.UTC).toLocalDate()
        val datePath = date.format(DateTimeFormatter.ofPattern(datePathPattern))
        val dotIndex = item.name.lastIndexOf('.')
        val extension = if (dotIndex != -1) item.name.substring(dotIndex) else ""
        return "$datePath/${item.id}$extension"
    }

    suspend operator fun invoke(itemTypes: List<ItemType>): Int {
        var exitCode = 0
        val lastItemId = offsetId?.trim() ?: exporter.get(syncFileName)?.toString(Charsets.UTF_8)?.trim()
        var lastSyncedItem: String? = null
        val flow = googlePhotosRepository
            .download(itemTypes, lastItemId)
            .onEmpty {
                logger.info("No new content")
            }
            .catch {
                logger.error("Failed fetching content", it)
                exitCode = 1
            }
            .onEach { item ->
                exporter.upload(
                    item.bytes,
                    item.name,
                    pathForItem(item),
                    false,
                )
                lastSyncedItem = item.id
            }
            .catch {
                logger.error("Failed uploading item", it)
                exitCode = 2
            }
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                runBlocking {
                    lastSyncedItem?.let {
                        exporter.upload(
                            it.toByteArray(),
                            syncFileName,
                            syncFileName,
                            true,
                        )
                        logger.info("Last uploaded item: $it")
                    }
                }
            }
        })
        flow.collect()
        return exitCode
    }
}
