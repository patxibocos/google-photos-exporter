package io.github.patxibocos.googlephotosgithubexporter

import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onEmpty
import mu.KotlinLogging
import org.slf4j.Logger
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class ExportItems(
    private val googlePhotosRepository: GooglePhotosRepository,
    private val exportRepository: ExportRepository,
    private val offsetId: String?,
    private val datePathPattern: String,
    private val logger: Logger = KotlinLogging.logger {}
) {

    private val syncFileName = "last-synced-item"
    private fun pathForItem(item: Item): String {
        val date = item.creationTime.atOffset(ZoneOffset.UTC).toLocalDate()
        val datePath = date.format(DateTimeFormatter.ofPattern(datePathPattern))
        val dotIndex = item.name.lastIndexOf('.')
        val extension = if (dotIndex != -1) item.name.substring(dotIndex) else ""
        return "$datePath/${item.id}$extension"
    }

    suspend operator fun invoke(itemTypes: List<ItemType>): Int {
        var exitCode = 0
        val lastItemId = offsetId?.trim() ?: exportRepository.get(syncFileName)?.toString(Charsets.UTF_8)?.trim()
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
                exportRepository.upload(
                    item.bytes,
                    item.name,
                    pathForItem(item)
                )
            }
            .catch {
                logger.error("Failed uploading item", it)
                exitCode = 2
            }
            .lastOrNull()?.let {
                exportRepository.upload(
                    it.id.toByteArray(),
                    syncFileName,
                    syncFileName,
                    true
                )
                logger.info("Last uploaded item: ${it.name}")
            }
        return exitCode
    }
}
