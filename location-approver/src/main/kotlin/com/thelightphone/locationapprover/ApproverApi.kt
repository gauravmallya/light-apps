package com.thelightphone.locationapprover

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Base URL for the location-gate backend, configured per-install via local.properties.
// See README.md for setup instructions. Never hardcode a real URL here.
private val BASE_URL: String = BuildConfig.BASE_URL

@Serializable
data class PendingRequest(
    val id: Int,
    val name: String,
    val created_at: Long,
)

@Serializable
data class AllRequest(
    val id: Int,
    val name: String,
    val status: String,
    val created_at: Long,
    val decided_at: Long? = null,
    val last_seen_at: Long? = null,
)

@Serializable
internal data class DecideBody(val request_id: Int, val decision: String)

@Serializable
internal data class RevokeBody(val request_id: Int)

@Serializable
internal data class OkResponse(val ok: Boolean = false)

internal class ApproverApi(private val adminToken: String) {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authHeader() {
        header("Authorization", "Bearer $adminToken")
    }

    suspend fun fetchPending(): Result<List<PendingRequest>> = runCatching {
        val response = client.get("$BASE_URL/api/admin/pending") {
            authHeader()
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("HTTP ${response.status.value}: ${response.bodyAsText().take(300)}")
        }
        response.body()
    }

    suspend fun fetchAll(): Result<List<AllRequest>> = runCatching {
        val response = client.get("$BASE_URL/api/admin/all") {
            authHeader()
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("HTTP ${response.status.value}: ${response.bodyAsText().take(300)}")
        }
        response.body()
    }

    suspend fun decide(requestId: Int, approve: Boolean): Result<Unit> = runCatching {
        val response = client.post("$BASE_URL/api/admin/decide") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody(DecideBody(requestId, if (approve) "approve" else "deny"))
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("HTTP ${response.status.value}: ${response.bodyAsText().take(300)}")
        }
    }

    suspend fun revoke(requestId: Int): Result<Unit> = runCatching {
        val response = client.post("$BASE_URL/api/admin/revoke") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody(RevokeBody(requestId))
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("HTTP ${response.status.value}: ${response.bodyAsText().take(300)}")
        }
    }

    fun close() {
        client.close()
    }
}
