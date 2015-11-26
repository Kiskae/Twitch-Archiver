package net.serverpeon.twitcharchiver.network

import javafx.application.Platform
import javafx.beans.binding.BooleanBinding
import javafx.beans.property.SimpleObjectProperty
import net.serverpeon.twitcharchiver.twitch.TwitchApi
import rx.Observable
import rx.Scheduler
import rx.Single
import rx.schedulers.Schedulers
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ApiWrapper(private val api: TwitchApi) {
    private val ownerProp: SimpleObjectProperty<Any?> = SimpleObjectProperty(null)
    private val ownerLock: AtomicReference<Any?> = AtomicReference(null)
    private val accessLock: AtomicBoolean = AtomicBoolean(false)

    fun hasAccess(lockObj: Any): BooleanBinding {
        return ownerProp.isNull.or(ownerProp.isEqualTo(lockObj))
    }

    fun <T> request(lockObj: Any, f: TwitchApi.() -> Observable<T>): Observable<T> {
        if (setLock(lockObj)) {
            return api.f()
                    .observeOn(schedulerFx)
                    .doOnTerminate { // Create request and unlock
                        check(setLock(null))
                    }
        } else {
            return Observable.empty()
        }
    }

    private fun setLock(value: Any?): Boolean {
        val success = if (value != null) {
            ownerLock.compareAndSet(null, value)
        } else {
            ownerLock.getAndSet(null) != null
        }

        if (success) {
            ownerProp.set(value)
            return true
        } else {
            return false;
        }
    }

    private val schedulerFx: Scheduler by lazy {
        Schedulers.from { Platform.runLater(it) }
    }
}