package com.app.pustakam.core.network

import com.app.pustakam.core.database.localdb.preferences.IAppPreferences
import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.NetworkError
import com.app.pustakam.core.common.util.log_d
import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.core.model.models.request.RefreshReq
import com.app.pustakam.core.model.models.response.User
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.app.pustakam.core.common.util.Result
import kotlinx.coroutines.flow.first

// 🔐 20-Aug-2026 sync: routes whose own 401 means "bad credentials", never "token expired" — refreshing on them loops
private val NON_REFRESHABLE_PATHS = listOf("/login", "/register", "/auth/refresh")

private const val BEARER_PREFIX = "Bearer "
@PublishedApi internal const val STATUS_BAD_REQUEST = 400
@PublishedApi internal const val STATUS_UNAUTHORIZED = 401
@PublishedApi internal const val STATUS_FORBIDDEN = 403
@PublishedApi internal const val STATUS_NOT_FOUND = 404
@PublishedApi internal const val STATUS_REQUEST_TIMEOUT = 408
@PublishedApi internal const val STATUS_CONFLICT = 409
@PublishedApi internal const val STATUS_PAYLOAD_TOO_LARGE = 413
@PublishedApi internal const val STATUS_UNPROCESSABLE_ENTITY = 422
@PublishedApi internal const val STATUS_TOO_MANY_REQUESTS = 429
// 📥 20-Sep-2026 resumable downloads: 206 is a served Range, 416 an impossible one
private const val STATUS_PARTIAL_CONTENT = 206
private const val STATUS_RANGE_NOT_SATISFIABLE = 416
private const val RANGE_UNIT = "bytes"
// 📥 64 KB — small enough that progress moves visibly, large enough not to thrash the IO thread
private const val DOWNLOAD_CHUNK_BYTES = 64 * 1024

abstract class BaseClient  : KoinComponent {
    val userPrefs : IAppPreferences by inject<IAppPreferences>()

    // 🔐 20-Aug-2026 sync: set synchronously by a refresh so the immediate retry does not race the DataStore write
    private var freshToken: String? = null

    private val refreshLock = Mutex()

    protected val httpClient: HttpClient = createHttpClient { freshToken ?: userPrefs.currentTokenOrNull() }

    protected suspend inline fun < reified T, E: Error> baseApiCall(
        crossinline actualApiCall : suspend  () -> HttpResponse,
    ) : Result<T, Error> = flow {
           var response : HttpResponse = try {
               actualApiCall.invoke()
           }
           catch (e: UnresolvedAddressException) {
               log_d("Error", "$e")
               emit(Result.Error(NetworkError.NO_INTERNET))
               return@flow
           }
           catch (e: SerializationException){
               log_d("Error", "$e")
               emit( Result.Error(NetworkError.SERIALIZATION))
               return@flow
           }
        catch (e: ConnectTimeoutException){
            log_d("Error", "$e")
            emit( Result.Error(NetworkError.CONNECTION_FAILED))
            return@flow
        }
        catch (e: Throwable){
            log_d("Error", "$e")
            emit( Result.Error(NetworkError.CONNECTION_FAILED))
            return@flow
        }

        // 🔐 20-Aug-2026 sync: one silent refresh + one replay. Only a FAILED refresh ends the session.
        if (response.status.value == STATUS_UNAUTHORIZED && isRefreshable(response)) {
            if (!refreshSession()) {
                emit(Result.Error(NetworkError.SESSION_EXPIRED))
                return@flow
            }
            response = try {
                actualApiCall.invoke()
            } catch (e: Throwable) {
                log_d("Error", "$e")
                emit(Result.Error(NetworkError.CONNECTION_FAILED))
                return@flow
            }
        }

        captureHeaderToken(response)

           when (response.status.value){
               in 200..299 -> {
                   // 🔧 28-Aug-2026: a body that will not decode used to throw straight out of the
                   //   flow and take the collector with it; it is an error result like any other now.
                   val decoded: Result<T, Error> = try {
                       Result.Success(response.body<T>())
                   } catch (e: Throwable) {
                       log_d("Error", "$e")
                       Result.Error(NetworkError.SERIALIZATION)
                   }
                   emit(decoded)
               }
               // 🔧 20-Sep-2026: one status table, shared with the streaming download path
               else -> emit(Result.Error(errorForStatus(response.status.value)))
           }

       }.flowOn(Dispatchers.IO).first()

