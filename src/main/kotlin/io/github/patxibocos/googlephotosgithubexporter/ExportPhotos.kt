package io.github.patxibocos.googlephotosgithubexporter

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onEmpty
import mu.KotlinLogging
import org.slf4j.Logger

class ExportPhotos(
    private val downloader: GooglePhotosDownloader,
    private val uploader: GitHubUploader,
    private val logger: Logger = KotlinLogging.logger {}
) {
    suspend operator fun invoke(lastPhotoId: String?) {
        downloader
            .download(lastPhotoId)
            .onEmpty {
                logger.info("No new photos")
            }
            .catch {
                logger.error("Failed fetching photos", it)
            }
            .buffer(capacity = 2, onBufferOverflow = BufferOverflow.SUSPEND)
            .onEach(uploader::upload)
            .catch {
                logger.error("Failed uploading photo", it)
            }
            .lastOrNull()?.let {
                logger.info("Last uploaded photo ${it.name}")
            }
    }
}
