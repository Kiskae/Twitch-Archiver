package net.serverpeon.twitcharchiver.twitch.json

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.time.ZonedDateTime

object ZonedDateTimeAdapter : TypeAdapter<ZonedDateTime>() {
    override fun write(writer: JsonWriter, value: ZonedDateTime?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value.toString())
        }
    }

    override fun read(reader: JsonReader): ZonedDateTime? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        } else {
            return ZonedDateTime.parse(reader.nextString())
        }
    }
}