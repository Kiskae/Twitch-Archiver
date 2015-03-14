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
import java.util.concurrent.TimeUnit;

public class VideoStoreDownloader implements Runnable {
    private final static Logger logger = LogManager.getLogger(VideoStoreDownloader.class);

    private final ForkJoinPool pool;
    private final VideoStore vs;
    private final Runnable guiCallback;

    public VideoStoreDownloader(final VideoStore vs, final Runnable guiCallback, int numberOfProcesses) {
        this.vs = vs;
        this.guiCallback = guiCallback;
        this.pool = new ForkJoinPool(numberOfProcesses, ForkJoinPool.defaultForkJoinWorkerThreadFactory, new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                logger.error(new ParameterizedMessage("Uncaught exception in thread {}", t), e);
            }
        }, false);
    }

    @Override
    public void run() {
        final long startTime = System.nanoTime();

        final List<ForkJoinTask<?>> tasks = Lists.newArrayList();

        final Iterator<StoredBroadcast> it = this.vs.getAllStoredBroadcasts().iterator();
        final VideoStoreTableView tm = this.vs.getTableView();

        int rowIdx = 0;
        while (it.hasNext()) {
            final StoredBroadcast sb = it.next();

            if (sb.isSelected()) {
                tasks.add(pool.submit(sb.getDownloadTask(tm, rowIdx)));
            }

            ++rowIdx;
        }

        for (final ForkJoinTask<?> f : tasks) {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                logger.debug(e.getMessage(), e);
            }
        }

        this.pool.shutdown();
        SwingUtilities.invokeLater(guiCallback);

        long runTime = System.nanoTime() - startTime;
        logger.debug("All downloads finished after {} seconds", TimeUnit.NANOSECONDS.toSeconds(runTime));
    }
}
