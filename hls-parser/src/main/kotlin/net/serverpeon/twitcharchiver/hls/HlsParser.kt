package net.serverpeon.twitcharchiver.hls

import com.google.common.base.Preconditions.checkState
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Multimap
import com.google.common.collect.Multimaps
import com.google.common.io.CharSource
import com.google.common.io.LineProcessor
import java.net.URI
import java.util.*

object HlsParser {

    fun parseSimplePlaylist(location: URI, source: CharSource, tags: TagRepository = TagRepository.DEFAULT): HlsPlaylist<HlsPlaylist.Segment> {
        return source.readLines(ParserState(location, tags, { uri, data -> HlsPlaylist.Segment(uri, data) }))
    }

    fun parseVariantPlaylist(location: URI, source: CharSource, tags: TagRepository = TagRepository.DEFAULT): HlsPlaylist<HlsPlaylist.Variant> {
        return source.readLines(ParserState(location, tags, { uri, data -> HlsPlaylist.Variant(uri, data) }))
    }

    private class ParserState<Type>(
            val location: URI,
            val tags: TagRepository,
            val builder: (URI, Map<HlsTag<*>, Any?>) -> Type
    ) : LineProcessor<HlsPlaylist<Type>> {
        private var checkHeader = true
        private val playlistMap: MutableMap<HlsTag<*>, Any?> = HashMap()
        private val persistentMap: MutableMap<HlsTag<*>, Any?> = HashMap()
        private val segmentMap: MutableMap<HlsTag<*>, Any?> = HashMap()
        private val dataMap: Multimap<HlsTag<*>, Any?> = Multimaps.newListMultimap<HlsTag<*>, Any?>(HashMap(), { ArrayList() })
        private val parts: MutableList<Type> = LinkedList()

        override fun processLine(line: String?): Boolean {
            if (line.isNullOrBlank()) return true
            val safeLine: String = line!!
            if (safeLine.startsWith('#')) {
                if (safeLine.startsWith("#EXT")) {
                    // Its a tag, process
                    return processTag(safeLine.substring(1).split(':', limit = 2))
                } else {
                    // Its a comment, ignore
                    return true
                }
            } else {
                return createNewPartial(this.location.resolve(safeLine))
            }
        }

        private fun processTag(parts: List<String>): Boolean {
            val tag: HlsTag<*> = tags[parts[0]] ?: return true
            verifyHeader(tag)

            if (tag.unique) {
                checkState(!getData(tag.appliesTo).containsKey(tag), "Duplicate key where unique is expected: ${tag.tag}")
            }

            @Suppress("IMPLICIT_CAST_TO_UNIT_OR_ANY")
            val value: Any? = if (tag.hasAttributes) {
                checkState(parts.size == 2, "Tag expects attributes but none found: %s", parts[0])
                tag.processor(parts[1])
            } else {
                true
            }

            putData(tag, value)
            return true
        }

        private fun putData(tag: HlsTag<*>, value: Any?) {
            return when (tag.appliesTo) {
                HlsTag.AppliesTo.ENTIRE_PLAYLIST -> {
                    if (value != null ) this.playlistMap[tag] = value else this.playlistMap.remove(tag)
                }
                HlsTag.AppliesTo.NEXT_SEGMENT -> {
                    if (value != null ) this.segmentMap[tag] = value else this.segmentMap.remove(tag)
                }
                HlsTag.AppliesTo.FOLLOWING_SEGMENTS -> {
                    if (value != null ) this.persistentMap[tag] = value else this.persistentMap.remove(tag)
                }
                HlsTag.AppliesTo.ADDITIONAL_DATA -> {
                    if (value != null) {
                        this.dataMap.put(tag, value)
                    }
                }
            }
        }

        private fun getData(type: HlsTag.AppliesTo): Map<HlsTag<*>, *> {
            return when (type) {
                HlsTag.AppliesTo.ENTIRE_PLAYLIST -> this.playlistMap
                HlsTag.AppliesTo.NEXT_SEGMENT -> this.segmentMap
                HlsTag.AppliesTo.FOLLOWING_SEGMENTS -> this.persistentMap
                HlsTag.AppliesTo.ADDITIONAL_DATA -> this.dataMap.asMap()
            }
        }

        private fun verifyHeader(tag: HlsTag<*>) {
            if (OfficialTags.EXTM3U == tag) {
                if (checkHeader) {
                    checkHeader = false
                } else {
                    throw IllegalStateException("Multiple #EXTM3U tags")
                }
            }

            checkState(!checkHeader, "Missing #EXTM3U tag at the beginning of the file")
        }

        private fun createNewPartial(partialUrl: URI): Boolean {
            this.persistentMap.plus(this.segmentMap)
            val partial = this.builder(partialUrl, ImmutableMap.builder<HlsTag<*>, Any?>()
                    .putAll(this.persistentMap)
                    .putAll(this.segmentMap)
                    .build())
            this.segmentMap.clear()
            this.parts.add(partial)
            return true
        }

        override fun getResult(): HlsPlaylist<Type>? {
            return HlsPlaylist(
                    ImmutableMap.builder<HlsTag<*>, Any?>()
                            .putAll(this.playlistMap)
                            .putAll(this.dataMap.asMap())
                            .build(),
                    ImmutableList.copyOf(this.parts)
            )
        }
    }
}