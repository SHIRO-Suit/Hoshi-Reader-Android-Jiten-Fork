package moe.antimony.hoshi.features.reader

internal enum class ReaderSwipeDirection {
    Left,
    Right,
}

internal fun readerNavigationDirectionForSwipe(
    isVerticalWriting: Boolean,
    swipeDirection: ReaderSwipeDirection,
): ReaderNavigationDirection =
    when (swipeDirection) {
        ReaderSwipeDirection.Left -> {
            if (isVerticalWriting) {
                ReaderNavigationDirection.Backward
            } else {
                ReaderNavigationDirection.Forward
            }
        }
        ReaderSwipeDirection.Right -> {
            if (isVerticalWriting) {
                ReaderNavigationDirection.Forward
            } else {
                ReaderNavigationDirection.Backward
            }
        }
    }
