# Contributing to ShedLock

Пользовательская документация: [README.md](README.md).  
Инструкции для AI-агентов и типовые задачи: [AGENTS.md](AGENTS.md).  
Правила Cursor под конкретные задачи: [`.cursor/rules/`](.cursor/rules/).

## Что это за проект

ShedLock — библиотека distributed lock для scheduled tasks. Она гарантирует, что задача с одним именем выполняется не более чем на одном узле (или потоке) одновременно.

Если lock уже занят, другие попытки **не ждут** — выполнение **пропускается**.

ShedLock **не** является и не должен становиться distributed scheduler. Для планировщика используйте другие проекты (см. README).

Locks time-based: предполагается синхронизация часов узлов (или использование времени хранилища, например `usingDbTime()`).

## Основные сценарии использования

1. Spring / Micronaut / CDI: аннотация `@SchedulerLock` + бин `LockProvider`.
2. Без фреймворка: `DefaultLockingTaskExecutor` + `LockConfiguration`.
3. Новый store: реализация `LockProvider` в модуле `providers/<store>/shedlock-provider-*`.

Подробности и примеры — в [README.md](README.md).

## Стек и запуск

- Java **17+**
- Maven multi-module, wrapper: `./mvnw`
- Версия в корневом `pom.xml` (сейчас `7.11.1-SNAPSHOT`)

Полный `./mvnw test` тяжёлый (много модулей и интеграционных тестов). Для точечной работы:

```bash
./mvnw -pl <module> -am test
```

Пример:

```bash
./mvnw -pl providers/inmemory/shedlock-provider-inmemory -am test
```

Часть IT-тестов providers может требовать Docker / Testcontainers.

## Модули и ответственность

| Область | Путь | Ответственность |
|---------|------|-----------------|
| Core | `shedlock-core` | `LockProvider`, `SimpleLock`, `LockConfiguration`, executors, `LockAssert` |
| Test support | `shedlock-test-support` | База интеграционных тестов (`AbstractLockProviderIntegrationTest` и др.) |
| Spring | `spring/` | AOP / интеграция со Spring Scheduling |
| Micronaut | `micronaut/` | Интеграция с Micronaut |
| CDI | `cdi/` | Интеграция с CDI (Quarkus и совместимые) |
| Providers | `providers/<store>/shedlock-provider-*` | Lock в конкретном внешнем store |
| BOM | `shedlock-bom` | Согласованные версии артефактов |
| Micrometer | `shedlock-micrometer` | Метрики |

## Ключевые термины

- **`lockAtMostFor`** — максимальное время удержания lock; safety net, если узел упал до unlock.
- **`lockAtLeastFor`** — минимальное время удержания; снижает повторный запуск коротких задач при расхождении часов.
- **Skip-not-block** — при занятом lock задача пропускается, а не ставится в очередь.
- **`usingDbTime()`** (и аналоги) — время сервера хранилища вместо часов app-сервера.
- **`LockAssert`** — проверка, что lock реально удерживается (ловит misconfiguration AOP).
- **Storage-based provider** — lock хранится во внешнем store (таблица, документ, key-value).

## Границы изменений

Без явного запроса **нельзя**:

- менять публичный контракт `LockProvider` / `SimpleLock` / `LockConfiguration` и семантику skip-not-wait;
- превращать проект в distributed scheduler;
- делать несовместимые изменения схем существующих providers без миграции и документации;
- менять `LICENSE.txt`, `KEYS`, процесс release signing.

## Стиль кода

- Ориентируйтесь на соседние модули того же типа (другой provider, соседний Spring-тест).
- Пакеты: `net.javacrumbs.shedlock...`.
- Добавляйте `package-info.java` и при необходимости `module-info.java`, как в соседних модулях.
- Соблюдайте Apache License header (см. [header.txt](header.txt)).
- Не рефакторьте код вне scope задачи.

## Тесты и проверки

- Багфикс и новый provider — с тестами (для provider: IT на базе `shedlock-test-support`).
- Проверка затронутого модуля: `./mvnw -pl <module> -am test`.
- Рефакторинг без смены поведения: существующие тесты должны оставаться зелёными.

## Commit и pull request

В **теле commit message** и в **описании PR** обязательна строка:

```text
Сделано с агентом, проверено человеком
```

## Уточнения vs допущения

**Спросите**, если задача затрагивает:

- публичный API core или семантику lock;
- зависимости / версии в core или BOM;
- схему или контракт уже существующего store.

**Можно опираться на соседние модули без лишних вопросов**, если:

- добавляете provider по образцу существующего;
- делаете локальный рефакторинг или багфикс внутри одного модуля без смены API.
