package net.serverpeon.twitcharchiver.fx.data

data class VodInformation(val twitch: TwitchMetadata, val parts: Int, val mutedParts: Int) {
    val approximateSize: Long
        get() {
            val kBps300 = 300 * 1000 //kBps rate at 2.5 Mbps stream
            return twitch.length.seconds * kBps300
        }
}