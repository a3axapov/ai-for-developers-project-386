import { expect, test } from './real-server';
import type { APIRequestContext, Page } from '@playwright/test';

test.use({
  fixedNow: '2026-11-01T09:00:00Z',
  serverArguments: ['--calendar.initial.time-zone=Europe/Moscow'],
});

const typePath = '/api/owner/meeting-types/Consult';

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
  expect(
    (
      await request.put('/api/owner/public-booking', {
        headers: { 'Idempotency-Key': 'open' },
        data: { expectedVersion: 1, enabled: true },
      })
    ).status(),
  ).toBe(200);
}

async function editor(page: Page, date = '2026-11-02') {
  await page.goto('/admin');
  await page.getByRole('button', { name: 'Настроить исключения дат' }).click();
  const form = page.getByRole('form', { name: 'Исключение даты' });
  await form.getByLabel('Местная дата').fill(date);
  return form;
}

async function book(request: APIRequestContext) {
  const { meetingType } = await (
    await request.get('/api/public/anna-1/meeting-types/Consult')
  ).json();
  const prepared = await (
    await request.post('/api/public/anna-1/booking-attempts', {
      data: {
        meetingTypeId: 'Consult',
        conditionsToken: meetingType.conditionsToken,
        startsAt: '2026-11-02T09:00:00+03:00',
        guestName: 'Гость',
        guestEmail: 'guest@example.com',
      },
    })
  ).json();
  const result = await request.post(
    `/api/booking-attempts/${prepared.attemptId}/submit`,
    {
      headers: { Authorization: `Bearer ${prepared.attemptSecret}` },
    },
  );
  expect(result.status()).toBe(201);
  return (await result.json()).booking;
}

test('редактор даты закрывает день и удаление возвращает текущую неделю; клавиатура, минуты и открытие выходного', async ({
  page,
  request,
}) => {
  await seed(request);
  let form = await editor(page);
  await form.getByRole('button', { name: 'Добавить интервал' }).click();
  await form.getByLabel('Начало 1', { exact: true }).fill('24:00');
  await form
    .getByRole('button', { name: 'Сохранить исключение', exact: true })
    .click();
  await expect(form.getByLabel('Начало 1', { exact: true })).toHaveAttribute(
    'aria-invalid',
    'true',
  );
  await form.getByLabel('Начало 1', { exact: true }).fill('20:10');
  await form.getByLabel('Конец 1', { exact: true }).fill('22:00');
  await form
    .getByRole('button', { name: 'Сохранить исключение', exact: true })
    .focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('article')).toContainText(
    '02.11.2026: 20:10–22:00',
  );
  form = await editor(page);
  await expect(form.getByLabel('Начало 1', { exact: true })).toHaveValue(
    '20:10',
  );
  await form.getByRole('button', { name: 'Удалить интервал 1' }).click();
  await form
    .getByRole('button', { name: 'Сохранить исключение', exact: true })
    .click();
  await expect(page.getByRole('article')).toContainText('02.11.2026: Закрыто');
  expect(
    (await (await request.get(typePath)).json()).dateOverrides,
  ).toHaveLength(1);
  expect(
    (
      await request.put(`${typePath}/weekly-hours`, {
        headers: { 'Idempotency-Key': 'weekly' },
        data: {
          expectedVersion: 3,
          days: [
            { day: 'MONDAY', intervals: [{ start: '12:00', end: '13:00' }] },
          ],
        },
      })
    ).status(),
  ).toBe(200);
  form = await editor(page);
  await form
    .getByRole('button', { name: 'Удалить исключение', exact: true })
    .click();
  await expect(form).toHaveCount(0);
  expect(
    (await (await request.get(typePath)).json()).dateOverrides,
  ).toHaveLength(0);
  form = await editor(page, '2026-11-08');
  await form.getByRole('button', { name: 'Добавить интервал' }).click();
  await form.getByLabel('Начало 1', { exact: true }).fill('20:10');
  await form.getByLabel('Конец 1', { exact: true }).fill('21:00');
  await form
    .getByRole('button', { name: 'Сохранить исключение', exact: true })
    .click();
  await expect(form).toHaveCount(0);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.goto('/booking/anna-1/Consult');
  await page
    .getByRole('combobox', { name: 'Часовой пояс', exact: true })
    .selectOption('Europe/Moscow');
  await page.getByRole('button', { name: '02.11.2026', exact: true }).click();
  await expect(page.getByRole('button', { name: /^12:00/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /^09:00/ })).toHaveCount(0);
  await page.getByRole('button', { name: '08.11.2026', exact: true }).click();
  await expect(page.getByRole('button', { name: /^20:15/ })).toBeVisible();
});

