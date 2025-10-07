import express from 'express';
import fs from 'fs/promises';
import { existsSync } from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import crypto from 'crypto';
import TelegramBot from 'node-telegram-bot-api';
import dotenv from 'dotenv';

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = Number(process.env.PORT || 3000);
const BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN;
const BOT_LINK_OVERRIDE = process.env.TELEGRAM_BOT_LINK || '';
const DATA_FILE = process.env.TEXTS_FILE
  ? path.resolve(process.env.TEXTS_FILE)
  : path.join(__dirname, 'data.json');
const PAYMENT_PROVIDER_TOKEN = process.env.PAYMENT_PROVIDER_TOKEN || '';
const SUBSCRIPTION_PRICE = Number(process.env.SUBSCRIPTION_PRICE || 19900);
const SUBSCRIPTION_CURRENCY = process.env.SUBSCRIPTION_CURRENCY || 'RUB';
const SUBSCRIPTION_DURATION_DAYS = Number(
  process.env.SUBSCRIPTION_DURATION_DAYS || 30
);
const SUBSCRIPTION_ACTIVATION_SECRET =
  process.env.SUBSCRIPTION_ACTIVATION_SECRET || '';

if (!BOT_TOKEN) {
  throw new Error('TELEGRAM_BOT_TOKEN is required');
}

const DEFAULT_TEXTS = {
  title: 'Ваш заголовок появится здесь',
  subtitle: 'Обновите текст через Telegram-бота',
  body: 'После оплаты подписки вы сможете задать свои тексты командой /set_texts.'
};

let state = {
  users: {},
  tokens: {}
};
let resolvedBotLink = BOT_LINK_OVERRIDE;

function createEmptyUser() {
  return {
    token: crypto.randomUUID(),
    texts: { ...DEFAULT_TEXTS },
    subscriptionExpiresAt: null,
    updatedAt: null
  };
}

function normalizeState(raw) {
  const nextState = { users: {}, tokens: {} };
  if (!raw || typeof raw !== 'object') {
    return nextState;
  }

  const { users = {}, tokens = {} } = raw;
  Object.entries(users).forEach(([id, value]) => {
    if (!value || typeof value !== 'object') {
      return;
    }
    const user = createEmptyUser();
    user.token =
      typeof value.token === 'string' && value.token.length > 0
        ? value.token
        : crypto.randomUUID();
    user.texts = {
      ...DEFAULT_TEXTS,
      ...(value.texts && typeof value.texts === 'object' ? value.texts : {})
    };
    user.subscriptionExpiresAt = value.subscriptionExpiresAt || null;
    user.updatedAt = value.updatedAt || null;
    nextState.users[id] = user;
    nextState.tokens[user.token] = id;
  });

  Object.entries(tokens).forEach(([token, id]) => {
    if (!nextState.tokens[token] && nextState.users[id]) {
      nextState.tokens[token] = id;
    }
  });

  return nextState;
}

async function ensureDataFileDirectory() {
  const dir = path.dirname(DATA_FILE);
  await fs.mkdir(dir, { recursive: true });
}

async function loadState() {
  if (!existsSync(DATA_FILE)) {
    state = { users: {}, tokens: {} };
    await ensureDataFileDirectory();
    await saveState();
    return;
  }

  const raw = await fs.readFile(DATA_FILE, 'utf-8');
  const parsed = JSON.parse(raw);
  state = normalizeState(parsed);
}

async function saveState() {
  await ensureDataFileDirectory();
  const payload = JSON.stringify(state, null, 2);
  await fs.writeFile(DATA_FILE, payload, 'utf-8');
}

function isSubscriptionActive(user) {
  if (!user.subscriptionExpiresAt) return false;
  return new Date(user.subscriptionExpiresAt).getTime() > Date.now();
}

function extendSubscription(user, days = SUBSCRIPTION_DURATION_DAYS) {
  const start = isSubscriptionActive(user)
    ? new Date(user.subscriptionExpiresAt)
    : new Date();
  start.setUTCDate(start.getUTCDate() + days);
  user.subscriptionExpiresAt = start.toISOString();
}

async function ensureUser(chatId) {
  const id = String(chatId);
  if (!state.users[id]) {
    const user = createEmptyUser();
    state.users[id] = user;
    state.tokens[user.token] = id;
    await saveState();
  }
  return state.users[id];
}

function findUserByToken(token) {
  const userId = state.tokens[token];
  if (!userId) return null;
  const user = state.users[userId];
  if (!user) return null;
  return { user, userId };
}

function buildBotLink() {
  return resolvedBotLink || null;
}

await loadState();

const app = express();
app.use(express.json());

