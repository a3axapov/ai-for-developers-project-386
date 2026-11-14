import { expect, test } from './real-server';
import type { APIRequestContext, Page } from '@playwright/test';

test.use({
  fixedNow: '2026-11-01T09:00:00Z',
  serverArguments: ['--calendar.initial.time-zone=Europe/Moscow'],
});

async function seed(request: APIRequestContext, approval = false) {
  expect(
    (
      await request.post('/api/owner/meeting-types', {
        headers: { 'Idempotency-Key': 'seed' },
        data: {
          id: 'Consult',
          title: 'Консультация',
          description: 'Достаточно длинное описание встречи',
          durationMinutes: 30,
          requiresApproval: approval,
        },
      })
    ).status(),
  ).toBe(201);
}

async function editor(page: Page) {
  await page.goto('/admin');
  await page.getByRole('button', { name: 'Настроить рабочие часы' }).click();
  return page.getByRole('form', { name: 'Недельные рабочие часы' });
}

test('редактор часов проверяет минуты, объединяет интервалы и сохраняет настоящий перерыв с клавиатуры', async ({
  page,
  request,
}) => {
  await seed(request);
  const form = await editor(page);
  const monday = form.getByRole('group', { name: 'Пн', exact: true });
  await monday.getByLabel('Начало 1', { exact: true }).fill('24:00');
  await form
    .getByRole('button', { name: 'Сохранить часы', exact: true })
    .click();
  await expect(monday.getByLabel('Начало 1', { exact: true })).toHaveAttribute(
    'aria-invalid',
    'true',
  );
  await monday.getByLabel('Начало 1', { exact: true }).fill('09:10');
  await monday.getByLabel('Конец 1', { exact: true }).fill('10:00');
  await monday.getByRole('button', { name: 'Добавить интервал' }).click();
  await monday.getByLabel('Начало 2', { exact: true }).fill('10:00');
  await monday.getByLabel('Конец 2', { exact: true }).fill('11:00');
  await monday.getByRole('button', { name: 'Добавить интервал' }).click();
  await monday.getByLabel('Начало 3', { exact: true }).fill('12:00');
  await monday.getByLabel('Конец 3', { exact: true }).fill('13:00');
  await form
    .getByRole('button', { name: 'Сохранить часы', exact: true })
    .focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('article')).toContainText(
    'Пн: 09:10–11:00, 12:00–13:00',
  );
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await request.put('/api/owner/public-booking', {
    headers: { 'Idempotency-Key': 'open' },
    data: { enabled: true, expectedVersion: 1 },
  });
  await page.goto('/booking/anna-1/Consult');
  await page
    .getByRole('combobox', { name: 'Часовой пояс', exact: true })
    .selectOption('Europe/Moscow');
  await page.getByRole('button', { name: '02.11.2026', exact: true }).click();
  await expect(page.getByRole('button', { name: /^09:15/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /^10:45|^11:/ })).toHaveCount(
    0,
  );
});

async function enable(request: APIRequestContext) {
  expect(
    (
      await request.put('/api/owner/public-booking', {
        headers: { 'Idempotency-Key': 'open' },
        data: { enabled: true, expectedVersion: 1 },
      })
    ).status(),
  ).toBe(200);
}

async function book(
  request: APIRequestContext,
  time = '2026-11-02T09:00:00+03:00',
) {
  const conditions = await (
    await request.get('/api/public/anna-1/meeting-types/Consult')
  ).json();
  const prepared = await (
    await request.post('/api/public/anna-1/booking-attempts', {
      data: {
        meetingTypeId: 'Consult',
        conditionsToken: conditions.meetingType.conditionsToken,
        startsAt: time,
        guestName: 'Гость',
        guestEmail: 'guest@example.com',
      },
    })
  ).json();
  const submitted = await request.post(
    `/api/booking-attempts/${prepared.attemptId}/submit`,
    { headers: { Authorization: `Bearer ${prepared.attemptSecret}` } },
  );
  expect(submitted.status()).toBe(201);
  return (await submitted.json()).booking;
}

