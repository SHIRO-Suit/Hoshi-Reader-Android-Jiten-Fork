package moe.antimony.hoshi.features.reader

import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import moe.antimony.hoshi.features.jiten.JitenApiException

internal class ReaderJitenBridge(
    private val webView: WebView,
    private val scope: CoroutineScope,
    private val parse: suspend (List<String>) -> String,
    private val onError: (ReaderJitenError) -> Unit,
) {
    private val activeJobsLock = Any()
    private val activeJobs = mutableSetOf<Job>()
    private var activeParseGroup: String? = null

    @JavascriptInterface
    fun postMessage(message: String) {
        val request = ReaderJitenBridgePayload.fromJson(message) ?: return
        if (request.texts.isEmpty()) return
        val parseGroup = request.parseGroup()
        val job = scope.launch {
            val start = android.os.SystemClock.elapsedRealtime()
            emitDebug("API start ${request.texts.size} texts / ${request.texts.sumOf(String::length)} chars")
            val result = try {
                parse(request.texts)
            } catch (error: CancellationException) {
                throw error
            } catch (error: SerializationException) {
                onError(ReaderJitenError.InvalidResponse)
                return@launch
            } catch (error: JitenApiException) {
                onError(ReaderJitenError.RequestFailed(error.message))
                return@launch
            } catch (_: Exception) {
                onError(ReaderJitenError.RequestFailed())
                return@launch
            }
            emitDebug("API done ${request.texts.size} texts / ${android.os.SystemClock.elapsedRealtime() - start}ms / ${result.length}B")
            webView.post {
                webView.evaluateJavascript(
                    "window.hoshiJiten?.apply(${readerJavaScriptStringLiteral(request.token)}, $result);",
                    null,
                )
            }
        }
        synchronized(activeJobsLock) {
            if (activeParseGroup != parseGroup) {
                activeJobs.forEach { it.cancel() }
                activeJobs.clear()
                activeParseGroup = parseGroup
                emitDebug("API group ${parseGroup.takeLast(48)}")
            }
            activeJobs.add(job)
        }
        job.invokeOnCompletion {
            synchronized(activeJobsLock) {
                activeJobs.remove(job)
            }
        }
    }

    @JavascriptInterface
    fun debugMessage(message: String) {
        emitDebug(message)
    }

    private fun emitDebug(message: String) {
        webView.post {
            webView.evaluateJavascript(
                "window.hoshiJiten?.debugLog?.(${readerJavaScriptStringLiteral(message.take(160))});",
                null,
            )
        }
    }
}

internal sealed interface ReaderJitenError {
    data object InvalidResponse : ReaderJitenError
    data class RequestFailed(val details: String? = null) : ReaderJitenError
}

@Serializable
internal data class ReaderJitenParseRequest(
    val token: String,
    val texts: List<String>,
) {
    fun parseGroup(): String =
        token
            .replace(Regex(""":visible$"""), "")
            .replace(Regex(""":background:\d+$"""), "")
}

internal object ReaderJitenBridgePayload {
    private val json = Json { ignoreUnknownKeys = true }

    fun fromJson(message: String): ReaderJitenParseRequest? =
        runCatching { json.decodeFromString<ReaderJitenParseRequest>(message) }.getOrNull()
}
