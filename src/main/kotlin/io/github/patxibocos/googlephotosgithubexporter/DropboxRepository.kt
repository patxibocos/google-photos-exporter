package io.github.patxibocos.googlephotosgithubexporter

import com.dropbox.core.v2.DbxClientV2
import mu.KotlinLogging
import org.slf4j.Logger

class DropboxRepository(
    private val client: DbxClientV2,
    private val prefixPath: String,
    private val logger: Logger = KotlinLogging.logger {}
) : ExportRepository {
    override suspend fun get(filePath: String): ByteArray {
        // TODO Handle exceptions
        return client.files().download("/$prefixPath/$filePath").inputStream.use {
            it.readBytes()
        }
    }

    override suspend fun upload(data: ByteArray, name: String, filePath: String, overrideContent: Boolean) {
        // TODO Handle exceptions
        data.inputStream().use {
            client.files().upload("/$prefixPath/$filePath").uploadAndFinish(it)
        }
    }
}
