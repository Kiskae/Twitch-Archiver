package net.serverpeon.twitcharchiver.fx.data

import java.time.Duration
import java.time.Instant

data class TwitchMetadata(val title: String, val length: Duration, val views: Long, val recordedAt: Instant)