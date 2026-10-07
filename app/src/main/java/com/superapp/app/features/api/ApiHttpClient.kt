package com.superapp.app.features.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private class RequestBuildException(message: String) : Exception(message)

/**
 * Executes an ApiBlock with OkHttp. Never logs URLs, headers, bodies or credentials.
 * HTTPS/certificate validation and Android's cleartext policy are left untouched.
 */
object ApiHttpClient {
    private const val MAX_BODY_BYTES = 2 * 1024 * 1024

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun execute(block: ApiBlock, vars: Map<String, String>): ApiResult = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(block, vars)
            val start = System.nanoTime()
            client.newCall(request).await().use { resp ->
                val contentType = resp.header("Content-Type") ?: ""
                val body = resp.body
                val (bytes, truncated) =
                    if (body != null && request.method != "HEAD") readLimited(body.byteStream(), MAX_BODY_BYTES)
                    else Pair(ByteArray(0), false)
                val elapsed = (System.nanoTime() - start) / 1_000_000
                val text = if (isBinary(contentType) && bytes.isNotEmpty()) {
                    "(binary content: ${bytes.size} bytes, $contentType)"
                } else {
                    String(bytes, body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
                }
                ApiResult.Success(
                    ApiResponse(
                        code = resp.code,
                        message = resp.message,
                        headers = resp.headers.map { it.first to it.second },
                        body = text,
                        contentType = contentType,
                        timeMs = elapsed,
                        sizeBytes = bytes.size.toLong(),
                        truncated = truncated
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RequestBuildException) {
            ApiResult.Failure("Invalid request", e.message ?: "The request could not be built.")
        } catch (e: IllegalArgumentException) {
            ApiResult.Failure("Invalid request", "A URL, header name or header value contains invalid characters.")
        } catch (e: UnknownServiceException) {
            ApiResult.Failure(
                "Cleartext blocked",
                "Android blocks plain http:// connections by default. Use an https:// URL."
            )
        } catch (e: InterruptedIOException) {
            ApiResult.Failure("Timeout", "The server did not respond in time.")
        } catch (e: UnknownHostException) {
            ApiResult.Failure("Unknown host", "The host name could not be resolved. Check the URL and your connection.")
        } catch (e: SSLException) {
            ApiResult.Failure("TLS/SSL error", "The secure connection failed: ${e.message ?: "certificate or handshake problem"}")
        } catch (e: ConnectException) {
            ApiResult.Failure("Connection failed", "Could not connect to the server.")
        } catch (e: IOException) {
            ApiResult.Failure("Network error", e.message ?: "The connection failed or the response was malformed.")
        } catch (e: Exception) {
            ApiResult.Failure("Unexpected error", e.javaClass.simpleName)
        }
    }

    private fun buildRequest(block: ApiBlock, vars: Map<String, String>): Request {
        var raw = block.url.applyVariables(vars).trim()
        if (raw.isEmpty()) throw RequestBuildException("The URL is empty.")
        if (!raw.contains("://")) raw = "https://$raw"
        val base = raw.toHttpUrlOrNull() ?: throw RequestBuildException("Not a valid http/https URL.")

        val urlBuilder = base.newBuilder()
        block.params.filter { it.enabled && it.key.isNotBlank() }.forEach {
            urlBuilder.addQueryParameter(it.key.applyVariables(vars), it.value.applyVariables(vars))
        }
        if (block.authType == AuthType.API_KEY && !block.apiKeyInHeader && block.apiKeyName.isNotBlank()) {
            urlBuilder.addQueryParameter(block.apiKeyName.applyVariables(vars), block.apiKeyValue.applyVariables(vars))
        }

        val builder = Request.Builder().url(urlBuilder.build())

        var userContentType: String? = null
        for (h in block.headers) {
            if (!h.enabled || h.key.isBlank()) continue
            val name = h.key.applyVariables(vars).trim()
            val value = h.value.applyVariables(vars).trim()
            if (name.equals("content-type", ignoreCase = true)) userContentType = value
            builder.addHeader(name, value)
        }

        when (block.authType) {
            AuthType.NONE -> Unit
            AuthType.BEARER -> builder.header("Authorization", "Bearer " + block.authToken.applyVariables(vars).trim())
            AuthType.BASIC -> builder.header(
                "Authorization",
                Credentials.basic(block.authUser.applyVariables(vars), block.authPassword.applyVariables(vars))
            )
            AuthType.API_KEY -> if (block.apiKeyInHeader && block.apiKeyName.isNotBlank()) {
                builder.header(block.apiKeyName.applyVariables(vars).trim(), block.apiKeyValue.applyVariables(vars).trim())
            }
        }

        val method = block.method
        var body: RequestBody? = null
        if (method.allowsBody && block.bodyType != BodyType.NONE) {
            val mediaType = (userContentType ?: block.bodyType.contentType)?.toMediaTypeOrNull()
            body = block.body.applyVariables(vars).toRequestBody(mediaType)
        } else if (method == ApiMethod.POST || method == ApiMethod.PUT || method == ApiMethod.PATCH) {
            body = ByteArray(0).toRequestBody(null)
        }
        builder.method(method.name, body)
        return builder.build()
    }

    private fun isBinary(contentType: String): Boolean {
        val ct = contentType.lowercase()
        return ct.startsWith("image/") || ct.startsWith("audio/") || ct.startsWith("video/") ||
            ct.contains("octet-stream") || ct.contains("pdf") || ct.contains("zip")
    }

    private fun readLimited(stream: InputStream, max: Int): Pair<ByteArray, Boolean> {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = stream.read(buf)
            if (n == -1) return Pair(out.toByteArray(), false)
            val take = minOf(n, max - total)
            out.write(buf, 0, take)
            total += take
            if (total >= max) return Pair(out.toByteArray(), n > take || stream.read() != -1)
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
    }
}
