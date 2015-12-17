package net.serverpeon.twitcharchiver.twitch.playlist

data class EncodingDescription(val parameters: List<String>, val type: EncodingDescription.IOType) {
    enum class IOType {
        FILE_CONCAT,
        INPUT_CONCAT
    }
}