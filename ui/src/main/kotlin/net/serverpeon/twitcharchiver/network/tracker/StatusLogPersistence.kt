package net.serverpeon.twitcharchiver.network.tracker

import com.google.common.collect.ImmutableMap
import com.google.common.io.CharSink
import com.google.common.io.CharSource
import com.google.gson.GsonBuilder

object StatusLogPersistence {
    enum class Status {
        UNTRACKED,
        FAILED,
        DOWNLOADED
    }

    fun read(source: CharSource?): Map<Int, Status> {
        if (source != null) {
            return readFrom(source)
        } else {
            return ImmutableMap.of()
        }
    }

    fun readFrom(source: CharSource): Map<Int, Status> {
        return source.openStream().use { gson.fromJson(it, Container::class.java) }.data
    }

    fun writeTo(sink: CharSink, data: Map<Int, Status>) {
        sink.openStream().use { gson.toJson(Container(data), it) }
    }

    private class Container(val data: Map<Int, Status>)

    private val gson = GsonBuilder().setPrettyPrinting().create()
}