test('предпросмотр не меняет часы, отмена сохраняет черновик, изменение статуса требует нового согласия и ключа', async ({
  page,
  request,
}) => {
  await seed(request, true);
  await enable(request);
  const booking = await book(request);
  const keys: string[] = [];
  page.on('request', (request) => {
    if (request.url().endsWith('/weekly-hours') && request.method() === 'PUT')
      keys.push(request.headers()['idempotency-key']);
  });
  const form = await editor(page);
  const monday = form.getByRole('group', { name: 'Пн', exact: true });
  await monday.getByLabel('Начало 1', { exact: true }).fill('10:00');
  await form
    .getByRole('button', { name: 'Сохранить часы', exact: true })
    .click();
  const preview = form.getByRole('region', {
    name: 'Последствия изменения часов',
  });
  await expect(preview).toContainText('Ожидает подтверждения');
  await expect(preview).toContainText('Гость');
  await expect(preview).toContainText('09:00 UTC+03:00');
  expect(
    (await (await request.get('/api/owner/meeting-types/Consult')).json())
      .version,
  ).toBe(1);
  await preview
    .getByRole('button', { name: 'Отменить изменение часов' })
    .click();
  await expect(preview).toHaveCount(0);
  await expect(monday.getByLabel('Начало 1', { exact: true })).toHaveValue(
    '10:00',
  );
  expect(keys).toHaveLength(1);
  await form
    .getByRole('button', { name: 'Сохранить часы', exact: true })
    .click();
  await expect(preview).toContainText('Ожидает подтверждения');
  expect(
    (
      await request.post(`/api/owner/bookings/${booking.id}/confirm`, {
        headers: { 'Idempotency-Key': 'confirm-other-tab' },
        data: { expectedVersion: 1 },
      })
    ).status(),
  ).toBe(200);
  const confirm = preview.getByRole('button', {
    name: 'Подтвердить последствия и сохранить часы',
  });
  await confirm.click();
  await expect(preview).toContainText('Подтверждено');
  expect(
    (await (await request.get('/api/owner/meeting-types/Consult')).json())
      .version,
  ).toBe(1);
  await confirm.click();
  await expect(form).toHaveCount(0);
  await expect(page.getByRole('article')).toContainText('Пн: 10:00–18:00');
  expect(keys).toHaveLength(4);
  expect(new Set(keys).size).toBe(4);
  const after = await (
    await request.get(`/api/owner/bookings/${booking.id}`)
  ).json();
  expect(after.status).toBe('CONFIRMED');
  expect(after.startsAt).toBe(booking.startsAt);
});

test('потеря ответа часов блокирует ВСЕ изменения владельца, восстановление только читает исход', async ({
  page,
  request,
}) => {
  await seed(request);
  const form = await editor(page);
  await form
    .getByRole('group', { name: 'Пн', exact: true })
    .getByLabel('Начало 1', { exact: true })
    .fill('09:10');
  let writes = 0;
  await page.route(
    '**/api/owner/meeting-types/Consult/weekly-hours',
    async (route) => {
      writes++;
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      await route.abort('failed');
    },
  );
  await form
    .getByRole('button', { name: 'Сохранить часы', exact: true })
    .click();
  const recovery = form.getByRole('button', {
    name: 'Выяснить результат изменения часов',
  });
  await expect(recovery).toBeVisible();
  await expect(
    page.getByRole('button', { name: 'Создать тип встречи', exact: true }),
  ).toBeDisabled();
  await expect(
    page.getByRole('button', { name: 'Редактировать тип', exact: true }),
  ).toBeDisabled();
  await expect(
    page.getByRole('button', { name: 'Архивировать тип', exact: true }),
  ).toBeDisabled();
  await expect(
    page.getByRole('button', { name: 'Сохранить профиль', exact: true }),
  ).toBeDisabled();
  await expect(
    page.getByRole('switch', { name: 'Публичная запись' }),
  ).toBeDisabled();
  await page.route('**/api/owner/operations/*', (route) =>
    route.abort('failed'),
  );
  await recovery.click();
  await expect(recovery).toBeEnabled();
  await expect(
    page.getByRole('button', { name: 'Редактировать тип', exact: true }),
  ).toBeDisabled();
  await page.unroute('**/api/owner/operations/*');
  await recovery.click();
  await expect(form).toHaveCount(0);
  await expect(page.getByRole('article')).toContainText('Пн: 09:10–18:00');
  await expect(
    page.getByRole('button', { name: 'Редактировать тип', exact: true }),
  ).toBeEnabled();
  expect(writes).toBe(1);
});

