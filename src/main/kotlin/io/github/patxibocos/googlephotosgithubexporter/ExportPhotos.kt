package io.github.patxibocos.googlephotosgithubexporter

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onEmpty
import mu.KotlinLogging
import org.slf4j.Logger
import java.time.ZoneOffset

class ExportPhotos(
    private val googlePhotosRepository: GooglePhotosRepository,
    private val gitHubContentsRepository: GitHubContentsRepository,
    private val logger: Logger = KotlinLogging.logger {}
) {
    private fun pathForPhoto(photo: Photo): String {
        val date = photo.creationTime.atOffset(ZoneOffset.UTC).toLocalDate()
        val year = date.year.toString()
        val month = "%02d".format(date.monthValue)
        val day = "%02d".format(date.dayOfMonth)
        val dotIndex = photo.name.lastIndexOf('.')
        val extension = if (dotIndex != -1) photo.name.substring(dotIndex) else ""
        return "photos/$year/$month/$day/${photo.id}$extension"
    }

    private val syncFileName = "last-synced-photo"

    suspend operator fun invoke() {
        val lastPhotoId = gitHubContentsRepository.get(syncFileName)?.toString(Charsets.UTF_8)?.trim()
        googlePhotosRepository
            .download(lastPhotoId)
            .onEmpty {
                logger.info("No new photos")
            }
            .catch {
                logger.error("Failed fetching photos", it)
            }
            .buffer(capacity = 1, onBufferOverflow = BufferOverflow.SUSPEND)
            .onEach { photo ->
                gitHubContentsRepository.upload(photo.bytes, pathForPhoto(photo), "Upload photo: ${photo.name}")
            }
            .catch {
                logger.error("Failed uploading photo", it)
            }
            .lastOrNull()?.let {
                gitHubContentsRepository.upload(it.id.toByteArray(), syncFileName, "Update last uploaded photo", true)
                logger.info("Last uploaded photo ${it.name}")
            }
    }
}
