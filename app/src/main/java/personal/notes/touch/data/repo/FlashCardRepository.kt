package personal.notes.touch.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import personal.notes.touch.data.db.dao.FlashCardDao
import personal.notes.touch.data.db.entity.FlashCardEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max

class FlashCardRepository(private val flashCardDao: FlashCardDao) {

    val allCards: Flow<List<FlashCardEntity>> = flashCardDao.getAllCards()

    fun getCardsByNote(noteId: String): Flow<List<FlashCardEntity>> = flashCardDao.getCardsByNote(noteId)

    suspend fun createCard(
        question: String,
        answer: String,
        tips: String? = null,
        noteId: String = "general",
        cardName: String = "Card"
    ): FlashCardEntity = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val nowStr = dateFormat.format(Date())
        val card = FlashCardEntity(
            id = UUID.randomUUID().toString(),
            cName = cardName,
            createTime = nowStr,
            cQuestion = question,
            cAns = answer,
            cTips = tips,
            cNum = 0,
            cRDate = nowStr,
            nid = noteId,
            reviewStatus = 0
        )
        flashCardDao.insertCard(card)
        card
    }

    suspend fun recordReview(card: FlashCardEntity, rating: Int) = withContext(Dispatchers.IO) {
        // SM-2 Spaced Repetition Algorithm (simplified)
        // rating: 1 (Again), 2 (Hard), 3 (Good), 4 (Easy)
        var ease = card.easeFactor
        val currentInterval = if (card.cNum == 0) 1 else max(1, (card.cNum * ease).toInt())

        val (nextIntervalDays, newEase) = when (rating) {
            1 -> {
                // Again: interval reset to 1 day, ease reduced
                1 to (ease * 0.6).coerceAtLeast(1.3)
            }
            2 -> {
                // Hard: interval adjusted, ease reduced by 0.6
                val interval = max(1, (currentInterval * ease * 0.6).toInt())
                interval to (ease * 0.6).coerceAtLeast(1.3)
            }
            3 -> {
                // Good: interval multiplied by ease, ease kept
                val interval = if (card.cNum == 0) 1 else max(1, (currentInterval * ease).toInt())
                interval to ease
            }
            4 -> {
                // Easy: interval boosted, ease boosted by 1.3
                val interval = if (card.cNum == 0) 2 else max(1, (currentInterval * ease * 1.3).toInt())
                interval to (ease * 1.3).coerceAtMost(3.5)
            }
            else -> 1 to ease
        }

        val finalEase = newEase
        val nextInterval = nextIntervalDays

        val nextReviewMillis = System.currentTimeMillis() + nextInterval.toLong() * 24 * 60 * 60 * 1000
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val nextReviewDate = dateFormat.format(Date(nextReviewMillis))

        // Review status mapping: new/review/learned
        val statusLabel = when {
            card.cNum == 0 && rating == 1 -> 0 // new
            rating <= 2 -> 1 // review
            else -> 2 // learned
        }

        val updatedCard = card.copy(
            cNum = card.cNum + 1,
            cRDate = nextReviewDate,
            reviewStatus = statusLabel,
            card_usn = (finalEase * 100).toInt(),
            easeFactor = finalEase
        )
        flashCardDao.insertCard(updatedCard)
    }

    suspend fun deleteCard(id: String) = withContext(Dispatchers.IO) {
        flashCardDao.deleteCard(id)
    }
}
