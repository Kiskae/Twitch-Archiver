package net.serverpeon.twitcharchiver.twitch.json

import com.google.gson.*
import java.lang.reflect.Type
import java.time.Duration

object DurationConverter : JsonSerializer<Duration>, JsonDeserializer<Duration> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Duration? {
        return json?.let { Duration.ofSeconds(it.asLong) }
    }

    override fun serialize(src: Duration?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement? {
        return src?.let { JsonPrimitive(it.seconds) }
    }
}