import { expect, test } from './real-server';
import type { APIRequestContext, Page } from '@playwright/test';

test.use({
  fixedNow: '2026-11-01T00:00:00Z',
  serverArguments: [
    '--calendar.initial.time-zone=Europe/Moscow',
    '--calendar.initial.booking-enabled=true',
  ],
});

async function book(request: APIRequestContext, manual = false) {
  expect(
    (
      await request.post('/api/owner/meeting-types', {
        headers: { 'Idempotency-Key': 'seed' },
        data: {
          id: 'Meeting',
          title: 'Консультация',
          description: 'Сохранённое подробное описание консультации',
          durationMinutes: 30,
          requiresApproval: manual,
        },
      })
    ).status(),
  ).toBe(201);
  const slots = await request.get(
    '/api/public/anna-1/meeting-types/Meeting/slots',
  );
  const prepared = await request.post('/api/public/anna-1/booking-attempts', {
    data: {
      meetingTypeId: 'Meeting',
      conditionsToken: (await slots.json()).conditionsToken,
      startsAt: '2026-11-02T09:00:00+03:00',
      guestName: 'Гость',
      guestEmail: 'guest@example.com',
      comment: 'Сохранённый комментарий',
    },
  });
  expect(prepared.status()).toBe(201);
  const attempt = await prepared.json();
  const submitted = await request.post(
    `/api/booking-attempts/${attempt.attemptId}/submit`,
    {
      headers: { Authorization: `Bearer ${attempt.attemptSecret}` },
    },
  );
  expect(submitted.status()).toBe(201);
  return submitted.json();
}

async function openDetails(page: Page) {
  await page.goto('/admin');
  const bookings = page.getByRole('region', { name: 'Бронирования владельца' });
  await bookings.getByRole('button', { name: 'Открыть Консультация' }).click();
  const details = bookings.getByRole('region', { name: 'Детали бронирования' });
  await expect(details.getByText('Версия: 1', { exact: true })).toBeVisible();
  return { bookings, details };
}

test('осознанная отмена сохраняет историю, календарь и гостевой результат', async ({
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
    if (request.method() === 'POST' && request.url().endsWith('/cancel'))
      commands++;
  });
  const cancel = details.getByRole('button', {
    name: 'Отменить встречу',
    exact: true,
  });
  page.once('dialog', async (dialog) => {
    expect(dialog.message()).toContain('Консультация');
    expect(dialog.message()).toContain('Гость');
    expect(dialog.message()).toContain('09:00');
    expect(dialog.message()).toContain('Europe/Moscow');
    expect(dialog.message()).toContain('только');
    expect(dialog.message()).toContain('невозможно');
    expect(dialog.message()).toContain('не гарантирует');
    await dialog.dismiss();
  });
  await cancel.click();
  expect(commands).toBe(0);
  await expect(details.getByText('Версия: 1', { exact: true })).toBeVisible();
  page.once('dialog', (dialog) => dialog.accept());
  await cancel.focus();
  await page.keyboard.press('Enter');
  await expect(details.getByText('Отменено', { exact: true })).toBeVisible();
  await expect(details.getByText('Версия: 2', { exact: true })).toBeVisible();
  await expect(cancel).toHaveCount(0);
  expect(commands).toBe(1);
  await expect(bookings.getByRole('list').getByRole('listitem')).toHaveCount(0);
  await bookings
    .getByLabel('Представление бронирований')
    .selectOption('history');
  await expect(bookings.getByRole('list')).toContainText('Отменено');
  await expect(details.getByText('Сохранённый комментарий')).toBeVisible();
  await bookings
    .getByLabel('Представление бронирований')
    .selectOption('calendar');
  await bookings.getByLabel('Начало диапазона').fill('2026-11-02T06:00:00Z');
  await bookings.getByLabel('Конец диапазона').fill('2026-11-02T06:30:00Z');
  await bookings.getByRole('button', { name: 'Показать диапазон' }).click();
  await expect(bookings.getByRole('list')).toContainText('Отменено');
  await expect(guest.getByText('Подтверждено', { exact: true })).toBeVisible();
  await guest.getByRole('button', { name: 'Обновить сведения' }).click();
  await expect(guest.getByText('Отменено', { exact: true })).toBeVisible();
  await expect(guest).toHaveURL(saved.resultUrl);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  expect(
    await guest.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});

