package net.serverpeon.twitcharchiver.fx.merging

import rx.Observable
import rx.Subscriber

class ExtractFirstOperator<F, T>(val firstTransform: (T) -> F) : Observable.Operator<Pair<F, T>, T> {
    override fun call(child: Subscriber<in Pair<F, T>>?): Subscriber<in T>? {
        return object : Subscriber<T>(child) {
            var initialValue: F? = null

            override fun onError(e: Throwable?) {
                child!!.onError(e)
            }

            override fun onNext(t: T) {
                if (initialValue != null) {
                    child!!.onNext(Pair(initialValue!!, t))
                } else {
                    //Swallow and store the initial value
                    initialValue = firstTransform(t)
                    request(1)
                }
            }

            override fun onCompleted() {
                child!!.onCompleted()
            }

        }
    }
}