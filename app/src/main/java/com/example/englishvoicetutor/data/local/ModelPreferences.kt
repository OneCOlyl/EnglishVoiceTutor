package com.example.englishvoicetutor.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранит, какая LLM-модель сейчас установлена (файл всегда `model.litertlm`,
 * но нам нужно помнить, что именно в нём лежит) и последнюю введённую «свою
 * ссылку». Токен HuggingFace здесь осознанно не сохраняется.
 */
@Singleton
class ModelPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** id установленной модели из [com.example.englishvoicetutor.domain.model.LlmModelCatalog]. */
    val installedModelId: String?
        get() = prefs.getString(KEY_MODEL_ID, null)

    /** Человекочитаемое имя установленной модели — для подсказки в UI. */
    val installedModelLabel: String?
        get() = prefs.getString(KEY_MODEL_LABEL, null)

    /** Последняя введённая прямая ссылка (вариант «своя ссылка»). */
    val customUrl: String?
        get() = prefs.getString(KEY_CUSTOM_URL, null)

    /** Запомнить успешно установленную модель. */
    fun setInstalledModel(id: String, label: String) {
        prefs.edit()
            .putString(KEY_MODEL_ID, id)
            .putString(KEY_MODEL_LABEL, label)
            .apply()
    }

    /** Запомнить последнюю введённую «свою ссылку», чтобы предзаполнить поле. */
    fun setCustomUrl(url: String) {
        prefs.edit().putString(KEY_CUSTOM_URL, url).apply()
    }

    private companion object {
        const val PREFS_NAME = "model_prefs"
        const val KEY_MODEL_ID = "installed_model_id"
        const val KEY_MODEL_LABEL = "installed_model_label"
        const val KEY_CUSTOM_URL = "custom_url"
    }
}
