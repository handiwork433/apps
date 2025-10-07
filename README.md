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
