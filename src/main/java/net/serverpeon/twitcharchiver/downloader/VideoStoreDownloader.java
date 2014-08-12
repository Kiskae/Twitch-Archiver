package net.serverpeon.twitcharchiver.downloader;

import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class VideoStoreDownloader extends Thread {
    private final static Logger logger = LogManager.getLogger(VideoStoreDownloader.class);
    private static final AtomicInteger EXECUTION_ID = new AtomicInteger(0);
    private final ExecutorService es;
    private final VideoStore vs;
    private final Runnable guiCallback;

    public VideoStoreDownloader(final VideoStore vs, final Runnable guiCallback, int numberOfProcesses) {
        super(String.format("Downloader #%d", EXECUTION_ID.incrementAndGet()));
        this.vs = vs;
        this.guiCallback = guiCallback;
        this.es = Executors.newFixedThreadPool(numberOfProcesses);
    }

    @Override
    public void run() {
        final List<Future<Void>> results = Lists.newArrayList();
        do {
            final Iterator<StoredBroadcast> it = this.vs.getAllStoredBroadcasts().iterator();

            final List<Callable<Void>> downloadTasks = Lists.newLinkedList();
            final int progressBarIdx = VideoStoreTableView.COLUMNS.DOWNLOAD_PROGRESS.getIdx();
            final VideoStoreTableView tm = this.vs.getTableView();

            int cursor = 0;
            while (it.hasNext()) {
                final StoredBroadcast sb = it.next();

                if (sb.isSelected()) {
                    final int rowIdx = cursor;
                    downloadTasks.add(new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            sb.download(tm, rowIdx, progressBarIdx);
                            return null;
                        }
                    });
                }

                ++cursor;
            }

            if (downloadTasks.isEmpty()) break;

            try {
                results.addAll(es.invokeAll(downloadTasks));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } while (false);

        for (final Future<Void> f : results) {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                logger.debug(e.getMessage(), e);
            }
        }

        this.es.shutdown();
        SwingUtilities.invokeLater(guiCallback);
    }
}
