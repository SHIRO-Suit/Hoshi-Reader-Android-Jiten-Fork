package moe.antimony.hoshi.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppShellNavigationTest {

    @Test
    fun readerBookmarkRefreshIsCoalescedUntilReaderClose() {
        val state = ReaderBookmarkRefreshState()

        assertFalse(state.consumeDirty())
        state.markDirty()
        state.markDirty()
        assertTrue(state.consumeDirty())
        assertFalse(state.consumeDirty())
    }

}