    @PublishedApi
    internal fun isRefreshable(response: HttpResponse): Boolean {
        val path = response.call.request.url.encodedPath
        return NON_REFRESHABLE_PATHS.none { path.endsWith(it) }
    }

    // 🔧 20-Aug-2026 sync: the header carries "Bearer <jwt>"; storing it raw made every request send "Bearer Bearer <jwt>"
    @PublishedApi
    internal suspend fun captureHeaderToken(response: HttpResponse) {
        if (!userPrefs.getAuthToken().isNullOrEmpty()) return
        val header = response.headers[headerAuth] ?: response.headers[headerAuth.lowercase()] ?: return
        val token = header.removePrefix(BEARER_PREFIX).trim()
        if (token.isNotEmpty()) {
            freshToken = token
            userPrefs.setToken(token)
        }
    }

    /** 🔐 Rotates the session. Returns false only when the server rejected the refresh token — a
     *  network failure leaves the tokens alone, because offline is not the same as signed out. */
    @PublishedApi
    internal suspend fun refreshSession(): Boolean {
        val tokenBeforeWait = freshToken ?: userPrefs.currentTokenOrNull()
        return refreshLock.withLock {
            // another coroutine already rotated while we waited — its token is the good one
            if ((freshToken ?: userPrefs.currentTokenOrNull()) != tokenBeforeWait) return@withLock true

            val refreshToken = userPrefs.getRefreshToken()?.takeIf { it.isNotBlank() }
                ?: return@withLock false

            val response = try {
                httpClient.post(urlString = ApiRoute.AUTH_REFRESH.getName()) {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshReq(refreshToken))
                }
            } catch (e: Throwable) {
                log_d("BaseClient", "refresh could not reach the server: $e")
                return@withLock false
            }

            if (response.status.value !in 200..299) {
                log_d("BaseClient", "refresh rejected with ${response.status.value}")
                // 🔐 28-Aug-2026 — ONLY the server saying "this refresh token is no good" ends the
                //   session. Clearing on any non-2xx meant a 502/504 from the tunnel silently wiped
                //   the tokens: the app still looked signed in, and nothing ever synced again.
                if (response.status.value == STATUS_UNAUTHORIZED || response.status.value == STATUS_FORBIDDEN) {
                    userPrefs.clearTokens()
                    freshToken = null
                }
                return@withLock false
            }

            val user = try {
                response.body<BaseResponse<User>>().data
            } catch (e: Throwable) {
                log_d("BaseClient", "refresh body unreadable: $e")
                return@withLock false
            }

            val access = user?.accessToken?.takeIf { it.isNotBlank() } ?: return@withLock false
            freshToken = access
            userPrefs.setToken(access)
            user.refreshToken?.takeIf { it.isNotBlank() }?.let { userPrefs.setRefreshToken(it) }
            true
        }
    }

    // ⏱️ files outlive the client-wide 30s request timeout; a stall still fails on the engines' own idle timeouts
    protected fun HttpRequestBuilder.withoutRequestTimeout() {
        timeout { requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS }
    }

    // 🔧 20-Sep-2026: the ONE status table, so a streamed response fails the same way a decoded one does
    @PublishedApi
    internal fun errorForStatus(status: Int): NetworkError = when (status) {
        STATUS_BAD_REQUEST -> NetworkError.BAD_REQUEST
        STATUS_UNAUTHORIZED -> NetworkError.UNAUTHORIZED
        STATUS_FORBIDDEN -> NetworkError.FORBIDDEN
        STATUS_NOT_FOUND -> NetworkError.NOT_FOUND
        STATUS_CONFLICT -> NetworkError.CONFLICT
        STATUS_REQUEST_TIMEOUT -> NetworkError.REQUEST_TIMEOUT
        STATUS_PAYLOAD_TOO_LARGE -> NetworkError.PAYLOAD_TOO_LARGE
        // 🔧 28-Aug-2026: the server answers a schema rejection with 422, not 400
        STATUS_UNPROCESSABLE_ENTITY -> NetworkError.VALIDATION_FAILED
        STATUS_TOO_MANY_REQUESTS -> NetworkError.TOO_MANY_REQUESTS
        in 500..599 -> NetworkError.SERVER_ERROR
        else -> NetworkError.UNKNOWN
    }

    // 🌊 20-Sep-2026 — streams chunks as they land (real progress); fromByte > 0 resumes with a Range request
    protected suspend fun streamBytes(
        url: String,
        fromByte: Long,
        onChunk: suspend (chunk: ByteArray, bytesSoFar: Long, totalBytes: Long) -> Unit,
    ): Result<Long, Error> {
        var attemptedRefresh = false
        while (true) {
            val outcome = try {
                withContext(Dispatchers.IO) {
                    httpClient.prepareGet(url) {
                        withoutRequestTimeout()
                        if (fromByte > 0) header(HttpHeaders.Range, "$RANGE_UNIT=$fromByte-")
                    }.execute { response -> consume(response, fromByte, onChunk) }
                }
            } catch (e: UnresolvedAddressException) {
                log_d("Error", "$e"); return Result.Error(NetworkError.NO_INTERNET)
            } catch (e: ConnectTimeoutException) {
                log_d("Error", "$e"); return Result.Error(NetworkError.CONNECTION_FAILED)
            } catch (e: CancellationException) {
                // ⏸️ a pause cancels the job — it must not be reported as a failure
                throw e
            } catch (e: Throwable) {
                log_d("Error", "$e"); return Result.Error(NetworkError.CONNECTION_FAILED)
            }

            // 🔐 one silent refresh + one replay, exactly as baseApiCall does
            if (outcome is Result.Error && outcome.error == NetworkError.UNAUTHORIZED && !attemptedRefresh) {
                attemptedRefresh = true
                if (!refreshSession()) return Result.Error(NetworkError.SESSION_EXPIRED)
                continue
            }
            return outcome
        }
    }

    private suspend fun consume(
        response: HttpResponse,
        fromByte: Long,
        onChunk: suspend (chunk: ByteArray, bytesSoFar: Long, totalBytes: Long) -> Unit,
    ): Result<Long, Error> {
        val status = response.status.value
        // 📥 416 means the local part file is longer than the asset — the caller restarts from zero
        if (status == STATUS_RANGE_NOT_SATISFIABLE) return Result.Error(NetworkError.CONFLICT)
        if (status !in 200..299) return Result.Error(errorForStatus(status))
        // 📥 a 200 to a RANGED request means Range was ignored — appending would corrupt the part, so restart clean
        if (fromByte > 0 && status != STATUS_PARTIAL_CONTENT) return Result.Error(NetworkError.CONFLICT)

        val total = totalBytesOf(response, fromByte)

        val channel = response.bodyAsChannel()
        var soFar = fromByte
        val buffer = ByteArray(DOWNLOAD_CHUNK_BYTES)
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            // 📥 -1 is end of stream; 0 only means "nothing right now"
            if (read < 0) break
            if (read == 0) continue
            soFar += read
            onChunk(buffer.copyOf(read), soFar, total)
        }
        return Result.Success(soFar)
    }

    // 📏 Content-Range wins ("bytes 400-999/1000"); without it Content-Length is the remainder
    private fun totalBytesOf(response: HttpResponse, startAt: Long): Long {
        response.headers[HttpHeaders.ContentRange]
            ?.substringAfter('/', "")
            ?.trim()
            ?.toLongOrNull()
            ?.let { return it }
        val length = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
        return if (length > 0) startAt + length else 0L
    }
}
