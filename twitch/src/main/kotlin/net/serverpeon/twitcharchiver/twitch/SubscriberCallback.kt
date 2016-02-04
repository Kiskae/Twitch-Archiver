package net.serverpeon.twitcharchiver.twitch

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import rx.Single
import rx.SingleSubscriber
import rx.subscriptions.Subscriptions

class SubscriberCallback<T>(val sub: SingleSubscriber<in T>) : Callback<T> {
    override fun onResponse(call: Call<T>, response: Response<T>) {
        if (response.isSuccess) {
            sub.onSuccess(response.body())
        } else {
            sub.onError(ResponseException(response))
        }
    }

    override fun onFailure(call: Call<T>, t: Throwable?) {
        sub.onError(t)
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