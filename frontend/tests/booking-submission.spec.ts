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

for (const loseRefusal of [false, true]) {
  test(`конфликт настоящей записи снимает выбор, обновляет календарь и сохраняет контакты; потеря отказа: ${loseRefusal}`, async ({
    page,
    request,
  }) => {
    await seed(request);
    const preparedResponse = page.waitForResponse((response) =>
      response.url().endsWith('/booking-attempts'),
    );
    await fillAndPrepare(page);
    const preparedRequest = (await preparedResponse).request().postDataJSON();
    const competitor = await request.post(
      '/api/public/anna-1/booking-attempts',
      {
        data: preparedRequest,
      },
    );
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
    if (loseRefusal) {
      await page.route('**/booking-attempts/*/submit', async (route) => {
        const response = await route.fetch();
        expect(response.status()).toBe(409);
        expect((await response.json()).code).toBe('TIME_CONFLICT');
        await route.abort('connectionreset');
      });
    }
    await page.getByRole('button', { name: 'Записаться', exact: true }).click();
    if (loseRefusal) {
      await expect(page.getByRole('alert')).toContainText(
        'Исход отправки неизвестен',
      );
      await page.unroute('**/booking-attempts/*/submit');
      await page
        .getByRole('button', { name: 'Выяснить результат записи' })
        .click();
    }
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
}

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
  expect(
    (
      await request.put('/api/owner/profile', {
        headers: { 'Idempotency-Key': 'rename-after-loss' },
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
        headers: { 'Idempotency-Key': 'disable-after-loss' },
        data: { enabled: false, expectedVersion: 3 },
      })
    ).status(),
  ).toBe(200);
  await expect(page.getByRole('alert')).toContainText(
    'Не удалось узнать результат записи',
  );
  await page.route('**/api/booking-attempts/*', async (route) => {
    expect((await route.fetch()).status()).toBe(200);
    await route.abort('connectionreset');
  });
  await page.getByRole('button', { name: 'Выяснить результат записи' }).click();
  await expect(page.getByRole('alert')).toContainText(
    'Исход отправки неизвестен',
  );
  await expect(page.getByLabel('Email')).not.toBeEditable();
  expect(prepares).toBe(0);
  await page.unroute('**/api/booking-attempts/*');
  await page.getByRole('button', { name: 'Выяснить результат записи' }).focus();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(savedResult!.resultUrl);
  await expect(page.getByText('Подтверждено', { exact: true })).toBeVisible();
  await expect(page.getByText('Новое имя', { exact: true })).toBeVisible();
  expect(prepares).toBe(0);
});

