package com.app.pustakam.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal expect  fun platformHttpClient (config: HttpClientConfig<*>.()-> Unit): HttpClient
fun createHttpClient(authTokenProvider:()-> String? ): HttpClient = platformHttpClient {
    val appJson = Json {
        explicitNulls = false
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint= true
        // 🔧 C8: sealed NoteContentModel discriminates on "type" (TEXT/MEDIA/LINK/LOCATION);
        //       the ContentType property serializes as "contentType" to avoid the clash
        classDiscriminator = "type"
        // 🔧 C8: server nulls coerce to defaults instead of throwing on non-null fields
        coerceInputValues = true
    }
    install(ContentNegotiation) { json(appJson) }
    install(Logging ){
        level= LogLevel.INFO
        logger = Logger.DEFAULT
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 30_000
    }
    defaultRequest {
        authTokenProvider()?.takeIf { it.isNotBlank() }?.let{
            headers.append(HttpHeaders.Authorization, "Bearer $it")
        }
    }
}

//private fun getBaseUrl(): String = "https://notesapp-s8wpnlgb.b4a.run"
private fun getBaseUrl(): String = "https://unarmored-bucket-yo-yo.ngrok-free.dev"
private fun getBaseUrlDev(): String =
    //"http://192.168.68.103:3000"
"http://192.168.31.4:3000"
fun getUrl(): String = getBaseUrl()

// 💬 31-Aug-2026 chat: the socket rides the SAME host as the REST API, so there is one base url to
//   change and one certificate to trust. https -> wss, http -> ws; anything else is left alone.
private const val CHAT_SOCKET_PATH = "/ws/chat"
private const val HTTPS_SCHEME = "https://"
private const val HTTP_SCHEME = "http://"
private const val WSS_SCHEME = "wss://"
private const val WS_SCHEME = "ws://"

fun getWebSocketUrl(): String {
    val base = getUrl().trimEnd('/')
    val socketBase = when {
        base.startsWith(HTTPS_SCHEME) -> WSS_SCHEME + base.removePrefix(HTTPS_SCHEME)
        base.startsWith(HTTP_SCHEME) -> WS_SCHEME + base.removePrefix(HTTP_SCHEME)
        else -> base
    }
    return socketBase + CHAT_SOCKET_PATH
}

// 🖼️ 31-Aug-2026 profile: avatarUrl is stored RELATIVE (/media/avatar/<id>) because the base
//   URL is a tunnel that changes; an absolute URL baked into the database rots on restart.
//   An already-absolute value is passed through untouched.
fun String?.toAbsoluteMediaUrl(): String? {
    val path = this?.takeIf { it.isNotBlank() } ?: return null
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    return getUrl().trimEnd('/') + if (path.startsWith("/")) path else "/" + path
}
