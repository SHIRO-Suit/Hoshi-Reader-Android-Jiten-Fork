package moe.antimony.hoshi.features.reader

data class ReaderSelectionData(
    val text: String,
    val sentence: String,
    val rect: ReaderSelectionRect,
    val normalizedOffset: Int?,
    val sentenceOffset: Int? = null,
    val jitenWordId: Long? = null,
    val jitenReadingIndex: Int? = null,
    val jitenTapOffset: Int? = null,
    val jitenText: String? = null,
    val jitenConjugations: List<String> = emptyList(),
    val jitenRects: List<ReaderSelectionRect> = emptyList(),
)

data class ReaderSelectionRect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
)
