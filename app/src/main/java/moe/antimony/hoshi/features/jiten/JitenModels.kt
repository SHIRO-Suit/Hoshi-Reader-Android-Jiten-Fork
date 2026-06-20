package moe.antimony.hoshi.features.jiten

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class JitenParseRequest(val text: List<String>)

@Serializable
data class JitenParseResult(
    val tokens: List<List<JitenToken>> = emptyList(),
    val vocabulary: List<JitenVocabulary> = emptyList(),
)

@Serializable
internal data class JitenWireParseResult(
    val tokens: List<List<JitenWireToken>> = emptyList(),
    val vocabulary: List<JitenVocabulary> = emptyList(),
)

@Serializable
internal data class JitenWireToken(
    val card: JitenCard? = null,
    val wordId: Long,
    val readingIndex: Int,
    val start: Int,
    val end: Int,
    val length: Int,
    val sentence: String? = null,
    val pitchClass: String = "",
    val rubies: List<JitenRuby> = emptyList(),
    val conjugations: List<String> = emptyList(),
)

@Serializable
data class JitenToken(
    val card: JitenCard,
    val wordId: Long,
    val readingIndex: Int,
    val start: Int,
    val end: Int,
    val length: Int,
    val sentence: String? = null,
    val pitchClass: String = "",
    val rubies: List<JitenRuby> = emptyList(),
    val conjugations: List<String> = emptyList(),
)

@Serializable
data class JitenRuby(
    val text: String,
    val start: Int,
    val end: Int,
    val length: Int,
)

@Serializable
data class JitenVocabulary(
    val wordId: Long,
    val readingIndex: Int,
    val spelling: String,
    val reading: String,
    val frequencyRank: Int = 0,
    @Serializable(with = JitenStringListSerializer::class)
    val partsOfSpeech: List<String> = emptyList(),
    @Serializable(with = JitenNestedStringListSerializer::class)
    val meaningsChunks: List<List<String>> = emptyList(),
    @Serializable(with = JitenNestedStringListSerializer::class)
    val meaningsPartOfSpeech: List<List<String>> = emptyList(),
    val knownState: List<Int> = emptyList(),
    val pitchAccents: List<Int>? = null,
    val studyDeckIds: List<Long> = emptyList(),
)

@Serializable
data class JitenCard(
    val wordId: Long,
    val readingIndex: Int,
    val spelling: String,
    val reading: String,
    val frequencyRank: Int = 0,
    val partsOfSpeech: List<String> = emptyList(),
    val meanings: List<JitenMeaning> = emptyList(),
    val cardState: List<JitenCardState> = emptyList(),
    val pitchAccents: List<Int> = emptyList(),
    val wordWithReading: String? = null,
    val deckIds: List<Long> = emptyList(),
)

@Serializable
data class JitenMeaning(
    val glosses: List<String> = emptyList(),
    val partsOfSpeech: List<String> = emptyList(),
)

@Serializable
enum class JitenCardState(val wireName: String) {
    @SerialName("new") New("new"),
    @SerialName("young") Young("young"),
    @SerialName("mature") Mature("mature"),
    @SerialName("mastered") Mastered("mastered"),
    @SerialName("blacklisted") Blacklisted("blacklisted"),
    @SerialName("due") Due("due"),
    @SerialName("redundant") Redundant("redundant"),
}

enum class JitenVocabularyList(val wireName: String) {
    NeverForget("neverForget"),
    Blacklist("blacklist"),
    Mining("mining"),
    Forget("forget"),
    ;

    companion object {
        fun fromWireName(value: String): JitenVocabularyList? = entries.firstOrNull { it.wireName == value }
    }
}

enum class JitenVocabularyAction(val wireName: String) {
    Add("add"),
    Remove("remove"),
    ;

    companion object {
        fun fromWireName(value: String): JitenVocabularyAction? = entries.firstOrNull { it.wireName == value }
    }
}

@Serializable
internal data class JitenSetVocabularyStateRequest(
    val wordId: Long,
    val readingIndex: Int,
    val state: String,
)

@Serializable
internal data class JitenErrorResponse(
    @SerialName("error_message") val errorMessage: String? = null,
)

internal object JitenStringListSerializer : KSerializer<List<String>> {
    private val delegate = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<String> =
        when (val element = (decoder as JsonDecoder).decodeJsonElement()) {
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> listOfNotNull(element.contentOrNull)
            else -> emptyList()
        }

    override fun serialize(encoder: Encoder, value: List<String>) {
        (encoder as JsonEncoder).encodeJsonElement(JsonArray(value.map(::JsonPrimitive)))
    }
}

internal object JitenNestedStringListSerializer : KSerializer<List<List<String>>> {
    private val delegate = ListSerializer(ListSerializer(String.serializer()))
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<List<String>> {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        if (element !is JsonArray) return emptyList()
        if (element.all { it is JsonPrimitive }) {
            return element.mapNotNull { item ->
                (item as JsonPrimitive).contentOrNull?.let(::listOf)
            }
        }
        return element.map { item ->
            when (item) {
                is JsonArray -> item.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                is JsonPrimitive -> listOfNotNull(item.contentOrNull)
                else -> emptyList()
            }
        }
    }

    override fun serialize(encoder: Encoder, value: List<List<String>>) {
        (encoder as JsonEncoder).encodeJsonElement(
            JsonArray(value.map { items -> JsonArray(items.map(::JsonPrimitive)) }),
        )
    }
}
