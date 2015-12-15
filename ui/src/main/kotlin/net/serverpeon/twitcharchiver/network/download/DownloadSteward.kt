package net.serverpeon.twitcharchiver.network.download

import com.squareup.okhttp.Response

interface DownloadSteward<T> {
    fun validatePre(entry: ForkJoinDownloader.DownloadEntry<T>, response: Response)

    fun validatePost(entry: ForkJoinDownloader.DownloadEntry<T>, response: Response, totalBytesDownloaded: Long)

    fun onBegin(entry: ForkJoinDownloader.DownloadEntry<T>)

    fun onUpdate(deltaSinceLastUpdate: Int)

    fun onEnd(entry: ForkJoinDownloader.DownloadEntry<T>)

    fun onException(entry: ForkJoinDownloader.DownloadEntry<T>, th: Throwable)
}