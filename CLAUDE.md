до# CLAUDE.md

Гайд для Claude Code по этому репозиторию. Полное описание проекта — в `README.md`.

## Что это

Офлайн голосовой репетитор английского под Android. Цикл: микрофон → STT (Parakeet через sherpa-onnx) → LLM (Gemma 4 E2B через LiteRT-LM, GPU с откатом на CPU) → TTS (Piper через sherpa-onnx) → Room-история.
Стек: Kotlin, Jetpack Compose, MVVM, Hilt, Room, Coroutines/Flow. `minSdk 26`, `compileSdk 37`.

Комментарии в коде и документация — **на русском**. Держите этот язык при правках комментариев/доков; идентификаторы и строковые ключи — на английском.

## Сборка и запуск

- Сборка: `./gradlew assembleDebug` (Windows: `gradlew.bat`). Wrapper может отсутствовать — тогда `gradle wrapper` или сборка из Android Studio.
- Тесты: `./gradlew test` (unit), `./gradlew connectedAndroidTest` (instrumented). Реальных тестов пока нет — только шаблонные `ExampleUnitTest`/`ExampleInstrumentedTest`.
- **Только реальное устройство.** Эмулятор не подходит: нужен микрофон и GPU c OpenCL для LLM. Ориентир по железу — 8 ГБ ОЗУ.
- Версии зафиксированы в `gradle/libs.versions.toml`; строковые версии-исключения — `litertlm` и `vosk-android` прописаны прямо в `app/build.gradle.kts`.
- Репозиторий Vosk (`alphacephei.com/maven`) добавлен в `settings.gradle.kts`.

## Архитектура: что важно знать

- **Развязка через интерфейсы.** UI и вьюмодели зависят только от `SttEngine`/`TtsEngine`/`LlmEngine` (`data/engine/Engines.kt`). Конкретные реализации привязываются в **`di/AppModule.kt`** — это единственное место смены движка.
- Активные реализации: `SherpaOnnxSttEngine`, `SherpaOnnxTtsEngine`, `LiteRtLlmEngine`.
- Неактивные (лежат в `data/engine/`, но не привязаны): `StubTutorLlmEngine` (заглушка LLM для разработки UI без модели), `AndroidSttEngine` (системный SpeechRecognizer), `VoskSttEngine`, `AndroidTtsEngine` (системный TextToSpeech). Не удалять — это осознанные fallback-и.
- Голосовой цикл целиком — в `presentation/conversation/ConversationViewModel.kt` (`onMicTapped` / `onSpeechResult` → общий ход репетитора `runTutorTurn`). Push-to-talk, без VAD.
- **Начало и конец диалога.** В уроке курса (`topicId != null`) первым говорит репетитор: `speakOpening` гоняет `TutorPrompt.openingKick()`. Прощание ловит `domain/ConversationFlow.isFarewell` (чистая функция, покрыта тестом): реплика уходит в модель как `TutorPrompt.farewellKick`, диалог помечается `ended_at`, а экран показывает панель «Разговор завершён» с разбором и кнопкой «Продолжить» (`continueConversation` снимает отметку).
- **Разбор диалога** — `presentation/conversation/SessionReview*` (маршрут `conversation/{id}/report`): каждая реплика учащегося отдельным запросом `LlmEngine.feedback` (батчем маленькая модель теряет формат), парсинг — общий `domain/FeedbackParser`. Результат нигде не кешируется, повторный вход считает заново.
- Системный промпт репетитора — только в `domain/TutorPrompt.kt`. Это главный рычаг качества на маленькой модели; менять поведение репетитора — здесь, не в вьюмоделях.
- Слои данных: `data/local/` (Room: `AppDatabase`, `ConversationDao`, `Entities`) → `data/repository/ConversationRepository` → вьюмодели. В контекст LLM идут последние `RECENT_MESSAGES_FOR_CONTEXT` (=20) сообщений.
- Навигация: `presentation/navigation/AppNavHost.kt`. Нижние вкладки — `roadmap` (Курс) / `vocabulary` / `grammar` / `history`; экраны-детали (`topic/{id}`, `rule/{id}`, `review`, `conversation/...`, `conversation/{id}/report`, `setup`, `settings`) идут поверх, панель на них скрыта. Стартовый экран выбирает `MainActivity` по наличию файла `filesDir/model.litertlm`.
- **Учебный курс.** Контент статический: `assets/curriculum/a1.json … c1.json` → `data/curriculum/CurriculumSource` (парсер на `org.json`, кеш в памяти) → `CurriculumRepository` / `VocabularyRepository` (склейка с прогрессом из Room) → вьюмодели в `presentation/learning/`. Менять курс — в JSON, код трогать не нужно. id слова = `<topicId>:<word>`, id правила = `<topicId>:rule<N>`; на них ссылается прогресс, так что переименования обнуляют его.
- Интервальные повторения — `domain/SrsSchedule.kt` (Лейтнер, чистые функции, покрыты `SrsScheduleTest`). Сборка сессии — `VocabularyRepository.buildSession()`.
- Диалог по теме курса передаёт `topicId` в `ConversationViewModel`, который подставляет сценарий/уровень темы и добавляет в системный промпт `TutorPrompt.TopicFocus`: невыученные слова + эталонные предложения из правил (не тексты правил — они русские и модель их путает).

