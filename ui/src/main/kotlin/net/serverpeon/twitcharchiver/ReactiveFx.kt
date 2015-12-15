package net.serverpeon.twitcharchiver

import com.sun.javafx.binding.ExpressionHelper
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import rx.Observable
import rx.Scheduler
import rx.Subscriber
import rx.Subscription
import rx.functions.Action0
import rx.functions.Action1
import rx.schedulers.Schedulers
import rx.subscriptions.Subscriptions
import java.util.concurrent.atomic.AtomicReference

private class ReactiveFxObservable<T>(val base: Observable<T>, default: T) : ObservableValue<T>, Action1<T> {
    override fun call(t: T) {
        ref.set(t)
        ExpressionHelper.fireValueChangedEvent(helper)
    }

    private val ref: AtomicReference<T> = AtomicReference(default)
    private val sub: AtomicReference<Subscription?> = AtomicReference(null)
    private var helper: ExpressionHelper<T>? = null

    override fun getValue(): T {
        return ref.get()
    }

    override fun addListener(listener: ChangeListener<in T>) {
        ensureSubscription()
        helper = ExpressionHelper.addListener(helper, this, listener)
    }

    override fun removeListener(listener: ChangeListener<in T>) {
        if (helper == null) return
        helper = ExpressionHelper.removeListener(helper, listener)
        clearSubscription()
    }

    override fun addListener(listener: InvalidationListener?) {
        ensureSubscription()
        helper = ExpressionHelper.addListener(helper, this, listener)
    }

    override fun removeListener(listener: InvalidationListener?) {
        if (helper == null) return
        helper = ExpressionHelper.removeListener(helper, listener)
        clearSubscription()
    }

    private fun ensureSubscription() {
        if (helper == null) {
            check(sub.compareAndSet(null, base.subscribe(this)))
        }
    }

    private fun clearSubscription() {
        if (helper == null) {
            checkNotNull(sub.getAndSet(null)).unsubscribe()
        }
    }
}

object ReactiveFx {
    val scheduler: Scheduler = Schedulers.from { Platform.runLater(it) }
}

fun <T> Observable<T>.toFx(def: T): ObservableValue<T> {
    return ReactiveFxObservable(this, def)
}

fun <T : javafx.beans.Observable> T.observeInvalidation(): Observable<T> {
    return observeInvalidation { it }
}

private class SubscriptionInvalidator<T : javafx.beans.Observable, O>(
        val sub: Subscriber<O>,
        val obs: T,
        val extractor: (T) -> O
) : InvalidationListener, Action0 {
    init {
        sub.add(Subscriptions.create(this))
    }

    override fun invalidated(observable: javafx.beans.Observable?) {
        sub.onNext(extractor(obs))
    }

    override fun call() {
        //Subscription ended
        obs.removeListener(this)
    }

}

fun <T : javafx.beans.Observable, O> T.observeInvalidation(extractor: (T) -> O): Observable<O> {
    return Observable.create { sub ->
        this.addListener(SubscriptionInvalidator(sub, this, extractor))
    }
}