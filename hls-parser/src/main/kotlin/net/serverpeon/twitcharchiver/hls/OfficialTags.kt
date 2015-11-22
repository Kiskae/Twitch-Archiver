package net.serverpeon.twitcharchiver.hls

import com.google.common.collect.ImmutableList
import java.math.BigInteger
import java.net.URI
import java.time.Duration
import java.time.ZonedDateTime

/**
 * [http://tools.ietf.org/html/draft-pantos-http-live-streaming-08]
 */
object OfficialTags {
    /**
     * An Extended M3U file is distinguished from a basic M3U file by its
     * first line, which MUST be the tag #EXTM3U.
     */
    val EXTM3U: HlsTag<Nothing> = HlsTag(
            "EXTM3U",
            appliesTo = HlsTag.AppliesTo.ENTIRE_PLAYLIST,
            hasAttributes = false,
            required = true,
            unique = true
    )

    /**
     * The EXTINF tag specifies the duration of a media segment.  It applies
     * only to the media URI that follows it.  Each media segment URI MUST
     * be preceded by an EXTINF tag.  Its format is:
     *
     * #EXTINF:<duration>,<title>
     *
     * "duration" is an integer or floating-point number in decimal
     * positional notation that specifies the duration of the media segment
     * in seconds.  Durations that are reported as integers SHOULD be
     * rounded to the nearest integer.  Durations MUST be integers if the
     * protocol version of the Playlist file is less than 3.  The remainder
     * of the line following the comma is an optional human-readable
     * informative title of the media segment.
     */
    val EXTINF: HlsTag<SegmentInformation> = HlsTag(
            "EXTINF",
            appliesTo = HlsTag.AppliesTo.NEXT_SEGMENT,
            required = true,
            unique = true
    ) { rawData ->
        val data = rawData.split(',', limit = 2)
        SegmentInformation(data[0].toDuration(), if (data.size == 2) data[1] else "")
    }

    /**
     * The EXT-X-BYTERANGE tag indicates that a media segment is a sub-range
     * of the resource identified by its media URI.  It applies only to the
     * next media URI that follows it in the Playlist.  Its format is:
     *
     * #EXT-X-BYTERANGE:<n>[@o]
     *
     * where n is a decimal-integer indicating the length of the sub-range
     * in bytes.  If present, o is a decimal-integer indicating the start of
     * the sub-range, as a byte offset from the beginning of the resource.
     * If o is not present, the sub-range begins at the next byte following
     * the sub-range of the previous media segment.
     *
     * If o is not present, a previous media segment MUST appear in the
     * Playlist file and MUST be a sub-range of the same media resource.
     *
     * A media URI with no EXT-X-BYTERANGE tag applied to it specifies a
     * media segment that consists of the entire resource.
     *
     * The EXT-X-BYTERANGE tag appeared in version 4 of the protocol.
     */
    val EXT_X_BYTERANGE: HlsTag<SourceSubrange> = HlsTag(
            "EXT-X-BYTERANGE",
            appliesTo = HlsTag.AppliesTo.NEXT_SEGMENT,
            unique = true
    ) { rawData ->
        val data = rawData.split('@', limit = 2)
        SourceSubrange(data[0].toLong(), if (data.size == 2) data[1].toLong() else 0)
    }

    /**
     * The EXT-X-TARGETDURATION tag specifies the maximum media segment
     * duration.  The EXTINF duration of each media segment in the Playlist
     * file MUST be less than or equal to the target duration.  This tag
     * MUST appear once in the Playlist file.  It applies to the entire
     * Playlist file.  Its format is:
     *
     * #EXT-X-TARGETDURATION:<s>
     *
     * where s is an integer indicating the target duration in seconds.
     */
    val EXT_X_TARGETDURATION: HlsTag<Duration> = HlsTag(
            "EXT-X-TARGETDURATION",
            appliesTo = HlsTag.AppliesTo.ENTIRE_PLAYLIST,
            required = true,
            unique = true
    ) { rawData ->
        Duration.ofSeconds(rawData.toLong())
    }