## Модели (скачиваются на устройство, не в APK)

- **LLM** — `ModelSetupViewModel`: качает `model.litertlm` с HuggingFace (`litert-community/gemma-4-E2B-it-litert-lm`, gated → нужен токен `hf_...`, вводится вручную на экране настройки). Загрузка резюмируемая через HTTP `Range`, идёт в `viewModelScope` (не WorkManager).
- **STT** — `SherpaOnnxSttEngine`: лениво качает `sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-non-streaming.tar.bz2` (~500 МБ) с релизов `k2-fsa/sherpa-onnx` при первом использовании микрофона, распаковывает в `filesDir`. Модель прошлой версии (Whisper `small.en`) при этом удаляется.
- **TTS** — `SherpaOnnxTtsEngine`: лениво качает `vits-piper-en_US-libritts_r-medium.tar.bz2` (~75 МБ, тег релиза `tts-models`) при первой попытке озвучки. Голос и скорость — `data/local/VoicePreferences` (SharedPreferences + StateFlow), каталог вариантов — `VoiceCatalog`. Движок читает их на каждое предложение, поэтому смена настройки слышна сразу, без пересоздания `OfflineTts`. Дикторы модели анонимные — ярлыки в UI нейтральные («Голос 1…12»), выбор на слух через пробную фразу.
- Все три модели большие; в `assets` их класть нельзя. Каталог TTS-модели (`vits-piper-...`) не подпадает под префиксы очистки в `SherpaOnnxSttEngine.deleteOutdatedModels()` — при переименовании это учесть, иначе STT снесёт голос.

## Подводные камни

- `LiteRtLlmEngine.initialize()` тяжёлый (~30 сек) — вызывается один раз на экране настройки после скачивания, не на каждый запрос.
- Backend LLM — сначала `Backend.GPU()`, при исключении автоматически `Backend.CPU()` (флаг `LiteRtLlmEngine.usingCpuFallback`). `libOpenCL.so`/`libvndksupport.so` объявлены опциональными в `AndroidManifest.xml`.
- Разрешение `RECORD_AUDIO` запрашивается в `MainActivity`; `INTERNET` нужен только для разовой загрузки моделей.
- **Системного TTS может не быть вовсе.** Поэтому озвучка своя — `SherpaOnnxTtsEngine` (Piper через sherpa-onnx, модель `~75 МБ` качается лениво). Проверка наличия системного движка: `adb shell pm query-services -a android.intent.action.TTS_SERVICE`.
- **`OfflineTts.generateWithCallback` использовать нельзя.** Нативная часть ищет у колбэка метод с точной сигнатурой `invoke([F)Ljava/lang/Integer;`, а Kotlin 2.x компилирует лямбды через invokedynamic — процесс падает с «JNI DETECTED ERROR ... ExternalSyntheticLambda». Синтез идёт по предложениям обычным `generate()`.
- Скачивание и распаковка `.tar.bz2` моделей sherpa-onnx — общий `data/model/ArchiveModelDownloader` (используют STT и TTS). У каждой модели своё имя временного архива, иначе параллельные загрузки перетрут друг друга.
- **Блочные комментарии в Kotlin вложенные.** Путь вроде `assets/curriculum/*.json` внутри KDoc открывает вложенный комментарий и молча превращает остаток файла в комментарий — ошибка при этом выглядит как «Unresolved reference» во всех использующих файлах, а не как проблема в исходном.
- Баннер состояния озвучки (загрузка голоса / недоступность) — общий `presentation/components/TtsStatusBanner`; подключён в курсе, словаре, повторении, правилах, настройках и диалоге.
- Схема Room — версия 3, миграции `MIGRATION_1_2` (колонка `conversations.topic_id` + таблицы `vocab_progress` и `topic_progress`) и `MIGRATION_2_3` (колонка `conversations.ended_at`) в `AppDatabase.kt`. `exportSchema = false`, автотеста миграции нет — при изменении сущностей миграцию писать руками и проверять на устройстве с уже установленной сборкой.
- Не-обязательные ветки, помеченные в README/коде как «упрощения MVP» (VAD, потоковый TTS, суммаризация длинных диалогов), объявлены, но не подключены — не считать их рабочими без проверки.

## Правила окружения

- ОС — Windows, оболочка PowerShell. Для файловых операций используйте абсолютные Windows-пути с буквой диска и обратными слэшами (напр. `C:\Users\sverq\AndroidStudioProjects\EnglishVoiceTutor\...`).
- Коммитить/пушить только по явной просьбе пользователя. Текущая рабочая ветка — `feature/1`, основная — `main`.
