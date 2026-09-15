package com.example.englishvoicetutor.domain

import com.example.englishvoicetutor.domain.model.VocabProgress
import com.example.englishvoicetutor.domain.model.VocabStatus
import java.util.concurrent.TimeUnit

/**
 * Планировщик интервальных повторений (система Лейтнера).
 *
 * Сознательно берём Лейтнера, а не полноценный SM-2: у нас нет оценки «насколько
 * легко было» — в голосовой карточке ответ бинарный (сказал верно / не сказал),
 * а значит вся вариативность SM-2 всё равно выродилась бы в две ветки.
 * Вся логика — чистые функции без времени внутри, чтобы её можно было тестировать.
 */
object SrsSchedule {

    /**
     * Интервалы до следующего повторения по номеру коробки, в днях.
     * Коробка 0 — слово только что провалено или ещё не изучалось: показываем в этой же сессии.
     */
    val INTERVALS_DAYS = intArrayOf(0, 1, 3, 7, 21, 60)

    /** Начиная с этой коробки слово считается выученным. */
    private const val KNOWN_BOX = 4

    val maxBox: Int get() = INTERVALS_DAYS.lastIndex

    /** Новый прогресс после успешного ответа: коробка вверх, интервал растёт. */
    fun onCorrect(progress: VocabProgress, nowMillis: Long): VocabProgress {
        val nextBox = (progress.box + 1).coerceAtMost(maxBox)
        return progress.copy(
            box = nextBox,
            status = if (nextBox >= KNOWN_BOX) VocabStatus.KNOWN else VocabStatus.LEARNING,
            dueAtMillis = nowMillis + intervalMillis(nextBox),
            lastReviewedAtMillis = nowMillis,
            timesCorrect = progress.timesCorrect + 1
        )
    }

    /**
     * Новый прогресс после ошибки. Сбрасываем в первую коробку и возвращаем в
     * текущую сессию: слово, на котором споткнулись, должно встретиться ещё раз сегодня.
     */
    fun onWrong(progress: VocabProgress, nowMillis: Long): VocabProgress = progress.copy(
        box = 0,
        status = VocabStatus.LEARNING,
        dueAtMillis = nowMillis,
        lastReviewedAtMillis = nowMillis,
        timesWrong = progress.timesWrong + 1
    )

    /**
     * Пользователь вручную пометил слово выученным (кнопка в списке слов) —
     * ставим максимальную коробку, чтобы оно не всплывало в ближайшие месяцы.
     */
    fun markKnown(progress: VocabProgress, nowMillis: Long): VocabProgress = progress.copy(
        box = maxBox,
        status = VocabStatus.KNOWN,
        dueAtMillis = nowMillis + intervalMillis(maxBox),
        lastReviewedAtMillis = nowMillis
    )

    /** Сброс слова в «новое» — снять отметку «выучено». */
    fun reset(progress: VocabProgress): VocabProgress = progress.copy(
        box = 0,
        status = VocabStatus.NEW,
        dueAtMillis = 0,
        lastReviewedAtMillis = null
    )

    private fun intervalMillis(box: Int): Long =
        TimeUnit.DAYS.toMillis(INTERVALS_DAYS[box.coerceIn(0, maxBox)].toLong())
}
