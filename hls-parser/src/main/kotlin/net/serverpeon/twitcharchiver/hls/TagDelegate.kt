package net.serverpeon.twitcharchiver.hls

import kotlin.reflect.KProperty

class TagDelegate<T>(private val tag: HlsTag<T>, private val data: Map<HlsTag<*>, Any?>) {
    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return data[tag] as T
    }
}