# BuildConfig для модов IMPACT

Общая Gradle build logic для Forge 1.7.10. RetroFuturaGradle 2.0.6 обеспечивает
Minecraft, MCP, deobf и reobf. Локальные плагины настраивают сборку;
Java/Kotlin toolchain и vendor проекта не задаются.

## Подключение

В `settings.gradle.kts` проекта мода:

```kotlin
pluginManagement {
    includeBuild("remote-build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("settings-setup")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("remote-build-logic/libs.versions.toml"))
        }
    }
}
```

Зависимости build logic разрешаются через репозитории самого сабмодуля.
`settings-setup` добавляет Maven-репозитории для зависимостей и плагинов;
он не подключает Foojay, Blowdryer и плагины ко всем подпроектам.
RFG также добавляет свои репозитории, поэтому `FAIL_ON_PROJECT_REPOS`
для Minecraft-модулей не подходит.

В `build.gradle.kts` мода:

```kotlin
plugins {
    id("minecraft-setup")
    id("publish-setup")
}

impactMod {
    modId.set("example")
    modName.set("Example")
    modGroup.set("space.impact.example")
    mavenGroup.set("space.impact")
    mavenUrl.set("https://maven.accident.space/repository/maven-releases/")
}
```

`minecraft-setup` подключает Kotlin, RFG, Shadow и генератор BuildConfig.
Существующий блок `buildConfig { ... }` остаётся в моде: имя класса,
package, Java/Kotlin-вывод и дополнительные поля определяет сам мод.
`publish-setup` создаёт Maven-публикацию `maven`; публикует dev, reobf
и sources через Java component. Для обычной библиотеки он публикует обычный JAR.
Доступ к Maven задают `IMPACT_MAVEN_USER` и `IMPACT_MAVEN_PASSWORD`.

## Миграция существующих модов

1. Обновить сабмодуль BuildConfig и wrapper основного проекта до Gradle 9.7.1; запускать Gradle на JDK 25.
2. Подключить `settings-setup` в settings вместо прежнего плагина настроек.
3. Сохранить `minecraft-setup`, `publish-setup`, каталог `libs` и блок `buildConfig`.
4. Удалить устаревшие настройки прежних convention-плагинов и неиспользуемых инструментов.
5. Для Shadow использовать id `com.gradleup.shadow`; конфигурацию relocation адаптировать к Property API.
6. BuildConfig 6 использует задачу `generateBuildConfigClasses` вместо `generateBuildConfig`; обновить явные ссылки на задачу. Для Kotlin-генерации проверить visibility полей.
7. При компиляции Lombok на JDK 25 использовать Lombok 1.18.40 или новее.
8. Выполнить `build`, проверить dev/reobf JAR, manifest и Maven POM.

BuildConfig 6 по умолчанию генерирует Kotlin-класс с видимостью `internal`.
Если его используют другие модули, задать публичную видимость в моде:

```kotlin
buildConfig {
    useKotlinOutput { internalVisibility = false }
}
```

Legacy-свойства `modId`, `modName`, `modGroup`, `mavenGroupId`, `mavenPublishUrl`,
`coreModClass`, `usesMixins`, `usesMixinDebug`, `forceEnableMixins`,
`accessTransformersFile`, `enableGenericInjection`, `usesShadowedDependencies`,
`minimizeShadowedDependencies`, `relocateShadowedDependencies`, `noPublishedSources`,
`minecraftVersion`, `channel`, `mappingsVersion`, `developmentEnvironmentUserName`
и `customArchiveBaseName` продолжают работать.
Значения в `impactMod` имеют приоритет над ними.

Версия берётся из `RELEASE_VERSION`, затем `VERSION`, затем последнего Git-тега
одинаково локально и в CI. Без тегов используется Git-версия без суффикса `.dirty`.
Явная `project.version` или legacy `modVersion`
заменяет значение по умолчанию. `getVersionMod()` остаётся доступным.
`RELEASE_VERSION`, `ARTIFACT_GROUP_ID`, `ARTIFACT_ID` переопределяют Maven-публикацию.
При подключённом `publish-setup` локальные архивы используют те же `artifactId`
и версию, что и Maven: `<artifactId>-<version>[-<classifier>].jar`.
Это относится к обычным, dev, Shadow, reobf и sources JAR; `archiveName`, `modId`
и `customArchiveBaseName` больше не задают имя публикуемых архивов.
Внутренние архивы RFG (`packageMcLauncher`, `packagePatchedMc`) сохраняют свои
имена и версию Minecraft; настройки Maven к ним не применяются.

## Несколько модулей

Корневой проект перечисляет модули, но не применяет Minecraft-плагин:

```kotlin
include(":api", ":first-mod", ":second-mod")
```

Обычная Java-библиотека `api/build.gradle.kts`:

```kotlin
plugins {
    id("jvm-setup")
    id("publish-setup")
}

group = "space.impact"
version = "1.0.0"
```

Для Kotlin-библиотеки дополнительно применить `org.jetbrains.kotlin.jvm`.
В каждом моде применить `minecraft-setup`, задать собственный `impactMod`
и добавить зависимость:

