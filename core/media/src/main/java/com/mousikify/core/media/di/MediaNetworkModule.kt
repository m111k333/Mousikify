package com.mousikify.core.media.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaNetworkModule {

    private const val VISIONOS_USER_AGENT =
        "com.google.visionos.youtube/1.02(RealityDevice14,1; U; CPU visionOS 25_6_0 like Mac OS X; GB)"
    private const val VR_USER_AGENT =
        "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 14; en_US; Quest 3; Build/UP1A.231005.007.A1; Cronet/132.0.6808.3)"
    private const val IOS_USER_AGENT =
        "com.google.ios.youtube/21.03.2(iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X; GB)"
    private const val ANDROID_TESTSUITE_USER_AGENT =
        "com.google.android.youtube/1.9 (Linux; U; Android 14; en_US)"
    private const val ANDROID_USER_AGENT =
        "com.google.android.youtube/21.03.36 (Linux; U; Android 15; GB) gzip"

    @Provides
    @Singleton
    fun provideSharedOkHttpClient(): OkHttpClient {
        val googleVideoInterceptor = Interceptor { chain ->
            val original = chain.request()
            val url = original.url
            if (url.host.contains("googlevideo.com", ignoreCase = true)) {
                val clientParam = url.queryParameter("c").orEmpty()
                val matchingUa = when {
                    clientParam.contains("VISIONOS", ignoreCase = true) -> VISIONOS_USER_AGENT
                    clientParam.contains("ANDROID_VR", ignoreCase = true) -> VR_USER_AGENT
                    clientParam.contains("IOS", ignoreCase = true) -> IOS_USER_AGENT
                    clientParam.contains("ANDROID_TESTSUITE", ignoreCase = true) -> ANDROID_TESTSUITE_USER_AGENT
                    clientParam.contains("ANDROID", ignoreCase = true) -> ANDROID_USER_AGENT
                    else -> original.header("User-Agent") ?: VISIONOS_USER_AGENT
                }
                val builder = original.newBuilder()
                    .removeHeader("User-Agent")
                    .header("User-Agent", matchingUa)

                // Critical: Media3 omits the Range header when position == 0 and length == C.LENGTH_UNSET,
                // which triggers googlevideo.com 28 KB/s bandwidth throttling. Sending Range: bytes=0-
                // forces HTTP 206 unthrottled streaming (~2.7 MB/s).
                if (original.header("Range") == null) {
                    builder.header("Range", "bytes=0-")
                }
                chain.proceed(builder.build())
            } else {
                chain.proceed(original)
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(googleVideoInterceptor)
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
