package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderJitenBridgeTest {
    @Test
    fun decodesChapterTextRequest() {
        val request = ReaderJitenBridgePayload.fromJson(
            """{"token":"chapter-1","texts":["読む","本"]}""",
        )

        assertEquals("chapter-1", request?.token)
        assertEquals(listOf("読む", "本"), request?.texts)
    }

    @Test
    fun rejectsMalformedRequest() {
        assertNull(ReaderJitenBridgePayload.fromJson("not-json"))
    }
}
