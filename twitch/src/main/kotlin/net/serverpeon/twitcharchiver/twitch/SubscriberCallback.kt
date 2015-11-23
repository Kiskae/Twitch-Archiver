package net.serverpeon.twitcharchiver.twitch

import retrofit.Call
import retrofit.Callback
import retrofit.Response
import retrofit.Retrofit
import rx.Single
import rx.SingleSubscriber
import rx.subscriptions.Subscriptions

class SubscriberCallback<T>(val sub: SingleSubscriber<in T>) : Callback<T> {
    override fun onFailure(t: Throwable?) {
        sub.onError(t)
    }

    override fun onResponse(response: Response<T>, retrofit: Retrofit?) {
        if (response.isSuccess) {
            sub.onSuccess(response.body())
        } else {
            sub.onError(ResponseException(response))
        }
    }

    class ResponseException(val resp: Response<*>) : RuntimeException(resp.message()) {
    }
}

fun <T> Call<T>.toRx(): Single<T> {
    return Single.create { sub ->
        this.clone().apply {
            // Kick off the request
            enqueue(SubscriberCallback(sub))

            // If the subscriber unsubscribes, cancel the request
            sub.add(Subscriptions.create {
                this.cancel()
            })
        }
    }
}