test('предупреждение сохраняет черновик при отмене, новый статус требует свежего согласия; удаление тоже предупреждает', async ({
  page,
  request,
}) => {
  await seed(request, true);
  const booking = await book(request);
  const keys: string[] = [];
  page.on('request', (request) => {
    if (
      request.url().includes('/date-overrides/') &&
      ['PUT', 'DELETE'].includes(request.method())
    )
      keys.push(request.headers()['idempotency-key']);
  });
  let form = await editor(page);
  await form
    .getByRole('button', { name: 'Сохранить исключение', exact: true })
    .click();
  let preview = form.getByRole('region', {
    name: 'Последствия изменения исключения',
  });
  await expect(preview).toContainText('Ожидает подтверждения');
  await expect(preview).toContainText('09:00 UTC+03:00');
  await preview
    .getByRole('button', { name: 'Отменить изменение исключения' })
    .click();
  await expect(preview).toHaveCount(0);
  expect((await (await request.get(typePath)).json()).version).toBe(1);
  await form
    .getByRole('button', { name: 'Сохранить исключение', exact: true })
    .click();
  await expect(preview).toContainText('Ожидает подтверждения');
  expect(
    (
      await request.post(`/api/owner/bookings/${booking.id}/confirm`, {
        headers: { 'Idempotency-Key': 'confirm' },
        data: { expectedVersion: 1 },
      })
    ).status(),
  ).toBe(200);
  await preview
    .getByRole('button', { name: 'Подтвердить последствия' })
    .click();
  await expect(preview).toContainText('Подтверждено');
  expect((await (await request.get(typePath)).json()).version).toBe(1);
  await preview
    .getByRole('button', { name: 'Подтвердить последствия' })
    .click();
  await expect(form).toHaveCount(0);
  expect(keys).toHaveLength(4);
  expect(new Set(keys).size).toBe(4);
  expect(
    (
      await request.put(`${typePath}/weekly-hours`, {
        headers: { 'Idempotency-Key': 'weekly' },
        data: { expectedVersion: 2, days: [] },
      })
    ).status(),
  ).toBe(200);
  expect(
    (
      await request.put(`${typePath}/date-overrides/2026-11-02`, {
        headers: { 'Idempotency-Key': 'reopen' },
        data: {
          expectedVersion: 3,
          intervals: [{ start: '09:00', end: '10:00' }],
        },
      })
    ).status(),
  ).toBe(200);
  form = await editor(page);
  await form
    .getByRole('button', { name: 'Удалить исключение', exact: true })
    .click();
  preview = form.getByRole('region', {
    name: 'Последствия изменения исключения',
  });
  await expect(preview).toContainText('Удаление');
  await expect(preview).toContainText('Подтверждено');
  await preview
    .getByRole('button', { name: 'Отменить изменение исключения' })
    .click();
  expect(
    (await (await request.get(typePath)).json()).dateOverrides,
  ).toHaveLength(1);
  await form
    .getByRole('button', { name: 'Удалить исключение', exact: true })
    .click();
  await preview
    .getByRole('button', { name: 'Подтвердить последствия' })
    .click();
  await expect(form).toHaveCount(0);
  const after = await (
    await request.get(`/api/owner/bookings/${booking.id}`)
  ).json();
  expect(after.status).toBe('CONFIRMED');
  expect(after.startsAt).toBe(booking.startsAt);
});

