package moe.antimony.hoshi.features.dictionary

import moe.antimony.hoshi.features.reader.ReaderSelectionRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LookupPopupTest {
    @Test
    fun verticalLayoutChoosesLargerSideLikeIosPopupLayout() {
        val layout = LookupPopupLayout(
            selectionRect = ReaderSelectionRect(x = 100.0, y = 200.0, width = 20.0, height = 30.0),
            screenWidth = 400.0,
            screenHeight = 800.0,
            maxWidth = 320.0,
            maxHeight = 250.0,
            isVertical = true,
        )

        val result = layout.calculate()

        assertEquals(270.0, result.width, 0.0)
        assertEquals(250.0, result.height, 0.0)
        assertEquals(259.0, result.centerX, 0.0)
        assertEquals(325.0, result.centerY, 0.0)
    }

    @Test
    fun horizontalLayoutAppearsBelowSelectionWhenThereIsRoom() {
        val layout = LookupPopupLayout(
            selectionRect = ReaderSelectionRect(x = 100.0, y = 100.0, width = 20.0, height = 30.0),
            screenWidth = 400.0,
            screenHeight = 800.0,
            maxWidth = 320.0,
            maxHeight = 250.0,
            isVertical = false,
        )

        val result = layout.calculate()

        assertEquals(320.0, result.width, 0.0)
        assertEquals(250.0, result.height, 0.0)
        assertEquals(234.0, result.centerX, 0.0)
        assertEquals(259.0, result.centerY, 0.0)
    }

    @Test
    fun fullWidthLayoutMatchesIosPopupLayout() {
        val layout = LookupPopupLayout(
            selectionRect = ReaderSelectionRect(x = 100.0, y = 100.0, width = 20.0, height = 30.0),
            screenWidth = 400.0,
            screenHeight = 800.0,
            maxWidth = 320.0,
            maxHeight = 250.0,
            isVertical = true,
            isFullWidth = true,
        )

        val result = layout.calculate()

        assertEquals(388.0, result.width, 0.0)
        assertEquals(250.0, result.height, 0.0)
        assertEquals(200.0, result.centerX, 0.0)
        assertEquals(669.0, result.centerY, 0.0)
    }

    @Test
    fun popupSwipeDismissRequiresEnabledSetting() {
        assertFalse(isPopupSwipeDismissTriggered(enabled = false, threshold = 40f, dx = 120f, dy = 0f))
        assertFalse(isPopupSwipeDismissTriggered(enabled = true, threshold = 40f, dx = 120f, dy = 60f))
        assertTrue(isPopupSwipeDismissTriggered(enabled = true, threshold = 40f, dx = 120f, dy = 0f))
    }
}