app.get('/texts', (req, res) => {
  const authHeader = req.get('authorization');
  const headerToken = authHeader?.startsWith('Bearer ')
    ? authHeader.substring('Bearer '.length).trim()
    : null;
  const queryToken = typeof req.query.token === 'string' ? req.query.token : null;
  const token = (headerToken || queryToken || '').trim();

  if (!token) {
    return res.status(400).json({
      error: 'Token is required',
      bot_link: buildBotLink()
    });
  }

  const found = findUserByToken(token);
  if (!found) {
    return res.status(401).json({
      error: 'Invalid token. Request a new one in the Telegram bot.',
      bot_link: buildBotLink()
    });
  }

  const { user } = found;
  if (!isSubscriptionActive(user)) {
    return res.status(403).json({
      error: 'Subscription is not active.',
      bot_link: buildBotLink(),
      subscription: {
        active: false,
        expires_at: user.subscriptionExpiresAt
      }
    });
  }

  return res.json({
    data: user.texts,
    last_updated: user.updatedAt,
    subscription: {
      active: true,
      expires_at: user.subscriptionExpiresAt
    }
  });
});

app.get('/health', (_req, res) => {
  res.json({
    status: 'ok',
    users: Object.keys(state.users).length,
    bot_link: buildBotLink()
  });
});

const bot = new TelegramBot(BOT_TOKEN, { polling: true });

bot.getMe()
  .then((me) => {
    if (!resolvedBotLink && me?.username) {
      resolvedBotLink = `https://t.me/${me.username}`;
    }
    console.log(`Bot @${me.username} is ready.`);
  })
  .catch((error) => {
    console.error('Failed to fetch bot info', error.message);
  });

function ensureActiveSubscription(chatId, user) {
  if (isSubscriptionActive(user)) {
    return true;
  }

  const link = buildBotLink();
  const message = link
    ? `Ваша подписка не активна. Оформите или продлите её в боте: ${link}`
    : 'Ваша подписка не активна. Оформите или продлите её в боте.';
  bot.sendMessage(chatId, message).catch((error) => {
    console.error('Failed to send inactive subscription message', error.message);
  });
  return false;
}

function parseTextsPayload(raw) {
  const parts = raw.split('|').map((part) => part.trim());
  if (parts.length !== 3) {
    throw new Error(
      'Формат: /set_texts Заголовок | Подзаголовок | Основной текст'
    );
  }
  return {
    title: parts[0],
    subtitle: parts[1],
    body: parts[2]
  };
}

async function updateTexts(chatId, updater) {
  const user = await ensureUser(chatId);
  if (!ensureActiveSubscription(chatId, user)) {
    return;
  }
  const nextTexts = updater(user.texts);
  user.texts = nextTexts;
  user.updatedAt = new Date().toISOString();
  await saveState();
  await bot.sendMessage(chatId, 'Тексты обновлены. Проверьте приложение.');
}

bot.onText(/\/start/, async (msg) => {
  const chatId = msg.chat.id;
  const user = await ensureUser(chatId);
  const link = buildBotLink();
  const instructions = [
    'Добро пожаловать! Используйте команды:',
    '/status — проверить статус подписки',
    '/my_token — получить токен для входа в приложение',
    '/set_texts Заголовок | Подзаголовок | Основной текст — обновить тексты',
    '/set_title <текст> — обновить только заголовок',
    '/set_subtitle <текст> — обновить подзаголовок',
    '/set_body <текст> — обновить основной текст'
  ];

  if (PAYMENT_PROVIDER_TOKEN) {
    instructions.unshift('/subscribe — оплатить подписку на месяц');
  } else {
    instructions.unshift('/subscribe — запросить оплату (требуется подключение платежей Telegram)');
  }

  const subscriptionInfo = isSubscriptionActive(user)
    ? `Подписка активна до ${user.subscriptionExpiresAt}`
    : 'Подписка не активна';

  const message = [
    instructions.join('\n'),
    '',
    `Ваш токен: ${user.token}`,
    subscriptionInfo,
    link ? `Ссылка на бота: ${link}` : null
  ]
    .filter(Boolean)
    .join('\n');

  await bot.sendMessage(chatId, message);
});

bot.onText(/\/help/, async (msg) => {
  const chatId = msg.chat.id;
  const link = buildBotLink();
  const message =
    'Доступные команды:\n' +
    ['/status', '/subscribe', '/my_token', '/set_texts', '/set_title', '/set_subtitle', '/set_body']
      .map((cmd) => `• ${cmd}`)
      .join('\n') +
    (link ? `\n\nСсылка на бота: ${link}` : '');
  await bot.sendMessage(chatId, message);
});

bot.onText(/\/status/, async (msg) => {
  const chatId = msg.chat.id;
  const user = await ensureUser(chatId);
  const active = isSubscriptionActive(user);
  const expires = user.subscriptionExpiresAt
    ? `до ${user.subscriptionExpiresAt}`
    : 'не оформлена';
  const message = `Подписка ${active ? 'активна' : 'не активна'} ${expires}.`;
  await bot.sendMessage(chatId, message);
});

