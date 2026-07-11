package com.example.englishvoicetutor.domain.model

/**
 * Описание одной LLM-модели, которую можно скачать и подключить.
 * Каталог фиксированный ([LlmModelCatalog]) плюс особый вариант «своя ссылка»
 * ([LlmModelCatalog.CUSTOM_ID]) — он позволяет качать модель откуда угодно,
 * без HuggingFace и без токена.
 */
data class LlmModelOption(
    val id: String,
    val displayName: String,
    /** Короткое пояснение для UI: размер, источник, нужен ли токен. */
    val description: String,
    /** Прямая ссылка на файл `.litertlm`. Для варианта «своя ссылка» пустая. */
    val url: String,
    /** Требуется ли токен HuggingFace (`Authorization: Bearer …`). */
    val requiresToken: Boolean,
) {
    val isCustom: Boolean get() = id == LlmModelCatalog.CUSTOM_ID
}

object LlmModelCatalog {

    const val CUSTOM_ID = "custom"

    /**
     * Особый вариант: пользователь сам вставляет прямую ссылку на файл модели.
     * Именно он закрывает требование «скачивать модель без HuggingFace» —
     * подойдёт любой публичный URL (свой сервер, зеркало, прямая ссылка облака).
     */
    val customOption = LlmModelOption(
        id = CUSTOM_ID,
        displayName = "Своя ссылка (без HuggingFace)",
        description = "Прямая ссылка на файл .litertlm с любого сервера. " +
                "Токен не нужен, если ссылка публичная.",
        url = "",
        requiresToken = false,
    )

    /** Предустановленные модели. Первая — по умолчанию. */
    val presets = listOf(
        LlmModelOption(
            id = "gemma-4-e2b",
            displayName = "Gemma 4 E2B (рекомендуется)",
            description = "HuggingFace, ~2.4 ГБ. Нужен бесплатный токен и принятая " +
                    "лицензия litert-community/gemma-4-E2B-it-litert-lm.",
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/" +
                    "resolve/main/gemma-4-E2B-it.litertlm",
            requiresToken = true,
        ),
    )

    /** Все варианты для показа в списке выбора: пресеты + «своя ссылка». */
    val all: List<LlmModelOption> = presets + customOption

    val default: LlmModelOption = presets.first()

    fun byId(id: String?): LlmModelOption? = all.firstOrNull { it.id == id }
}
