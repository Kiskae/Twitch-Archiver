package net.serverpeon.twitcharchiver.twitch.json

import com.google.gson.*
import java.lang.reflect.Type
import java.time.ZonedDateTime

object DateTimeConverter : JsonSerializer<ZonedDateTime>, JsonDeserializer<ZonedDateTime> {
    override fun serialize(src: ZonedDateTime?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement? {
        return src?.let { JsonPrimitive(it.toString()) }
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): ZonedDateTime? {
        return json?.let { ZonedDateTime.parse(it.asString) }
    }

}