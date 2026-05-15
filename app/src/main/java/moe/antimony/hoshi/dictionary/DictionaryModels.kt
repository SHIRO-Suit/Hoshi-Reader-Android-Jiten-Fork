package moe.antimony.hoshi.dictionary

import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

enum class DictionaryType(val directoryName: String) {
    Term("Term"),
    Frequency("Frequency"),
    Pitch("Pitch"),
}

data class DictionaryInfo(
    val id: String = UUID.randomUUID().toString(),
    val index: DictionaryIndex,
    val path: File,
    val isEnabled: Boolean = true,
    val order: Int = 0,
)

data class DictionaryUpdateCandidate(
    val dictionary: DictionaryInfo,
    val type: DictionaryType,
)

enum class DictionaryUpdateStage {
    Checking,
    Downloading,
    Importing,
}

data class DictionaryUpdateProgress(
    val stage: DictionaryUpdateStage,
    val title: String,
)

data class DictionaryRename(
    val oldTitle: String,
    val newTitle: String,
)

data class DictionaryUpdateSummary(
    val checkedCount: Int,
    val updatedCount: Int,
    val renamedDictionaries: List<DictionaryRename> = emptyList(),
)

data class ImportedDictionary(
    val fileName: String,
    val index: DictionaryIndex,
)

@Serializable
data class DictionaryConfig(
    val termDictionaries: List<DictionaryEntry>,
    val frequencyDictionaries: List<DictionaryEntry>,
    val pitchDictionaries: List<DictionaryEntry>,
) {
    @Serializable
    data class DictionaryEntry(
        val fileName: String,
        val isEnabled: Boolean,
        val order: Int,
    )
}

@Serializable
data class DictionaryIndex(
    val title: String,
    val format: Int,
    val revision: String,
    val isUpdatable: Boolean = false,
    val indexUrl: String = "",
    val downloadUrl: String = "",
)
