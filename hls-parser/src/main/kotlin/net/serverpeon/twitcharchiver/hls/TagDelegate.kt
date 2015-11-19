package net.serverpeon.twitcharchiver.hls

import com.google.common.collect.ImmutableList
import kotlin.reflect.KProperty

class TagDelegate<T>(private val tag: HlsTag<T>, private val data: Map<HlsTag<*>, Any?>) {
    init {
        check(tag.appliesTo != HlsTag.AppliesTo.ADDITIONAL_DATA, { "ListTagDelegate should be used for AppliesTo.ADDITIONAL_DATA keys" })
    }

    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return data[tag] as T
    }
}

class ListTagDelegate<T>(private val tag: HlsTag<T>, private val data: Map<HlsTag<*>, Any?>) {
    init {
        check(tag.appliesTo == HlsTag.AppliesTo.ADDITIONAL_DATA, { "TagDelegate should be used for non-AppliesTo.ADDITIONAL_DATA keys" })
    }

    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Collection<T> {
        return data[tag] as Collection<T>? ?: ImmutableList.of()
    }
}