    /**
     * Each media URI in a Playlist has a unique integer sequence number.
     * The sequence number of a URI is equal to the sequence number of the
     * URI that preceded it plus one.  The EXT-X-MEDIA-SEQUENCE tag
     * indicates the sequence number of the first URI that appears in a
     * Playlist file.  Its format is:
     *
     * #EXT-X-MEDIA-SEQUENCE:<number>
     *
     * A Playlist file MUST NOT contain more than one EXT-X-MEDIA-SEQUENCE
     * tag.  If the Playlist file does not contain an EXT-X-MEDIA-SEQUENCE
     * tag then the sequence number of the first URI in the playlist SHALL
     * be considered to be 0.
     *
     * A media URI is not required to contain its sequence number.
     *
     * See Section 6.3.2 and Section 6.3.5 for information on handling the
     * EXT-X-MEDIA-SEQUENCE tag.
     */
    val EXT_X_MEDIA_SEQUENCE: HlsTag<Int> = HlsTag(
            "EXT-X-MEDIA-SEQUENCE",
            appliesTo = HlsTag.AppliesTo.ENTIRE_PLAYLIST,
            unique = true
    ) { rawData ->
        rawData.toInt()
    }

    /**
     * Media segments MAY be encrypted.  The EXT-X-KEY tag specifies how to
     * decrypt them.  It applies to every media URI that appears between it
     * and the next EXT-X-KEY tag in the Playlist file (if any).  Its format
     * is:
     *
     * #EXT-X-KEY:<attribute-list>
     *
     * The following attributes are defined:
     *
     * The METHOD attribute specifies the encryption method.  It is of type
     * enumerated-string.  Each EXT-X-KEY tag MUST contain a METHOD
     * attribute.
     *
     * Two methods are defined: NONE and AES-128.
     *
     * An encryption method of NONE means that media segments are not
     * encrypted.  If the encryption method is NONE, the URI and the IV
     * attributes MUST NOT be present.
     *
     * An encryption method of AES-128 means that media segments are
     * encrypted using the Advanced Encryption Standard [AES_128] with a
     * 128-bit key and PKCS7 padding [RFC5652].  If the encryption method is
     * AES-128, the URI attribute MUST be present.  The IV attribute MAY be
     * present; see Section 5.2.
     *
     * The URI attribute specifies how to obtain the key.  Its value is a
     * quoted-string that contains a URI [RFC3986] for the key.
     *
     * The IV attribute, if present, specifies the Initialization Vector to
     * be used with the key.  Its value is a hexadecimal-integer.  The IV
     * attribute appeared in protocol version 2.
     *
     * If the Playlist file does not contain an EXT-X-KEY tag then media
     * segments are not encrypted.
     *
     * See Section 5 for the format of the key file, and Section 5.2,
     * Section 6.2.3 and Section 6.3.6 for additional information on media
     * segment encryption.
     */
    val EXT_X_KEY: HlsTag<EncryptionKey?> = HlsTag(
            "EXT-X-KEY",
            appliesTo = HlsTag.AppliesTo.FOLLOWING_SEGMENTS
    ) { rawData ->
        val parser = AttributeListParser(rawData)

        var uri: URI? = null
        var iv: BigInteger? = null
        var method: String = "NONE"
        while (parser.hasMoreAttributes()) {
            when (parser.readAttributeName()) {
                "URI" -> uri = URI.create(parser.readQuotedString())
                "IV" -> iv = parser.readHexadecimalInt()
                "METHOD" -> method = parser.readEnumeratedString()
            }
        }

        if (!"NONE".equals(method)) EncryptionKey(method, uri!!, iv) else null
    }