test('ожидающий запрос можно только подтвердить или отклонить, затем подтверждённую встречу отменить', async ({
  page,
  request,
}) => {
  await book(request, true);
  const { details } = await openDetails(page);
  await expect(
    details.getByRole('button', { name: 'Отменить встречу' }),
  ).toHaveCount(0);
  await details.getByRole('button', { name: 'Подтвердить запрос' }).click();
  await expect(details.getByText('Версия: 2', { exact: true })).toBeVisible();
  page.once('dialog', (dialog) => dialog.accept());
  await details.getByRole('button', { name: 'Отменить встречу' }).click();
  await expect(details.getByText('Отменено', { exact: true })).toBeVisible();
  await expect(details.getByText('Версия: 3', { exact: true })).toBeVisible();
  await expect(
    details.getByText('Требуется ручное подтверждение'),
  ).toBeVisible();
});

test('устаревшая отмена из другой вкладки показывает текущую запись без повторного изменения', async ({
  page,
  context,
  request,
}) => {
  const saved = await book(request);
  const { bookings, details } = await openDetails(page);
  const second = await context.newPage();
  const other = await openDetails(second);
  second.once('dialog', (dialog) => dialog.accept());
  await other.details.getByRole('button', { name: 'Отменить встречу' }).click();
  await expect(
    other.details.getByText('Версия: 2', { exact: true }),
  ).toBeVisible();
  await expect(details.getByText('Версия: 1', { exact: true })).toBeVisible();
  page.once('dialog', (dialog) => dialog.accept());
  await details.getByRole('button', { name: 'Отменить встречу' }).click();
  await expect(bookings.getByRole('alert')).toContainText(
    'Бронирование изменилось',
  );
  await expect(details.getByText('Отменено', { exact: true })).toBeVisible();
  await expect(details.getByText('Версия: 2', { exact: true })).toBeVisible();
  await expect(
    details.getByRole('button', { name: 'Отменить встречу' }),
  ).toHaveCount(0);
  expect(
    (
      await (
        await request.get(`/api/owner/bookings/${saved.booking.id}`)
      ).json()
    ).version,
  ).toBe(2);
});

