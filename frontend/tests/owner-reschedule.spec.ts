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
    { headers: { Authorization: `Bearer ${attempt.attemptSecret}` } },
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
  await details
    .getByRole('button', { name: 'Перенести встречу', exact: true })
    .click();
  const editor = details.getByRole('region', { name: 'Перенос бронирования' });
  return { bookings, details, editor };
}

for (const manual of [false, true]) {
  test(`перенос ${manual ? 'ожидающей' : 'подтверждённой'} встречи сохраняет условия и ссылку за гостевым окном`, async ({
    page,
    context,
    request,
  }) => {
    const saved = await book(request, manual);
    expect(
      (
        await request.put('/api/owner/meeting-types/Meeting', {
          headers: { 'Idempotency-Key': 'edit' },
          data: {
            title: 'Новые условия',
            description: 'Новое подробное описание изменённого типа',
            durationMinutes: 60,
            requiresApproval: !manual,
            expectedVersion: 1,
          },
        })
      ).status(),
    ).toBe(200);
    expect(
      (
        await request.put('/api/owner/public-booking', {
          headers: { 'Idempotency-Key': 'off' },
          data: { enabled: false, expectedVersion: 1 },
        })
      ).status(),
    ).toBe(200);
    const guest = await context.newPage();
    await guest.goto(saved.resultUrl);
    const { bookings, details, editor } = await openDetails(page);
    await expect(editor.getByText('30 мин', { exact: true })).toBeVisible();
    await editor.getByRole('button', { name: 'Следующие даты' }).click();
    await editor.getByRole('button', { name: 'Следующие даты' }).click();
    await editor
      .getByRole('button', {
        name: 'Выбрать 2026-11-16T09:00:00+03:00',
        exact: true,
      })
      .click();
    await expect(editor.getByText(/Прежнее время:/)).toContainText('09:00');
    await expect(editor.getByText(/Новое время:/)).toContainText('09:30');
    let commands = 0;
    page.on('request', (r) => {
      if (r.method() === 'POST' && r.url().endsWith('/reschedule')) commands++;
    });
    page.once('dialog', (dialog) => dialog.dismiss());
    await editor.getByRole('button', { name: 'Подтвердить перенос' }).click();
    expect(commands).toBe(0);
    page.once('dialog', async (dialog) => {
      expect(dialog.message()).toContain('Прежнее время');
      expect(dialog.message()).toContain('Новое время');
      expect(dialog.message()).toContain('Europe/Moscow');
      await dialog.accept();
    });
    await editor.getByRole('button', { name: 'Подтвердить перенос' }).focus();
    await page.keyboard.press('Enter');
    await expect(details.getByText('Версия: 2', { exact: true })).toBeVisible();
    await expect(details.getByText('30 мин', { exact: true })).toBeVisible();
    await expect(details.getByText('Сохранённый комментарий')).toBeVisible();
    await expect(
      details.getByText(manual ? 'Ожидает подтверждения' : 'Подтверждено', {
        exact: true,
      }),
    ).toBeVisible();
    await expect(
      bookings.getByRole('list', { name: 'Список бронирований' }),
    ).toContainText('16.11.2026');
    await guest.getByRole('button', { name: 'Обновить сведения' }).click();
    await expect(guest.getByText('30 мин', { exact: true })).toBeVisible();
    await expect(guest.locator('main')).toContainText('16.11.2026');
    await expect(guest).toHaveURL(saved.resultUrl);
    expect(commands).toBe(1);
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
}

test('гостевая отмена после переноса требует нового согласия, даже после прежнего начала', async ({
  page,
  context,
  request,
  setServerNow,
}) => {
  const saved = await book(request);
  const guest = await context.newPage();
  await guest.goto(saved.resultUrl);
  await guest.getByLabel('Часовой пояс').selectOption('Europe/Moscow');
  const { details, editor } = await openDetails(page);
  await editor
    .getByRole('button', {
      name: 'Выбрать 2026-11-02T10:00:00+03:00',
      exact: true,
    })
    .click();
  page.once('dialog', (dialog) => dialog.accept());
  await editor.getByRole('button', { name: 'Подтвердить перенос' }).click();
  await expect(details.getByText('Версия: 2', { exact: true })).toBeVisible();
  await setServerNow('2026-11-02T06:00:00Z');
  guest.once('dialog', async (dialog) => {
    expect(dialog.message()).toContain('09:00');
    await dialog.accept();
  });
  await guest
    .getByRole('button', { name: 'Отменить встречу', exact: true })
    .click();
  await expect(guest.getByRole('alert')).toContainText(
    'Бронирование изменилось',
  );
  await expect(guest.locator('main')).toContainText('10:00');
  expect(
    (
      await (
        await request.get(`/api/owner/bookings/${saved.booking.id}`)
      ).json()
    ).status,
  ).toBe('CONFIRMED');
  guest.once('dialog', async (dialog) => {
    expect(dialog.message()).toContain('10:00');
    await dialog.accept();
  });
  await guest
    .getByRole('button', { name: 'Отменить встречу', exact: true })
    .click();
  await expect(guest.getByText('Отменено', { exact: true })).toBeVisible();
});

for (const outcome of ['success', 'failure', 'not-delivered'] as const) {
  test(`неизвестный перенос ${outcome} блокирует изменения и восстанавливается только чтением`, async ({
    page,
    request,
  }) => {
    const saved = await book(request);
    const { bookings, details, editor } = await openDetails(page);
    if (outcome === 'success') {
      await bookings
        .getByLabel('Представление бронирований')
        .selectOption('calendar');
      await bookings
        .getByLabel('Начало диапазона')
        .fill('2026-11-02T06:00:00Z');
      await bookings.getByLabel('Конец диапазона').fill('2026-11-02T06:30:00Z');
      await bookings.getByRole('button', { name: 'Показать диапазон' }).click();
      await expect(
        bookings.getByRole('list', { name: 'Список бронирований' }),
      ).toContainText('Консультация');
    }
    await editor
      .getByRole('button', {
        name: 'Выбрать 2026-11-02T10:00:00+03:00',
        exact: true,
      })
      .click();
    if (outcome === 'failure') {
      expect(
        (
          await request.post('/api/owner/time-blocks', {
            headers: { 'Idempotency-Key': 'block' },
            data: {
              title: 'Занято',
              startsAt: '2026-11-02T10:00:00+03:00',
              endsAt: '2026-11-02T10:30:00+03:00',
            },
          })
        ).status(),
      ).toBe(201);
    }
    let commands = 0;
    await page.route('**/api/owner/bookings/*/reschedule', async (route) => {
      commands++;
      if (outcome !== 'not-delivered') {
        const response = await route.fetch();
        expect(response.status()).toBe(outcome === 'success' ? 200 : 409);
      }
      await route.abort('failed');
    });
    page.once('dialog', (dialog) => dialog.accept());
    await editor.getByRole('button', { name: 'Подтвердить перенос' }).click();
    await expect(bookings.getByRole('alert')).toContainText(
      'Исход операции неизвестен',
    );
    await expect(
      page.getByRole('button', { name: 'Сохранить профиль' }),
    ).toBeDisabled();
    await expect(
      editor.getByRole('button', { name: 'Подтвердить перенос' }),
    ).toBeDisabled();
    await expect(editor.getByText(/Новое время:/)).toContainText('10:00');
    await page.route('**/api/owner/operations/*', async (route) => {
      await route.fetch();
      await route.abort('failed');
    });
    await bookings
      .getByRole('button', { name: 'Выяснить результат решения' })
      .click();
    await expect(bookings.getByRole('alert')).toContainText(
      'Исход операции неизвестен',
    );
    await expect(
      page.getByRole('button', { name: 'Сохранить профиль' }),
    ).toBeDisabled();
    await page.unroute('**/api/owner/operations/*');
    await bookings
      .getByRole('button', { name: 'Выяснить результат решения' })
      .click();
    if (outcome === 'not-delivered') {
      await expect(bookings.getByRole('alert')).toContainText(
        'Возможен сброс данных',
      );
      await expect(
        page.getByRole('button', { name: 'Сохранить профиль' }),
      ).toBeDisabled();
      await bookings
        .getByRole('button', { name: 'Загрузить актуальные бронирования' })
        .click();
    }
    await expect(
      details.getByText(`Версия: ${outcome === 'success' ? 2 : 1}`, {
        exact: true,
      }),
    ).toBeVisible();
    await expect(
      page.getByRole('button', { name: 'Сохранить профиль' }),
    ).toBeEnabled();
    if (outcome === 'success') {
      await expect(
        bookings
          .getByRole('list', { name: 'Список бронирований' })
          .getByRole('listitem'),
      ).toHaveCount(0);
      await expect(details).toContainText('10:00');
    }
    expect(commands).toBe(1);
    const current = await (
      await request.get(`/api/owner/bookings/${saved.booking.id}`)
    ).json();
    expect(current.startsAt).toBe(
      outcome === 'success'
        ? '2026-11-02T10:00:00+03:00'
        : saved.booking.startsAt,
    );
    if (outcome === 'failure')
      await expect(bookings.getByRole('alert')).toContainText(
        'Время уже занято',
      );
  });
}

test('навигация очищает выбор, текущий конфликт не подставляет замену, архивный тип не переносится', async ({
  page,
  request,
}) => {
  const saved = await book(request);
  const { bookings, details, editor } = await openDetails(page);
  const submit = editor.getByRole('button', { name: 'Подтвердить перенос' });
  await editor
    .getByRole('button', {
      name: 'Выбрать 2026-11-02T10:00:00+03:00',
      exact: true,
    })
    .click();
  await expect(submit).toBeEnabled();
  await editor.getByRole('button', { name: 'Предыдущие даты' }).click();
  await expect(submit).toBeDisabled();
  await editor.getByRole('button', { name: 'Следующие даты' }).click();
  await expect(submit).toBeDisabled();
  await editor
    .getByRole('button', {
      name: 'Выбрать 2026-11-02T10:00:00+03:00',
      exact: true,
    })
    .click();
  expect(
    (
      await request.post('/api/owner/time-blocks', {
        headers: { 'Idempotency-Key': 'block' },
        data: {
          title: 'Занято',
          startsAt: '2026-11-02T10:00:00+03:00',
          endsAt: '2026-11-02T10:30:00+03:00',
        },
      })
    ).status(),
  ).toBe(201);
  page.once('dialog', (dialog) => dialog.accept());
  await submit.click();
  await expect(bookings.getByRole('alert')).toContainText('Время уже занято');
  await expect(details.getByText('Версия: 1', { exact: true })).toBeVisible();
  await expect(editor).toHaveCount(0);
  expect(
    await (await request.get(`/api/owner/bookings/${saved.booking.id}`)).json(),
  ).toEqual(saved.booking);
  expect(
    (
      await request.post('/api/owner/meeting-types/Meeting/archive', {
        headers: { 'Idempotency-Key': 'archive' },
        data: { expectedVersion: 1 },
      })
    ).status(),
  ).toBe(200);
  await bookings.getByRole('button', { name: 'Обновить бронирования' }).click();
  await expect(details.getByText('Версия: 1', { exact: true })).toBeVisible();
  await expect(
    details.getByRole('button', { name: 'Перенести встречу', exact: true }),
  ).toHaveCount(0);
  await expect(details.getByText('Сохранённый комментарий')).toBeVisible();
});

test('сброс после неизвестного переноса требует явной загрузки без повторной команды', async ({
  page,
  request,
  restartServer,
}) => {
  await book(request);
  const { bookings, details, editor } = await openDetails(page);
  await editor
    .getByRole('button', {
      name: 'Выбрать 2026-11-02T10:00:00+03:00',
      exact: true,
    })
    .click();
  let commands = 0;
  await page.route('**/api/owner/bookings/*/reschedule', async (route) => {
    commands++;
    expect((await route.fetch()).status()).toBe(200);
    await route.abort('failed');
  });
  page.once('dialog', (dialog) => dialog.accept());
  await editor.getByRole('button', { name: 'Подтвердить перенос' }).click();
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
    editor.getByRole('button', { name: 'Подтвердить перенос' }),
  ).toBeDisabled();
  await expect(editor.getByText(/Новое время:/)).toContainText('10:00');
  await bookings
    .getByRole('button', { name: 'Загрузить актуальные бронирования' })
    .click();
  await expect(
    details.getByText('Бронирование не найдено', { exact: true }),
  ).toBeVisible();
  await expect(
    bookings
      .getByRole('list', { name: 'Список бронирований' })
      .getByRole('listitem'),
  ).toHaveCount(0);
  await expect(
    page.getByRole('button', { name: 'Сохранить профиль' }),
  ).toBeEnabled();
  expect(commands).toBe(1);
});

test('перенос на сегодня исключает свою занятость, устаревшее решение показывает новую запись без повтора', async ({
  page,
  request,
  setServerNow,
}) => {
  const saved = await book(request, true);
  await setServerNow('2026-11-02T05:59:59.999999999Z');
  const { bookings, details, editor } = await openDetails(page);
  await editor
    .getByRole('button', {
      name: 'Выбрать 2026-11-02T09:15:00+03:00',
      exact: true,
    })
    .click();
  expect(
    (
      await request.post(`/api/owner/bookings/${saved.booking.id}/reschedule`, {
        headers: { 'Idempotency-Key': 'other-tab' },
        data: { startsAt: '2026-11-02T10:00:00+03:00', expectedVersion: 1 },
      })
    ).status(),
  ).toBe(200);
  let commands = 0;
  page.on('request', (r) => {
    if (r.method() === 'POST' && r.url().endsWith('/reschedule')) commands++;
  });
  page.once('dialog', (dialog) => dialog.accept());
  await editor.getByRole('button', { name: 'Подтвердить перенос' }).click();
  await expect(bookings.getByRole('alert')).toContainText(
    'Бронирование изменилось',
  );
  await expect(details.getByText('Версия: 2', { exact: true })).toBeVisible();
  await expect(details).toContainText('10:00');
  await expect(editor).toHaveCount(0);
  expect(commands).toBe(1);
  expect(
    (
      await (
        await request.get(`/api/owner/bookings/${saved.booking.id}`)
      ).json()
    ).startsAt,
  ).toBe('2026-11-02T10:00:00+03:00');
});