    /**
     * The EXT-X-PROGRAM-DATE-TIME tag associates the first sample of a
     * media segment with an absolute date and/or time.  It applies only to
     * the next media URI.
     *
     * The date/time representation is ISO/IEC 8601:2004 [ISO_8601] and
     * SHOULD indicate a time zone:
     *
     * #EXT-X-PROGRAM-DATE-TIME:<YYYY-MM-DDThh:mm:ssZ>
     *
     * For example:
     *
     * #EXT-X-PROGRAM-DATE-TIME:2010-02-19T14:54:23.031+08:00
     *
     * See Section 6.2.1 and Section 6.3.3 for more information on the EXT-
     * X-PROGRAM-DATE-TIME tag.
     */
    val EXT_X_PROGRAM_DATE_TIME: HlsTag<ZonedDateTime> = HlsTag(
            "EXT-X-PROGRAM-DATE-TIME",
            appliesTo = HlsTag.AppliesTo.NEXT_SEGMENT,
            unique = true
    ) { rawData ->
        ZonedDateTime.parse(rawData)
    }

    /**
     * The EXT-X-ALLOW-CACHE tag indicates whether the client MAY or MUST
     * NOT cache downloaded media segments for later replay.  It MAY occur
     * anywhere in the Playlist file; it MUST NOT occur more than once.  The
     * EXT-X-ALLOW-CACHE tag applies to all segments in the playlist.  Its
     * format is:
     *
     * #EXT-X-ALLOW-CACHE:<YES|NO>
     *
     * See Section 6.3.3 for more information on the EXT-X-ALLOW-CACHE tag.
     */
    val EXT_X_ALLOW_CACHE: HlsTag<Boolean> = HlsTag(
            "EXT-X-ALLOW-CACHE",
            appliesTo = HlsTag.AppliesTo.ENTIRE_PLAYLIST,
            unique = true
    ) { rawData ->
        readYesNo(rawData)
    }

    /**
     * The EXT-X-PLAYLIST-TYPE tag provides mutability information about the
     * Playlist file.  It applies to the entire Playlist file.  It is
     * optional.  Its format is:
     *
     * #EXT-X-PLAYLIST-TYPE:<EVENT|VOD>
     *
     * Section 6.2.1 defines the implications of the EXT-X-PLAYLIST-TYPE
     * tag.
     */
    val EXT_X_PLAYLIST_TYPE: HlsTag<PlaylistType> = HlsTag(
            "EXT-X-PLAYLIST-TYPE",
            appliesTo = HlsTag.AppliesTo.ENTIRE_PLAYLIST,
            unique = true
    ) { rawData ->
        when (rawData) {
            "EVENT" -> PlaylistType.EVENT
            "VOD" -> PlaylistType.VOD
            else -> throw IllegalArgumentException("Invalid value for EXT-X-PLAYLIST-TYPE: $rawData")
        }
    }

    /**
     * The EXT-X-ENDLIST tag indicates that no more media segments will be
     * added to the Playlist file.  It MAY occur anywhere in the Playlist
     * file; it MUST NOT occur more than once.  Its format is:
     *
     * #EXT-X-ENDLIST
     */
    val EXT_X_ENDLIST: HlsTag<Nothing> = HlsTag(
            "EXT-X-ENDLIST",
            appliesTo = HlsTag.AppliesTo.ENTIRE_PLAYLIST,
            hasAttributes = false,
            unique = true
    )

