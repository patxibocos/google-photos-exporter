package io.github.patxibocos.googlephotosexporter

import io.github.patxibocos.googlephotosexporter.exporters.Exporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.time.Duration

sealed interface ExportEvent {
    object ExportStarted : ExportEvent
    data class ItemsCollected(val photos: Int, val videos: Int) : ExportEvent
    data class ItemDownloaded(val item: Item) : ExportEvent
    data class ItemUploaded(val item: Item) : ExportEvent
    object DownloadFailed : ExportEvent
    object UploadFailed : ExportEvent
    object ExportCompleted : ExportEvent
}

class ExportItems(
    private val googlePhotosRepository: GooglePhotosRepository,
    private val exporter: Exporter,
    private val overrideContent: Boolean,
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
        itemTypes: List<ItemType>,
        timeout: Duration,
    ): Flow<ExportEvent> {
        val scope = CoroutineScope(Job())
        scope.launch {
            delay(timeout)
            scope.cancel()
        }
        return channelFlow {
            googlePhotosRepository
                .download(itemTypes, offsetId) { photoCount, videoCount ->
                    send(ExportEvent.ItemsCollected(photoCount, videoCount))
                }
                .onStart {
                    send(ExportEvent.ExportStarted)
                }
                .catch {
                    send(ExportEvent.DownloadFailed)
                }
                .onEach { item ->
                    send(ExportEvent.ItemDownloaded(item))
                    exporter.upload(
                        item.bytes,
                        item.name,
                        pathForItem(item, datePathPattern),
                        overrideContent,
                    )
                    send(ExportEvent.ItemUploaded(item))
                }
                .catch {
                    send(ExportEvent.UploadFailed)
                }.onCompletion {
                    send(ExportEvent.ExportCompleted)
                }.launchIn(scope).join()
        }
    }
}
