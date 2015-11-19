package net.serverpeon.twitcharchiver.hls

import java.time.Duration
import java.util.concurrent.TimeUnit

internal fun CharSequence.toDuration(): Duration {
    val parts = this.split('.', limit = 2)
    return Duration.ofSeconds(parts[0].toLong(), if (parts.size == 2) TimeUnit.MILLISECONDS.toNanos(parts[1].toLong()) else 0)
}