    /**
     * The EXT-X-MEDIA tag is used to relate Playlists that contain
     * alternative renditions of the same content.  For example, three EXT-
     * X-MEDIA tags can be used to identify audio-only Playlists that
     * contain English, French and Spanish renditions of the same
     * presentation.  Or two EXT-X-MEDIA tags can be used to identify video-
     * only Playlists that show two different camera angles.
     *
     * The EXT-X-MEDIA tag stands alone, in that it does not apply to a
     * particular URI in the Playlist.  Its format is:
     *
     * #EXT-X-MEDIA:<attribute-list>
     *
     * The following attributes are defined:
     *
     * URI
     *
     * The value is a quoted-string containing a URI that identifies the
     * Playlist file.  This attribute is optional; see Section 3.4.10.1.
     *
     * TYPE
     *
     * The value is enumerated-string; valid strings are AUDIO and VIDEO.
     * If the value is AUDIO, the Playlist described by the tag MUST contain
     * audio media.  If the value is VIDEO, the Playlist MUST contain video
     * media.
     *
     * GROUP-ID
     *
     * The value is a quoted-string identifying a mutually-exclusive group
     * of renditions.  The presence of this attribute signals membership in
     * the group.  See Section 3.4.9.1.
     *
     * LANGUAGE
     *
     * The value is a quoted-string containing an RFC 5646 [RFC5646]
     * language tag that identifies the primary language used in the
     * rendition.  This attribute is optional.
     *
     * NAME
     *
     * The value is a quoted-string containing a human-readable description
     * of the rendition.  If the LANGUAGE attribute is present then this
     * description SHOULD be in that language.
     *
     * DEFAULT
     *
     * The value is enumerated-string; valid strings are YES and NO.  If the
     * value is YES, then the client SHOULD play this rendition of the
     * content in the absence of information from the user indicating a
     * different choice.  This attribute is optional.  Its absence indicates
     * an implicit value of NO.
     *
     * AUTOSELECT
     *
     * The value is enumerated-string; valid strings are YES and NO.  This
     * attribute is optional.  Its absence indicates an implicit value of
     * NO.  If the value is YES, then the client MAY choose to play this
     * rendition in the absence of explicit user preference because it
     * matches the current playback environment, such as chosen system
     * language.
     *
     * The EXT-X-MEDIA tag appeared in version 4 of the protocol.
     */
    val EXT_X_MEDIA: HlsTag<MediaRendition> = HlsTag(
            "EXT-X-MEDIA",
            appliesTo = HlsTag.AppliesTo.ADDITIONAL_DATA
    ) { rawData ->
        val parser = AttributeListParser(rawData)

        var type: String? = null
        var uri: URI? = null
        var group: String? = null
        var language: String? = null
        var name: String? = null
        var default: Boolean = false
        var autoSelect: Boolean = false

        while (parser.hasMoreAttributes()) {
            when (parser.readAttributeName()) {
                "URI" -> uri = URI.create(parser.readQuotedString())
                "TYPE" -> type = parser.readEnumeratedString()
                "GROUP-ID" -> group = parser.readQuotedString()
                "LANGUAGE" -> language = parser.readQuotedString()
                "NAME" -> name = parser.readQuotedString()
                "DEFAULT" -> default = readYesNo(parser.readEnumeratedString())
                "AUTOSELECT" -> autoSelect = readYesNo(parser.readEnumeratedString())
            }
        }

        MediaRendition(when (type) {
            "VIDEO" -> MediaType.VIDEO
            "AUDIO" -> MediaType.AUDIO
            else -> throw IllegalStateException("invalid MediaType: $type")
        }, uri, group, language, name, default, autoSelect)
    }

