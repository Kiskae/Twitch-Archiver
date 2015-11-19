package net.serverpeon.twitcharchiver.hls

import com.google.common.collect.ImmutableMap

/**
 *
 */
sealed class TagRepository(tags: Collection<HlsTag<*>>) {
    protected var internalTags: ImmutableMap<String, HlsTag<*>> = buildInitialMap(tags)

    private fun buildInitialMap(tags: Collection<HlsTag<*>>): ImmutableMap<String, HlsTag<*>> {
        val builder = ImmutableMap.builder<String, HlsTag<*>>()
        tags.forEach { builder.put(it.tag, it) }
        return builder.build()
    }

    operator fun get(index: String): HlsTag<*>? {
        return this.internalTags[index]
    }

    companion object {
        val DEFAULT: TagRepository = TagRepository.Mutable(arrayListOf(
                OfficialTags.EXT_X_ALLOW_CACHE,
                OfficialTags.EXT_X_BYTERANGE,
                OfficialTags.EXT_X_DISCONTINUITY,
                OfficialTags.EXT_X_ENDLIST,
                OfficialTags.EXT_X_I_FRAME_STREAM_INF,
                OfficialTags.EXT_X_I_FRAMES_ONLY,
                OfficialTags.EXT_X_MEDIA,
                OfficialTags.EXT_X_MEDIA_SEQUENCE,
                OfficialTags.EXT_X_PLAYLIST_TYPE,
                OfficialTags.EXT_X_PROGRAM_DATE_TIME,
                OfficialTags.EXT_X_STREAM_INF,
                OfficialTags.EXT_X_TARGETDURATION,
                OfficialTags.EXT_X_VERSION,
                OfficialTags.EXTINF,
                OfficialTags.EXTM3U,
                OfficialTags.EXT_X_KEY
        ))

        fun newRepository(): TagRepository.Mutable = TagRepository.Mutable(DEFAULT.internalTags.values)
    }

    class Mutable internal constructor(tags: Collection<HlsTag<*>>) : TagRepository(tags) {
        fun register(tag: HlsTag<Any?>) {
            this.internalTags = ImmutableMap.builder<String, HlsTag<*>>()
                    .putAll(this.internalTags)
                    .put(tag.tag, tag)
                    .build()
        }
    }
}