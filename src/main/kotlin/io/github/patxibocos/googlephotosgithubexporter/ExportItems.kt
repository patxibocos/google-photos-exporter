package io.github.patxibocos.googlephotosgithubexporter

import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onEmpty
import mu.KotlinLogging
import org.slf4j.Logger
import java.time.ZoneOffset

class ExportItems(
    private val googlePhotosRepository: GooglePhotosRepository,
    private val gitHubContentsRepository: GitHubContentsRepository,
    private val logger: Logger = KotlinLogging.logger {}
) {
    private fun pathForItem(item: Item): String {
        val date = item.creationTime.atOffset(ZoneOffset.UTC).toLocalDate()
        val year = date.year.toString()
        val month = "%02d".format(date.monthValue)
        val day = "%02d".format(date.dayOfMonth)
        val dotIndex = item.name.lastIndexOf('.')
        val extension = if (dotIndex != -1) item.name.substring(dotIndex) else ""
        return "$year/$month/$day/${item.id}$extension"
    }

    suspend operator fun invoke(itemType: ItemType) {
        val syncFileName = when (itemType) {
            ItemType.PHOTO -> "last-synced-photo"
            ItemType.VIDEO -> "last-synced-video"
        }
        val lastItemId = gitHubContentsRepository.get(syncFileName)?.toString(Charsets.UTF_8)?.trim()
        googlePhotosRepository
            .download(itemType, lastItemId)
            .onEmpty {
                logger.info("No new content")
            }
            .catch {
                logger.error("Failed fetching content", it)
            }
            .onEach { item ->
                gitHubContentsRepository.upload(
                    item.bytes,
                    item.name,
                    pathForItem(item),
                    "Upload item: ${item.name}"
                )
            }
            .catch {
                logger.error("Failed uploading item", it)
            }
            .lastOrNull()?.let {
                gitHubContentsRepository.upload(
                    it.id.toByteArray(),
                    syncFileName,
                    syncFileName,
                    "Update last uploaded item",
                    true
                )
                logger.info("Last uploaded item: ${it.name}")
            }
    }
}
