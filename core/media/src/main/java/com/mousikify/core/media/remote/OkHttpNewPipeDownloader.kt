package com.mousikify.core.media.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Custom keyless OkHttp Downloader for NewPipeExtractor.
 * Requires zero API keys or user credentials.
 */
class OkHttpNewPipeDownloader(
    private val client: OkHttpClient
) : Downloader() {

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
        private const val CONSENT_COOKIE = "SOCS=CAI"
        private val isInitialized = AtomicBoolean(false)

        fun ensureInitialized(okHttpClient: OkHttpClient) {
            if (isInitialized.compareAndSet(false, true)) {
                val configuredClient = okHttpClient.newBuilder()
                    .readTimeout(20, TimeUnit.SECONDS)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .retryOnConnectionFailure(true)
                    .build()

                NewPipe.init(
                    OkHttpNewPipeDownloader(configuredClient),
                    Localization.DEFAULT,
                    ContentCountry.DEFAULT
                )
            }
        }
    }

    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBody = when {
            dataToSend != null -> dataToSend.toRequestBody()
            httpMethod.equals("POST", ignoreCase = true) -> ByteArray(0).toRequestBody()
            else -> null
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .method(httpMethod, requestBody)
            .header("User-Agent", USER_AGENT)

        var customCookie: String? = null
        var hasCustomMobileUserAgent = false
        for ((headerName, headerValueList) in headers) {
            if (headerName.equals("Cookie", ignoreCase = true)) {
                customCookie = headerValueList.joinToString("; ")
                continue
            }
            if (headerName.equals("User-Agent", ignoreCase = true)) {
                hasCustomMobileUserAgent = headerValueList.any {
                    it.startsWith("com.google.", ignoreCase = true)
                }
            }
            requestBuilder.removeHeader(headerName)
            for (headerValue in headerValueList) {
                requestBuilder.addHeader(headerName, headerValue)
            }
        }

        if (!customCookie.isNullOrBlank()) {
            requestBuilder.header("Cookie", "$CONSENT_COOKIE; $customCookie")
        } else if (!hasCustomMobileUserAgent && !url.contains("youtubei.googleapis.com")) {
            requestBuilder.header("Cookie", CONSENT_COOKIE)
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("Rate-limited (HTTP 429)", url)
        }

        val responseBodyToReturn = response.body?.string()
        val latestUrl = response.request.url.toString()
        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBodyToReturn,
            latestUrl
        )
    }
}
