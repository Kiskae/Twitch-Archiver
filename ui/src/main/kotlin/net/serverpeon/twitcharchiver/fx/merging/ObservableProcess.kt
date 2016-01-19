package net.serverpeon.twitcharchiver.fx.merging

import com.google.common.io.CharStreams
import com.google.common.io.LineProcessor
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Single
import rx.schedulers.Schedulers
import rx.subscriptions.Subscriptions
import java.io.InputStream
import java.io.InputStreamReader

class ObservableProcess(private val processSource: Observable<Process>) {
    companion object {
        private val log = LoggerFactory.getLogger(ObservableProcess::class.java)

        fun create(processBuilder: ProcessBuilder): ObservableProcess {
            return ObservableProcess(Observable.create<Process> { sub ->
                log.debug("Starting process with command: {}", processBuilder.command())
                val process = processBuilder.start()
                log.debug("Process: {}", process)

                sub.add(Subscriptions.create { // Destroy process if unsubscribed
                    if (process.isAlive) {
                        process.destroyForcibly()
                    }

                    log.debug("Process: {} ended", process)
                })

                sub.onNext(process) // Emit process

                // Since the unsubscribe method above will kill the process if it is still running when
                //   onCompleted is called we need to block until it is finished.
                process.waitFor()

                sub.onCompleted()
            }.subscribeOn(Schedulers.newThread())) // Avoid blocking something important by spinning up a new thread.
        }
    }

    fun <T> observe(block: ObservableProcess.Api.() -> Observable<T>): Observable<T> {
        return Observable.create { sub ->
            val process = processSource.publish()
            sub.add(Api(process).block().subscribe(sub)) // Create child observable
            sub.add(process.connect()) // Launch process
        }
    }

    /**
     * @return an observable that will either complete when the process ends or emit an [NonZeroExit] exception.
     */
    fun observe(): Observable<Void> {
        return processSource.last().flatMap { process ->
            Observable.create<Void> { sub ->
                if (process.exitValue() == 0) {
                    sub.onCompleted()
                } else {
                    sub.onError(NonZeroExit(process))
                }
            }
        }
    }

    class Api(private val internalProcess: Observable<Process>) {
        val inputStream: Observable<String> by lazy {
            createLazyStream(internalProcess.map { it.inputStream })
        }

        val errorStream: Observable<String> by lazy {
            createLazyStream(internalProcess.map { it.errorStream })
        }

        val process: Single<Process> by lazy {
            internalProcess.replay(1).apply {
                connect() // By using replay, any subscriber can access the process
            }.toSingle()
        }

        val result: Single<Int> by lazy {
            internalProcess.last() // last returns when the process ends
                    .map { it.exitValue() }
                    .publish()
                    .refCount()
                    .toSingle()
        }

        private fun createLazyStream(source: Observable<InputStream>): Observable<String> {
            return source.flatMap { input ->
                Observable.create<String> { sub ->
                    CharStreams.readLines(InputStreamReader(input), object : LineProcessor<Unit> {
                        override fun getResult() {
                            if (!sub.isUnsubscribed) {
                                sub.onCompleted()
                            }
                        }

                        override fun processLine(line: String): Boolean {
                            if (!sub.isUnsubscribed) {
                                sub.onNext(line)
                                return true
                            } else {
                                return false
                            }
                        }
                    })
                }.subscribeOn(Schedulers.newThread()) // Reading the input-stream is blocking, so run on its own thread
            }.publish().refCount() // Share stream and use ref-counting to connect/disconnect
        }
    }

    class NonZeroExit(val process: Process) : RuntimeException(
            "Process exited with non-zero result ${process.exitValue()}"
    )
}