bot.onText(/\/my_token/, async (msg) => {
  const chatId = msg.chat.id;
  const user = await ensureUser(chatId);
  await bot.sendMessage(chatId, `Ваш токен: ${user.token}`);
});

bot.onText(/\/subscribe/, async (msg) => {
  const chatId = msg.chat.id;
  if (!PAYMENT_PROVIDER_TOKEN) {
    const link = buildBotLink();
    await bot.sendMessage(
      chatId,
      link
        ? `Платёжный провайдер не настроен. Свяжитесь с администратором или используйте секретную команду для продления. Ссылка на бота: ${link}`
        : 'Платёжный провайдер не настроен. Свяжитесь с администратором или используйте секретную команду для продления.'
    );
    return;
  }

  const payload = `subscription-${Date.now()}`;
  await bot.sendInvoice(
    chatId,
    'Месячная подписка',
    'Доступ к персональному контенту в приложении на 30 дней.',
    payload,
    PAYMENT_PROVIDER_TOKEN,
    'subscription',
    SUBSCRIPTION_CURRENCY,
    [
      {
        label: '1 месяц',
        amount: SUBSCRIPTION_PRICE
      }
    ]
  );
});

bot.on('pre_checkout_query', async (query) => {
  try {
    await bot.answerPreCheckoutQuery(query.id, true);
  } catch (error) {
    console.error('Failed to answer pre checkout query', error.message);
  }
});

bot.on('successful_payment', async (msg) => {
  const chatId = msg.chat.id;
  const user = await ensureUser(chatId);
  extendSubscription(user);
  await saveState();
  await bot.sendMessage(
    chatId,
    `Оплата получена! Подписка активна до ${user.subscriptionExpiresAt}.`
  );
});

if (SUBSCRIPTION_ACTIVATION_SECRET) {
  bot.onText(/\/activate (.+)/, async (msg, match) => {
    const chatId = msg.chat.id;
    const args = match?.[1]?.trim()?.split(/\s+/) ?? [];
    if (args.length === 0) {
      await bot.sendMessage(
        chatId,
        'Формат: /activate <секрет> [дней] — вручную продлить подписку.'
      );
      return;
    }

    const [secret, maybeDays] = args;
    if (secret !== SUBSCRIPTION_ACTIVATION_SECRET) {
      await bot.sendMessage(chatId, 'Неверный секрет.');
      return;
    }

    const days = Number(maybeDays || SUBSCRIPTION_DURATION_DAYS);
    if (Number.isNaN(days) || days <= 0) {
      await bot.sendMessage(chatId, 'Число дней должно быть положительным.');
      return;
    }

    const user = await ensureUser(chatId);
    extendSubscription(user, days);
    await saveState();
    await bot.sendMessage(
      chatId,
      `Подписка продлена на ${days} дней до ${user.subscriptionExpiresAt}.`
    );
  });
}

bot.onText(/\/set_texts (.+)/, async (msg, match) => {
  const chatId = msg.chat.id;
  const payload = match?.[1];
  if (!payload) {
    await bot.sendMessage(
      chatId,
      'Формат: /set_texts Заголовок | Подзаголовок | Основной текст'
    );
    return;
  }

  try {
    const parsed = parseTextsPayload(payload);
    await updateTexts(chatId, () => parsed);
  } catch (error) {
    await bot.sendMessage(chatId, error.message);
  }
});

bot.onText(/\/set_title (.+)/, async (msg, match) => {
  const chatId = msg.chat.id;
  const value = match?.[1]?.trim();
  if (!value) {
    await bot.sendMessage(chatId, 'Формат: /set_title <текст>');
    return;
  }
  await updateTexts(chatId, (current) => ({ ...current, title: value }));
});

bot.onText(/\/set_subtitle (.+)/, async (msg, match) => {
  const chatId = msg.chat.id;
  const value = match?.[1]?.trim();
  if (!value) {
    await bot.sendMessage(chatId, 'Формат: /set_subtitle <текст>');
    return;
  }
  await updateTexts(chatId, (current) => ({ ...current, subtitle: value }));
});

bot.onText(/\/set_body (.+)/, async (msg, match) => {
  const chatId = msg.chat.id;
  const value = match?.[1]?.trim();
  if (!value) {
    await bot.sendMessage(chatId, 'Формат: /set_body <текст>');
    return;
  }
  await updateTexts(chatId, (current) => ({ ...current, body: value }));
});

bot.on('polling_error', (error) => {
  console.error('Polling error', error.message);
});

app.listen(PORT, () => {
  console.log(`Server is running on port ${PORT}`);
});
