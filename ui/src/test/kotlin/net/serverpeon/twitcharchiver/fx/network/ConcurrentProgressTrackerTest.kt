package net.serverpeon.twitcharchiver.fx.network

import com.google.common.collect.Lists
import com.google.common.util.concurrent.Uninterruptibles
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.TimeUnit

class ConcurrentProgressTrackerTest {
    private val MB = 1024.toLong() * 1024
    private var testingPool: ForkJoinPool? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        testingPool = ForkJoinPool(5)
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        testingPool!!.shutdown()
        testingPool = null
    }

    fun runDistibutedTest(
            target: ConcurrentProgressTracker,
            concurrency: Int,
            totalDownload: Long) {
        testingPool!!.invoke(ForkJoinTask.adapt {
            val batchSize = (Math.ceil((totalDownload / (1.toDouble() * concurrency))) as Number).toLong()
            var cursor = totalDownload

            val subTasks: MutableList<ForkJoinTask<*>> = Lists.newArrayListWithCapacity(concurrency)
            while (cursor > 0) {
                val newCursor = cursor - batchSize
                subTasks.add(ForkJoinTask.adapt(DownloadSimulator(target, cursor - Math.max(newCursor, 0))))
                cursor = newCursor
            }

            ForkJoinTask.invokeAll<ForkJoinTask<*>>(subTasks)
        })
    }

    @Test
    fun testBasicDistributedDownload() {
        val pBar = ConcurrentProgressTracker()
        pBar.reset(0, 10)
        runDistibutedTest(pBar, 10, 10 * MB)

        Assert.assertEquals(1000, pBar.progress(1000))
    }

    @Test
    fun testResumedDownload() {
        val pBar = ConcurrentProgressTracker()
        pBar.reset(2, 10)
        Assert.assertEquals(200, pBar.progress(1000))


        runDistibutedTest(pBar, 8, 10 * MB)
        Assert.assertEquals(1000, pBar.progress(1000))
    }

    @Test
    fun testPartialDownload() {
        val pBar = ConcurrentProgressTracker()
        pBar.reset(2, 10)
        Assert.assertEquals(200, pBar.progress(1000))


        runDistibutedTest(pBar, 5, 10 * MB)

        // 2 + 5 completed
        Assert.assertEquals(700, pBar.progress(1000))
    }

    @Test
    fun testLargeDistributedDownload() {
        val pBar = ConcurrentProgressTracker()
        pBar.reset(0, 10)
        runDistibutedTest(pBar, 10, 1024 * MB) //1GB

        Assert.assertEquals(1000, pBar.progress(1000))
    }

    @Test
    fun testLargeMajorlyDistributedDownload() {
        val pBar = ConcurrentProgressTracker()
        pBar.reset(0, 300)
        runDistibutedTest(pBar, 300, 1024 * MB) //1GB

        Assert.assertEquals(1000, pBar.progress(1000))
    }

    private inner class DownloadSimulator(private val target: ConcurrentProgressTracker, private val totalSize: Long) : Runnable {
        private var init = false

        override fun run() {
            if (!init) {
                target.modifyExpectedTotal(totalSize, this)
                this.init = true
            }

            var cursor = totalSize
            while (cursor > 0) {
                val newCursor = cursor - 64 * 1024 //64Kb
                target.addAndGetProgress(cursor - Math.max(newCursor, 0), 1)
                cursor = newCursor

                //Artificially delay
                Uninterruptibles.sleepUninterruptibly(1, TimeUnit.MILLISECONDS)
            }
        }
    }
}