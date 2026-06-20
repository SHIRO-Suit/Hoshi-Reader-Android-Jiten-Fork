package moe.antimony.hoshi.features.reader

import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
    @JavascriptInterface
    fun postMessage(message: String) {
        val request = ReaderJitenBridgePayload.fromJson(message) ?: return
        if (request.texts.isEmpty()) return
        scope.launch {
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
            webView.post {
                webView.evaluateJavascript(
                    "window.hoshiJiten?.apply(${readerJavaScriptStringLiteral(request.token)}, $result);",
                    null,
                )
            }
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
)

internal object ReaderJitenBridgePayload {
    private val json = Json { ignoreUnknownKeys = true }

    fun fromJson(message: String): ReaderJitenParseRequest? =
        runCatching { json.decodeFromString<ReaderJitenParseRequest>(message) }.getOrNull()
}
