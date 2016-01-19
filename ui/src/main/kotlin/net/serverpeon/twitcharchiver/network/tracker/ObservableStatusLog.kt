package net.serverpeon.twitcharchiver.network.tracker

import com.google.common.io.Files
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ObservableMap
import net.serverpeon.twitcharchiver.ReactiveFx
import net.serverpeon.twitcharchiver.fx.transformObservable
import net.serverpeon.twitcharchiver.observeInvalidation
import net.serverpeon.twitcharchiver.toFx
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Observable
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.collections.set

class ObservableStatusLog(resourceDirectory: ReadOnlyObjectProperty<Path?>) {
    private val IO = transformObservable(resourceDirectory) {
        it?.let {
            it.resolve("status.json") //status.json file
        }?.let {
            it.toFile()
        }
    }

    private val status: ObservableMap<Int, StatusLogPersistence.Status> = FXCollections.observableHashMap()
    private var inLoad: Boolean = false
    private val mapInvalidation: Observable<ObservableMap<Int, StatusLogPersistence.Status>>

    private fun batchUpdate(newState: Map<Int, StatusLogPersistence.Status>) {
        status.keys.retainAll(newState.keys)
        status.putAll(newState)
    }

    private fun <T> accessStatusIfPresent(value: File?, func: (File) -> T): T? {
        return value?.let {
            if (it.exists()) {
                func(it)
            } else {
                null
            }
        }
    }

    init {
        //Load initial state
        batchUpdate(StatusLogPersistence.read(accessStatusIfPresent(IO.value) {
            Files.asCharSource(it, Charsets.UTF_8)
        }))

        IO.addListener { obs, oldVal, newVal ->
            inLoad = true
            batchUpdate(StatusLogPersistence.read(accessStatusIfPresent(newVal) { Files.asCharSource(it, Charsets.UTF_8) }))
            inLoad = false
        }

        val baseObservable = status.observeInvalidation()
        mapInvalidation = limitUpdates(baseObservable)

        //Special listener for the write trigger
        limitUpdates(baseObservable.filter { !inLoad }).subscribe { //Should save after change unless loading
            IO.value?.apply {
                StatusLogPersistence.writeTo(this.let { Files.asCharSink(it, Charsets.UTF_8) }, it)
            }
        }
    }

    operator fun get(idx: Int): StatusLogPersistence.Status = status[idx] ?: StatusLogPersistence.Status.UNTRACKED

    operator fun set(idx: Int, state: StatusLogPersistence.Status?) {
        if (state != null) {
            status[idx] = state
        } else {
            status.remove(idx)
        }
    }

    fun <T> createProperty(processor: (Map<Int, StatusLogPersistence.Status>) -> T): ObservableValue<T> {
        return mapInvalidation.map { processor(it) }.toFx(processor(status))
    }

    fun size(): Int {
        return status.size
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ObservableStatusLog::class.java)

        private fun <T> limitUpdates(obs: Observable<T>): Observable<T> {
            return obs.throttleLast(1, TimeUnit.SECONDS).observeOn(ReactiveFx.scheduler)
        }
    }
}