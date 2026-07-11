# CLAUDE.md

Гайд для Claude Code по этому репозиторию. Полное описание проекта — в `README.md`.

## Что это

Офлайн голосовой репетитор английского под Android. Цикл: микрофон → STT (Vosk) → LLM (Gemma 4 E2B через LiteRT-LM, GPU) → TTS (системный) → Room-история.
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
- Активные реализации: `VoskSttEngine`, `AndroidTtsEngine`, `LiteRtLlmEngine`.
- Неактивные (лежат в `data/engine/`, но не привязаны): `StubTutorLlmEngine` (заглушка LLM для разработки UI без модели), `AndroidSttEngine` (системный SpeechRecognizer). Не удалять — это осознанные fallback-и.
- Голосовой цикл целиком — в `presentation/conversation/ConversationViewModel.kt` (`onMicTapped` / `onSpeechResult`). Push-to-talk, без VAD.
- Системный промпт репетитора — только в `domain/TutorPrompt.kt`. Это главный рычаг качества на маленькой модели; менять поведение репетитора — здесь, не в вьюмоделях.
- Слои данных: `data/local/` (Room: `AppDatabase`, `ConversationDao`, `Entities`) → `data/repository/ConversationRepository` → вьюмодели. В контекст LLM идут последние `RECENT_MESSAGES_FOR_CONTEXT` (=20) сообщений.
- Навигация: `presentation/navigation/AppNavHost.kt`, маршруты `setup → history → conversation`. Стартовый экран выбирает `MainActivity` по наличию файла `filesDir/model.litertlm`.

## Модели (скачиваются на устройство, не в APK)

- **LLM** — `ModelSetupViewModel`: качает `model.litertlm` с HuggingFace (`litert-community/gemma-4-E2B-it-litert-lm`, gated → нужен токен `hf_...`, вводится вручную на экране настройки). Загрузка резюмируемая через HTTP `Range`, идёт в `viewModelScope` (не WorkManager).
- **STT** — `VoskSttEngine`: лениво качает `vosk-model-en-us-0.22-lgraph.zip` с alphacephei при первом использовании микрофона, распаковывает в `filesDir`.
- Обе модели большие; в `assets` их класть нельзя.

## Подводные камни

- `LiteRtLlmEngine.initialize()` тяжёлый (~30 сек) — вызывается один раз на экране настройки после скачивания, не на каждый запрос.
- Backend LLM — `Backend.GPU()`. На устройстве без рабочего OpenCL инициализация упадёт; `libOpenCL.so`/`libvndksupport.so` объявлены опциональными в `AndroidManifest.xml`.
- Разрешение `RECORD_AUDIO` запрашивается в `MainActivity`; `INTERNET` нужен только для разовой загрузки моделей.
- Не-обязательные ветки, помеченные в README/коде как «упрощения MVP» (VAD, потоковый TTS, суммаризация длинных диалогов), объявлены, но не подключены — не считать их рабочими без проверки.

## Правила окружения

- ОС — Windows, оболочка PowerShell. Для файловых операций используйте абсолютные Windows-пути с буквой диска и обратными слэшами (напр. `C:\Users\sverq\AndroidStudioProjects\EnglishVoiceTutor\...`).
- Коммитить/пушить только по явной просьбе пользователя. Текущая рабочая ветка — `feature/1`, основная — `main`.
