# CommitDuck

`Commit Duck` — плагин для JetBrains IDE, представляющий собой виртуального питомца и небольшого напарника разработчика. Утка живёт прямо в среде разработки, реагирует на действия пользователя и сопровождает его в процессе работы: радуется активности, коммитам и завершению задач, предлагает мотивирующие фразы, а при длительном отсутствии активности может скучать или засыпать.

Основная идея — сделать привычный процесс разработки немного веселее, добавив в него игровые механики и элемент эмоционального взаимодействия.

## Технологии

- **Kotlin 2.3** + IntelliJ Platform Plugin SDK (Gradle IntelliJ Platform Plugin 2.2)
- **Gradle 8.14** (wrapper), JDK 21
- Целевая IDE: IntelliJ IDEA Community 2026.1 (build 261)

## Структура проекта

```
src/main/kotlin/com/commitduck/
├── pet/            # модель питомца: состояния, события, конечный автомат
├── phrases/        # пул мотивационных фраз
├── persistence/    # сохранение состояния (PersistentStateComponent)
└── ui/             # tool window, панель утки, иконки
src/main/resources/
├── META-INF/plugin.xml
└── icons/          # картинки утки по состояниям (SVG)
gradle/jetbrains-updates.xml  # локальный mirror для updates.xml (см. ниже)
docs/
├── contracts.md          # контракты между компонентами
└── test-scenarios.md     # сценарии проверки MVP
```

## Сборка и запуск

Требуется JDK 21.

```bash
# Собрать плагин (результат в build/distributions/CommitDuck-<ver>.zip)
./gradlew buildPlugin

# Запустить тестовую IDE с установленным плагином
./gradlew runIde
```

После запуска откройте **View → Tool Windows → CommitDuck** — там появится утка.

### Особенности этой машины (важно)

В сети, где вы работаете, заблокирован `www.jetbrains.com` (и `download.jetbrains.com`),
а на macOS плагин Gradle некорректно разрешает локальную IDE. Из-за этого в
`build.gradle.kts`/`gradle.properties` сделан набор обходов:

1. **`updates.xml`** — плагин Gradle безусловно скачивает
   `https://www.jetbrains.com/updates/updates.xml` на этапе применения. Указан локальный
   mirror через свойство `org.jetbrains.intellij.platform.productsReleasesJetBrainsIdesUrl`
   в `gradle.properties` (см. `gradle/jetbrains-updates.xml`).

2. **Платформа** — вместо скачивания IDE используется локально установленная
   `IntelliJ IDEA CE 2026.1` через `local("/Applications/IntelliJ IDEA CE 2.app")`.
   На macOS плагин добавляет в classpath каталог `Contents` вместо jar-файлов,
   поэтому jar-файлы платформы добавлены отдельно (`compileOnly(fileTree(...))`).
   Инструментирование кода (`instrumentCode`) отключено: чистый Kotlin-плагин
   не требует Java-инструментации, а нужные для неё артефакты лежат в заблокированном репозитории.

3. **`runIde`** — для локальной macOS-IDE плагин собирает неверный путь к `nio-fs.jar`
   (`lib/nio-fs.jar` вместо `Contents/lib/nio-fs.jar`), из-за чего IDE падает на старте
   (`ClassNotFoundException: ...MultiRoutingFileSystemProvider`). В `tasks.runIde` добавлен
   корректный `-Xbootclasspath/a`. Также добавлен `kotlinx-coroutines-debug` в classpath
   запуска — иначе не загружается java-агент корутин
   (`ClassNotFoundException: kotlinx.coroutines.debug.AgentPremain`).

На сети, где `www.jetbrains.com` доступен, можно вернуться к стандартной сборке:
удалить override в `gradle.properties`, заменить `local(...)` + обходы в `build.gradle.kts`
на `intellijIdeaCommunity("<version>")` и включить `instrumentCode` обратно.

## Статус

- [x] День 1: болванка плагина, tool window, отображение состояний, модель питомца, события, фразы, сохранение состояния, контракты и сценарии. Проект собирается (`./gradlew buildPlugin`) и запускается (`./gradlew runIde`) — IDE стартует, утка отображается в tool window.
- [ ] День 2: отслеживание активности в редакторе, реакция на бездействие, полный MVP.
- [ ] День 3: Git-события, Debug Duck, интеграция с LLM.