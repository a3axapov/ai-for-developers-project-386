import { expect, test } from './real-server';
import type { APIRequestContext, Page } from '@playwright/test';

test.use({
  fixedNow: '2026-11-01T00:00:00Z',
  timezoneId: 'Europe/Moscow',
  serverArguments: ['--calendar.initial.time-zone=Europe/Moscow'],
});

async function seed(request: APIRequestContext, requiresApproval = false) {
  for (const [id, title] of [
    ['Consult', 'Консультация'],
    ['Other', 'Другая встреча'],
  ]) {
    expect(
      (
        await request.post('/api/owner/meeting-types', {
          headers: { 'Idempotency-Key': id },
          data: {
            id,
            title,
            description: 'Подробное описание нашей консультации',
            durationMinutes: 30,
            requiresApproval,
          },
        })
      ).status(),
    ).toBe(201);
  }
  expect(
    (
      await request.put('/api/owner/public-booking', {
        headers: { 'Idempotency-Key': 'open' },
        data: { enabled: true, expectedVersion: 1 },
      })
    ).status(),
  ).toBe(200);
}

async function choose(page: Page) {
  await page.getByRole('button', { name: '02.11.2026', exact: true }).click();
  await page
    .getByRole('button', {
      name: '09:00 UTC+03:00 — 09:30 UTC+03:00',
      exact: true,
    })
    .click();
}

async function fill(page: Page) {
  await page.getByLabel('Ваше имя').fill('  Анна   李  ');
  await page.getByLabel('Email').fill('Guest+tag@Example.com');
  await page.getByLabel('Комментарий').fill('Первый вопрос\nВторая строка');
}

