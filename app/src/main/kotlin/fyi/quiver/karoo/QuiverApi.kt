package fyi.quiver.karoo

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Quiver backend client. All network goes through the Karoo System's managed
 * HTTP (`OnHttpResponse.MakeHttpRequest`) rather than a raw socket: the Karoo routes
 * it over whatever connectivity it has (Wi‑Fi or the paired phone) and enforces a
 * 100 KB body cap. We dispatch a request as an event consumer and await the single
 * terminal `HttpResponseState.Complete`.
 */
object QuiverApi {
    const val BASE_URL = "https://quiver.fyi"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false // omit null/absent fields — seed is fill-empty/idempotent
    }

    data class HttpResult(val statusCode: Int, val body: String?, val error: String?) {
        val ok: Boolean get() = error == null && statusCode in 200..299
    }

    /**
     * Perform one HTTP request via the Karoo System and suspend until it completes
     * (or [timeoutMs] elapses). `waitForConnection=true` lets the Karoo queue the
     * request until it has connectivity.
     */
    private suspend fun request(
        karoo: KarooSystemService,
        method: String,
        url: String,
        token: String? = null,
        jsonBody: String? = null,
        waitForConnection: Boolean = true,
        timeoutMs: Long = 45_000,
    ): HttpResult {
        val headers = buildMap {
            put("Content-Type", "application/json")
            token?.let { put("Authorization", "Bearer $it") }
        }
        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<HttpResult> { cont ->
                val done = AtomicBoolean(false)
                var consumerId: String? = null
                fun finish(r: HttpResult) {
                    if (done.compareAndSet(false, true)) {
                        consumerId?.let { karoo.removeConsumer(it) }
                        if (cont.isActive) cont.resumeWith(Result.success(r))
                    }
                }
                consumerId = karoo.addConsumer<OnHttpResponse>(
                    OnHttpResponse.MakeHttpRequest(
                        method = method,
                        url = url,
                        headers = headers,
                        body = jsonBody?.toByteArray(),
                        waitForConnection = waitForConnection,
                    ),
                    onError = { msg -> finish(HttpResult(0, null, msg)) },
                ) { event ->
                    when (val state = event.state) {
                        is HttpResponseState.Complete ->
                            finish(
                                HttpResult(
                                    statusCode = state.statusCode,
                                    body = state.body?.decodeToString(),
                                    error = state.error,
                                ),
                            )
                        // Queued / InProgress: keep waiting for Complete
                        else -> Unit
                    }
                }
                cont.invokeOnCancellation { consumerId?.let { karoo.removeConsumer(it) } }
            }
        }
        return result ?: HttpResult(0, null, "timeout")
    }

    sealed class ClaimResult {
        data class Success(val token: String) : ClaimResult()
        data class Failure(val message: String) : ClaimResult()
    }

    /** Exchange a short pairing code (typed by the rider) for a device token. */
    suspend fun claimPairing(
        karoo: KarooSystemService,
        code: String,
        deviceName: String?,
    ): ClaimResult {
        val body = json.encodeToString(mapOf("code" to code, "deviceName" to (deviceName ?: "")))
        val res = request(karoo, "POST", "$BASE_URL/api/karoo/pair/claim", jsonBody = body, timeoutMs = 30_000)
        if (res.error != null) return ClaimResult.Failure(res.error)
        if (!res.ok) {
            val msg = res.body?.let { runCatching { json.decodeFromString<ApiError>(it).error }.getOrNull() }
            return ClaimResult.Failure(msg ?: "Pairing failed (${res.statusCode}).")
        }
        val token = res.body?.let { runCatching { json.decodeFromString<ClaimOk>(it).token }.getOrNull() }
        return if (token.isNullOrBlank()) {
            ClaimResult.Failure("Pairing response was missing a token.")
        } else {
            ClaimResult.Success(token)
        }
    }

    /** POST a garage snapshot. Returns true on 2xx. */
    suspend fun seed(karoo: KarooSystemService, token: String, payload: SeedPayload): Boolean {
        val res = request(karoo, "POST", "$BASE_URL/api/karoo/seed", token = token, jsonBody = json.encodeToString(payload))
        return res.ok
    }

    /**
     * POST a finished ride. Returns true when the backend accepted it (2xx). The
     * endpoint dedups on (user, source, externalId), so re-sending the same ride is
     * a safe no-op.
     */
    suspend fun ingestRide(karoo: KarooSystemService, token: String, payload: RidePayload): Boolean {
        val res = request(karoo, "POST", "$BASE_URL/api/ride/ingest", token = token, jsonBody = json.encodeToString(payload))
        return res.ok
    }
}

@kotlinx.serialization.Serializable
private data class ApiError(val error: String? = null)

@kotlinx.serialization.Serializable
private data class ClaimOk(val token: String? = null)
