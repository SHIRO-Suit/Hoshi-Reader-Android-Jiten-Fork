package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSelectionScriptsTest {
    @Test
    fun exposesIosNamedSelectionApi() {
        val script = ReaderSelectionScripts.script()

        assertTrue(script.contains("window.hoshiSelection"))
        assertTrue(script.contains("selectText: function(x, y, maxLength)"))
        assertTrue(script.contains("clearSelection: function()"))
        assertTrue(script.contains("highlightSelection: function(charCount)"))
    }

    @Test
    fun exposesClearSelectionInvocationForNativeDismissPaths() {
        assertEquals("window.hoshiSelection.clearSelection()", ReaderSelectionScripts.clearInvocation())
    }
}
