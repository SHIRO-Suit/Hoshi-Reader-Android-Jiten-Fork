package moe.antimony.hoshi.features.reader

import java.util.Locale

data class ReaderChromeState(
    val title: String,
    val currentCharacter: Int,
    val totalCharacters: Int,
) {
    fun progressText(): String {
        if (totalCharacters <= 0) return currentCharacter.toString()
        val percent = currentCharacter.toDouble() / totalCharacters.toDouble() * 100.0
        return String.format(Locale.US, "%d / %d %.2f%%", currentCharacter, totalCharacters, percent)
    }
}

data class ReaderChromeColors(
    val buttonContainer: Long,
    val buttonContent: Long,
    val buttonBorder: Long,
    val infoText: Long,
)

fun readerChromeColors(settings: ReaderSettings): ReaderChromeColors = when (settings.theme) {
    ReaderTheme.Dark -> ReaderChromeColors(
        buttonContainer = 0x661A1A1A,
        buttonContent = 0xFFF4F4F4,
        buttonBorder = 0x33FFFFFF,
        infoText = 0x99FFFFFF,
    )
    ReaderTheme.Sepia -> ReaderChromeColors(
        buttonContainer = 0x40FFFFFF,
        buttonContent = 0xFF1F170D,
        buttonBorder = 0x80FFFFFF,
        infoText = 0x7A5C5448,
    )
    ReaderTheme.Light, ReaderTheme.System -> ReaderChromeColors(
        buttonContainer = 0xD9FFFFFF,
        buttonContent = 0xFF111111,
        buttonBorder = 0xCCFFFFFF,
        infoText = 0x8A000000,
    )
}
