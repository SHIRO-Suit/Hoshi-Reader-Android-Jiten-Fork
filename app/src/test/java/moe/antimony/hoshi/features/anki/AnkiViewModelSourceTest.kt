package moe.antimony.hoshi.features.anki

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AnkiViewModelSourceTest {
    @Test
    fun configuredSettingsWithoutCachedFieldsTriggerOneRestoreFetch() {
        val source = File("src/main/java/moe/antimony/hoshi/features/anki/AnkiViewModel.kt").readText()

        assertTrue(source.contains("private var attemptedRestoreFetch = false"))
        assertTrue(source.contains("settings.availableDecks.isEmpty() || settings.availableNoteTypes.isEmpty()"))
        assertTrue(source.contains("attemptedRestoreFetch = true"))
        assertTrue(source.contains("fetchConfiguration()"))
    }

    @Test
    fun emptyFieldMappingsRemoveExistingFieldMapping() {
        val source = File("src/main/java/moe/antimony/hoshi/features/anki/AnkiViewModel.kt").readText()
        val updateFieldMapping = source.substringAfter("fun updateFieldMapping(")
            .substringBefore("fun updateTags(")

        assertTrue(updateFieldMapping.contains("settings.fieldMappings - field"))
    }

    @Test
    fun lapisDefaultsAreAppliedOnlyWhenSelectingNoteType() {
        val source = File("src/main/java/moe/antimony/hoshi/features/anki/AnkiViewModel.kt").readText()
        val selectNoteType = source.substringAfter("fun selectNoteType(")
            .substringBefore("fun updateFieldMapping(")

        assertTrue(selectNoteType.contains("fieldMappings = LapisPreset.applyDefaults(noteType, emptyMap())"))
    }

    @Test
    fun duplicateChecksForPopupUseViewModelScopeInsteadOfRunBlocking() {
        val source = File("src/main/java/moe/antimony/hoshi/features/anki/AnkiViewModel.kt").readText()
        val duplicateCheckAsync = source.substringAfter("fun duplicateCheckAsync(")

        assertTrue(source.contains("fun duplicateCheckAsync(expression: String, onResult: (Boolean) -> Unit)"))
        assertTrue(duplicateCheckAsync.contains("viewModelScope.launch"))
        assertTrue(duplicateCheckAsync.contains("onResult("))
        assertTrue(!duplicateCheckAsync.substringBefore("fun ").contains("runBlocking"))
    }
}
