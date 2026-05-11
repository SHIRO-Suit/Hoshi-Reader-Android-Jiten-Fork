package moe.antimony.hoshi.features.reader

import moe.antimony.hoshi.features.sasayaki.SasayakiSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAppearanceSasayakiTest {
    @Test
    fun appearanceShowsSasayakiToggleWhenSasayakiIsEnabled() {
        assertEquals(
            listOf("Show Sasayaki Toggle"),
            readerAppearanceSasayakiRows(SasayakiSettings(enabled = true)),
        )
    }

    @Test
    fun appearanceHidesSasayakiToggleWhenSasayakiIsDisabled() {
        assertTrue(readerAppearanceSasayakiRows(SasayakiSettings(enabled = false)).isEmpty())
    }
}