test('сброс процесса требует явного чтения, сохраняет черновик и снимает блокировку даже если тип исчез', async ({
  page,
  request,
  restartServer,
}) => {
  await seed(request);
  const form = await editor(page);
  const input = form
    .getByRole('group', { name: 'Пн', exact: true })
    .getByLabel('Начало 1', { exact: true });
  await input.fill('09:10');
  let writes = 0;
  await page.route(
    '**/api/owner/meeting-types/Consult/weekly-hours',
    async (route) => {
      writes++;
      await route.abort('failed');
    },
  );
  await form
    .getByRole('button', { name: 'Сохранить часы', exact: true })
    .click();
  await expect(
    form.getByRole('button', { name: 'Выяснить результат изменения часов' }),
  ).toBeVisible();
  await restartServer();
  await form
    .getByRole('button', { name: 'Выяснить результат изменения часов' })
    .click();
  await expect(
    form.getByText('Результат операции недоступен. Возможен сброс данных.'),
  ).toBeVisible();
  await form.getByRole('button', { name: 'Загрузить актуальные часы' }).click();
  await expect(
    page.getByRole('button', { name: 'Создать тип встречи', exact: true }),
  ).toBeEnabled();
  await expect(input).toHaveValue('09:10');
  await expect(
    form.getByRole('button', { name: 'Сохранить часы', exact: true }),
  ).toBeDisabled();
  await expect(page.getByRole('article')).toHaveCount(0);
  expect(writes).toBe(1);
});

for (const action of ['hours', 'archive'] as const) {
  test(`конфликт ${action} показывает текущие часы отдельно от сохранённого черновика`, async ({
    page,
    request,
  }) => {
    await seed(request);
    const form = await editor(page);
    const input = form
      .getByRole('group', { name: 'Пн', exact: true })
      .getByLabel('Начало 1', { exact: true });
    await input.fill('09:10');
    if (action === 'hours') {
      expect(
        (
          await request.put('/api/owner/meeting-types/Consult/weekly-hours', {
            headers: { 'Idempotency-Key': 'other-tab' },
            data: { expectedVersion: 1, days: [] },
          })
        ).status(),
      ).toBe(200);
    } else {
      expect(
        (
          await request.post('/api/owner/meeting-types/Consult/archive', {
            headers: { 'Idempotency-Key': 'other-tab' },
            data: { expectedVersion: 1 },
          })
        ).status(),
      ).toBe(200);
    }
    const save = form.getByRole('button', {
      name: 'Сохранить часы',
      exact: true,
    });
    await save.click();
    await expect(
      form.getByText('Тип встречи изменился. Проверьте актуальные данные.'),
    ).toBeVisible();
    await expect(form).toContainText('Сохранённые часы (версия 2)');
    await expect(input).toHaveValue('09:10');
    if (action === 'archive') {
      await expect(save).toBeDisabled();
      await expect(
        form.getByText('Архивный тип нельзя редактировать.'),
      ).toBeVisible();
    } else {
      await expect(form.getByRole('list').first()).toContainText('Пн: Закрыто');
      await save.click();
      await expect(form).toHaveCount(0);
      await expect(page.getByRole('article')).toContainText('Пн: 09:10–18:00');
    }
  });
}

for (const stillValid of [true, false]) {
  test(`открытая гостевая попытка: слот ${stillValid ? 'остаётся допустимым без CONDITIONS_CHANGED' : 'закрывается без потери контактов'}`, async ({
    page,
    request,
  }) => {
    await seed(request);
    await enable(request);
    await page.goto('/booking/anna-1/Consult');
    await page
      .getByRole('combobox', { name: 'Часовой пояс', exact: true })
      .selectOption('Europe/Moscow');
    await page.getByRole('button', { name: '02.11.2026', exact: true }).click();
    await page
      .getByRole('button', {
        name: '09:00 UTC+03:00 — 09:30 UTC+03:00',
        exact: true,
      })
      .click();
    await page.getByLabel('Ваше имя').fill('Гость');
    await page.getByLabel('Email', { exact: true }).fill('guest@example.com');
    await page.getByLabel('Комментарий').fill('Сохранить вопросы');
    await page.getByRole('button', { name: 'Подготовить попытку' }).click();
    await expect(
      page.getByRole('button', { name: 'Записаться', exact: true }),
    ).toBeVisible();
    expect(
      (
        await request.put('/api/owner/meeting-types/Consult/weekly-hours', {
          headers: { 'Idempotency-Key': 'hours' },
          data: {
            expectedVersion: 1,
            days: [
              {
                day: 'MONDAY',
                intervals: [
                  { start: stillValid ? '09:00' : '10:00', end: '12:00' },
                ],
              },
            ],
          },
        })
      ).status(),
    ).toBe(200);
    await page.getByRole('button', { name: 'Записаться', exact: true }).click();
    if (stillValid) {
      await expect(page).toHaveURL(/\/bookings\/[A-Za-z0-9_-]+$/);
      await expect(
        page.getByRole('heading', {
          name: 'Результат бронирования',
          exact: true,
        }),
      ).toBeVisible();
    } else {
      await page
        .getByRole('combobox', { name: 'Часовой пояс', exact: true })
        .selectOption('Europe/Moscow');
      await expect(page.getByRole('button', { name: /^09:00/ })).toHaveCount(0);
      await expect(
        page.getByRole('button', { name: 'Подготовить попытку' }),
      ).toHaveCount(0);
      await page.getByRole('button', { name: /^10:00/ }).click();
      await expect(page.getByLabel('Ваше имя')).toHaveValue('Гость');
      await expect(page.getByLabel('Email', { exact: true })).toHaveValue(
        'guest@example.com',
      );
      await expect(page.getByLabel('Комментарий')).toHaveValue(
        'Сохранить вопросы',
      );
      expect(
        (await (await request.get('/api/owner/bookings?view=upcoming')).json())
          .items,
      ).toHaveLength(0);
    }
  });
}

