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
            return Duration.ofSeconds(reader.nextLong())
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