package net.serverpeon.twitcharchiver.downloader;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.gson.JsonElement;
import net.serverpeon.twitcharchiver.twitch.BroadcastInformation;
import net.serverpeon.twitcharchiver.twitch.OAuthToken;
import net.serverpeon.twitcharchiver.twitch.TwitchApi;
import net.serverpeon.twitcharchiver.twitch.TwitchApiException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

public class VideoStore {
    private final static Logger logger = LogManager.getLogger(VideoStore.class);
    private final VideoStoreTableView tableView = new VideoStoreTableView(this);
    private final File storageDirectory;
    private final String channelName;
    private final OAuthToken oAuthToken;
    private final List<StoredBroadcast> items = Lists.newArrayList();

    public VideoStore(final File storageDirectory, final String channelName, final OAuthToken oAuthToken) {
        this.storageDirectory = checkNotNull(storageDirectory, "Storage location cannot be NULL");
        this.channelName = checkNotNull(channelName, "Channel name cannot be NULL");
        this.oAuthToken = checkNotNull(oAuthToken, "OAuth token cannot be NULL");
        this.storageDirectory.mkdirs();
    }

    private void loadBroadcastInformation(final Iterator<JsonElement> videos, final Predicate<BroadcastInformation> processor) {
        //int requestCount = 0;
        while (videos.hasNext()) {
            final JsonElement videoInfo = videos.next();
            try {
                processor.apply(TwitchApi.getBroadcastInformation(videoInfo, oAuthToken));
            } catch (TwitchApiException ex) {
                logger.warn(new ParameterizedMessage("Failure to get {}", videoInfo), ex);
            }

            Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
        }
    }

    public File getStorageDirectory() {
        return this.storageDirectory;
    }

    List<StoredBroadcast> getAllStoredBroadcasts() {
        return Collections.unmodifiableList(this.items);
    }

    StoredBroadcast getBroadcastByIdx(final int idx) {
        return items.get(idx);
    }

    int getNumberOfBroadcasts() {
        return items.size();
    }

    public VideoStoreTableView getTableView() {
        return this.tableView;
    }

    public void loadData(final int videoLimit, final Runnable readyCallback) {
        loadBroadcastInformation(
                videoLimit > 0 ?
                        TwitchApi.getLimitedPastBroadcastsForChannel(channelName, oAuthToken, videoLimit) :
                        TwitchApi.getAllPastBroadcastsForChannel(channelName, oAuthToken),
                new Predicate<BroadcastInformation>() {
                    @Override
                    public boolean apply(BroadcastInformation broadcastInformation) {
                        items.add(new StoredBroadcast(broadcastInformation, storageDirectory));
                        tableView.fireTableRowsInserted(items.size(), items.size());
                        return false;
                    }
                }
        );

        readyCallback.run();
    }

    public void setSelectedForAll(final boolean selected) {
        for (final StoredBroadcast sb : items) {
            sb.setSelected(selected);
        }
        tableView.fireTableDataChanged();
    }
}
