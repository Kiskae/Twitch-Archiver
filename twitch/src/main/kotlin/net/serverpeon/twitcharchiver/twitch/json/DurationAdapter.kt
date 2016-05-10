package net.serverpeon.twitcharchiver.twitch.json

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.time.Duration

object DurationAdapter : TypeAdapter<Duration>() {
    override fun read(reader: JsonReader): Duration? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        } else {
            val parts = reader.nextString().split('.', limit = 2).map { it.toLong() }
            return when (parts.size) {
                2 -> Duration.ofSeconds(parts[0], parts[1])
                else -> Duration.ofSeconds(parts[0])
            }
        }
    }

    override fun write(writer: JsonWriter, value: Duration?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value.seconds)
        }
    }
}