package com.muufin.compose.core

import com.muufin.compose.BuildConfig
import com.muufin.compose.model.AuthState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.security.SecureRandom
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import android.util.Base64
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object HttpClients {
    private val apiRef = AtomicReference<OkHttpClient?>()
    private val playerRef = AtomicReference<OkHttpClient?>()

    
    fun rebuild() {
        apiRef.set(buildApiClient())
        playerRef.set(buildPlayerClient())
    }

    fun apiOkHttp(): OkHttpClient {
        return apiRef.get() ?: buildApiClient().also { apiRef.set(it) }
    }

    fun playerOkHttp(): OkHttpClient {
        return playerRef.get() ?: buildPlayerClient().also { playerRef.set(it) }
    }

    private fun buildApiClient(): OkHttpClient {
        return buildClient(
            acceptHeader = "application/json",
            forPlayback = false,
        )
    }

    private fun buildPlayerClient(): OkHttpClient {
        
        return buildClient(
            acceptHeader = "*/*",
            forPlayback = true,
            readTimeoutSeconds = 0,
        )
    }

    private fun buildClient(
        acceptHeader: String,
        forPlayback: Boolean,
        readTimeoutSeconds: Long = 30,
    ): OkHttpClient {
        val state: AuthState = runBlocking { AuthManager.state.first() }

        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthHeaderInterceptor(acceptHeader = acceptHeader, forPlayback = forPlayback))

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
        }

        if (state.disableTls) {
            val unsafe = unsafeTrustManager()
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(unsafe), SecureRandom())

            builder.sslSocketFactory(sslContext.socketFactory, unsafe)
            builder.hostnameVerifier(HostnameVerifier { _, _ -> true })
        } else if (state.customCaBase64.isNotBlank()) {
            
            val tm = compositeTrustManagerFromBase64(state.customCaBase64)
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(tm), SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, tm)
        }

        return builder.build()
    }

    private class AuthHeaderInterceptor(
        private val acceptHeader: String,
        private val forPlayback: Boolean,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val state = AuthManager.state.value
            val req: Request = chain.request()

            val mediaBrowserAuth = JellyfinAuthorization.buildFrom(
                state,
                includeToken = !state.accessToken.isBlank(),
            )

            val b = req.newBuilder()
                
                .header("Accept", acceptHeader)

            
            if (req.header("Authorization").isNullOrBlank()) {
                b.header("Authorization", mediaBrowserAuth)
            }

            
            if (forPlayback && !state.accessToken.isBlank()) {
                b.header("X-Emby-Authorization", mediaBrowserAuth)
                b.header("X-Emby-Token", state.accessToken)
                b.header("X-MediaBrowser-Token", state.accessToken)
            }

            return chain.proceed(b.build())
        }
    }

    private fun unsafeTrustManager(): X509TrustManager {
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
    }

    private fun compositeTrustManagerFromBase64(certBase64: String): X509TrustManager {
        val sysTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        sysTmf.init(null as KeyStore?)
        val systemTm = sysTmf.trustManagers.filterIsInstance<X509TrustManager>().first()

        val decoded = Base64.decode(certBase64, Base64.DEFAULT)
        val cf = CertificateFactory.getInstance("X.509")
        val cert = cf.generateCertificate(decoded.inputStream())

        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null)
        ks.setCertificateEntry("custom", cert)

        val customTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        customTmf.init(ks)
        val customTm = customTmf.trustManagers.filterIsInstance<X509TrustManager>().first()

        return CompositeTrustManager(listOf(systemTm, customTm))
    }

    private class CompositeTrustManager(
        private val delegates: List<X509TrustManager>,
    ) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            var last: Throwable? = null
            for (tm in delegates) {
                try {
                    tm.checkClientTrusted(chain, authType)
                    return
                } catch (t: Throwable) {
                    last = t
                }
            }
            throw last ?: java.security.cert.CertificateException("No trust manager accepted client cert")
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            var last: Throwable? = null
            for (tm in delegates) {
                try {
                    tm.checkServerTrusted(chain, authType)
                    return
                } catch (t: Throwable) {
                    last = t
                }
            }
            throw last ?: java.security.cert.CertificateException("No trust manager accepted server cert")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return delegates.flatMap { it.acceptedIssuers.toList() }.toTypedArray()
        }
    }
}
