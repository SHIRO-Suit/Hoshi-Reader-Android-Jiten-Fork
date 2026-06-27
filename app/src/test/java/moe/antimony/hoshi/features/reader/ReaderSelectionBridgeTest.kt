package moe.antimony.hoshi.features.reader

import moe.antimony.hoshi.features.jiten.JitenRuby
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
                "jitenConjugations": ["without doing so"],
                "jitenRubies": [
                    {
                        "text": "ta",
                        "start": 0,
                        "end": 1,
                        "length": 1
                    }
                ],
                "jitenRects": [
                    {
                        "x": 10.0,
                        "y": 20.0,
                        "width": 30.0,
                        "height": 14.0
                    }
                ],
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
                jitenConjugations = listOf("without doing so"),
                jitenRubies = listOf(
                    JitenRuby(
                        text = "ta",
                        start = 0,
                        end = 1,
                        length = 1,
                    ),
                ),
                jitenRects = listOf(
                    ReaderSelectionRect(
                        x = 10.0,
                        y = 20.0,
                        width = 30.0,
                        height = 14.0,
                    ),
                ),
            ),
            ReaderSelectionBridgePayload.fromJson(payload),
        )
    }
}
