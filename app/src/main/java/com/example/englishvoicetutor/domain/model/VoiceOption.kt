package com.example.englishvoicetutor.domain.model

/** Вариант голоса для озвучки — один из дикторов модели Piper. */
data class VoiceOption(
    /** `sid` диктора внутри модели. */
    val id: Int,
    val label: String
)

/**
 * Каталог голосов.
 *
 * В модели `vits-piper-en_US-libritts_r-medium` 904 диктора, и все они анонимные:
 * это номера говорящих из корпуса LibriTTS-R, без имён, пола и описания акцента.
 * Придумывать им ярлыки вроде «мужской» или «британский» было бы враньём, поэтому
 * даём короткий список пронумерованных вариантов и кнопку прослушивания —
 * выбирать предлагается на слух. Идентификаторы разнесены по всему диапазону,
 * чтобы голоса заметно отличались друг от друга.
 */
object VoiceCatalog {

    const val DEFAULT_SPEAKER_ID = 200

    const val DEFAULT_SPEED = 0.9f
    const val MIN_SPEED = 0.6f
    const val MAX_SPEED = 1.4f

    /** Фраза для прослушивания: короткая, с типичной для репетитора интонацией вопроса. */
    const val PREVIEW_TEXT = "Hello! I'm your English tutor. What would you like to talk about?"

    val options: List<VoiceOption> = listOf(
        16, 92, 145, 200, 267, 331, 408, 486, 555, 640, 733, 861
    ).mapIndexed { index, id -> VoiceOption(id = id, label = "Голос ${index + 1}") }

    /** Подпись выбранного диктора; для голоса не из списка показываем его номер. */
    fun labelFor(speakerId: Int): String =
        options.firstOrNull { it.id == speakerId }?.label ?: "Голос №$speakerId"
}
