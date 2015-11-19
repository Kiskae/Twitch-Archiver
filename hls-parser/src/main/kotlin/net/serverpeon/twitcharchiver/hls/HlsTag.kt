package net.serverpeon.twitcharchiver.hls

class HlsTag<T>(val tag: String,
                val appliesTo: HlsTag.AppliesTo,
                val hasAttributes: Boolean = true,
                val required: Boolean = false,
                val unique: Boolean = false,
                val processor: (String) -> T = HlsTag.errorProcessor(tag)
) {
    companion object {
        private fun <T> errorProcessor(tag: String): (String) -> T {
            return {
                throw IllegalStateException("Unhandled tag: $tag")
            }
        }
    }

    enum class AppliesTo {
        NEXT_SEGMENT,
        ENTIRE_PLAYLIST,
        FOLLOWING_SEGMENTS,
        ADDITIONAL_DATA
    }

    override fun equals(other: Any?): Boolean {
        return if (other is HlsTag<*>) this.tag.equals(other.tag) else false
    }

    override fun hashCode(): Int {
        return this.tag.hashCode()
    }

    override fun toString(): String {
        return "HlsTag($tag)"
    }
}