test('потеря ответа исключения блокирует все изменения; выяснение результата только читает журнал', async ({
  page,
  request,
}) => {
  await seed(request);
  const form = await editor(page);
  let writes = 0;
  await page.route(
    '**/api/owner/meeting-types/Consult/date-overrides/*',
    async (route) => {
      writes++;
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      await route.abort('failed');
    },
  );
  await form
    .getByRole('button', { name: 'Сохранить исключение', exact: true })
    .click();
  const recovery = form.getByRole('button', {
    name: 'Выяснить результат изменения исключения',
  });
  await expect(recovery).toBeVisible();
  for (const name of [
    'Сохранить профиль',
    'Создать тип встречи',
    'Редактировать тип',
    'Архивировать тип',
    'Настроить рабочие часы',
    'Настроить исключения дат',
  ]) {
    await expect(
      page.getByRole('button', { name, exact: true }),
    ).toBeDisabled();
  }
  await expect(
    page.getByRole('switch', { name: 'Публичная запись' }),
  ).toBeDisabled();
  await expect(form.getByLabel('Местная дата')).toBeDisabled();
  await page.route('**/api/owner/operations/*', (route) =>
    route.abort('failed'),
  );
  await recovery.click();
  await expect(recovery).toBeEnabled();
  await expect(
    page.getByRole('button', { name: 'Сохранить профиль', exact: true }),
  ).toBeDisabled();
  await page.unroute('**/api/owner/operations/*');
  await recovery.click();
  await expect(form).toHaveCount(0);
  await expect(page.getByRole('article')).toContainText('02.11.2026: Закрыто');
  await expect(
    page.getByRole('button', { name: 'Сохранить профиль', exact: true }),
  ).toBeEnabled();
  expect(writes).toBe(1);
});

test('потерянное предупреждение восстанавливается без согласия и без повторной команды', async ({
  page,
  request,
}) => {
  await seed(request, true);
  await book(request);
  const form = await editor(page);
  let writes = 0;
  await page.route(
    '**/api/owner/meeting-types/Consult/date-overrides/*',
    async (route) => {
      writes++;
      expect((await route.fetch()).status()).toBe(409);
      await route.abort('failed');
    },
  );
  await form
    .getByRole('button', { name: 'Сохранить исключение', exact: true })
    .click();
  await form
    .getByRole('button', { name: 'Выяснить результат изменения исключения' })
    .click();
  const preview = form.getByRole('region', {
    name: 'Последствия изменения исключения',
  });
  await expect(preview).toContainText('Ожидает подтверждения');
  expect((await (await request.get(typePath)).json()).version).toBe(1);
  await preview
    .getByRole('button', { name: 'Отменить изменение исключения' })
    .click();
  await expect(form.getByLabel('Местная дата')).toHaveValue('2026-11-02');
  expect(writes).toBe(1);
});

test('потеря ответа удаления восстанавливает удаление без повторной отправки', async ({
  page,
  request,
}) => {
  await seed(request);
  expect(
    (
      await request.put(`${typePath}/date-overrides/2026-11-02`, {
        headers: { 'Idempotency-Key': 'closed' },
        data: { expectedVersion: 1, intervals: [] },
      })
    ).status(),
  ).toBe(200);
  const form = await editor(page);
  let writes = 0;
  await page.route(
    '**/api/owner/meeting-types/Consult/date-overrides/*',
    async (route) => {
      writes++;
      expect(route.request().method()).toBe('DELETE');
      expect((await route.fetch()).status()).toBe(200);
      await route.abort('failed');
    },
  );
  await form
    .getByRole('button', { name: 'Удалить исключение', exact: true })
    .click();
  await form
    .getByRole('button', { name: 'Выяснить результат изменения исключения' })
    .click();
  await expect(form).toHaveCount(0);
  expect(
    (await (await request.get(typePath)).json()).dateOverrides,
  ).toHaveLength(0);
  expect(writes).toBe(1);
});

test('сброс процесса сохраняет черновик даты, требует явного чтения и не повторяет команду', async ({
  page,
  request,
  restartServer,
}) => {
  await seed(request);
  const form = await editor(page);
  await form.getByRole('button', { name: 'Добавить интервал' }).click();
  await form.getByLabel('Начало 1', { exact: true }).fill('20:10');
  let writes = 0;
  await page.route(
    '**/api/owner/meeting-types/Consult/date-overrides/*',
    async (route) => {
      writes++;
      await route.abort('failed');
    },
  );
  await form
    .getByRole('button', { name: 'Сохранить исключение', exact: true })
    .click();
  const recover = form.getByRole('button', {
    name: 'Выяснить результат изменения исключения',
  });
  await expect(recover).toBeVisible();
  await restartServer();
  await recover.click();
  await expect(
    form.getByText('Результат операции недоступен. Возможен сброс данных.'),
  ).toBeVisible();
  await form
    .getByRole('button', { name: 'Загрузить актуальные исключения' })
    .click();
  await expect(form.getByLabel('Местная дата')).toHaveValue('2026-11-02');
  await expect(form.getByLabel('Начало 1', { exact: true })).toHaveValue(
    '20:10',
  );
  await expect(
    form.getByRole('button', { name: 'Сохранить исключение', exact: true }),
  ).toBeDisabled();
  await expect(
    page.getByRole('button', { name: 'Создать тип встречи', exact: true }),
  ).toBeEnabled();
  expect(writes).toBe(1);
});

