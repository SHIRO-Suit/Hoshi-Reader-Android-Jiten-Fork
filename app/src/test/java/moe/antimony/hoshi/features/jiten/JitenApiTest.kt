package moe.antimony.hoshi.features.jiten

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JitenApiTest {
    @Test
    fun defaultEndpointUsesJitenApiPrefix() = runBlocking {
        val transport = RecordingTransport(responseBody = """{"tokens":[],"vocabulary":[]}""")

        JitenApi(transport).parse(listOf("読む"), "secret")

        assertEquals("https://api.jiten.moe/api/reader/parse", transport.url)
    }

    @Test
    fun parseUsesJitenAuthorizationAndBuildsCardsFromBackendVocabulary() = runBlocking {
        val transport = RecordingTransport(
            responseBody = """
                {
                  "tokens": [[{
                    "wordId": 42,
                    "readingIndex": 1,
                    "start": 0,
                    "end": 2,
                    "length": 2,
                    "rubies": [{"text":"よ","start":0,"end":1,"length":1}]
                  }]],
                  "vocabulary": [{
                    "wordId": 42,
                    "readingIndex": 1,
                    "spelling": "読む",
                    "reading": "よむ",
                    "partsOfSpeech": "verb",
                    "meaningsChunks": ["to see", "to examine"],
                    "meaningsPartOfSpeech": ["v1", "aux-v"],
                    "knownState": [1]
                  }]
                }
            """.trimIndent(),
        )

        val result = JitenApi(transport).parse(
            paragraphs = listOf("読む"),
            apiKey = " secret ",
            endpoint = "https://example.test/",
        )

        assertEquals("https://example.test/reader/parse", transport.url)
        assertEquals("ApiKey secret", transport.headers["Authorization"])
        assertTrue(transport.body.contains("\"text\":[\"読む\"]"))
        assertEquals(JitenCardState.Young, result.tokens.single().single().card.cardState.single())
        val meanings = result.tokens.single().single().card.meanings
        assertEquals(listOf("to see"), meanings[0].glosses)
        assertEquals(listOf("v1"), meanings[0].partsOfSpeech)
        assertEquals(listOf("to examine"), meanings[1].glosses)
        assertEquals(listOf("aux-v"), meanings[1].partsOfSpeech)
        assertEquals("読[よ]む", result.tokens.single().single().card.wordWithReading)
    }

    @Test
    fun neverForgetUsesTheStateContractFromJitenReader() = runBlocking {
        val transport = RecordingTransport(responseBody = "")

        JitenApi(transport).setVocabularyState(
            wordId = 42,
            readingIndex = 1,
            list = JitenVocabularyList.NeverForget,
            action = JitenVocabularyAction.Add,
            apiKey = "secret",
        )

        assertTrue(transport.url.endsWith("/srs/set-vocabulary-state"))
        assertTrue(transport.body.contains("\"state\":\"neverForget-add\""))
    }

    @Test
    fun forgetUsesTheDestructiveStateContractFromJitenReader() = runBlocking {
        val transport = RecordingTransport(responseBody = "")

        JitenApi(transport).setVocabularyState(
            wordId = 42,
            readingIndex = 1,
            list = JitenVocabularyList.Forget,
            action = JitenVocabularyAction.Add,
            apiKey = "secret",
        )

        assertTrue(transport.body.contains("\"state\":\"forget-add\""))
    }

    @Test(expected = JitenApiException::class)
    fun exposesServerErrorMessage() {
        runBlocking {
            JitenApi(
                RecordingTransport(
                    statusCode = 401,
                    responseBody = """{"error_message":"Invalid API key"}""",
                ),
            ).parse(listOf("読む"), "bad-key")
        }
    }

    private class RecordingTransport(
        private val statusCode: Int = 200,
        private val responseBody: String,
    ) : JitenHttpTransport {
        var url: String = ""
        var headers: Map<String, String> = emptyMap()
        var body: String = ""

        override suspend fun post(
            url: String,
            headers: Map<String, String>,
            body: ByteArray,
        ): JitenHttpResponse {
            this.url = url
            this.headers = headers
            this.body = body.decodeToString()
            return JitenHttpResponse(statusCode, responseBody.encodeToByteArray())
        }
    }
}
