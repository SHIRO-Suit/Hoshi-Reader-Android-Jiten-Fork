package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSwipeNavigationTest {
    @Test
    fun horizontalWritingUsesLeftSwipeForNextPageLikeIos() {
        assertEquals(
            ReaderNavigationDirection.Forward,
            readerNavigationDirectionForSwipe(
                isVerticalWriting = false,
                swipeDirection = ReaderSwipeDirection.Left,
            ),
        )
        assertEquals(
            ReaderNavigationDirection.Backward,
            readerNavigationDirectionForSwipe(
                isVerticalWriting = false,
                swipeDirection = ReaderSwipeDirection.Right,
            ),
        )
    }

    @Test
    fun verticalWritingUsesRightSwipeForNextPageLikeIos() {
        assertEquals(
            ReaderNavigationDirection.Backward,
            readerNavigationDirectionForSwipe(
                isVerticalWriting = true,
                swipeDirection = ReaderSwipeDirection.Left,
            ),
        )
        assertEquals(
            ReaderNavigationDirection.Forward,
            readerNavigationDirectionForSwipe(
                isVerticalWriting = true,
                swipeDirection = ReaderSwipeDirection.Right,
            ),
        )
    }
}
