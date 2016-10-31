package net.serverpeon.twitcharchiver.network

import javafx.beans.binding.BooleanBinding
import javafx.beans.property.SimpleObjectProperty
import net.serverpeon.twitcharchiver.ReactiveFx
import net.serverpeon.twitcharchiver.twitch.LegacyTwitchApi
import org.slf4j.LoggerFactory
import rx.Observable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ApiWrapper(private val apiLegacy: LegacyTwitchApi) {
    private val ownerProp: SimpleObjectProperty<Any?> = SimpleObjectProperty(null)
    private val ownerLock: AtomicReference<Any?> = AtomicReference(null)
    private val log = LoggerFactory.getLogger(ApiWrapper::class.java)

    fun hasAccess(lockObj: Any): BooleanBinding {
        return ownerProp.isNull.or(ownerProp.isEqualTo(lockObj))
    }

    fun <T> request(lockObj: Any, f: LegacyTwitchApi.() -> Observable<T>): Observable<T> {
        if (setLock(lockObj)) {
            return apiLegacy.f()
                    .doOnError { log.warn("Error in request", it) }
                    .observeOn(ReactiveFx.scheduler)
                    .doOnUnsubscribe { // Create request and unlock
                        check(setLock(null))
                    }
        } else {
            return Observable.empty()
        }
    }

    fun lock(lockObj: Any): AutoCloseable? {
        if (setLock(lockObj)) {
            return AutoCloseable {
                setLock(null)
            }
        } else {
            return null
        }
    }

    private fun setLock(value: Any?): Boolean {
        log.debug("Api lock: {} -> {}", ownerLock, value)

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
}