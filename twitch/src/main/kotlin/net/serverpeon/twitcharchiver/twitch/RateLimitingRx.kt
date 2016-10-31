package net.serverpeon.twitcharchiver.twitch

import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.SingleOnSubscribe
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Suppress("UsePropertyAccessSyntax")
class RateLimitingRx(scheduler: Scheduler, val time: Long, val unit: TimeUnit) {
    private val worker = scheduler.createWorker()
    private val unitsInFlight: AtomicLong = AtomicLong(0)
    private val emitters = ConcurrentLinkedQueue<SingleEmitter<Unit>>()

    private val rxSource: SingleOnSubscribe<Unit> = SingleOnSubscribe { emitter ->
        emitters.add(emitter)
        if (unitsInFlight.incrementAndGet() == 1L) {
            scheduleEmitter()
        }
    }

    private fun scheduleEmitter() {
        val emitter = emitters.remove()
        if (emitter.isDisposed) {
            // If disposed, immediately try to schedule the next emitter
            worker.schedule {
                if (unitsInFlight.decrementAndGet() > 0) {
                    scheduleEmitter()
                }
            }
            return
        }

        emitter!!.onSuccess(Unit)
        worker.schedule({
            if (unitsInFlight.decrementAndGet() > 0) {
                scheduleEmitter()
            }
        }, time, unit)
    }

    fun rx(): Single<Unit> {
        return Single.create(rxSource)
    }

    fun dispose() {
        worker.dispose()
    }
}