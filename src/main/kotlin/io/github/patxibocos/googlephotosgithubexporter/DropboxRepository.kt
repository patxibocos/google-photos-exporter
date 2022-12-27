package io.github.patxibocos.googlephotosgithubexporter

import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.DownloadErrorException
import com.dropbox.core.v2.files.LookupError
import com.dropbox.core.v2.files.UploadErrorException
import mu.KotlinLogging
import org.slf4j.Logger

class DropboxRepository(
    private val client: DbxClientV2,
    private val prefixPath: String,
    private val logger: Logger = KotlinLogging.logger {}
) : ExportRepository {
    override suspend fun get(filePath: String): ByteArray? {
        return try {
            client.files().download("/$prefixPath/$filePath").inputStream.use {
                it.readBytes()
            }
        } catch (e: DownloadErrorException) {
            if (e.errorValue.pathValue == LookupError.NOT_FOUND) {
                null
            } else {
                throw e
            }
        }
    }

    override suspend fun upload(data: ByteArray, name: String, filePath: String, overrideContent: Boolean) {
        try {
            client.files().upload("/$prefixPath/$filePath").uploadAndFinish(data.inputStream())
        } catch (e: UploadErrorException) {
            if (e.errorValue.pathValue.reason.isConflict) {
                logger.warn("File $filePath already exists")
            } else {
                throw e
            }
        }
    }
}
