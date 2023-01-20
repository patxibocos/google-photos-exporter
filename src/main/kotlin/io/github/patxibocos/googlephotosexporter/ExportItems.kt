package io.github.patxibocos.googlephotosexporter

import io.github.patxibocos.googlephotosexporter.exporters.Exporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.slf4j.Logger
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.time.Duration

class ExportItems(
    private val googlePhotosRepository: GooglePhotosRepository,
    private val exporter: Exporter,
    private val logger: Logger = KotlinLogging.logger {},
) {
    private fun pathForItem(item: Item, datePathPattern: String): String {
        val date = item.creationTime.atOffset(ZoneOffset.UTC).toLocalDate()
        val datePath = date.format(DateTimeFormatter.ofPattern(datePathPattern))
        val dotIndex = item.name.lastIndexOf('.')
        val extension = if (dotIndex != -1) item.name.substring(dotIndex) else ""
        return "$datePath/${item.id}$extension"
    }

    suspend operator fun invoke(
        offsetId: String?,
        datePathPattern: String,
        syncFileName: String,
        itemTypes: List<ItemType>,
        timeout: Duration,
        lastSyncedItem: String?,
    ): Int {
        var exitCode = 0
        val lastItemId =
            lastSyncedItem?.trim() ?: offsetId?.trim() ?: exporter.get(syncFileName)?.toString(Charsets.UTF_8)?.trim()
        var lastSuccessfulSyncedItem: String? = null
        val scope = CoroutineScope(Job())
        val shutdownHook = object : Thread() {
            override fun run() {
                runBlocking {
                    updateLastSyncedItem(syncFileName, lastSuccessfulSyncedItem)
                    scope.cancel()
                }
            }
        }
        scope.launch {
            delay(timeout)
            scope.cancel()
        }
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        googlePhotosRepository
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
                    pathForItem(item, datePathPattern),
                    false,
                )
                lastSuccessfulSyncedItem = item.id
            }
            .catch {
                logger.error("Failed uploading item", it)
                exitCode = 2
            }.onCompletion {
                updateLastSyncedItem(syncFileName, lastSuccessfulSyncedItem)
            }.launchIn(scope).join()
        Runtime.getRuntime().removeShutdownHook(shutdownHook)
        return exitCode
    }

    private suspend fun updateLastSyncedItem(syncFileName: String, lastSyncedItem: String?) {
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
