# Версии и обновление зависимостей

Сверка выполнена 11 ноября 2026 года. Это снимок версий на дату настройки,
а не автоматически обновляемый список. Предварительные релизы не используются.

## Бэкенд

| Компонент                    | Выбранная версия       | Последняя стабильная на дату сверки | Решение                                               |
| ---------------------------- | ---------------------- | ----------------------------------- | ----------------------------------------------------- |
| Eclipse Temurin              | 25.0.5+8 LTS           | 25.0.5+8 в ветке 25                 | Остаёмся на Java 25 LTS                               |
| Spring Boot                  | 4.1.2                  | 4.1.2                               | 4.2.0-RC2 не является стабильным релизом              |
| Gradle Wrapper               | 9.9.1                  | 9.9.1                               | Обновлён с 9.8.2; закреплена SHA-256 дистрибутива     |
| Dependency Management plugin | 1.1.7                  | 1.1.7                               | Версия плагина закреплена                             |
| Spring Framework             | 7.0.10, через Boot BOM | Отдельно не сверялся                | Не переопределяем BOM                                 |
| JUnit Jupiter / Platform     | 6.0.3, через Boot BOM  | 6.1.3                               | Совместимый набор из BOM важнее отдельного обновления |
| Checkstyle                   | 14.4.0                 | 14.4.0                              | Явная версия в Gradle                                 |

Vaadin и интеграция Spring Boot Docker Compose удалены. Lombok тоже удалён:
в текущем каркасе он не используется. Docker нужен только для сборки и запуска
образа, а не для локальных JUnit-тестов.

Spring Boot управляет версиями своих стартеров и транзитивных библиотек.
Фактически разрешённые версии закреплены в `gradle.lockfile`.

## Фронтенд и инструменты

| Пакет                                       | Выбрано | Последняя стабильная на дату сверки |
| ------------------------------------------- | ------- | ----------------------------------- |
| react / react-dom                           | 19.3.1  | 19.3.1                              |
| vite                                        | 8.3.8   | 8.3.8                               |
| @vitejs/plugin-react                        | 6.3.0   | 6.3.0                               |
| typescript                                  | 6.0.3   | 7.0.2                               |
| @types/react / @types/react-dom             | 19.3.0  | 19.3.0                              |
| @types/node                                 | 26.6.6  | 26.6.6                              |
| @playwright/test                            | 1.65.0  | 1.65.0                              |
| @typespec/compiler, http, openapi, openapi3 | 1.18.0  | 1.18.0                              |
| @hey-api/openapi-ts                         | 0.99.0  | 0.99.0                              |
| eslint                                      | 10.14.0 | 10.14.0                             |
| @eslint/js                                  | 10.0.1  | 10.0.1                              |
| typescript-eslint                           | 8.72.2  | 8.72.2                              |
| eslint-plugin-react-hooks                   | 7.1.1   | 7.1.1                               |
| eslint-plugin-react-refresh                 | 0.5.7   | 0.5.7                               |
| globals                                     | 17.14.0 | 17.14.0                             |
| prettier                                    | 3.9.10  | 3.9.10                              |

TypeScript 7 пока не выбираем: `typescript-eslint@8.72.2` заявляет поддержку
TypeScript `>=4.8.4 <6.1.0`. `@typespec/rest` пока не нужен и не установлен.
Дополнительные peer-зависимости TypeSpec закреплены npm lock-файлом.

Playwright используется только для браузерных тестов. Chromium устанавливается
отдельно через `mise run test:browser:install`; в production-образ он не входит.

## Окружение

| Инструмент | Выбрано   | Сравнение на дату сверки |
| ---------- | --------- | ------------------------ |
| Node.js    | 26.13.0   | Последний LTS            |
| npm        | 12.2.2    | Последний стабильный     |
| mise       | 2026.11.6 | Последний стабильный     |
| hk         | 2.12.0    | Последний стабильный     |
| pitchfork  | 2.34.0    | Последний стабильный     |

В `mise.toml` npm объявлен перед Node.js, чтобы его исполняемые файлы имели
приоритет над npm, поставляемым внутри Node.js.

Docker использует Node 26.13.0 и Temurin 25.0.5+8. Для каждого базового образа
закреплены тег и digest multi-platform manifest. В runtime остаются только JRE
и приложение; Node.js, npm, Gradle и генераторы туда не копируются.

## Известные предупреждения npm audit

На дату первоначальной установки `npm audit` сообщает о 14 уязвимых пакетах
(10 moderate, 4 high) исключительно в инструментах разработки:

- `js-yaml@4.2.0` через генератор Hey API: предупреждения о чрезмерном потреблении
  CPU при разборе специально подготовленного YAML. Генератор получает только
  локальную спецификацию, скомпилированную из TypeSpec; не подавайте ему
  недоверенные внешние спецификации.
- `minimatch@10.2.6` через ESLint и typescript-eslint: предупреждение об отказе
  в обслуживании при обработке специально подготовленных glob-шаблонов.

`npm audit --omit=dev` сообщает о 0 уязвимостей. Это не заменяет аудит Java
и базовых Docker-образов и не означает, что предупреждения можно игнорировать.

Не используются `audit fix --force`, подавление advisory или `overrides` с
выходом за заявленные диапазоны совместимости. Устранение требует согласованного
обновления или замены инструментов. `mise run audit` выводит актуальный отчёт;
он намеренно отделён от базового `check`, пока эти предупреждения не устранены.

## Как обновлять

1. Обновить точные версии в `mise.toml` и/или `package.json`.
2. Выполнить `mise install`, затем `npm install`, если менялись npm-зависимости.
3. Обновить Gradle lock-файлы: `mise exec -- ./gradlew dependencies --write-locks`.
4. При обновлении Gradle использовать задачу `wrapper`, проверить SHA-256
   официального дистрибутива и закоммитить оба скрипта и Wrapper JAR.
5. Выполнить `mise run api:generate`, просмотреть изменения спецификации и клиента.
6. Выполнить `mise run audit`, `mise run check`, `mise run image:build`
   и `mise run image:test`.
7. Закоммитить исходники, оба npm/Gradle lock-файла и сгенерированные API-артефакты.

Dependabot предлагает обновления npm, Gradle, Docker и GitHub Actions.
Версии mise, hk, pitchfork, Java и Node в `mise.toml` проверяются при обновлении
окружения вручную. Служебный workflow Hexlet не редактируется.

## Первичные источники

- [npm Registry](https://registry.npmjs.org/): dist-tag `latest`, `engines` и `peerDependencies` пакетов.
- [Spring Initializr](https://start.spring.io/metadata/client).
- [Maven Central](https://repo.maven.apache.org/maven2/) и Spring Boot Dependencies BOM 4.1.2.
- [Gradle releases](https://services.gradle.org/versions/current).
- [Node.js releases](https://nodejs.org/dist/index.json).
- [Adoptium API](https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse).
- GitHub Releases: [mise](https://github.com/jdx/mise/releases), [hk](https://github.com/jdx/hk/releases), [pitchfork](https://github.com/jdx/pitchfork/releases).
- npm audit advisories: [js-yaml merge chains](https://github.com/advisories/GHSA-52cp-r559-cp3m),
  [js-yaml omap](https://github.com/advisories/GHSA-5p4m-2wfm-xmqj),
  [js-yaml empty merge sources](https://github.com/advisories/GHSA-2883-xcg3-v3hh),
  [minimatch](https://github.com/advisories/GHSA-g3hv-75wg-m249).
