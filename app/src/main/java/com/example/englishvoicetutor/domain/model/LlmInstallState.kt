package com.example.englishvoicetutor.domain.model

/**
 * Состояние установки LLM-модели. Общее для экрана первичной настройки и
 * экрана настроек — эмитится [com.example.englishvoicetutor.data.model.ModelInstaller].
 */
sealed interface LlmInstallState {
    /** Ничего не происходит: показываем форму выбора модели. */
    data object Idle : LlmInstallState

    /** Идёт скачивание файла модели. */
    data class Downloading(
        val progressPercent: Int,
        val downloadedMb: Long,
        val totalMb: Long,
    ) : LlmInstallState

    /** Файл скачан, инициализируем движок в память (~30 сек). */
    data class Loading(val message: String) : LlmInstallState

    /** Модель скачана и подключена к движку — можно переходить дальше. */
    data object Ready : LlmInstallState

    /** Ошибка на любом этапе. */
    data class Error(val message: String) : LlmInstallState
}