for (const archived of [false, true]) {
  test(`конфликт ${archived ? 'архива' : 'версии'} показывает сохранённые исключения отдельно от черновика`, async ({
    page,
    request,
  }) => {
    await seed(request);
    const form = await editor(page);
    await form.getByRole('button', { name: 'Добавить интервал' }).click();
    await form.getByLabel('Начало 1', { exact: true }).fill('20:10');
    if (archived) {
      expect(
        (
          await request.post(`${typePath}/archive`, {
            headers: { 'Idempotency-Key': 'archive' },
            data: { expectedVersion: 1 },
          })
        ).status(),
      ).toBe(200);
    } else {
      expect(
        (
          await request.put(`${typePath}/date-overrides/2026-11-02`, {
            headers: { 'Idempotency-Key': 'other-tab' },
            data: { expectedVersion: 1, intervals: [] },
          })
        ).status(),
      ).toBe(200);
    }
    const save = form.getByRole('button', {
      name: 'Сохранить исключение',
      exact: true,
    });
    await save.click();
    await expect(form).toContainText('Сохранённые исключения (версия 2)');
    await expect(form.getByLabel('Начало 1', { exact: true })).toHaveValue(
      '20:10',
    );
    if (archived) {
      await expect(save).toBeDisabled();
      await expect(
        form.getByText('Архивный тип нельзя редактировать.'),
      ).toBeVisible();
    } else {
      await expect(form.getByRole('list').first()).toContainText(
        '02.11.2026: Закрыто',
      );
      await form.getByLabel('Конец 1', { exact: true }).fill('21:00');
      await save.click();
      await expect(form).toHaveCount(0);
      await expect(page.getByRole('article')).toContainText(
        '02.11.2026: 20:10–21:00',
      );
    }
  });
}

for (const stillValid of [true, false]) {
  test(`открытая гостевая попытка после исключения: ${stillValid ? 'сохраняет допустимый слот' : 'обновляет календарь без потери контактов'}`, async ({
    page,
    request,
  }) => {
    await seed(request);
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
        await request.put(`${typePath}/date-overrides/2026-11-02`, {
          headers: { 'Idempotency-Key': 'exception' },
          data: {
            expectedVersion: 1,
            intervals: [
              { start: stillValid ? '09:00' : '10:00', end: '12:00' },
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

test('полностью закрытая дата обновляет календарь, сохраняет контакты и требует нового выбора', async ({
  page,
  request,
}) => {
  await seed(request);
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
  await page.getByRole('button', { name: 'Подготовить попытку' }).click();
  await expect(
    page.getByRole('button', { name: 'Записаться', exact: true }),
  ).toBeVisible();
  expect(
    (
      await request.put(`${typePath}/date-overrides/2026-11-02`, {
        headers: { 'Idempotency-Key': 'close-date' },
        data: { expectedVersion: 1, intervals: [] },
      })
    ).status(),
  ).toBe(200);
  await page.getByRole('button', { name: 'Записаться', exact: true }).click();
  await page
    .getByRole('combobox', { name: 'Часовой пояс', exact: true })
    .selectOption('Europe/Moscow');
  await expect(
    page.getByRole('button', { name: '02.11.2026', exact: true }),
  ).toBeDisabled();
  await expect(
    page.getByRole('button', { name: 'Подготовить попытку' }),
  ).toHaveCount(0);
  await page.getByRole('button', { name: '03.11.2026', exact: true }).click();
  await page
    .getByRole('button', {
      name: '09:00 UTC+03:00 — 09:30 UTC+03:00',
      exact: true,
    })
    .click();
  await expect(page.getByLabel('Ваше имя')).toHaveValue('Гость');
  await expect(page.getByLabel('Email', { exact: true })).toHaveValue(
    'guest@example.com',
  );
});
