import { expect, test } from './real-server';
import type { APIRequestContext, Page } from '@playwright/test';

test.use({
  fixedNow: '2026-11-01T00:00:00Z',
  timezoneId: 'Europe/Moscow',
  serverArguments: ['--calendar.initial.time-zone=Europe/Moscow'],
});

async function seed(request: APIRequestContext, requiresApproval = false) {
  expect(
    (
      await request.post('/api/owner/meeting-types', {
        headers: { 'Idempotency-Key': 'consult' },
        data: {
          id: 'Consult',
          title: 'Консультация',
          description: 'Подробное описание нашей консультации',
          durationMinutes: 30,
          requiresApproval,
        },
      })
    ).status(),
  ).toBe(201);
  expect(
    (
      await request.put('/api/owner/public-booking', {
        headers: { 'Idempotency-Key': 'open' },
        data: { enabled: true, expectedVersion: 1 },
      })
    ).status(),
  ).toBe(200);
}

async function fillAndPrepare(page: Page) {
  await page.goto('/booking/anna-1/Consult');
  await page.getByRole('button', { name: '02.11.2026', exact: true }).click();
  await page
    .getByRole('button', {
      name: '09:00 UTC+03:00 — 09:30 UTC+03:00',
      exact: true,
    })
    .click();
  await page.getByLabel('Ваше имя').fill('  Анна   李  ');
  await page.getByLabel('Email').fill('guest@example.com');
  await page.getByLabel('Комментарий').fill('Первый вопрос\nВторая строка');
  await page.getByRole('button', { name: 'Подготовить попытку' }).click();
  await expect(
    page.getByText(
      'Попытка подготовлена. Бронирование ещё не создано; время не занято.',
    ),
  ).toBeVisible();
}

