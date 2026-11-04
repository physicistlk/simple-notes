package personal.notes.touch.ui.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import personal.notes.touch.data.db.entity.FlashCardEntity
import personal.notes.touch.data.repo.FlashCardRepository

class FlashcardViewModel(private val repository: FlashCardRepository) : ViewModel() {

    val cards: StateFlow<List<FlashCardEntity>> = repository.allCards
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isStudyMode = MutableStateFlow(false)
    val isStudyMode: StateFlow<Boolean> = _isStudyMode.asStateFlow()

    private val _studyIndex = MutableStateFlow(0)
    val studyIndex: StateFlow<Int> = _studyIndex.asStateFlow()

    private val _isFlipped = MutableStateFlow(false)
    val isFlipped: StateFlow<Boolean> = _isFlipped.asStateFlow()

    fun startStudy() {
        if (cards.value.isNotEmpty()) {
            _studyIndex.value = 0
            _isFlipped.value = false
            _isStudyMode.value = true
        }
    }

    fun exitStudy() {
        _isStudyMode.value = false
        _isFlipped.value = false
    }

    fun flipCard() {
        _isFlipped.value = !_isFlipped.value
    }

    fun rateCurrentCard(rating: Int) {
        val currentCards = cards.value
        val idx = _studyIndex.value
        if (idx in currentCards.indices) {
            val card = currentCards[idx]
            viewModelScope.launch {
                repository.recordReview(card, rating)
                if (idx + 1 < currentCards.size) {
                    _studyIndex.value = idx + 1
                    _isFlipped.value = false
                } else {
                    _isStudyMode.value = false
                    _isFlipped.value = false
                }
            }
        }
    }

    fun createCard(question: String, answer: String, tips: String? = null) {
        viewModelScope.launch {
            repository.createCard(question.trim(), answer.trim(), tips?.trim()?.ifBlank { null })
        }
    }

    fun deleteCard(cardId: String) {
        viewModelScope.launch {
            repository.deleteCard(cardId)
        }
    }

    class Factory(private val repository: FlashCardRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FlashcardViewModel(repository) as T
        }
    }
}