```kotlin
dependencies {
    implementation(project(":api"))
    implementation(project(":first-mod"))
}
```

Компиляция и dev-запуск используют MCP-варианты; подготовка игрового запуска
выбирает локальные SRG JAR через `reobfElements`, без предварительной Maven-публикации.
Координаты `group:name` локальных проектов должны быть уникальными.
Зависимости между модулями должны быть без циклов. В частности, API,
который нужен одновременно GregTech и Impact-Core, следует выделить отдельно.
При сборке нескольких модов source sets, ресурсы и артефакты остаются в своих модулях.

## JDK и целевой байткод

По умолчанию Java использует компилятор Gradle JVM, с целевым Java 8;
на JDK 9+ применяется `--release 8`. Kotlin получает `jvmTarget=1.8`.
JVM target Kotlin сам по себе не запрещает обращаться к новым JDK API.

Настройки задаются для каждого модуля:

```kotlin
impactMod {
    javaRelease.set(8)
    // Необязательно: выбрать только Java-компилятор этого модуля.
    compilerJavaVersion.set(JavaLanguageVersion.of(21))
    minecraftJavaVersion.set(8)
    runJavaVersion.set(8)
}
```

`JavaLanguageVersion` импортируется из `org.gradle.jvm.toolchain`.
Собственные настройки `kotlin.jvmToolchain(...)` остаются доступными.
RFG продолжает выбирать JDK для своих Minecraft-задач. Для стандартного
Minecraft 1.7.10 нужен установленный JDK 8; автоматическая загрузка JDK не включена.
Gradle запускается на современном JDK отдельно от игрового runtime.
Используется Gradle 9.7.1 на JDK 25. Это JVM сборки; она не задаёт глобальный
toolchain модов и не меняет Java 8 игрового runtime.

## Зависимости разработки и встраивание

```kotlin
dependencies {
    // Только запуск и тесты; не попадает в Maven POM.
    runtimeOnlyNonPublishable(libs.impact.forgelin)
    // Компиляция, запуск и тесты; не попадает в Maven POM.
    devOnlyNonPublishable(libs.mixin.fernflower)
    // Встраивается в JAR перед reobf.
    shadowImplementation(libs.kotlin.stdlib.core)
}
```

Shading включается при наличии `shadowImplementation` либо при
`shadeDependencies=true`. Minimize и relocation по умолчанию выключены;
legacy-свойства могут включить их. Для Forgelin оба должны оставаться выключенными.
Встроенные библиотеки не становятся внешними Maven-зависимостями.
RFG `modUtils.deobfuscate(...)` и `rfg.deobf(...)` доступны как раньше.

## Coremods и Mixins

```kotlin
impactMod {
    coreModClass.set("mixinplugin.EarlyLoader") // Относительно modGroup.
    usesMixins.set(true)
    mixinConfigs.set(listOf("mixins.example.json", "mixins.example.early.json"))
    mixinRefmap.set("mixins.example.refmap.json")
    mixinDebug.set(true)
}
```

Для Mixins подключается Unimixins из каталога и annotation processor, настраивается
refmap и manifest. Kotlin kapt учитывается, если мод подключил его сам.
JSON-конфиги должны лежать в `src/main/resources`; плагин их не перезаписывает.
Early/late-загрузчики остаются кодом мода. Версия провайдера задаётся alias
`mixin-unimixins` в TOML; `mixinDependency` также принимает другой library alias.
Все JSON должны ссылаться на заданный `mixinRefmap`. Classifier `dev`
добавляется плагином.

AT из `src/main/resources/META-INF/*_at.cfg` обнаруживаются автоматически
и записываются в `FMLAT`. Для явного списка используется `accessTransformers`.
Дополнительные source sets, Lombok, свои Kotlin compiler flags и настройка
JUnit остаются в build-скрипте соответствующего модуля.

## Каталог зависимостей

Все внешние зависимости build logic и плагинов, включая Unimixins, его processors,
Fernflower, IC2 runtime и JUnit, задаются в `libs.versions.toml`. Каталог нужно
подключить под именем `libs`, как в примере settings выше; он доступен во всех модулях.
Нативные Gradle-плагины и `gradleTestKit()` предоставляются самим Gradle.

В build-скриптах используются обычные типизированные aliases:

```kotlin
dependencies {
    implementation(libs.impact.forgelin)
    testImplementation(libs.test.junit)
}
```

В коде convention-плагинов каталог читается через публичный `VersionCatalogsExtension`:
`pluginManager.alias(libs.plugin("kotlin-jvm"))`, `dependencies.add("implementation",
libs.library("impact-forgelin"))`. Это не требует сгенерированного `LibrariesForLibs`
или внутренних Gradle services в classpath. Версия Kotlin для компиляции build logic
задаётся отдельно от Kotlin модов и совпадает со встроенным Kotlin Gradle 9.7.1.
Modrinth Maven подключён для группы `maven.modrinth`.

## Проверка build logic

Из каталога BuildConfig, установленным Gradle:

```shell
gradle :plugins:check
```

TestKit проверяет компиляцию библиотек с разными JVM targets, Maven POM,
метаданные, coremod manifest, AT, Mixins, Shadow и локальные MCP/SRG-варианты.