test('потеря настоящего ответа подготовки не бронирует время; повтор безопасен, ввод сохранён', async ({
  page,
  request,
}) => {
  await seed(request);
  const before = await (
    await request.get('/api/public/anna-1/meeting-types/Consult/slots')
  ).json();
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  let preparedSecret = '';
  let calls = 0;
  page.on('request', (request) => {
    if (request.url().endsWith('/booking-attempts')) calls += 1;
  });
  await page.route('**/booking-attempts', async (route) => {
    const response = await route.fetch();
    expect(response.status()).toBe(201);
    preparedSecret = (await response.json()).attemptSecret;
    await gate;
    await route.abort('connectionreset');
  });
  await page.goto('/booking/anna-1/Consult');
  await choose(page);
  await fill(page);
  await page.getByRole('button', { name: 'Подготовить попытку' }).click();
  try {
    await expect(
      page.getByText('Подготовка попытки…', { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole('button', { name: 'Подготовить попытку' }),
    ).toBeDisabled();
    await expect(page.getByLabel('Ваше имя')).toBeDisabled();
  } finally {
    release();
  }
  await expect(page.getByRole('alert')).toContainText(
    'Не удалось получить ответ подготовки. Бронирование не создавалось; можно повторить подготовку.',
  );
  expect(calls).toBe(1);
  expect(
    await (
      await request.get('/api/public/anna-1/meeting-types/Consult/slots')
    ).json(),
  ).toEqual(before);
  await expect(page.getByLabel('Ваше имя')).toHaveValue('  Анна   李  ');
  await expect(page.getByLabel('Email')).toHaveValue('Guest+tag@Example.com');
  await expect(page).toHaveURL('/booking/anna-1/Consult');
  expect(
    await page.evaluate(
      (secret) =>
        JSON.stringify([localStorage, sessionStorage]).includes(secret),
      preparedSecret,
    ),
  ).toBe(false);
  await page.unroute('**/booking-attempts');
  await page.getByRole('button', { name: 'Подготовить попытку' }).click();
  await expect(
    page.getByText(
      'Попытка подготовлена. Бронирование ещё не создано; время не занято.',
    ),
  ).toBeVisible();
  expect(calls).toBe(2);
  expect(
    await (
      await request.get('/api/public/anna-1/meeting-types/Consult/slots')
    ).json(),
  ).toEqual(before);
});

test('смена username перед подготовкой показывает ненайденный старый адрес без перенаправления', async ({
  page,
  request,
}) => {
  await seed(request);
  await page.goto('/booking/anna-1/Consult');
  await choose(page);
  await fill(page);
  expect(
    (
      await request.put('/api/owner/profile', {
        headers: { 'Idempotency-Key': 'rename' },
        data: {
          name: 'Анна Петрова',
          username: 'changed-owner',
          expectedVersion: 2,
        },
      })
    ).status(),
  ).toBe(200);
  await page.getByRole('button', { name: 'Подготовить попытку' }).click();
  await expect(
    page.getByRole('heading', { name: 'Страница не найдена' }),
  ).toBeVisible();
  await expect(page).toHaveURL('/booking/anna-1/Consult');
  await expect(
    page.getByRole('button', { name: 'Подготовить попытку' }),
  ).toHaveCount(0);
});

test('серверные ошибки связаны с полями и сохраняют ввод для исправления', async ({
  page,
  request,
}) => {
  await seed(request);
  await page.goto('/booking/anna-1/Consult');
  await choose(page);
  await page.getByLabel('Ваше имя').fill('   ');
  await page.getByLabel('Email').fill('not-an-address');
  await page.getByLabel('Комментарий').fill('Вопрос\tс табуляцией');
  const response = page.waitForResponse((response) =>
    response.url().endsWith('/booking-attempts'),
  );
  await page.getByRole('button', { name: 'Подготовить попытку' }).click();
  expect((await response).status()).toBe(422);
  for (const label of ['Ваше имя', 'Email', 'Комментарий']) {
    await expect(page.getByLabel(label)).toHaveAttribute(
      'aria-invalid',
      'true',
    );
    await expect(page.getByLabel(label)).toHaveAccessibleDescription(/Введите/);
  }
  await expect(page.getByLabel('Ваше имя')).toHaveValue('   ');
  await expect(page.getByLabel('Email')).toHaveValue('not-an-address');
  await expect(page.getByLabel('Комментарий')).toHaveValue(
    'Вопрос\tс табуляцией',
  );
  await fill(page);
  await page.getByRole('button', { name: 'Подготовить попытку' }).click();
  await expect(
    page.getByText(
      'Попытка подготовлена. Бронирование ещё не создано; время не занято.',
    ),
  ).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
});

test('отключение записи скрывает подготовку; явное обновление после включения сохраняет контакты и сбрасывает время', async ({
  page,
  request,
}) => {
  await seed(request);
  await page.goto('/booking/anna-1/Consult');
  await choose(page);
  await fill(page);
  expect(
    (
      await request.put('/api/owner/public-booking', {
        headers: { 'Idempotency-Key': 'close' },
        data: { enabled: false, expectedVersion: 2 },
      })
    ).status(),
  ).toBe(200);
  await page.getByRole('button', { name: 'Подготовить попытку' }).click();
  await expect(
    page.getByText('Запись временно недоступна', { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole('button', { name: 'Подготовить попытку' }),
  ).toHaveCount(0);
  expect(
    (
      await request.put('/api/owner/public-booking', {
        headers: { 'Idempotency-Key': 'reopen' },
        data: { enabled: true, expectedVersion: 3 },
      })
    ).status(),
  ).toBe(200);
  await page.getByRole('button', { name: 'Обновить доступность' }).click();
  await expect(page.getByRole('status')).toHaveCount(0);
  await choose(page);
  await expect(page.getByLabel('Ваше имя')).toHaveValue('  Анна   李  ');
  await expect(page.getByLabel('Email')).toHaveValue('Guest+tag@Example.com');
  await expect(page.getByLabel('Комментарий')).toHaveValue(
    'Первый вопрос\nВторая строка',
  );
});

test('контакты сохраняются при смене времени, даты, пояса и типа; тип сбрасывает время', async ({
  page,
  request,
}) => {
  await seed(request);
  await page.goto('/booking/anna-1/Consult');
  await choose(page);
  await fill(page);
  await page
    .getByRole('button', {
      name: '09:15 UTC+03:00 — 09:45 UTC+03:00',
      exact: true,
    })
    .click();
  await expect(page.getByLabel('Ваше имя')).toHaveValue('  Анна   李  ');
  await page.getByLabel('Часовой пояс').selectOption('UTC');
  await expect(page.getByLabel('Email')).toHaveValue('Guest+tag@Example.com');
  await page.getByRole('button', { name: '03.11.2026', exact: true }).click();
  await expect(
    page.getByRole('button', { name: 'Подготовить попытку' }),
  ).toHaveCount(0);
  await page
    .getByRole('button', {
      name: '06:00 UTC+00:00 — 06:30 UTC+00:00',
      exact: true,
    })
    .click();
  await expect(page.getByLabel('Комментарий')).toHaveValue(
    'Первый вопрос\nВторая строка',
  );
  await page.getByRole('link', { name: 'Все типы встреч' }).click();
  await page
    .getByRole('link', { name: 'Выбрать тип «Другая встреча»' })
    .click();
  await expect(page.getByRole('status')).toHaveCount(0);
  await choose(page);
  await expect(page.getByLabel('Ваше имя')).toHaveValue('  Анна   李  ');
  await expect(page.getByLabel('Email')).toHaveValue('Guest+tag@Example.com');
  await expect(page.getByLabel('Комментарий')).toHaveValue(
    'Первый вопрос\nВторая строка',
  );
  await page.goBack();
  await page.goBack();
  await choose(page);
  await expect(page.getByLabel('Email')).toHaveValue('Guest+tag@Example.com');
});

test('гость проверяет условия и готовит попытку без бронирования и секрета в URL', async ({
  page,
  request,
}) => {
  await seed(request, true);
  const before = await (
    await request.get('/api/public/anna-1/meeting-types/Consult/slots')
  ).json();
  await page.goto('/booking/anna-1/Consult');
  await choose(page);
  await expect(
    page.getByRole('heading', { name: 'Анна Петрова' }),
  ).toBeVisible();
  await expect(
    page.getByRole('heading', { name: 'Консультация', exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText('Подробное описание нашей консультации'),
  ).toBeVisible();
  await expect(page.getByText('30 мин', { exact: true })).toBeVisible();
  await expect(page.getByText('Требуется ручное подтверждение')).toBeVisible();
  await expect(page.getByRole('status')).toContainText(
    '02.11.2026, 09:00 UTC+03:00 — 02.11.2026, 09:30 UTC+03:00 (Europe/Moscow)',
  );
  await fill(page);
  const response = page.waitForResponse(
    (response) =>
      response.url().endsWith('/booking-attempts') &&
      response.request().method() === 'POST',
  );
  const prepare = page.getByRole('button', { name: 'Подготовить попытку' });
  await prepare.focus();
  await page.keyboard.press('Enter');
  const actual = await response;
  expect(actual.status()).toBe(201);
  const credentials = await actual.json();
  expect(actual.request().postDataJSON()).toEqual({
    meetingTypeId: 'Consult',
    conditionsToken: before.conditionsToken,
    startsAt: '2026-11-02T09:00:00+03:00',
    guestName: '  Анна   李  ',
    guestEmail: 'Guest+tag@Example.com',
    comment: 'Первый вопрос\nВторая строка',
  });
  await expect(
    page.getByText(
      'Попытка подготовлена. Бронирование ещё не создано; время не занято.',
    ),
  ).toBeVisible();
  await expect(page).toHaveURL('/booking/anna-1/Consult');
  await expect(
    page.getByText(credentials.attemptSecret, { exact: false }),
  ).toHaveCount(0);
  expect(
    await (
      await request.get('/api/public/anna-1/meeting-types/Consult/slots')
    ).json(),
  ).toEqual(before);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});
