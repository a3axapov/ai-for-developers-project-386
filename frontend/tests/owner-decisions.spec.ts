import { expect, test } from './real-server';
import type { APIRequestContext, Page } from '@playwright/test';

test.use({
  fixedNow: '2026-11-01T00:00:00Z',
  serverArguments: [
    '--calendar.initial.time-zone=Europe/Moscow',
    '--calendar.initial.booking-enabled=true',
  ],
});

async function book(request: APIRequestContext) {
  expect(
    (
      await request.post('/api/owner/meeting-types', {
        headers: { 'Idempotency-Key': 'seed' },
        data: {
          id: 'Manual',
          title: 'Консультация Manual',
          description: 'Сохранённое подробное описание консультации',
          durationMinutes: 30,
          requiresApproval: true,
        },
      })
    ).status(),
  ).toBe(201);
  const slots = await request.get(
    '/api/public/anna-1/meeting-types/Manual/slots',
  );
  const prepared = await request.post('/api/public/anna-1/booking-attempts', {
    data: {
      meetingTypeId: 'Manual',
      conditionsToken: (await slots.json()).conditionsToken,
      startsAt: '2026-11-02T09:00:00+03:00',
      guestName: 'Гость',
      guestEmail: 'guest@example.com',
    },
  });
  expect(prepared.status()).toBe(201);
  const attempt = await prepared.json();
  const submitted = await request.post(
    `/api/booking-attempts/${attempt.attemptId}/submit`,
    { headers: { Authorization: `Bearer ${attempt.attemptSecret}` } },
  );
  expect(submitted.status()).toBe(201);
  return submitted.json();
}

async function openDetails(page: Page) {
  await page.goto('/admin');
  const bookings = page.getByRole('region', { name: 'Бронирования владельца' });
  await bookings
    .getByRole('button', { name: 'Открыть Консультация Manual' })
    .click();
  const details = bookings.getByRole('region', { name: 'Детали бронирования' });
  await expect(details.getByText('Версия: 1', { exact: true })).toBeVisible();
  return { bookings, details };
}

test('владелец явно подтверждает запрос, гость обновляет результат без изменения снимка', async ({
  page,
  context,
  request,
}) => {
  const saved = await book(request);
  const guest = await context.newPage();
  await guest.goto(saved.resultUrl);
  await expect(
    guest.getByText('Ожидает подтверждения', { exact: true }),
  ).toBeVisible();
  const { bookings, details } = await openDetails(page);
  const confirm = details.getByRole('button', {
    name: 'Подтвердить запрос',
    exact: true,
  });
  await confirm.focus();
  await page.keyboard.press('Enter');
  await expect(
    details.getByText('Подтверждено', { exact: true }),
  ).toBeVisible();
  await expect(details.getByText('Версия: 2', { exact: true })).toBeVisible();
  await expect(
    details.getByText('Требуется ручное подтверждение'),
  ).toBeVisible();
  await expect(bookings.getByRole('list')).toContainText('Подтверждено');
  await expect(
    guest.getByText('Ожидает подтверждения', { exact: true }),
  ).toBeVisible();
  await guest.getByRole('button', { name: 'Обновить сведения' }).click();
  await expect(guest.getByText('Подтверждено', { exact: true })).toBeVisible();
  await expect(guest).toHaveURL(saved.resultUrl);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});

test('отклонение требует предупреждения, отмена диалога ничего не меняет, гость обновляет итог', async ({
  page,
  context,
  request,
}) => {
  const saved = await book(request);
  const guest = await context.newPage();
  await guest.goto(saved.resultUrl);
  const { bookings, details } = await openDetails(page);
  let commands = 0;
  page.on('request', (request) => {
    if (request.method() === 'POST' && request.url().endsWith('/reject'))
      commands++;
  });
  page.once('dialog', async (dialog) => {
    expect(dialog.message()).toContain('освободит');
    expect(dialog.message()).toContain('невозможно');
    expect(dialog.message()).toContain('не гарантирует');
    await dialog.dismiss();
  });
  const reject = details.getByRole('button', {
    name: 'Отклонить запрос',
    exact: true,
  });
  await reject.click();
  await expect(details.getByText('Версия: 1', { exact: true })).toBeVisible();
  expect(commands).toBe(0);
  page.once('dialog', (dialog) => dialog.accept());
  await reject.focus();
  await page.keyboard.press('Enter');
  await expect(details.getByText('Отклонено', { exact: true })).toBeVisible();
  await expect(details.getByText('Версия: 2', { exact: true })).toBeVisible();
  expect(commands).toBe(1);
  await expect(bookings.getByRole('list').getByRole('listitem')).toHaveCount(0);
  await bookings
    .getByLabel('Представление бронирований')
    .selectOption('history');
  await expect(bookings.getByRole('list')).toContainText('Отклонено');
  await guest.getByRole('button', { name: 'Обновить сведения' }).click();
  await expect(guest.getByText('Отклонено', { exact: true })).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});

