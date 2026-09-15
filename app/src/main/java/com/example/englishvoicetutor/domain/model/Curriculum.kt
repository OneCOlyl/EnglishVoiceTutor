package com.example.englishvoicetutor.domain.model

/**
 * Модели учебной программы (roadmap): уровень → тема → слова и правила.
 *
 * Контент статичный и лежит в `assets/curriculum/` (файл на уровень CEFR) — это «скелет» курса,
 * который должен работать мгновенно и без LLM. LLM подключается сверху, чтобы
 * оживить статику: дать ещё примеров, объяснить ошибку, проверить ответ.
 * Прогресс пользователя хранится отдельно в Room и приклеивается к темам
 * в репозитории — сам контент неизменяемый.
 */

/** Тема курса — минимальная единица roadmap: цель, сценарий диалога, слова, правила. */
data class LearningTopic(
    val id: String,
    val level: CefrLevel,
    /** Порядок внутри уровня — определяет и последовательность прохождения. */
    val order: Int,
    val titleRu: String,
    val titleEn: String,
    /** Чему учит тема — одна фраза по-русски для карточки в roadmap. */
    val goalRu: String,
    /** Сценарий для голосового диалога: подставляется в `TutorPrompt.system`. */
    val scenario: String,
    val vocabulary: List<VocabularyItem>,
    val rules: List<GrammarRule>
)

/** Слово или устойчивое выражение из темы. */
data class VocabularyItem(
    /** Стабильный id вида `a1-greetings:hello` — он же ключ прогресса в Room. */
    val id: String,
    val topicId: String,
    val word: String,
    val translationRu: String,
    /** Транскрипция в IPA, например `/həˈloʊ/`. Может отсутствовать. */
    val transcription: String?,
    val exampleEn: String,
    val exampleRu: String
)

/** Грамматическое правило темы с объяснением по-русски и примерами. */
data class GrammarRule(
    val id: String,
    val topicId: String,
    val titleRu: String,
    /** Суть в одну строку — то, что видно в списке правил. */
    val summaryRu: String,
    /** Подробное объяснение по-русски — основной текст экрана правила. */
    val explanationRu: String,
    val examples: List<GrammarExample>,
    /** Типичная ошибка русскоязычных по этому правилу. */
    val pitfallRu: String?
)

/**
 * Пример к правилу. [wrong] — неверный вариант той же мысли: показываем его
 * зачёркнутым рядом с верным, так правило запоминается заметно лучше.
 */
data class GrammarExample(
    val en: String,
    val ru: String,
    val wrong: String? = null
)

/** Статус слова в интервальном повторении. */
enum class VocabStatus {
    /** Ещё ни разу не повторялось. */
    NEW,

    /** В процессе заучивания — повторения идут часто. */
    LEARNING,

    /** Выучено: пережило достаточно успешных повторений, интервал большой. */
    KNOWN
}

/**
 * Прогресс по одному слову. Планирование — по схеме Лейтнера: [box] это номер
 * коробки, интервал до следующего показа берётся из `SrsSchedule.INTERVALS_DAYS`.
 * Правильный ответ поднимает на коробку вверх, ошибка сбрасывает в первую.
 */
data class VocabProgress(
    val itemId: String,
    val box: Int = 0,
    val status: VocabStatus = VocabStatus.NEW,
    /** Когда слово снова попадёт в повторение. 0 — доступно сразу. */
    val dueAtMillis: Long = 0,
    val lastReviewedAtMillis: Long? = null,
    val timesCorrect: Int = 0,
    val timesWrong: Int = 0
)

/** Слово вместе с его прогрессом — то, что показывает UI словаря и повторения. */
data class VocabularyCard(
    val item: VocabularyItem,
    val progress: VocabProgress
)

/** Прогресс по теме: сколько диалогов проведено и выучено ли слово/правило. */
data class TopicProgress(
    val topicId: String,
    val completedAtMillis: Long? = null,
    val conversationsCount: Int = 0
) {
    val isCompleted: Boolean get() = completedAtMillis != null
}

/** Тема + агрегированный прогресс — модель карточки на экране roadmap. */
data class TopicWithProgress(
    val topic: LearningTopic,
    val progress: TopicProgress,
    val wordsTotal: Int,
    val wordsKnown: Int
) {
    /** Доля выученных слов 0f..1f — прогресс-бар в карточке темы. */
    val wordsRatio: Float
        get() = if (wordsTotal == 0) 0f else wordsKnown.toFloat() / wordsTotal

    /**
     * Тема считается пройденной, когда был хотя бы один диалог по ней
     * и выучено не меньше 80% слов — только диалога мало, только слов тоже.
     */
    val isDone: Boolean
        get() = progress.isCompleted && wordsRatio >= 0.8f
}

/** Уровень целиком со своими темами — секция на экране roadmap. */
data class LevelSection(
    val level: CefrLevel,
    val topics: List<TopicWithProgress>
) {
    val doneCount: Int get() = topics.count { it.isDone }
}