for (const approval of [false, true]) {
  test(`явная запись и отдельный актуальный результат, ручное подтверждение: ${approval}`, async ({
    page,
    request,
    context,
  }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await seed(request, approval);
    await fillAndPrepare(page);
    const response = page.waitForResponse((response) =>
      response.url().endsWith('/submit'),
    );
    await page.getByRole('button', { name: 'Записаться', exact: true }).focus();
    await page.keyboard.press('Enter');
    const submitted = await response;
    expect(submitted.status()).toBe(201);
    expect(submitted.request().postData()).toBeNull();
    expect(submitted.request().headers().authorization).toMatch(
      /^Bearer [a-f0-9]{64}$/,
    );
    const replay = await request.post(submitted.url(), {
      headers: { Authorization: submitted.request().headers().authorization },
    });
    expect(replay.status()).toBe(200);
    const result = await replay.json();
    await expect(page).toHaveURL(result.resultUrl);
    await expect(
      page.getByRole('heading', { name: 'Результат бронирования' }),
    ).toBeVisible();
    await expect(
      page.getByText(approval ? 'Ожидает подтверждения' : 'Подтверждено', {
        exact: true,
      }),
    ).toBeVisible();
    await expect(page.getByText('Анна 李', { exact: true })).toBeVisible();
    await expect(
      page.getByText('guest@example.com', { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByText('Первый вопрос\nВторая строка', { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByText(
        '02.11.2026, 09:00 UTC+03:00 — 02.11.2026, 09:30 UTC+03:00',
        { exact: true },
      ),
    ).toBeVisible();
    await expect(page.getByText(/Любой обладатель ссылки/)).toBeVisible();
    await expect(page.getByText(/Уведомления не отправляются/)).toBeVisible();
    await page.getByLabel('Часовой пояс').selectOption('UTC');
    await expect(
      page.getByText(
        '02.11.2026, 06:00 UTC+00:00 — 02.11.2026, 06:30 UTC+00:00',
        { exact: true },
      ),
    ).toBeVisible();
    await page.getByRole('button', { name: 'Скопировать ссылку' }).click();
    expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(
      page.url(),
    );
    expect(
      (
        await request.put('/api/owner/profile', {
          headers: { 'Idempotency-Key': 'rename' },
          data: {
            name: 'Новое имя',
            username: 'changed-owner',
            expectedVersion: 2,
          },
        })
      ).status(),
    ).toBe(200);
    expect(
      (
        await request.put('/api/owner/public-booking', {
          headers: { 'Idempotency-Key': 'close' },
          data: { enabled: false, expectedVersion: 3 },
        })
      ).status(),
    ).toBe(200);
    await page.getByRole('button', { name: 'Обновить сведения' }).click();
    await expect(page.getByText('Новое имя', { exact: true })).toBeVisible();
    await expect(page.getByLabel('Часовой пояс')).toHaveValue('UTC');
    const documentResponse = await page.reload();
    expect(documentResponse?.headers()['cache-control']).toBe('no-store');
    expect(documentResponse?.headers()['referrer-policy']).toBe('no-referrer');
    await expect(page.getByText('Новое имя', { exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.referrer)).not.toContain(
      '/bookings/',
    );
    const healthRequest = page.waitForRequest((request) =>
      request.url().endsWith('/api/health'),
    );
    await page.evaluate(() => fetch('/api/health'));
    expect((await healthRequest).headers().referer).toBeFalsy();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    await page.goto('/bookings/unknown');
    await expect(
      page.getByRole('heading', { name: 'Бронирование не найдено' }),
    ).toBeVisible();
  });
}

test('конфликт настоящей записи снимает выбор, обновляет календарь и сохраняет контакты', async ({
  page,
  request,
}) => {
  await seed(request);
  const preparedResponse = page.waitForResponse((response) =>
    response.url().endsWith('/booking-attempts'),
  );
  await fillAndPrepare(page);
  const preparedRequest = (await preparedResponse).request().postDataJSON();
  const competitor = await request.post('/api/public/anna-1/booking-attempts', {
    data: preparedRequest,
  });
  expect(competitor.status()).toBe(201);
  const credentials = await competitor.json();
  expect(
    (
      await request.post(
        `/api/booking-attempts/${credentials.attemptId}/submit`,
        {
          headers: { Authorization: `Bearer ${credentials.attemptSecret}` },
        },
      )
    ).status(),
  ).toBe(201);
  await page.getByRole('button', { name: 'Записаться', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Время уже занято');
  await expect(
    page.getByRole('button', { name: 'Записаться', exact: true }),
  ).toHaveCount(0);
  await page.getByRole('button', { name: '02.11.2026', exact: true }).click();
  await expect(
    page.getByRole('button', {
      name: '09:00 UTC+03:00 — 09:30 UTC+03:00',
      exact: true,
    }),
  ).toHaveCount(0);
  await page
    .getByRole('button', {
      name: '09:30 UTC+03:00 — 10:00 UTC+03:00',
      exact: true,
    })
    .click();
  await expect(page.getByLabel('Ваше имя')).toHaveValue('  Анна   李  ');
  await expect(page.getByLabel('Email')).toHaveValue('guest@example.com');
  await expect(page.getByLabel('Комментарий')).toHaveValue(
    'Первый вопрос\nВторая строка',
  );
  await page.getByRole('button', { name: 'Подготовить попытку' }).click();
  await page.getByRole('button', { name: 'Записаться', exact: true }).click();
  await expect(page.getByText('Подтверждено', { exact: true })).toBeVisible();
});

test('потеря настоящего ответа отправки не объявляет успех и блокирует изменённую новую попытку', async ({
  page,
  request,
}) => {
  await seed(request);
  await fillAndPrepare(page);
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  let savedResult: { resultUrl: string; booking: { id: string } } | undefined;
  let secret = '';
  let attemptUrl = '';
  let prepares = 0;
  page.on('request', (request) => {
    if (request.url().endsWith('/booking-attempts')) prepares++;
  });
  await page.route('**/booking-attempts/*/submit', async (route) => {
    secret = route.request().headers().authorization;
    attemptUrl = route.request().url();
    const response = await route.fetch();
    expect(response.status()).toBe(201);
    savedResult = await response.json();
    await gate;
    await route.abort('connectionreset');
  });
  await page.getByRole('button', { name: 'Записаться', exact: true }).click();
  try {
    await expect(
      page.getByText('Отправка записи…', { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole('button', { name: 'Подготовить попытку' }),
    ).toHaveCount(0);
    await expect(page.getByLabel('Ваше имя')).toHaveValue('  Анна   李  ');
    await expect(page.getByLabel('Ваше имя')).not.toBeEditable();
  } finally {
    release();
  }
  await expect(page.getByRole('alert')).toContainText(
    'Исход отправки неизвестен',
  );
  await expect(page.getByText('Подтверждено', { exact: true })).toHaveCount(0);
  await expect(page.getByLabel('Email')).toHaveValue('guest@example.com');
  await expect(
    page.getByRole('button', { name: 'Записаться', exact: true }),
  ).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Все типы встреч' })).toHaveCount(
    0,
  );
  await page.evaluate(() => {
    history.pushState(null, '', '/booking/anna-1');
    window.dispatchEvent(new PopStateEvent('popstate'));
  });
  await expect(page.getByRole('alert')).toContainText(
    'Исход отправки неизвестен',
  );
  await expect(
    page.getByRole('button', { name: 'Подготовить попытку' }),
  ).toHaveCount(0);
  expect(prepares).toBe(0);
  expect(savedResult).toBeDefined();
  expect(
    await page.evaluate(
      (value) => JSON.stringify([localStorage, sessionStorage]).includes(value),
      secret,
    ),
  ).toBe(false);
  const replay = await request.post(attemptUrl, {
    headers: { Authorization: secret },
  });
  expect(replay.status()).toBe(200);
  expect((await replay.json()).booking.id).toBe(savedResult?.booking.id);
});