for (const outcome of ['success', 'failure'] as const) {
  test(`потерянный ${outcome} отмены восстанавливается только чтением журнала`, async ({
    page,
    request,
    setServerNow,
  }) => {
    const saved = await book(request);
    const { bookings, details } = await openDetails(page);
    if (outcome === 'failure') await setServerNow('2026-11-02T06:00:00Z');
    let commands = 0;
    await page.route('**/api/owner/bookings/*/cancel', async (route) => {
      commands++;
      const response = await route.fetch();
      expect(response.status()).toBe(outcome === 'success' ? 200 : 409);
      await route.abort('failed');
    });
    page.once('dialog', (dialog) => dialog.accept());
    await details.getByRole('button', { name: 'Отменить встречу' }).click();
    await expect(bookings.getByRole('alert')).toContainText(
      'Исход операции неизвестен',
    );
    await expect(
      details.getByText('Подтверждено', { exact: true }),
    ).toBeVisible();
    await expect(details.getByText('Версия: 1', { exact: true })).toBeVisible();
    await expect(
      details.getByRole('button', { name: 'Отменить встречу' }),
    ).toBeDisabled();
    await expect(
      page.getByRole('button', { name: 'Сохранить профиль' }),
    ).toBeDisabled();
    await expect(
      page.getByRole('button', { name: 'Создать тип встречи', exact: true }),
    ).toBeDisabled();
    await expect(
      page.getByRole('switch', { name: 'Публичная запись' }),
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
    await expect(
      details.getByRole('button', { name: 'Отменить встречу' }),
    ).toBeDisabled();
    await page.unroute('**/api/owner/operations/*');
    await recover.focus();
    await page.keyboard.press('Enter');
    await expect(
      details.getByText(outcome === 'success' ? 'Отменено' : 'Подтверждено', {
        exact: true,
      }),
    ).toBeVisible();
    await expect(
      details.getByText(`Версия: ${outcome === 'success' ? 2 : 1}`, {
        exact: true,
      }),
    ).toBeVisible();
    if (outcome === 'failure')
      await expect(bookings.getByRole('alert')).toContainText(
        'Действие недоступно',
      );
    expect(commands).toBe(1);
    const current = await request.get(
      `/api/owner/bookings/${saved.booking.id}`,
    );
    expect((await current.json()).status).toBe(
      outcome === 'success' ? 'CANCELLED' : 'CONFIRMED',
    );
  });
}

for (const reset of [false, true]) {
  test(`недоступная операция отмены требует явной загрузки, сброс=${reset}`, async ({
    page,
    request,
    restartServer,
  }) => {
    await book(request);
    const { bookings, details } = await openDetails(page);
    let commands = 0;
    await page.route('**/api/owner/bookings/*/cancel', async (route) => {
      commands++;
      if (reset) await route.fetch();
      await route.abort('failed');
    });
    page.once('dialog', (dialog) => dialog.accept());
    await details.getByRole('button', { name: 'Отменить встречу' }).click();
    await expect(bookings.getByRole('alert')).toContainText(
      'Исход операции неизвестен',
    );
    if (reset) await restartServer();
    await bookings
      .getByRole('button', { name: 'Выяснить результат решения' })
      .click();
    await expect(bookings.getByRole('alert')).toContainText(
      'Возможен сброс данных',
    );
    await expect(
      details.getByRole('button', { name: 'Отменить встречу' }),
    ).toBeDisabled();
    await bookings
      .getByRole('button', { name: 'Загрузить актуальные бронирования' })
      .click();
    if (reset) {
      await expect(
        details.getByText('Бронирование не найдено', { exact: true }),
      ).toBeVisible();
      await expect(
        bookings.getByRole('list').getByRole('listitem'),
      ).toHaveCount(0);
    } else {
      await expect(
        details.getByText('Подтверждено', { exact: true }),
      ).toBeVisible();
      await expect(
        details.getByText('Версия: 1', { exact: true }),
      ).toBeVisible();
      await expect(
        details.getByRole('button', { name: 'Отменить встречу' }),
      ).toBeEnabled();
    }
    expect(commands).toBe(1);
  });
}

test('начавшуюся встречу отменить нельзя, ошибки диапазона связаны с полем', async ({
  page,
  request,
  setServerNow,
}) => {
  await book(request);
  const { bookings, details } = await openDetails(page);
  await setServerNow('2026-11-02T06:00:00Z');
  page.once('dialog', (dialog) => dialog.accept());
  await details.getByRole('button', { name: 'Отменить встречу' }).click();
  await expect(bookings.getByRole('alert')).toContainText(
    'Действие недоступно',
  );
  await expect(
    details.getByText('Подтверждено', { exact: true }),
  ).toBeVisible();
  await expect(details.getByText('Версия: 1', { exact: true })).toBeVisible();
  await expect(bookings.getByRole('list').getByRole('listitem')).toHaveCount(0);
  await bookings
    .getByLabel('Представление бронирований')
    .selectOption('calendar');
  await bookings.getByLabel('Начало диапазона').fill('2026-11-02T07:00:00Z');
  const end = bookings.getByLabel('Конец диапазона');
  await end.fill('2026-11-02T06:00:00Z');
  await bookings.getByRole('button', { name: 'Показать диапазон' }).click();
  await expect(end).toHaveAttribute('aria-invalid', 'true');
  await expect(end).toHaveAccessibleDescription(/Укажите конец позже начала/);
  await expect(end).toHaveValue('2026-11-02T06:00:00Z');
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});
