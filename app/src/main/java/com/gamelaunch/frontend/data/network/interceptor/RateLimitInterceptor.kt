package com.gamelaunch.frontend.data.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

open class RateLimitInterceptor(private val minIntervalMs: Long = 1200) : Interceptor {

    @Volatile private var lastRequestMs: Long = 0

    override fun intercept(chain: Interceptor.Chain): Response {
        if ("screenscraper.fr" in chain.request().url.host) {
            synchronized(this) {
                val now = System.currentTimeMillis()
                val elapsed = now - lastRequestMs
                if (elapsed < minIntervalMs) {
                    Thread.sleep(minIntervalMs - elapsed)
                }
                lastRequestMs = System.currentTimeMillis()
            }
        }
        return chain.proceed(chain.request())
    }
}
