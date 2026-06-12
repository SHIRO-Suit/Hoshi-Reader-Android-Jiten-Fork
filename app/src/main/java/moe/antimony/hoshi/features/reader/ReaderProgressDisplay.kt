package moe.antimony.hoshi.features.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import moe.antimony.hoshi.R
import moe.antimony.hoshi.content.ContentLanguageProfile

enum class ReaderProgressDisplayUnit {
    Characters,
    Words,
}

data class ReaderProgressDisplay(
    val unit: ReaderProgressDisplayUnit,
    private val wordSingular: String = "word",
    private val wordPlural: String = "words",
    private val wordCountFormat: String = "%1\$s %2\$s",
    private val wordRangeFormat: String = "%1\$s / %2\$s %3\$s",
    private val wordSpeedFormat: String = "%1\$s %2\$s / h",
) {
    val usesWords: Boolean get() = unit == ReaderProgressDisplayUnit.Words

    fun displayCount(rawCount: Int): Int =
        when (unit) {
            ReaderProgressDisplayUnit.Characters -> rawCount.coerceAtLeast(0)
            ReaderProgressDisplayUnit.Words -> ceil(max(rawCount, 0).toDouble() / CharactersPerDisplayedWord).toInt()
        }

    fun rawTargetFromDisplayCount(displayCount: Int, totalCharacters: Int): Int {
        val safeTotal = totalCharacters.coerceAtLeast(0)
        val rawTarget = when (unit) {
            ReaderProgressDisplayUnit.Characters -> displayCount.coerceAtLeast(0).toLong()
            ReaderProgressDisplayUnit.Words -> displayCount.coerceAtLeast(0).toLong() * CharactersPerDisplayedWord.toLong()
        }
        return rawTarget.coerceIn(0L, safeTotal.toLong()).toInt()
    }

    fun rangeText(currentRawCount: Int, totalRawCount: Int): String =
        when (unit) {
            ReaderProgressDisplayUnit.Characters -> "${displayCount(currentRawCount)} / ${displayCount(totalRawCount)}"
            ReaderProgressDisplayUnit.Words -> {
                val current = displayCount(currentRawCount)
                val total = displayCount(totalRawCount)
                String.format(
                    Locale.US,
                    wordRangeFormat,
                    formatDisplayNumber(current),
                    formatDisplayNumber(total),
                    wordLabel(total),
                )
            }
        }

    fun countText(rawCount: Int): String {
        val count = displayCount(rawCount)
        return when (unit) {
            ReaderProgressDisplayUnit.Characters -> count.toString()
            ReaderProgressDisplayUnit.Words -> String.format(
                Locale.US,
                wordCountFormat,
                formatDisplayNumber(count),
                wordLabel(count),
            )
        }
    }

    fun speedText(rawSpeed: Int): String {
        val speed = displayCount(rawSpeed)
        return when (unit) {
            ReaderProgressDisplayUnit.Characters -> "$speed / h"
            ReaderProgressDisplayUnit.Words -> String.format(
                Locale.US,
                wordSpeedFormat,
                formatDisplayNumber(speed),
                wordLabel(speed),
            )
        }
    }

    fun jumpTargetText(rawCount: Int): String =
        formatDisplayNumber(displayCount(rawCount))

    private fun formatDisplayNumber(value: Int): String =
        if (unit == ReaderProgressDisplayUnit.Words) {
            NumberFormat.getIntegerInstance(Locale.US).format(value.toLong())
        } else {
            value.toString()
        }

    private fun wordLabel(count: Int): String =
        if (count == 1) wordSingular else wordPlural

    companion object {
        private const val CharactersPerDisplayedWord = 5
        private val CharacterDisplay = ReaderProgressDisplay(ReaderProgressDisplayUnit.Characters)

        fun characters(): ReaderProgressDisplay = CharacterDisplay

        fun word(
            wordSingular: String = "word",
            wordPlural: String = "words",
            wordCountFormat: String = "%1\$s %2\$s",
            wordRangeFormat: String = "%1\$s / %2\$s %3\$s",
            wordSpeedFormat: String = "%1\$s %2\$s / h",
        ): ReaderProgressDisplay =
            ReaderProgressDisplay(
                unit = ReaderProgressDisplayUnit.Words,
                wordSingular = wordSingular,
                wordPlural = wordPlural,
                wordCountFormat = wordCountFormat,
                wordRangeFormat = wordRangeFormat,
                wordSpeedFormat = wordSpeedFormat,
            )

        fun forContentLanguageProfile(
            contentLanguageProfile: ContentLanguageProfile,
            wordSingular: String,
            wordPlural: String,
            wordCountFormat: String,
            wordRangeFormat: String,
            wordSpeedFormat: String,
        ): ReaderProgressDisplay =
            if (contentLanguageProfile.id == ContentLanguageProfile.EnglishLanguageId) {
                word(
                    wordSingular = wordSingular,
                    wordPlural = wordPlural,
                    wordCountFormat = wordCountFormat,
                    wordRangeFormat = wordRangeFormat,
                    wordSpeedFormat = wordSpeedFormat,
                )
            } else {
                characters()
            }
    }
}

@Composable
internal fun readerProgressDisplay(contentLanguageProfile: ContentLanguageProfile): ReaderProgressDisplay {
    val wordSingular = stringResource(R.string.reader_progress_word_singular)
    val wordPlural = stringResource(R.string.reader_progress_word_plural)
    val wordCountFormat = stringResource(R.string.reader_progress_word_count_format)
    val wordRangeFormat = stringResource(R.string.reader_progress_word_range_format)
    val wordSpeedFormat = stringResource(R.string.reader_progress_word_speed_format)
    return remember(
        contentLanguageProfile.id,
        wordSingular,
        wordPlural,
        wordCountFormat,
        wordRangeFormat,
        wordSpeedFormat,
    ) {
        ReaderProgressDisplay.forContentLanguageProfile(
            contentLanguageProfile = contentLanguageProfile,
            wordSingular = wordSingular,
            wordPlural = wordPlural,
            wordCountFormat = wordCountFormat,
            wordRangeFormat = wordRangeFormat,
            wordSpeedFormat = wordSpeedFormat,
        )
    }
}
