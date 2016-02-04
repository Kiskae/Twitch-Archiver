package net.serverpeon.twitcharchiver.network.download

import com.google.common.collect.ImmutableList
import com.google.common.io.Files
import okhttp3.Call
import okhttp3.Response
import java.nio.file.Path
import java.util.concurrent.ForkJoinTask

object ForkJoinDownloader {
    data class DownloadEntry<T>(val source: Call, val sink: Path, val ident: T) {
        internal fun doDownload(
                bufSize: Int = 64 * 1024,
                onStart: (Response) -> Unit,
                onUpdate: (Int) -> Unit,
                onEnd: (Response, Long) -> Unit
        ) {
            val file = sink.toFile()
            try {
                if (!file.exists()) {
                    check(file.createNewFile())
                    //File needs to exist to stream into it
                }

                val response = source.execute()
                response.body().byteStream().use { input ->
                    Files.asByteSink(file).openStream().use { output ->
                        onStart(response)
                        val buf = ByteArray(bufSize)
                        var bytesCopied: Long = 0
                        var bytes = input.read(buf)
                        while (bytes >= 0) {
                            output.write(buf, 0, bytes)
                            onUpdate(bytes)
                            bytesCopied += bytes
                            bytes = input.read(buf)
                        }
                        onEnd(response, bytesCopied)
                    }
                }
            } catch (ex: Exception) {
                file.delete() //Delete if an exception occurs
                throw ex
            }
        }
    }

    fun <T> create(downloads: List<DownloadEntry<T>>,
                   updater: DownloadSteward<T>): ForkJoinTask<*> {
        return ForkJoinTask.adapt(Main(
                ImmutableList.copyOf(downloads),
                updater
        ))
    }

    private class Main<T>(
            val entries: List<DownloadEntry<T>>,
            val updater: DownloadSteward<T>
    ) : Runnable {
        override fun run() {
            ForkJoinTask.invokeAll(entries.map {
                ForkJoinTask.adapt(Part(it, updater))
            })
        }
    }

    private class Part<T>(
            val entry: DownloadEntry<T>,
            val updater: DownloadSteward<T>
    ) : Runnable {
        override fun run() {
            updater.onBegin(entry)
            try {
                entry.doDownload(DEFAULT_BUFFER_SIZE, { response ->
                    check(response.isSuccessful) {
                        "Non-success response code"
                    }
                    updater.validatePre(entry, response)
                }, {
                    updater.onUpdate(it)
                }, { response, bytes ->
                    updater.validatePost(entry, response, bytes)
                })

                updater.onEnd(entry)
            } catch (ex: Exception) {
                updater.onException(entry, ex)
            }
        }
    }
}