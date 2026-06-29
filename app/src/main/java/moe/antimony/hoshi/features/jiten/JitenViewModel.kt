package moe.antimony.hoshi.features.jiten

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.antimony.hoshi.di.DefaultDispatcher

@HiltViewModel
internal class JitenViewModel @Inject constructor(
    private val api: JitenApi,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @Volatile
    private var cardsById: Map<Pair<Long, Int>, JitenCard> = emptyMap()

    suspend fun parseJson(
        texts: List<String>,
        apiKey: String,
        endpoint: String,
    ): String = withContext(defaultDispatcher) {
        val result = api.parse(texts, apiKey, endpoint).also { result ->
            mergeCards(result.tokens.flatten().associate { token ->
                (token.wordId to token.readingIndex) to token.card
            })
        }
        json.encodeToString(result)
    }

    fun card(wordId: Long?, readingIndex: Int?): JitenCard? =
        if (wordId == null || readingIndex == null) null else cardsById[wordId to readingIndex]

    suspend fun updateVocabularyState(
        card: JitenCard,
        list: JitenVocabularyList,
        action: JitenVocabularyAction,
        apiKey: String,
        endpoint: String,
    ): JitenCard = withContext(defaultDispatcher) {
        api.setVocabularyState(card.wordId, card.readingIndex, list, action, apiKey, endpoint)
        val targetState = when (list) {
            JitenVocabularyList.NeverForget -> JitenCardState.Mastered
            JitenVocabularyList.Blacklist -> JitenCardState.Blacklisted
            JitenVocabularyList.Mining -> null
            JitenVocabularyList.Forget -> JitenCardState.New
        }
        val updated = if (targetState == null) {
            card
        } else {
            card.copy(
                cardState = if (list == JitenVocabularyList.Forget) {
                    listOf(JitenCardState.New)
                } else if (action == JitenVocabularyAction.Add) {
                    listOf(targetState)
                } else {
                    listOf(JitenCardState.Mature)
                },
            )
        }
        mergeCards(
            mapOf(
                (card.wordId to card.readingIndex) to updated.copy(
                    matchedText = null,
                    conjugations = emptyList(),
                    rubies = emptyList(),
                ),
            ),
        )
        updated
    }

    suspend fun review(
        card: JitenCard,
        rating: JitenReviewRating,
        apiKey: String,
        endpoint: String,
    ): JitenCard = withContext(defaultDispatcher) {
        val state = api.review(card.wordId, card.readingIndex, rating, apiKey, endpoint)
        val updated = card.copy(
            cardState = state.states,
            deckIds = state.deckIds,
        )
        mergeCards(
            mapOf(
                (card.wordId to card.readingIndex) to updated.copy(
                    matchedText = null,
                    conjugations = emptyList(),
                    rubies = emptyList(),
                ),
            ),
        )
        updated
    }

    @Synchronized
    private fun mergeCards(cards: Map<Pair<Long, Int>, JitenCard>) {
        cardsById += cards
    }

    private companion object {
        val json = Json { explicitNulls = false }
    }
}