test('закрытые часы сохраняют ожидающий запрос и подтверждённую встречу; владелец видит запрет подтверждения', async ({
  page,
  request,
}) => {
  await seed(request, true);
  await enable(request);
  const pending = await book(request);
  const confirmed = await book(request, '2026-11-02T09:30:00+03:00');
  expect(
    (
      await request.post(`/api/owner/bookings/${confirmed.id}/confirm`, {
        headers: { 'Idempotency-Key': 'confirm' },
        data: { expectedVersion: 1 },
      })
    ).status(),
  ).toBe(200);
  const form = await editor(page);
  await form
    .getByRole('group', { name: 'Пн', exact: true })
    .getByLabel('Начало 1', { exact: true })
    .fill('10:00');
  await form
    .getByRole('button', { name: 'Сохранить часы', exact: true })
    .click();
  const preview = form.getByRole('region', {
    name: 'Последствия изменения часов',
  });
  await expect(preview.getByRole('listitem')).toHaveCount(2);
  await preview
    .getByRole('button', { name: 'Подтвердить последствия и сохранить часы' })
    .click();
  await expect(form).toHaveCount(0);
  const bookings = page.getByRole('region', { name: 'Бронирования владельца' });
  await bookings
    .getByRole('button', { name: 'Открыть Консультация' })
    .first()
    .click();
  const details = bookings.getByRole('region', { name: 'Детали бронирования' });
  await expect(
    details.getByText('Ожидает подтверждения', { exact: true }),
  ).toBeVisible();
  await details
    .getByRole('button', { name: 'Подтвердить запрос', exact: true })
    .click();
  await expect(bookings.getByRole('alert')).toContainText(
    'Время не соответствует текущей доступности.',
  );
  await expect(
    details.getByText('Ожидает подтверждения', { exact: true }),
  ).toBeVisible();
  expect(
    (await (await request.get(`/api/owner/bookings/${pending.id}`)).json())
      .status,
  ).toBe('PENDING');
  expect(
    (await (await request.get(`/api/owner/bookings/${confirmed.id}`)).json())
      .status,
  ).toBe('CONFIRMED');
});

test('потерянный ответ предупреждения восстанавливает только предпросмотр, без скрытого согласия', async ({
  page,
  request,
}) => {
  await seed(request, true);
  await enable(request);
  await book(request);
  const form = await editor(page);
  await form
    .getByRole('group', { name: 'Пн', exact: true })
    .getByLabel('Начало 1', { exact: true })
    .fill('10:00');
  let writes = 0;
  await page.route(
    '**/api/owner/meeting-types/Consult/weekly-hours',
    async (route) => {
      writes++;
      const response = await route.fetch();
      expect(response.status()).toBe(409);
      await route.abort('failed');
    },
  );
  await form
    .getByRole('button', { name: 'Сохранить часы', exact: true })
    .click();
  const recovery = form.getByRole('button', {
    name: 'Выяснить результат изменения часов',
  });
  await expect(recovery).toBeVisible();
  await recovery.click();
  const preview = form.getByRole('region', {
    name: 'Последствия изменения часов',
  });
  await expect(preview).toContainText('Ожидает подтверждения');
  expect(writes).toBe(1);
  expect(
    (await (await request.get('/api/owner/meeting-types/Consult')).json())
      .version,
  ).toBe(1);
  await page.unroute('**/api/owner/meeting-types/Consult/weekly-hours');
  await preview
    .getByRole('button', { name: 'Подтвердить последствия и сохранить часы' })
    .click();
  await expect(form).toHaveCount(0);
  expect(
    (await (await request.get('/api/owner/meeting-types/Consult')).json())
      .version,
  ).toBe(2);
});
