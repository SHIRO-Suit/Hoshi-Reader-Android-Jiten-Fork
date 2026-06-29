package moe.antimony.hoshi.features.jiten

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.antimony.hoshi.di.IoDispatcher
import moe.antimony.hoshi.features.dictionary.DictionarySettings

@Singleton
class JitenApi internal constructor(
    private val transport: JitenHttpTransport,
) {
    @Inject
    constructor(
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(UrlConnectionJitenHttpTransport(ioDispatcher))

    suspend fun parse(
        paragraphs: List<String>,
        apiKey: String,
        endpoint: String = DictionarySettings.DEFAULT_JITEN_API_ENDPOINT,
    ): JitenParseResult {
        require(paragraphs.isNotEmpty()) { "At least one paragraph is required." }
        val response = post(
            endpoint = endpoint,
            path = "reader/parse",
            apiKey = apiKey,
            body = json.encodeToString(JitenParseRequest(paragraphs)),
        )
        return json.decodeFromString<JitenWireParseResult>(response).toParseResult()
    }

    suspend fun setVocabularyState(
        wordId: Long,
        readingIndex: Int,
        list: JitenVocabularyList,
        action: JitenVocabularyAction,
        apiKey: String,
        endpoint: String = DictionarySettings.DEFAULT_JITEN_API_ENDPOINT,
    ) {
        post(
            endpoint = endpoint,
            path = "srs/set-vocabulary-state",
            apiKey = apiKey,
            body = json.encodeToString(
                JitenSetVocabularyStateRequest(
                    wordId = wordId,
                    readingIndex = readingIndex,
                    state = "${list.wireName}-${action.wireName}",
                ),
            ),
        )
    }

    suspend fun review(
        wordId: Long,
        readingIndex: Int,
        rating: JitenReviewRating,
        apiKey: String,
        endpoint: String = DictionarySettings.DEFAULT_JITEN_API_ENDPOINT,
    ): JitenCardStateResult {
        post(
            endpoint = endpoint,
            path = "srs/review",
            apiKey = apiKey,
            body = json.encodeToString(
                JitenReviewRequest(
                    wordId = wordId,
                    readingIndex = readingIndex,
                    rating = rating.wireValue,
                ),
            ),
        )
        return lookupCardState(wordId, readingIndex, apiKey, endpoint)
    }

    suspend fun lookupCardState(
        wordId: Long,
        readingIndex: Int,
        apiKey: String,
        endpoint: String = DictionarySettings.DEFAULT_JITEN_API_ENDPOINT,
    ): JitenCardStateResult {
        val response = post(
            endpoint = endpoint,
            path = "reader/lookup-vocabulary",
            apiKey = apiKey,
            body = json.encodeToString(JitenLookupVocabularyRequest(listOf(listOf(wordId, readingIndex.toLong())))),
        )
        val decoded = json.decodeFromString<JitenLookupVocabularyResponse>(response)
        return JitenCardStateResult(
            states = decoded.result.firstOrNull().toCardStates(),
            deckIds = decoded.decks.firstOrNull().orEmpty(),
        )
    }

    private suspend fun post(endpoint: String, path: String, apiKey: String, body: String): String {
        val normalizedKey = apiKey.trim()
        require(normalizedKey.isNotEmpty()) { "Jiten API key is not configured." }
        val normalizedEndpoint = endpoint.trim().trimEnd('/').ifBlank {
            DictionarySettings.DEFAULT_JITEN_API_ENDPOINT
        }
        val response = transport.post(
            url = "$normalizedEndpoint/$path",
            headers = mapOf(
                "Content-Type" to "application/json",
                "Accept" to "application/json",
                "Authorization" to "ApiKey $normalizedKey",
            ),
            body = body.encodeToByteArray(),
        )
        if (response.statusCode !in 200..299) {
            val error = runCatching {
                json.decodeFromString<JitenErrorResponse>(response.body.decodeToString()).errorMessage
            }.getOrNull()
            throw JitenApiException(response.statusCode, error ?: "Jiten request failed.")
        }
        return response.body.decodeToString()
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
    }
}

data class JitenCardStateResult(
    val states: List<JitenCardState>,
    val deckIds: List<Long>,
)

private fun JitenWireParseResult.toParseResult(): JitenParseResult {
    val cards = vocabulary.associate { item ->
        (item.wordId to item.readingIndex) to item.toCard()
    }
    return JitenParseResult(
        tokens = tokens.map { paragraph ->
            paragraph.mapNotNull { token ->
                val rawCard = token.card ?: cards[token.wordId to token.readingIndex] ?: return@mapNotNull null
                val card = rawCard.copy(
                    wordWithReading = rawCard.wordWithReading ?: token.annotate(rawCard.spelling),
                )
                JitenToken(
                    card = card,
                    wordId = token.wordId,
                    readingIndex = token.readingIndex,
                    start = token.start,
                    end = token.end,
                    length = token.length,
                    sentence = token.sentence,
                    pitchClass = token.pitchClass,
                    rubies = token.rubies,
                    conjugations = token.conjugations,
                )
            }
        },
        vocabulary = vocabulary,
    )
}

private fun JitenWireToken.annotate(spelling: String): String? {
    if (rubies.isEmpty()) return null
    val annotated = StringBuilder(spelling)
    rubies.sortedByDescending(JitenRuby::start).forEach { ruby ->
        val start = ruby.start - this.start
        val end = ruby.end - this.start
        if (start < 0 || end <= start || end > spelling.length) return@forEach
        annotated.insert(end, "[${ruby.text}]")
    }
    return annotated.toString().takeIf { it != spelling }
}

private fun JitenVocabulary.toCard(): JitenCard = JitenCard(
    wordId = wordId,
    readingIndex = readingIndex,
    spelling = spelling,
    reading = reading,
    frequencyRank = frequencyRank,
    partsOfSpeech = partsOfSpeech,
    meanings = meaningsChunks.mapIndexed { index, glosses ->
        JitenMeaning(glosses, meaningsPartOfSpeech.getOrElse(index) { emptyList() })
    },
    cardState = knownState.mapNotNull(::jitenCardState).ifEmpty { listOf(JitenCardState.Mature) },
    pitchAccents = pitchAccents.orEmpty(),
    deckIds = studyDeckIds,
)

private fun jitenCardState(state: Int): JitenCardState? = when (state) {
    0 -> JitenCardState.New
    1 -> JitenCardState.Young
    2 -> JitenCardState.Mature
    3 -> JitenCardState.Blacklisted
    4 -> JitenCardState.Due
    5 -> JitenCardState.Mastered
    6 -> JitenCardState.Redundant
    else -> null
}

private fun List<Int>?.toCardStates(): List<JitenCardState> =
    orEmpty().mapNotNull(::jitenCardState).ifEmpty { listOf(JitenCardState.New) }

class JitenApiException(val statusCode: Int, message: String) : IOException(message)

internal data class JitenHttpResponse(val statusCode: Int, val body: ByteArray)

internal fun interface JitenHttpTransport {
    suspend fun post(url: String, headers: Map<String, String>, body: ByteArray): JitenHttpResponse
}

private class UrlConnectionJitenHttpTransport(
    private val ioDispatcher: CoroutineDispatcher,
) : JitenHttpTransport {
    override suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: ByteArray,
    ): JitenHttpResponse = withContext(ioDispatcher) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = RequestTimeoutMillis
            readTimeout = RequestTimeoutMillis
            doOutput = true
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            connection.outputStream.use { it.write(body) }
            val statusCode = connection.responseCode
            val responseBody = if (statusCode >= 400) {
                connection.errorStream?.use { it.readBytes() } ?: ByteArray(0)
            } else {
                connection.inputStream?.use { it.readBytes() } ?: ByteArray(0)
            }
            JitenHttpResponse(statusCode, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val RequestTimeoutMillis = 30_000
    }
}
