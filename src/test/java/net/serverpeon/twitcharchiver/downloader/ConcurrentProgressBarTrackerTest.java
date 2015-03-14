package net.serverpeon.twitcharchiver.downloader;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Uninterruptibles;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;

public class ConcurrentProgressBarTrackerTest {
    private final static long MB = (long) 1024 * 1024;
    private ForkJoinPool testingPool = null;

    @Before
    public void setUp() throws Exception {
        testingPool = new ForkJoinPool(5);
    }

    @After
    public void tearDown() throws Exception {
        testingPool.shutdown();
        testingPool = null;
    }

    public void runDistibutedTest(
            final ConcurrentProgressBarTracker target,
            final int concurrency,
            final long totalDownload
    ) {
        testingPool.invoke(ForkJoinTask.adapt(new Runnable() {
            @Override
            public void run() {
                long batchSize = ((Number) Math.ceil(totalDownload / (1.f * concurrency))).longValue();
                long cursor = totalDownload;

                List<ForkJoinTask<?>> subTasks = Lists.newArrayListWithCapacity(concurrency);
                while (cursor > 0) {
                    long newCursor = cursor - batchSize;
                    subTasks.add(ForkJoinTask.adapt(new DownloadSimulator(target, cursor - Math.max(newCursor, 0))));
                    cursor = newCursor;
                }

                ForkJoinTask.invokeAll(subTasks);
            }
        }));
    }

    @Test
    public void testBasicDistributedDownload() {
        final ConcurrentProgressBarTracker pBar = new ConcurrentProgressBarTracker();
        pBar.reset(0, 10);
        runDistibutedTest(pBar, 10, 10 * MB);

        Assert.assertEquals(1000, pBar.getProgress(1000));
    }

    @Test
    public void testResumedDownload() {
        final ConcurrentProgressBarTracker pBar = new ConcurrentProgressBarTracker();
        pBar.reset(2, 10);
        Assert.assertEquals(200, pBar.getProgress(1000));


        runDistibutedTest(pBar, 8, 10 * MB);
        Assert.assertEquals(1000, pBar.getProgress(1000));
    }

    @Test
    public void testPartialDownload() {
        final ConcurrentProgressBarTracker pBar = new ConcurrentProgressBarTracker();
        pBar.reset(2, 10);
        Assert.assertEquals(200, pBar.getProgress(1000));


        runDistibutedTest(pBar, 5, 10 * MB);

        // 2 + 5 completed
        Assert.assertEquals(700, pBar.getProgress(1000));
    }

    @Test
    public void testLargeDistributedDownload() {
        final ConcurrentProgressBarTracker pBar = new ConcurrentProgressBarTracker();
        pBar.reset(0, 10);
        runDistibutedTest(pBar, 10, 1024 * MB); //1GB

        Assert.assertEquals(1000, pBar.getProgress(1000));
    }

    private class DownloadSimulator implements Runnable {
        private final ConcurrentProgressBarTracker target;
        private final long totalSize;
        private boolean init = false;

        public DownloadSimulator(final ConcurrentProgressBarTracker target, long totalSize) {
            this.target = target;
            this.totalSize = totalSize;
        }

        @Override
        public void run() {
            if (!init) {
                target.modifyExpectedTotal(totalSize, this);
                this.init = true;
            }

            long cursor = totalSize;
            while (cursor > 0) {
                long newCursor = cursor - 64 * 1024; //64Kb
                target.updateProgress(cursor - Math.max(newCursor, 0));
                cursor = newCursor;

                //Artificially delay
                Uninterruptibles.sleepUninterruptibly(1, TimeUnit.MILLISECONDS);
            }
        }
    }
}