    /**
     * The EXT-X-STREAM-INF tag identifies a media URI as a Playlist file
     * containing a multimedia presentation and provides information about
     * that presentation.  It applies only to the URI that follows it.  Its
     * format is:
     *
     * #EXT-X-STREAM-INF:<attribute-list>
     * <URI>
     *
     * The following attributes are defined:
     *
     * BANDWIDTH
     *
     * The value is a decimal-integer of bits per second.  It MUST be an
     * upper bound of the overall bitrate of each media segment (calculated
     * to include container overhead) that appears or will appear in the
     * Playlist.
     *
     * Every EXT-X-STREAM-INF tag MUST include the BANDWIDTH attribute.
     *
     * PROGRAM-ID
     *
     * The value is a decimal-integer that uniquely identifies a particular
     * presentation within the scope of the Playlist file.
     * A Playlist file MAY contain multiple EXT-X-STREAM-INF tags with the
     * same PROGRAM-ID to identify different encodings of the same
     * presentation.  These variant playlists MAY contain additional EXT-X-
     * STREAM-INF tags.
     *
     * CODECS
     *
     * The value is a quoted-string containing a comma-separated list of
     * formats, where each format specifies a media sample type that is
     * present in a media segment in the Playlist file.  Valid format
     * identifiers are those in the ISO File Format Name Space defined by
     * RFC 6381 [RFC6381].
     *
     * Every EXT-X-STREAM-INF tag SHOULD include a CODECS attribute.
     *
     * RESOLUTION
     *
     * The value is a decimal-resolution describing the approximate encoded
     * horizontal and vertical resolution of video within the presentation.
     *
     * AUDIO
     *
     * The value is a quoted-string.  It MUST match the value of the
     * GROUP-ID attribute of an EXT-X-MEDIA tag elsewhere in the Playlist
     * whose TYPE attribute is AUDIO.  It indicates the set of audio
     * renditions that MAY be used when playing the presentation.  See
     * Section 3.4.10.1.
     *
     * VIDEO
     *
     * The value is a quoted-string.  It MUST match the value of the
     * GROUP-ID attribute of an EXT-X-MEDIA tag elsewhere in the Playlist
     * whose TYPE attribute is VIDEO.  It indicates the set of video
     * renditions that MAY be used when playing the presentation.  See
     * Section 3.4.10.1.
     */
    val EXT_X_STREAM_INF: HlsTag<StreamInformation> = HlsTag(
            "EXT-X-STREAM-INF",
            appliesTo = HlsTag.AppliesTo.NEXT_SEGMENT,
            unique = true
    ) { rawData ->
        val parser = AttributeListParser(rawData)

        var bandwidth: Long? = null
        var programId: Long? = null
        var codecs: String? = null
        var resolution: AttributeListParser.Resolution? = null
        var audio: String? = null
        var video: String? = null

        while (parser.hasMoreAttributes()) {
            when (parser.readAttributeName()) {
                "BANDWIDTH" -> bandwidth = parser.readDecimalInt()
                "PROGRAM-ID" -> programId = parser.readDecimalInt()
                "CODECS" -> codecs = parser.readQuotedString()
                "RESOLUTION" -> resolution = parser.readResolution(allowStrayQuotes = true)
                "AUDIO" -> audio = parser.readQuotedString()
                "VIDEO" -> video = parser.readQuotedString()
            }
        }

        StreamInformation(
                bandwidth!!,
                programId,
                codecs?.let { ImmutableList.copyOf(it.split(',')) } ?: ImmutableList.of(),
                resolution,
                audio,
                video
        )
    }

    /**
     * The EXT-X-DISCONTINUITY tag indicates an encoding discontinuity
     * between the media segment that follows it and the one that preceded
     * it.  The set of characteristics that MAY change is:
     *
     * o  file format
     *
     * o  number and type of tracks
     *
     * o  encoding parameters
     *
     * o  encoding sequence
     *
     * o  timestamp sequence
     *
     * Its format is:
     *
     * #EXT-X-DISCONTINUITY
     *
     * See Section 4, Section 6.2.1, and Section 6.3.3 for more information
     * about the EXT-X-DISCONTINUITY tag.
     */
    val EXT_X_DISCONTINUITY: HlsTag<Nothing> = HlsTag(
            "EXT-X-DISCONTINUITY",
            appliesTo = HlsTag.AppliesTo.NEXT_SEGMENT,
            hasAttributes = false
    )

    /**
     * The EXT-X-I-FRAMES-ONLY tag indicates that each media segment in the
     * Playlist describes a single I-frame.  I-frames (or Intra frames) are
     * encoded video frames whose encoding does not depend on any other
     * frame.
     *
     * The EXT-X-I-FRAMES-ONLY tag applies to the entire Playlist.  Its
     * format is:
     *
     * #EXT-X-I-FRAMES-ONLY
     *
     * In a Playlist with the EXT-X-I-FRAMES-ONLY tag, the media segment
     * duration (EXTINF tag value) is the time between the presentation time
     * of the I-frame in the media segment and the presentation time of the
     * next I-frame in the Playlist, or the end of the presentation if it is
     * the last I-frame in the Playlist.
     *
     * Media resources containing I-frame segments MUST begin with a
     * Transport Stream PAT/PMT.  The byte range of an I-frame segment with
     * an EXT-X-BYTERANGE tag applied to it (Section 3.4.1) MUST NOT include
     * a PAT/PMT.
     *
     * The EXT-X-I-FRAMES-ONLY tag appeared in version 4 of the protocol.
     */
    val EXT_X_I_FRAMES_ONLY: HlsTag<Nothing> = HlsTag(
            "EXT-X-I-FRAMES-ONLY",
            appliesTo = HlsTag.AppliesTo.ENTIRE_PLAYLIST,
            hasAttributes = false
    )

