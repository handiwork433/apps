# Telegram Subscription Text App

Этот репозиторий содержит два компонента, которые совместно предоставляют персональные тексты в Android-приложении после оплаты 
подписки:

- **Android-приложение** на Jetpack Compose. Пользователь вводит персональный токен, приложение запрашивает тексты по сети и пок
азывает статус подписки.
- **Node.js-сервер** с REST API и Telegram-ботом. Бот принимает оплату (через Telegram Payments или вручную), выдаёт токен, позв
оляет редактировать тексты и контролирует срок подписки.

## Структура проекта

- `android-app/` — Android-приложение (Kotlin, Compose, DataStore).
- `server/` — Express API и Telegram-бот на `node-telegram-bot-api`.
- `README.md` — текущее описание.

## Пошаговая инструкция: от GitHub Desktop до готового APK

Ниже описан полный цикл запуска сервиса на Windows 10/11 c использованием **GitHub Desktop**. На macOS шаги аналогичны, отличаться
будут только пути и менеджер пакетов.

### 1. Подготовка окружения

1. Установите [GitHub Desktop](https://desktop.github.com/) и войдите в свой GitHub-аккаунт.
2. Установите [Node.js 18 LTS](https://nodejs.org/en/download) (во время установки поставьте галочку «Добавить в PATH»).
3. Установите [Android Studio Hedgehog или новее](https://developer.android.com/studio) вместе с Android SDK (API 34+) и Android
   SDK Platform-Tools.
4. (Опционально) Установите редактор кода — например, Visual Studio Code или JetBrains Fleet — для удобного редактирования
   файлов `.env` и JSON.
5. Создайте Telegram-бота через [@BotFather](https://t.me/BotFather):
   - Отправьте команду `/newbot`, придумайте имя и @username.
   - Сохраните выданный токен — он понадобится в `.env`.
6. Если хотите принимать платежи прямо в боте, получите токен платежного провайдера у BotFather командой `/setpayment`
   (можно пропустить и продлевать подписки вручную).

### 2. Клонирование репозитория через GitHub Desktop

1. Откройте GitHub Desktop → **File > Clone repository...**.
2. Вкладка **URL** → вставьте ссылку на репозиторий (например, `https://github.com/username/telegram-text-app.git`).
3. Выберите папку на диске (по умолчанию `C:\Users\<вы>\Documents\GitHub\telegram-text-app`).
4. Нажмите **Clone** и дождитесь завершения загрузки.
5. После клонирования в правом верхнем углу выберите **Open in Explorer**, чтобы открыть папку проекта, или **Open in Terminal**, чтобы
   сразу перейти в командную строку внутри репозитория.

### 3. Настройка Telegram-бота и данных пользователя

1. В Telegram отправьте боту команду `/start` и сохраните полученный токен (его будет вводить пользователь в приложении).
2. Для тестирования без платежей используйте команду `/activate <секрет>` (по умолчанию `manually-extend`) — она продлит подписку
   на 30 дней.
3. Обновите тексты командами `/set_texts`, `/set_title`, `/set_subtitle`, `/set_body`.
4. Сами данные лежат в `server/data.json`. При необходимости можно отредактировать файл вручную и перезапустить сервер.

### 4. Конфигурация и запуск сервера

1. В GitHub Desktop нажмите **Open in Terminal** → откроется PowerShell/Command Prompt в корне репозитория.
2. Перейдите в каталог сервера:
   ```powershell
   cd server
   ```
3. Создайте файл `.env` (можно скопировать пример ниже) и заполните свои значения:
   ```env
   TELEGRAM_BOT_TOKEN=123456:ABC-DEF
   TELEGRAM_BOT_LINK=https://t.me/your_bot_username
   TEXTS_FILE=./data.json
   PORT=3000
   PAYMENT_PROVIDER_TOKEN=
   SUBSCRIPTION_PRICE=19900
   SUBSCRIPTION_CURRENCY=RUB
   SUBSCRIPTION_DURATION_DAYS=30
   SUBSCRIPTION_ACTIVATION_SECRET=manually-extend
   ```
4. Установите зависимости и запустите сервер:
   ```powershell
   npm install
   npm run start
   ```
   Сервер поднимется на `http://localhost:3000`. Telegram-бот заработает автоматически и будет сохранять данные в `data.json`.
5. Чтобы сервер работал постоянно, можно использовать [PM2](https://pm2.keymetrics.io/) или настроить задачу в Планировщике заданий
   Windows. На этапе разработки достаточно оставить консоль открытой.

### 5. Проверка API

1. В PowerShell выполните запрос (подставьте реальный токен):
   ```powershell
   curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:3000/texts
   ```
2. Убедитесь, что ответ содержит тексты и `subscription.active: true`. Если подписка истекла — вернётся `active: false` и ссылка на
   Telegram-бота.

### 6. Настройка Android-приложения

1. Вернитесь в GitHub Desktop и нажмите **Open in Android Studio** (или откройте `android-app` вручную через `File > Open...`).
2. В корне `android-app` создайте `local.properties` со своим путём к SDK, например:
   ```properties
   sdk.dir=C:\\Users\\<вы>\\AppData\\Local\\Android\\Sdk
   ```
3. Откройте `app/build.gradle.kts` и задайте правильный URL API/бота:
   ```kotlin
   buildConfigField("String", "TEXTS_API_BASE_URL", "\"http://10.0.2.2:3000\"") // эмулятор
   buildConfigField("String", "TELEGRAM_BOT_URL", "\"https://t.me/your_bot_username\"")
   ```
   - Для физического устройства, подключённого по USB, замените `10.0.2.2` на IP-адрес компьютера в локальной сети (например,
     `http://192.168.1.10:3000`).
4. Синхронизируйте проект (`Sync Now`), дождитесь загрузки зависимостей.
5. Соберите APK через **Build > Build Bundle(s) / APK(s) > Build APK(s)** или командой в терминале Android Studio:
   ```bash
   ./gradlew assembleRelease
   ```
6. Готовый файл появится в `android-app/app/build/outputs/apk/release/app-release.apk`. Для тестов используйте debug-сборку
   (`assembleDebug`) и установите APK на устройство (`adb install`).

### 7. Использование приложения

1. При первом запуске приложение попросит ввести токен. Введите значение из Telegram-бота.
2. После успешной авторизации появятся тексты и дата окончания подписки.
3. Если подписка истекла или токен неверный, покажется экран с кнопкой для перехода к боту и повторного продления.

### 8. Работа с GitHub Desktop после изменений

1. Внося изменения (например, правки в `data.json` или коде), GitHub Desktop подсветит изменённые файлы.
2. В поле **Summary** напишите описание, нажмите **Commit to main**, затем **Push origin** — так вы сохраните изменения в своём
   форке/репозитории.
3. Для обновления локальной копии после изменений на GitHub нажмите **Fetch origin** → **Pull**.

## Как это работает

1. Пользователь открывает Telegram-бота и отправляет команду `/start`. Бот создаёт запись для пользователя, выдаёт уникальный токе
н и предлагает оплатить подписку.
2. После успешной оплаты (или ручного продления командой администратора) бот сохраняет дату окончания подписки.
3. Пользователь вводит токен в Android-приложении. Токен сохраняется локально с помощью DataStore.
4. Приложение отправляет запрос `GET /texts` c заголовком `Authorization: Bearer <token>`.
5. Сервер проверяет токен и срок подписки. Если всё в порядке — возвращает персональные тексты и дату окончания подписки. Если су
бскрипция неактивна, приложение показывает экран оплаты со ссылкой на бота.

## Сервер и Telegram-бот

### Настройка окружения

1. Установите Node.js 18+.
2. Скопируйте `.env.example` (создайте самостоятельно на основе списка ниже) в `server/.env` и заполните значения:
   ```env
   TELEGRAM_BOT_TOKEN=ваш_бот_токен
   TELEGRAM_BOT_LINK=https://t.me/your_bot_username   # опционально, будет определён автоматически, если оставить пустым
   TEXTS_FILE=./data.json                              # путь к файлу с данными пользователей
   PORT=3000
   PAYMENT_PROVIDER_TOKEN=                             # опционально: токен платежного провайдера Telegram
   SUBSCRIPTION_PRICE=19900                            # стоимость подписки в минимальных единицах валюты (копейки, центы)
   SUBSCRIPTION_CURRENCY=RUB                           # код валюты ISO 4217
   SUBSCRIPTION_DURATION_DAYS=30                       # длительность одной подписки
   SUBSCRIPTION_ACTIVATION_SECRET=manually-extend      # опционально: секрет для ручного продления
   ```
3. Установите зависимости:
   ```bash
   cd server
   npm install
   ```
4. Запустите сервер:
   ```bash
   npm run start
   ```

> Если `PAYMENT_PROVIDER_TOKEN` не задан, команда `/subscribe` просто сообщит, что платёжный провайдер не настроен. Продлевать 
подписку можно командой `/activate <секрет> [дней]`.

### API

- `GET /texts`
  - Авторизация: `Authorization: Bearer <token>` или query-параметр `?token=<token>`.
  - Успешный ответ (`200`):
    ```json
    {
      "data": {
        "title": "...",
        "subtitle": "...",
        "body": "..."
      },
      "last_updated": "2024-01-01T00:00:00.000Z",
      "subscription": {
        "active": true,
        "expires_at": "2024-02-01T00:00:00.000Z"
      }
    }
    ```
  - Истёкшая подписка (`403`):
    ```json
    {
      "error": "Subscription is not active.",
      "bot_link": "https://t.me/your_bot",
      "subscription": {
        "active": false,
        "expires_at": "2024-01-01T00:00:00.000Z"
      }
    }
    ```
  - Неверный токен (`401`):
    ```json
    {
      "error": "Invalid token. Request a new one in the Telegram bot.",
      "bot_link": "https://t.me/your_bot"
    }
    ```

### Команды Telegram-бота

- `/start` — регистрация пользователя, выдача токена и краткая инструкция.
- `/my_token` — повторно показать токен.
- `/status` — текущий статус и срок подписки.
- `/subscribe` — выставить счёт через Telegram Payments (работает, если задан `PAYMENT_PROVIDER_TOKEN`).
- `/activate <секрет> [дней]` — ручное продление подписки на N дней (требует `SUBSCRIPTION_ACTIVATION_SECRET`).
- `/set_texts Заголовок | Подзаголовок | Основной текст` — обновить все тексты сразу.
- `/set_title <текст>` / `/set_subtitle <текст>` / `/set_body <текст>` — изменить конкретное поле.
- `/help` — список команд.

Все данные сохраняются в `data.json` (или файл, указанный в `TEXTS_FILE`). Структура:
```json
{
  "users": {
    "<telegram_chat_id>": {
      "token": "...",
      "texts": {"title": "...", "subtitle": "...", "body": "..."},
      "subscriptionExpiresAt": "2024-12-31T23:59:59.000Z",
      "updatedAt": "2024-01-01T00:00:00.000Z"
    }
  },
  "tokens": {
    "<token>": "<telegram_chat_id>"
  }
}
```

## Android-приложение

### Возможности

- Экран ввода токена с подсказками и кнопкой для перехода к боту.
- Хранение токена в DataStore и автоматический повторный запрос при запуске.
- Экран с текстами и датой окончания подписки.
- Обработка ошибок: недействительный токен, истёкшая подписка, сетевые проблемы.

### Конфигурация

Файл `android-app/app/build.gradle.kts` содержит две ключевые переменные окружения:

```kotlin
defaultConfig {
    buildConfigField("String", "TEXTS_API_BASE_URL", "\"https://your-server.example.com\"")
    buildConfigField("String", "TELEGRAM_BOT_URL", "\"https://t.me/your_bot_username\"")
}
```

- `TEXTS_API_BASE_URL` — базовый URL для API. Для эмулятора можно использовать `http://10.0.2.2:3000` (уже прописано в `debug`-сбор
ке).
- `TELEGRAM_BOT_URL` — ссылка, которая открывается из приложения, если сервер не вернул собственную.

### Сборка APK

1. Установите Android Studio (желательно Hedgehog или новее).
2. Откройте каталог `android-app` как проект.
3. Проверьте `local.properties` и путь до Android SDK.
4. Настройте значения `TEXTS_API_BASE_URL` и `TELEGRAM_BOT_URL`.
5. Соберите проект через меню `Build > Make Project` или командой:
   ```bash
   ./gradlew assembleRelease
   ```
6. Готовый APK находится в `android-app/app/build/outputs/apk/release/`.

## Тестирование

- Для сервера можно выполнить синтаксическую проверку:
  ```bash
  node --check index.js
  ```
- Для клиента рекомендуется запускать `./gradlew lint` или `./gradlew assembleDebug` в среде с установленными Android SDK и плаги
нами.

## Лицензия

Проект распространяется под лицензией MIT.