test('неотправленный запрос выясняется как PREPARED и повторяется только той же попыткой', async ({
  page,
  request,
}) => {
  await seed(request);
  await fillAndPrepare(page);
  let originalUrl = '';
  let originalSecret = '';
  let submits = 0;
  await page.route('**/booking-attempts/*/submit', async (route) => {
    submits++;
    originalUrl = route.request().url();
    originalSecret = route.request().headers().authorization;
    await route.abort('connectionreset');
  });
  await page.getByRole('button', { name: 'Записаться', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText(
    'Исход отправки неизвестен',
  );
  await page.unroute('**/booking-attempts/*/submit');
  await page.getByRole('button', { name: 'Выяснить результат записи' }).click();
  await expect(
    page.getByText(
      'Бронирование ещё не создано. Можно повторить отправку той же попытки.',
    ),
  ).toBeVisible();
  await expect(page.getByLabel('Email')).not.toBeEditable();
  expect(submits).toBe(1);
  const response = page.waitForResponse(
    (response) => response.url() === originalUrl,
  );
  await page
    .getByRole('button', { name: 'Повторить отправку той же попытки' })
    .click();
  const sent = await response;
  expect(sent.status()).toBe(201);
  expect(sent.request().headers().authorization).toBe(originalSecret);
  await expect(page.getByText('Подтверждено', { exact: true })).toBeVisible();
  const replay = await request.post(originalUrl, {
    headers: { Authorization: originalSecret },
  });
  expect(replay.status()).toBe(200);
  await expect(page).toHaveURL((await replay.json()).resultUrl);
});

for (const retryPrepared of [false, true]) {
  test(`сброс настоящего сервера требует осознанной новой записи; повтор отправки: ${retryPrepared}`, async ({
    page,
    request,
    restartServer,
  }) => {
    await seed(request);
    await fillAndPrepare(page);
    let prepares = 0;
    let submits = 0;
    page.on('request', (request) => {
      if (request.url().endsWith('/booking-attempts')) prepares++;
      if (request.url().endsWith('/submit')) submits++;
    });
    await page.route('**/booking-attempts/*/submit', async (route) => {
      if (!retryPrepared) expect((await route.fetch()).status()).toBe(201);
      await route.abort('connectionreset');
    });
    await page.getByRole('button', { name: 'Записаться', exact: true }).click();
    await expect(page.getByRole('alert')).toContainText(
      'Исход отправки неизвестен',
    );
    await page.unroute('**/booking-attempts/*/submit');
    if (retryPrepared) {
      await page
        .getByRole('button', { name: 'Выяснить результат записи' })
        .click();
      await expect(
        page.getByRole('button', { name: 'Повторить отправку той же попытки' }),
      ).toBeVisible();
    }
    await restartServer();
    await seed(request);
    await page
      .getByRole('button', {
        name: retryPrepared
          ? 'Повторить отправку той же попытки'
          : 'Выяснить результат записи',
      })
      .click();
    await expect(page.getByRole('alert')).toContainText(
      'Возможен сброс данных',
    );
    await expect(page.getByLabel('Email')).toHaveValue('guest@example.com');
    await expect(page.getByLabel('Email')).not.toBeEditable();
    await expect(
      page.getByRole('button', { name: 'Подготовить попытку' }),
    ).toHaveCount(0);
    expect(prepares).toBe(0);
    expect(submits).toBe(retryPrepared ? 2 : 1);
    await page.getByRole('button', { name: 'Начать новую запись' }).click();
    await page.getByRole('button', { name: '02.11.2026', exact: true }).click();
    await page
      .getByRole('button', {
        name: '09:00 UTC+03:00 — 09:30 UTC+03:00',
        exact: true,
      })
      .click();
    await expect(page.getByLabel('Ваше имя')).toHaveValue('  Анна   李  ');
    await expect(page.getByLabel('Email')).toHaveValue('guest@example.com');
    expect(prepares).toBe(0);
    await page.getByRole('button', { name: 'Подготовить попытку' }).click();
    await page.getByRole('button', { name: 'Записаться', exact: true }).click();
    await expect(page.getByText('Подтверждено', { exact: true })).toBeVisible();
    expect(prepares).toBe(1);
    expect(submits).toBe(retryPrepared ? 3 : 2);
  });
}

for (const changed of ['disabled', 'username']) {
  test(`изменение ${changed} после подготовки блокирует старую отправку без потери контактов`, async ({
    page,
    request,
  }) => {
    await seed(request);
    await fillAndPrepare(page);
    if (changed === 'disabled') {
      expect(
        (
          await request.put('/api/owner/public-booking', {
            headers: { 'Idempotency-Key': 'close' },
            data: { enabled: false, expectedVersion: 2 },
          })
        ).status(),
      ).toBe(200);
    } else {
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
    }
    await page.getByRole('button', { name: 'Записаться', exact: true }).click();
    await expect(
      page.getByRole('button', { name: 'Записаться', exact: true }),
    ).toHaveCount(0);
    if (changed === 'username') {
      await expect(
        page.getByRole('heading', { name: 'Страница не найдена' }),
      ).toBeVisible();
      await expect(page).toHaveURL('/booking/anna-1/Consult');
      return;
    }
    await expect(page.getByRole('alert')).toHaveText(
      'Запись временно недоступна',
    );
    expect(
      (
        await request.put('/api/owner/public-booking', {
          headers: { 'Idempotency-Key': 'reopen' },
          data: { enabled: true, expectedVersion: 3 },
        })
      ).status(),
    ).toBe(200);
    await page.getByRole('button', { name: 'Обновить доступность' }).click();
    await page.getByRole('button', { name: '02.11.2026', exact: true }).click();
    await page
      .getByRole('button', {
        name: '09:00 UTC+03:00 — 09:30 UTC+03:00',
        exact: true,
      })
      .click();
    await expect(page.getByLabel('Email')).toHaveValue('guest@example.com');
    await expect(page.getByLabel('Ваше имя')).toHaveValue('  Анна   李  ');
    await expect(page.getByLabel('Комментарий')).toHaveValue(
      'Первый вопрос\nВторая строка',
    );
    await page.getByRole('button', { name: 'Подготовить попытку' }).click();
    await page.getByRole('button', { name: 'Записаться', exact: true }).click();
    await expect(page.getByText('Подтверждено', { exact: true })).toBeVisible();
  });
}
