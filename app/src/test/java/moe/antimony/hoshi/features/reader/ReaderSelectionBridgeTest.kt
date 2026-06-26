package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSelectionBridgeTest {
    @Test
    fun parsesTextSelectionPayloadLikeIosMessageBody() {
        val payload = """
            {
                "text": "食べる",
                "sentence": "私は食べる。",
                "rect": {
                    "x": 12.5,
                    "y": 24.25,
                    "width": 40.0,
                    "height": 18.0
                },
                "normalizedOffset": 42,
                "jitenWordId": 123,
                "jitenReadingIndex": 2,
                "jitenTapOffset": 0,
                "jitenText": "é£Ÿã¹ã‚‹",
                "jitenConjugations": ["without having to"],
                "futureField": "ignored"
            }
        """.trimIndent()

        assertEquals(
            ReaderSelectionData(
                text = "食べる",
                sentence = "私は食べる。",
                rect = ReaderSelectionRect(
                    x = 12.5,
                    y = 24.25,
                    width = 40.0,
                    height = 18.0,
                ),
                normalizedOffset = 42,
                jitenWordId = 123,
                jitenReadingIndex = 2,
                jitenTapOffset = 0,
                jitenText = "é£Ÿã¹ã‚‹",
                jitenConjugations = listOf("without having to"),
            ),
            ReaderSelectionBridgePayload.fromJson(payload),
        )
    }
}