    /**
     * The EXT-X-I-FRAME-STREAM-INF tag identifies a Playlist file
     * containing the I-frames of a multimedia presentation.  It stands
     * alone, in that it does not apply to a particular URI in the Playlist.
     * Its format is:
     *
     * #EXT-X-I-FRAME-STREAM-INF:<attribute-list>
     *
     * All attributes defined for the EXT-X-STREAM-INF tag (Section 3.4.10)
     * are also defined for the EXT-X-I-FRAME-STREAM-INF tag, except for the
     * AUDIO attribute.  In addition, the following attribute is defined:
     *
     * URI
     *
     * The value is a quoted-string containing a URI that identifies the
     * I-frame Playlist file.
     *
     * Every EXT-X-I-FRAME-STREAM-INF tag MUST include a BANDWIDTH attribute
     * and a URI attribute.
     *
     * The provisions in Section 3.4.10.1 also apply to EXT-X-I-FRAME-
     * STREAM-INF tags with a VIDEO attribute.
     *
     * A Playlist that specifies alternative VIDEO renditions and I-frame
     * Playlists SHOULD include an alternative I-frame VIDEO rendition for
     * each regular VIDEO rendition, with the same NAME and LANGUAGE
     * attributes.
     *
     * The EXT-X-I-FRAME-STREAM-INF tag appeared in version 4 of the
     * protocol.  Clients that do not implement protocol version 4 or higher
     * MUST ignore it.
     */
    val EXT_X_I_FRAME_STREAM_INF: HlsTag<Any> = HlsTag(
            "EXT-X-I-FRAME-STREAM-INF",
            appliesTo = HlsTag.AppliesTo.ADDITIONAL_DATA
    ) //TODO: implement

    /**
     * The EXT-X-VERSION tag indicates the compatibility version of the
     * Playlist file.  The Playlist file, its associated media, and its
     * server MUST comply with all provisions of the most-recent version of
     * this document describing the protocol version indicated by the tag
     * value.
     *
     * The EXT-X-VERSION tag applies to the entire Playlist file.  Its
     * format is:
     *
     * #EXT-X-VERSION:<n>
     *
     * where n is an integer indicating the protocol version.
     *
     * A Playlist file MUST NOT contain more than one EXT-X-VERSION tag.  A
     * Playlist file that does not contain an EXT-X-VERSION tag MUST comply
     * with version 1 of this protocol.
     */
    val EXT_X_VERSION: HlsTag<Int> = HlsTag(
            "EXT-X-VERSION",
            appliesTo = HlsTag.AppliesTo.ENTIRE_PLAYLIST,
            required = true,
            unique = false
    ) { rawData ->
        rawData.toInt()
    }

    data class SegmentInformation(val duration: Duration, val title: String)

    data class SourceSubrange(val length: Long, val offset: Long)

    data class EncryptionKey(val method: String, val uri: URI, val iv: BigInteger?)

    data class MediaRendition(
            val type: MediaType,
            val uri: URI?,
            val group: String?,
            val language: String?,
            val name: String?,
            val default: Boolean,
            val autoSelect: Boolean
    )

    data class StreamInformation(
            val bandwidth: Long,
            val programId: Long?,
            val codecs: List<String>,
            val resolution: AttributeListParser.Resolution?,
            val audio: String?,
            val video: String?
    )

    enum class PlaylistType {
        EVENT,
        VOD
    }

    enum class MediaType {
        VIDEO,
        AUDIO,
    }

    private fun readYesNo(input: String): Boolean {
        return when (input) {
            "YES" -> true
            "NO" -> false
            else -> throw IllegalArgumentException("Invalid value for YES/NO: $input")
        }
    }
}

