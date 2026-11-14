import { expect, test } from './real-server';
import type { APIRequestContext } from '@playwright/test';

test.use({
  fixedNow: '2026-11-01T00:00:00Z',
  timezoneId: 'America/New_York',
  serverArguments: ['--calendar.initial.time-zone=Europe/Moscow'],
});

async function book(
  request: APIRequestContext,
  id: string,
  requiresApproval: boolean,
  startsAt: string,
) {
  expect(
    (
      await request.post('/api/owner/meeting-types', {
        headers: { 'Idempotency-Key': id },
        data: {
          id,
          title: `Консультация ${id}`,
          description: 'Сохранённое подробное описание консультации',
          durationMinutes: 30,
          requiresApproval,
        },
      })
    ).status(),
  ).toBe(201);
  const slots = await request.get(
    `/api/public/anna-1/meeting-types/${id}/slots`,
  );
  expect(slots.status()).toBe(200);
  const prepared = await request.post('/api/public/anna-1/booking-attempts', {
    data: {
      meetingTypeId: id,
      conditionsToken: (await slots.json()).conditionsToken,
      startsAt,
      guestName: `Гость ${id}`,
      guestEmail: `${id}@example.com`,
      comment: 'Первый вопрос\nВторая строка',
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

test('владелец находит все типы, фильтр, детали, историю и календарь с актуальным статусом', async ({
  page,
  request,
  setServerNow,
}) => {
  expect(
    (
      await request.put('/api/owner/public-booking', {
        headers: { 'Idempotency-Key': 'open' },
        data: { enabled: true, expectedVersion: 1 },
      })
    ).status(),
  ).toBe(200);
  await book(request, 'Auto', false, '2026-11-02T10:00:00+03:00');
  await book(request, 'Manual', true, '2026-11-02T09:00:00+03:00');
  await page.goto('/admin');
  const bookings = page.getByRole('region', { name: 'Бронирования владельца' });
  await expect(bookings).toBeVisible();
  await expect(bookings.getByText('Часовой пояс: Europe/Moscow')).toBeVisible();
  const list = bookings.getByRole('list', { name: 'Список бронирований' });
  await expect(list.getByRole('listitem')).toHaveCount(2);
  await expect(list.getByRole('listitem').first()).toContainText('Manual');
  await bookings.getByLabel('Статус бронирований').selectOption('PENDING');
  await expect(list.getByRole('listitem')).toHaveCount(1);
  await list
    .getByRole('button', { name: 'Открыть Консультация Manual' })
    .focus();
  await page.keyboard.press('Enter');
  const details = bookings.getByRole('region', { name: 'Детали бронирования' });
  await expect(
    details.getByText('Manual@example.com', { exact: true }),
  ).toBeVisible();
  await expect(
    details.getByText('Гость Manual', { exact: true }),
  ).toBeVisible();
  await expect(
    details.getByText('Первый вопрос\nВторая строка', { exact: true }),
  ).toBeVisible();
  await expect(
    details.getByText('Сохранённое подробное описание консультации', {
      exact: true,
    }),
  ).toBeVisible();
  await expect(details.getByText('Версия: 1', { exact: true })).toBeVisible();
  await expect(
    details.getByText(
      '02.11.2026, 09:00 UTC+03:00 — 02.11.2026, 09:30 UTC+03:00',
      { exact: true },
    ),
  ).toBeVisible();
  await expect(details.getByRole('textbox')).toHaveCount(0);
  await bookings.getByLabel('Статус бронирований').selectOption('');
  await expect(list.getByRole('listitem')).toHaveCount(2);
  await setServerNow('2026-11-02T07:00:00Z');
  await bookings
    .getByRole('button', { name: 'Обновить бронирования', exact: true })
    .click();
  await expect(list.getByRole('listitem')).toHaveCount(0);
  await expect(details.getByText('Версия: 2', { exact: true })).toBeVisible();
  await expect(
    details.getByText('Владелец не подтвердил запрос до начала встречи.'),
  ).toBeVisible();
  await bookings
    .getByLabel('Представление бронирований')
    .selectOption('history');
  await expect(list.getByRole('listitem')).toHaveCount(2);
  await bookings
    .getByLabel('Представление бронирований')
    .selectOption('calendar');
  await bookings.getByLabel('Начало диапазона').fill('2026-11-02T06:15:00Z');
  await bookings.getByLabel('Конец диапазона').fill('2026-11-02T07:15:00Z');
  await bookings.getByRole('button', { name: 'Показать диапазон' }).click();
  await expect(list.getByRole('listitem')).toHaveCount(2);
  await expect(list).toContainText('Отклонено');
  await expect(list).toContainText('Подтверждено');
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});

test('пустой календарь, ошибки диапазона и потеря чтения не объявляются успешной загрузкой', async ({
  page,
}) => {
  await page.goto('/admin');
  const bookings = page.getByRole('region', { name: 'Бронирования владельца' });
  await expect(
    bookings.getByText('В выбранном представлении нет бронирований.'),
  ).toBeVisible();
  await bookings
    .getByLabel('Представление бронирований')
    .selectOption('calendar');
  await bookings.getByLabel('Начало диапазона').fill('2026-11-03T00:00:00Z');
  await bookings.getByLabel('Конец диапазона').fill('2026-11-02T00:00:00Z');
  await bookings.getByRole('button', { name: 'Показать диапазон' }).click();
  await expect(bookings.getByRole('alert')).toHaveText(
    'Начало диапазона должно быть раньше конца.',
  );
  await expect(bookings.getByLabel('Конец диапазона')).toHaveAttribute(
    'aria-invalid',
    'true',
  );
  await expect(
    bookings.getByLabel('Конец диапазона'),
  ).toHaveAccessibleDescription(/Укажите конец позже начала/);
  await expect(bookings.getByLabel('Начало диапазона')).toHaveValue(
    '2026-11-03T00:00:00Z',
  );
  await bookings.getByLabel('Конец диапазона').fill('2026-11-04T00:00:00Z');
  await bookings.getByRole('button', { name: 'Показать диапазон' }).click();
  await expect(
    bookings.getByText('В выбранном представлении нет бронирований.'),
  ).toBeVisible();
  // Drop delivery of a real server response, not a substituted booking response.
  await page.route('**/api/owner/calendar?**', async (route) => {
    await route.fetch();
    await route.abort('failed');
  });
  await bookings.getByRole('button', { name: 'Обновить бронирования' }).click();
  await expect(bookings.getByRole('alert')).toHaveText(
    'Не удалось загрузить бронирования. Обновите сведения.',
  );
  await expect(
    bookings.getByText('В выбранном представлении нет бронирований.'),
  ).toHaveCount(0);
  await page.unroute('**/api/owner/calendar?**');
  await bookings.getByRole('button', { name: 'Обновить бронирования' }).focus();
  await page.keyboard.press('Enter');
  await expect(
    bookings.getByText('В выбранном представлении нет бронирований.'),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});

test.describe('осеннее повторение времени владельца', () => {
  test.use({
    fixedNow: '2026-10-24T00:00:00Z',
    serverArguments: [
      '--calendar.initial.time-zone=Europe/Berlin',
      '--calendar.initial.booking-enabled=true',
      '--calendar.initial.working-days=SUNDAY',
      '--calendar.initial.work-start=00:00',
      '--calendar.initial.work-end=24:00',
    ],
  });

  test('оба одинаковых местных начала различаются смещениями и попадают в фактический диапазон', async ({
    page,
    request,
  }) => {
    await book(request, 'First', false, '2026-10-25T02:00:00+02:00');
    await book(request, 'Second', true, '2026-10-25T02:00:00+01:00');
    await page.goto('/admin');
    const bookings = page.getByRole('region', {
      name: 'Бронирования владельца',
    });
    await expect(
      bookings.getByText('Часовой пояс: Europe/Berlin'),
    ).toBeVisible();
    const list = bookings.getByRole('list', { name: 'Список бронирований' });
    await expect(
      list.getByText(
        '25.10.2026, 02:00 UTC+02:00 — 25.10.2026, 02:30 UTC+02:00',
        { exact: true },
      ),
    ).toBeVisible();
    await expect(
      list.getByText(
        '25.10.2026, 02:00 UTC+01:00 — 25.10.2026, 02:30 UTC+01:00',
        { exact: true },
      ),
    ).toBeVisible();
    await bookings
      .getByLabel('Представление бронирований')
      .selectOption('calendar');
    await bookings.getByLabel('Начало диапазона').fill('2026-10-25T00:30:00Z');
    await bookings.getByLabel('Конец диапазона').fill('2026-10-25T01:30:00Z');
    await bookings.getByRole('button', { name: 'Показать диапазон' }).click();
    await expect(list.getByRole('listitem')).toHaveCount(1);
    await expect(list).toContainText('Second');
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
  });
});
