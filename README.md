# English Voice Tutor

Android-приложение — голосовой репетитор английского. Работает **офлайн**: распознавание речи, LLM-репетитор и синтез речи крутятся на самом устройстве. Сеть нужна только один раз — чтобы скачать модели при первом запуске.

Архитектура: Kotlin + Jetpack Compose, MVVM, Hilt, Room.

Голосовой цикл: **микрофон → STT (Vosk) → LLM (Gemma 4 через LiteRT-LM) → TTS → сохранение в историю**.

## Статус по компонентам

| Компонент | Реализация | Детали |
|---|---|---|
| STT (распознавание речи) | `VoskSttEngine` — Vosk (`vosk-model-en-us-0.22-lgraph`) | Модель скачивается лениво при первом нажатии на микрофон, в `filesDir`. Push-to-talk: тап — старт, ещё тап — стоп. |
| LLM (репетитор) | `LiteRtLlmEngine` — Gemma 4 E2B через LiteRT-LM, backend GPU | Модель `~1.5 ГБ` скачивается на экране первого запуска (нужен токен HuggingFace). |
| TTS (синтез речи) | `AndroidTtsEngine` — системный `TextToSpeech` | Работает из коробки. Озвучивает ответ целиком одним вызовом. |
| История диалогов | Room (`Conversation`/`Message`), экран «История», продолжение разговора | В контекст LLM отдаются последние `20` сообщений (`RECENT_MESSAGES_FOR_CONTEXT`). |
| Сценарий/уровень | Форма при старте диалога: текстовое поле сценария + выбор CEFR (A1–C2) | Системный промпт собирается в `TutorPrompt`. |

> `StubTutorLlmEngine` и `AndroidSttEngine` ещё лежат в `data/engine/` как более простые альтернативы (заглушка LLM и системный `SpeechRecognizer`), но **в сборке не используются** — оба заменены на реальные движки в `di/AppModule.kt`.

## Как открыть и запустить

1. Открыть папку проекта в Android Studio (актуальная стабильная версия). При первом открытии Studio предложит сгенерировать Gradle wrapper — согласитесь, либо запустите `gradle wrapper` вручную.
2. **Запускать только на реальном устройстве** — микрофон и GPU-backend LLM на эмуляторе не работают как надо. Нужно устройство с достаточной памятью (ориентир — 8 ГБ ОЗУ и GPU с OpenCL).
3. Требования к устройству: `minSdk 26`. GPU-инференс использует OpenCL (`libOpenCL.so` объявлена как опциональная native-library в манифесте).
4. Первый запуск открывает экран **«Настройка модели»**: нужно ввести токен HuggingFace (`hf_...`) и скачать Gemma. Токен нужен, потому что репозиторий модели gated. Дальше приложение стартует сразу в «Историю».
5. При первом нажатии на микрофон докачается Vosk-модель английского (`~128 МБ`) — это отдельная загрузка, токен не нужен.

### Откуда качаются модели

- **LLM:** `https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm` → файл `model.litertlm` в `filesDir`. Загрузка резюмируемая (докачка через HTTP `Range`), URL и имя файла — в `ModelSetupViewModel`.
- **STT:** `https://alphacephei.com/kaldi/models/vosk-model-en-us-0.22-lgraph.zip`, распаковывается в `filesDir`. URL — в `VoskSttEngine`.

Готовность LLM определяется наличием файла `filesDir/model.litertlm` (см. `MainActivity` → `AppNavHost.isModelReady`).

## Структура кода

```
app/src/main/java/com/example/englishvoicetutor/
├─ MainActivity.kt            — точка входа, запрос разрешения на микрофон, выбор стартового экрана
├─ EnglishVoiceTutorApp.kt    — @HiltAndroidApp
├─ di/AppModule.kt            — привязки движков (Stt/Tts/Llm) и Room. Единственное место смены реализаций.
├─ domain/
│  ├─ TutorPrompt.kt          — сборка системного промпта (главный рычаг качества на маленькой модели)
│  └─ model/                  — доменные модели: Conversation, Message, CefrLevel, VoiceUiState, ModelDownloadState
├─ data/
│  ├─ engine/                 — Stt/Tts/Llm движки (интерфейсы в Engines.kt)
│  ├─ local/                  — Room: AppDatabase, DAO, Entities
│  └─ repository/             — ConversationRepository
└─ presentation/
   ├─ setup/                  — экран первого запуска (скачивание LLM)
   ├─ history/                — список диалогов + создание нового
   ├─ conversation/           — экран диалога, голосовой цикл (ConversationViewModel)
   ├─ navigation/AppNavHost   — setup → history → conversation
   └─ theme/
```

Ключевая развязка: UI и `ConversationViewModel` зависят только от интерфейсов `SttEngine`/`TtsEngine`/`LlmEngine` (`data/engine/Engines.kt`). Смена реализации — это одна строка в `di/AppModule.kt`.

## Дальнейшие шаги / известные упрощения

- **VAD не задействован.** В `VoskSttEngine` объявлены пороги (`SPEECH_THRESHOLD`, `SILENCE_CHUNKS_TO_STOP`) под авто-детекцию конца фразы, но цикл записи их не использует — сейчас чистый push-to-talk. Довести до авто-остановки по тишине (или Silero VAD) — отдельная задача.
- **TTS не потоковый.** Ответ озвучивается целиком после полной генерации, хотя `LlmEngine.generateReply` уже отдаёт `Flow<String>` и спроектирован под потоковую озвучку по мере генерации.
- **Суммаризация длинных диалогов не вызывается.** `LlmEngine.summarize` и `TutorPrompt.summarization` есть, но при очень длинных диалогах контекст просто обрезается по `RECENT_MESSAGES_FOR_CONTEXT` в `ConversationRepository`. Поле `summary` в `Conversation` под это зарезервировано.
- **HF-токен вводится вручную** на экране настройки и в приложении не сохраняется — при переустановке ввести заново.
- **Скачивание LLM — не на WorkManager.** Идёт в `viewModelScope` в `ModelSetupViewModel`; при сворачивании приложения может прерваться (докачка это переживёт). Зависимость `work-runtime-ktx` подключена, но менеджер загрузок на ней ещё не написан.
- **Иконка приложения** — заглушка. Заменить через Android Studio: правый клик на `res` → New → Image Asset.
- **Тестов (unit/UI) практически нет** — только сгенерированные `ExampleUnitTest`/`ExampleInstrumentedTest`.

## Смена реализации движка

Всё завязано на `di/AppModule.kt`:

```kotlin
@Binds @Singleton
abstract fun bindSttEngine(impl: VoskSttEngine): SttEngine     // ← сюда, напр., WhisperSttEngine

@Binds @Singleton
abstract fun bindLlmEngine(impl: LiteRtLlmEngine): LlmEngine   // ← или StubTutorLlmEngine для разработки UI без модели
```

Чтобы разрабатывать UI без скачивания гигабайтной модели — верните `StubTutorLlmEngine` в `bindLlmEngine` (и, при желании, `AndroidSttEngine` в `bindSttEngine`), больше ничего менять не нужно.
