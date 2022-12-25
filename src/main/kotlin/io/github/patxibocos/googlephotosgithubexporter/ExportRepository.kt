package io.github.patxibocos.googlephotosgithubexporter

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import java.io.File

interface ExportRepository {
    suspend fun get(filePath: String): ByteArray?
    suspend fun upload(
        data: ByteArray,
        name: String,
        filePath: String,
        overrideContent: Boolean = false
    )

    fun decorate(maxChunkSize: Int): ExportRepository = SplitDecorator(this, maxChunkSize)
}

class SplitDecorator(private val repository: ExportRepository, maxChunkSizeMBs: Int) :
    ExportRepository by repository {

    private val maxSizeInBytes = maxChunkSizeMBs * 1024 * 1024

    private class ZipSplit(val name: String, val data: ByteArray)

    private fun zipAndSplit(name: String, content: ByteArray): List<ZipSplit> {
        val file = File(name).apply {
            createNewFile()
            writeBytes(content)
        }
        val zipFile = ZipFile("$name.zip")
        zipFile.createSplitZipFile(listOf(file), ZipParameters(), true, maxSizeInBytes.toLong())
        val zipSplits = zipFile.splitZipFiles.map {
            ZipSplit(it.name, it.readBytes())
        }
        file.delete()
        zipFile.splitZipFiles.forEach(File::delete)
        return zipSplits
    }

    override suspend fun upload(data: ByteArray, name: String, filePath: String, overrideContent: Boolean) {
        if (data.size > maxSizeInBytes) {
            val fileName = filePath.split("/").last()
            zipAndSplit(fileName, data).forEach {
                val path = "$filePath/${it.name}"
                upload(it.data, it.name, path, overrideContent)
            }
            return
        }
        repository.upload(data, name, filePath, overrideContent)
    }
}
