package net.serverpeon.twitcharchiver.downloader;

import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import javax.swing.*;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class VideoStoreDownloader extends Thread {
    private final static Logger logger = LogManager.getLogger(VideoStoreDownloader.class);
    private static final AtomicInteger EXECUTION_ID = new AtomicInteger(0);

    private final ForkJoinPool pool;
    private final VideoStore vs;
    private final Runnable guiCallback;

    public VideoStoreDownloader(final VideoStore vs, final Runnable guiCallback, int numberOfProcesses) {
        super(String.format("Downloader #%d", EXECUTION_ID.incrementAndGet()));
        this.vs = vs;
        this.guiCallback = guiCallback;
        this.pool = new ForkJoinPool(numberOfProcesses, ForkJoinPool.defaultForkJoinWorkerThreadFactory, new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                logger.error(new ParameterizedMessage("Uncaught exception in thread {}", t), e);
            }
        }, false);
    }

    @Override
    public void run() {
        final List<ForkJoinTask<Void>> tasks = Lists.newArrayList();
        do {
            final Iterator<StoredBroadcast> it = this.vs.getAllStoredBroadcasts().iterator();
            final VideoStoreTableView tm = this.vs.getTableView();

            int cursor = 0;
            while (it.hasNext()) {
                final StoredBroadcast sb = it.next();

                if (sb.isSelected()) {
                    final int rowIdx = cursor;
                    tasks.add(pool.submit(sb.getDownloadTask(tm, rowIdx)));
                }

                ++cursor;
            }
        } while (false);

        for (final ForkJoinTask<Void> f : tasks) {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                logger.debug(e.getMessage(), e);
            }
        }

        this.pool.shutdown();
        SwingUtilities.invokeLater(guiCallback);
    }
}