for (const action of ['confirm', 'reject'] as const) {
  test(`потерянный ответ ${action} блокирует изменения до чтения сохранённой операции`, async ({
    page,
    request,
  }) => {
    const saved = await book(request);
    const { bookings, details } = await openDetails(page);
    let commands = 0;
    page.on('request', (request) => {
      if (
        request.method() === 'POST' &&
        /\/(confirm|reject)$/.test(request.url())
      )
        commands++;
    });
    await page.route(`**/api/owner/bookings/*/${action}`, async (route) => {
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      await route.abort('failed');
    });
    if (action === 'reject') page.once('dialog', (dialog) => dialog.accept());
    await details
      .getByRole('button', {
        name: action === 'confirm' ? 'Подтвердить запрос' : 'Отклонить запрос',
        exact: true,
      })
      .click();
    await expect(bookings.getByRole('alert')).toContainText(
      'Исход операции неизвестен',
    );
    await expect(details.getByText('Версия: 1', { exact: true })).toBeVisible();
    await expect(
      details.getByRole('button', { name: 'Подтвердить запрос' }),
    ).toBeDisabled();
    await expect(
      details.getByRole('button', { name: 'Отклонить запрос' }),
    ).toBeDisabled();
    await expect(
      page.getByRole('button', { name: 'Сохранить профиль' }),
    ).toBeDisabled();
    await expect(
      page.getByRole('button', { name: 'Создать тип встречи', exact: true }),
    ).toBeDisabled();
    await page.route('**/api/owner/operations/*', async (route) => {
      await route.fetch();
      await route.abort('failed');
    });
    const recover = bookings.getByRole('button', {
      name: 'Выяснить результат решения',
    });
    await recover.click();
    await expect(bookings.getByRole('alert')).toContainText(
      'Исход операции неизвестен',
    );
    await expect(details.getByText('Версия: 1', { exact: true })).toBeVisible();
    await page.unroute('**/api/owner/operations/*');
    await recover.focus();
    await page.keyboard.press('Enter');
    await expect(details.getByText('Версия: 2', { exact: true })).toBeVisible();
    await expect(
      details.getByText(action === 'confirm' ? 'Подтверждено' : 'Отклонено', {
        exact: true,
      }),
    ).toBeVisible();
    expect(commands).toBe(1);
    const current = await request.get(
      `/api/owner/bookings/${saved.booking.id}`,
    );
    expect((await current.json()).version).toBe(2);
  });
}

test('решение в другой вкладке не позволяет отклонить устаревшую запись', async ({
  page,
  context,
  request,
}) => {
  const saved = await book(request);
  const { bookings, details } = await openDetails(page);
  const second = await context.newPage();
  const other = await openDetails(second);
  await other.details
    .getByRole('button', { name: 'Подтвердить запрос' })
    .click();
  await expect(
    other.details.getByText('Версия: 2', { exact: true }),
  ).toBeVisible();
  await expect(details.getByText('Версия: 1', { exact: true })).toBeVisible();
  page.once('dialog', (dialog) => dialog.accept());
  await details.getByRole('button', { name: 'Отклонить запрос' }).click();
  await expect(bookings.getByRole('alert')).toContainText(
    'Бронирование изменилось',
  );
  await expect(
    details.getByText('Подтверждено', { exact: true }),
  ).toBeVisible();
  await expect(details.getByText('Версия: 2', { exact: true })).toBeVisible();
  await expect(
    details.getByRole('button', { name: 'Отклонить запрос' }),
  ).toHaveCount(0);
  const current = await request.get(`/api/owner/bookings/${saved.booking.id}`);
  expect((await current.json()).status).toBe('CONFIRMED');
});

test('начало встречи истекает атомарно при решении из открытых деталей', async ({
  page,
  request,
  setServerNow,
}) => {
  await book(request);
  const { bookings, details } = await openDetails(page);
  await setServerNow('2026-11-02T06:00:00Z');
  await details.getByRole('button', { name: 'Подтвердить запрос' }).click();
  await expect(bookings.getByRole('alert')).toContainText(
    'Бронирование изменилось',
  );
  await expect(details.getByText('Отклонено', { exact: true })).toBeVisible();
  await expect(
    details.getByText('Владелец не подтвердил запрос до начала встречи.'),
  ).toBeVisible();
  await expect(
    details.getByRole('button', { name: 'Подтвердить запрос' }),
  ).toHaveCount(0);
});

