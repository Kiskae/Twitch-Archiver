package net.serverpeon.twitcharchiver.hls

import com.google.common.base.Preconditions
import java.net.URI
import java.time.Duration

@Suppress("UNCHECKED_CAST")
data class HlsPlaylist<out Type>(private val data: Map<HlsTag<*>, Any?>, private val segments: List<Type>) : List<Type> by segments {
    val maximum_segment_duration: Duration by TagDelegate(OfficialTags.EXT_X_TARGETDURATION, data)

    operator fun <T> get(key: HlsTag<T>): T? {
        Preconditions.checkArgument(key.hasAttributes, "Given key does not have properties")
        return data[key] as? T
    }

    data class Segment(val uri: URI, private val data: Map<HlsTag<*>, *>) {
        val info: OfficialTags.SegmentInformation by TagDelegate(OfficialTags.EXTINF, data)

        operator fun <T> get(key: HlsTag<T>): T? {
            Preconditions.checkArgument(key.hasAttributes, "Given key does not have properties")
            return data[key] as? T
        }
    }

    data class Variant(val uri: URI, private val data: Map<HlsTag<*>, *>) {
        val info: OfficialTags.StreamInformation by TagDelegate(OfficialTags.EXT_X_STREAM_INF, data)

        operator fun <T> get(key: HlsTag<T>): T? {
            Preconditions.checkArgument(key.hasAttributes, "Given key does not have properties")
            return data[key] as? T
        }
    }
}