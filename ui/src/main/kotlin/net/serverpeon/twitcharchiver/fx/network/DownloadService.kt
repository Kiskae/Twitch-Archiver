package net.serverpeon.twitcharchiver.fx.network

import javafx.concurrent.Service
import javafx.concurrent.Task
import javafx.concurrent.Worker
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

//TODO: include method to download, and way to query full download state
class DownloadService() : Service<Any>() {
    companion object {
        private const val PROGRESS_RESOLUTION: Long = 200
    }

    private val tracker: ConcurrentProgressTracker = ConcurrentProgressTracker()

    fun executeOn(executor: ExecutorService) {
        check(this.state == Worker.State.READY)
        this.executor = executor
        this.restart()
    }

    inner class WrappedUpdater(val task: Task<*>, val updateProxy: (Long) -> Unit) {
        fun update(f: ConcurrentProgressTracker.() -> Unit) {
            tracker.f()
            updateProxy(tracker.progress(PROGRESS_RESOLUTION))
        }

        fun updatePassthrough(f: ConcurrentProgressTracker.(Long) -> Long) {
            updateProxy(tracker.f(PROGRESS_RESOLUTION))
        }
    }

    override fun createTask(): Task<Any> {
        //TODO: reset download progress
        tracker.reset(0, 1)
        return object : Task<Any>() {
            override fun call(): Any? {
                val updater = WrappedUpdater(this) { updateProgress(it, PROGRESS_RESOLUTION + 1) }

                try {
                    updater.update { this.modifyExpectedTotal(200, updater) }

                    for (i in 1..200) {
                        println(tracker)
                        TimeUnit.MILLISECONDS.sleep(50)

                        updater.updatePassthrough { tracker.addAndGetProgress(1, it) }
                    }

                    updateProgress(PROGRESS_RESOLUTION.toLong(), PROGRESS_RESOLUTION.toLong())
                    return null
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    throw ex
                }
            }

        }
    }

}