test('сброс сервера после потерянного ответа требует явной загрузки, решение не повторяется', async ({
  page,
  request,
  restartServer,
}) => {
  await book(request);
  const { bookings, details } = await openDetails(page);
  let commands = 0;
  page.on('request', (request) => {
    if (request.method() === 'POST' && request.url().endsWith('/confirm'))
      commands++;
  });
  await page.route('**/api/owner/bookings/*/confirm', async (route) => {
    await route.fetch();
    await route.abort('failed');
  });
  await details.getByRole('button', { name: 'Подтвердить запрос' }).click();
  await expect(bookings.getByRole('alert')).toContainText(
    'Исход операции неизвестен',
  );
  await restartServer();
  await bookings
    .getByRole('button', { name: 'Выяснить результат решения' })
    .click();
  await expect(bookings.getByRole('alert')).toContainText(
    'Возможен сброс данных',
  );
  await expect(
    details.getByRole('button', { name: 'Подтвердить запрос' }),
  ).toBeDisabled();
  await bookings
    .getByRole('button', { name: 'Загрузить актуальные бронирования' })
    .click();
  await expect(
    details.getByText('Бронирование не найдено', { exact: true }),
  ).toBeVisible();
  await expect(bookings.getByRole('list').getByRole('listitem')).toHaveCount(0);
  expect(commands).toBe(1);
  expect(
    (await (await request.get('/api/owner/bookings?view=upcoming')).json())
      .items,
  ).toHaveLength(0);
});

test('потерянный определённый отказ восстанавливается чтением без повторного решения', async ({
  page,
  request,
  setServerNow,
}) => {
  const saved = await book(request);
  const { bookings, details } = await openDetails(page);
  await setServerNow('2026-11-02T06:00:00Z');
  let commands = 0;
  await page.route('**/api/owner/bookings/*/confirm', async (route) => {
    commands++;
    const response = await route.fetch();
    expect(response.status()).toBe(409);
    await route.abort('failed');
  });
  await details.getByRole('button', { name: 'Подтвердить запрос' }).click();
  await expect(bookings.getByRole('alert')).toContainText(
    'Исход операции неизвестен',
  );
  await expect(details.getByText('Версия: 1', { exact: true })).toBeVisible();
  await bookings
    .getByRole('button', { name: 'Выяснить результат решения' })
    .click();
  await expect(bookings.getByRole('alert')).toContainText(
    'Бронирование изменилось',
  );
  await expect(
    details.getByText('Владелец не подтвердил запрос до начала встречи.'),
  ).toBeVisible();
  await expect(details.getByText('Версия: 2', { exact: true })).toBeVisible();
  expect(commands).toBe(1);
  const current = await request.get(`/api/owner/bookings/${saved.booking.id}`);
  expect((await current.json()).status).toBe('REJECTED');
});

test('недоставленная команда не повторяется при выяснении и явной загрузке актуальной записи', async ({
  page,
  request,
}) => {
  await book(request);
  const { bookings, details } = await openDetails(page);
  let commands = 0;
  page.on('request', (request) => {
    if (request.method() === 'POST' && request.url().endsWith('/confirm'))
      commands++;
  });
  // Drop the request before delivery. The real server has no saved operation.
  await page.route('**/api/owner/bookings/*/confirm', (route) =>
    route.abort('failed'),
  );
  await details.getByRole('button', { name: 'Подтвердить запрос' }).click();
  await expect(bookings.getByRole('alert')).toContainText(
    'Исход операции неизвестен',
  );
  await bookings
    .getByRole('button', { name: 'Выяснить результат решения' })
    .click();
  await expect(bookings.getByRole('alert')).toContainText(
    'Возможен сброс данных',
  );
  await bookings
    .getByRole('button', { name: 'Загрузить актуальные бронирования' })
    .click();
  await expect(details.getByText('Версия: 1', { exact: true })).toBeVisible();
  await expect(
    details.getByText('Ожидает подтверждения', { exact: true }),
  ).toBeVisible();
  expect(commands).toBe(1);
  await page.unroute('**/api/owner/bookings/*/confirm');
  await details.getByRole('button', { name: 'Подтвердить запрос' }).click();
  await expect(details.getByText('Версия: 2', { exact: true })).toBeVisible();
  expect(commands).toBe(2);
});
