package net.serverpeon.twitcharchiver.hls;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.net.URI;
import java.util.List;
import java.util.Map;

public class HLSPlaylist<E> {
    public final List<E> resource;
    public final Map<String, Object> properties;

    public HLSPlaylist(final List<E> resource, final Map<String, Object> properties) {
        this.resource = ImmutableList.copyOf(resource);
        this.properties = ImmutableMap.copyOf(properties);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("resource", resource)
                .add("properties", properties)
                .toString();
    }

    public static class PlayList {
        public final String groupId;
        //bits per second
        public final long bandwidth;
        public final List<String> codec;
        public final URI playlistLocation;

        private PlayList(String groupId, long bandwidth, String[] codec, URI playlistLocation) {
            this.groupId = groupId;
            this.bandwidth = bandwidth;
            this.codec = ImmutableList.copyOf(codec);
            this.playlistLocation = playlistLocation;
        }

        public static PlayList make(String groupId, long bandwidth, String[] codec, URI playlistLocation) {
            return new PlayList(groupId, bandwidth, codec, playlistLocation);
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("groupId", groupId)
                    .add("bandwidth", bandwidth)
                    .add("codec", codec)
                    .add("playlistLocation", playlistLocation)
                    .toString();
        }
    }

    public static class Video {
        public final URI videoLocation;
        public final long lengthMS; //-1 indicates lack of specified length

        private Video(final URI videoLocation, final long lengthMS) {
            this.videoLocation = videoLocation;
            this.lengthMS = lengthMS;
        }

        public static Video make(final URI videoLocation, final long lengthMS) {
            return new Video(videoLocation, lengthMS);
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("videoLocation", videoLocation)
                    .add("lengthMS", lengthMS)
                    .toString();
        }
    }
}
