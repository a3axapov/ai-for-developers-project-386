import { expect, test } from './real-server';
import type { APIRequestContext, Page } from '@playwright/test';

test.use({
  fixedNow: '2026-11-01T09:00:00Z',
  serverArguments: ['--calendar.initial.time-zone=Europe/Moscow'],
});
const path = '/api/owner/out-of-office';

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

async function editor(page: Page, start = '2026-11-02', end = start) {
  await page.goto('/admin');
  await page
    .getByRole('button', { name: 'Добавить отпуск', exact: true })
    .click();
  const form = page.getByRole('form', { name: 'Отпуск' });
  await form.getByLabel('Начальная дата').fill(start);
  await form.getByLabel('Конечная дата').fill(end);
  return form;
}

test('общий отпуск: даты, ошибки полей, клавиатура, включительные дни, редактирование и удаление', async ({
  page,
  request,
}) => {
  await seed(request);
  let form = await editor(page, '2026-11-03', '2026-11-02');
  await form
    .getByRole('button', { name: 'Сохранить отпуск', exact: true })
    .click();
  await expect(form.getByLabel('Конечная дата')).toHaveAttribute(
    'aria-invalid',
    'true',
  );
  await expect(form.getByLabel('Конечная дата')).toHaveAttribute(
    'aria-describedby',
    'vacation-endDate-error',
  );
  await form.getByLabel('Начальная дата').fill('2026-11-02');
  await form.getByLabel('Конечная дата').fill('2026-11-03');
  await form
    .getByRole('button', { name: 'Сохранить отпуск', exact: true })
    .focus();
  await page.keyboard.press('Enter');
  await expect(form).toHaveCount(0);
  const section = page.getByRole('region', { name: 'Общие отпуска' });
  await expect(section).toContainText('02.11.2026 — 03.11.2026');
  await expect(section).toContainText('Europe/Moscow');
  await page
    .getByRole('button', { name: 'Редактировать отпуск', exact: true })
    .click();
  form = page.getByRole('form', { name: 'Отпуск' });
  await expect(form).toContainText('Сохранённый часовой пояс: Europe/Moscow');
  await form.getByLabel('Конечная дата').fill('2026-11-04');
  await form
    .getByRole('button', { name: 'Сохранить отпуск', exact: true })
    .click();
  await expect(form).toHaveCount(0);
  await page.goto('/booking/anna-1/Consult');
  await page
    .getByRole('combobox', { name: 'Часовой пояс', exact: true })
    .selectOption('Europe/Moscow');
  for (const day of ['02', '03', '04'])
    await expect(
      page.getByRole('button', { name: `${day}.11.2026`, exact: true }),
    ).toBeDisabled();
  await page.goto('/admin');
  await page
    .getByRole('button', { name: 'Редактировать отпуск', exact: true })
    .click();
  await page
    .getByRole('button', { name: 'Удалить отпуск', exact: true })
    .click();
  await expect(page.getByRole('form', { name: 'Отпуск' })).toHaveCount(0);
  expect((await (await request.get(path)).json()).items).toHaveLength(0);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});

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

test('предупреждение и отмена сохраняют даты; изменение статуса требует свежего согласия с новым ключом', async ({
  page,
  request,
}) => {
  await seed(request, true);
  const booking = await book(request);
  const form = await editor(page);
  const keys: string[] = [];
  page.on('request', (req) => {
    if (req.url().endsWith(path) && req.method() === 'POST')
      keys.push(req.headers()['idempotency-key']);
  });
  const save = form.getByRole('button', {
    name: 'Сохранить отпуск',
    exact: true,
  });
  await save.click();
  const preview = form.getByRole('region', {
    name: 'Последствия изменения отпуска',
  });
  await expect(preview).toContainText('Ожидает подтверждения');
  await expect(preview).toContainText('09:00 UTC+03:00');
  await preview
    .getByRole('button', { name: 'Отменить изменение отпуска' })
    .click();
  await expect(preview).toHaveCount(0);
  await expect(form.getByLabel('Начальная дата')).toHaveValue('2026-11-02');
  expect((await (await request.get(path)).json()).items).toHaveLength(0);
  await save.click();
  await expect(preview).toContainText('Ожидает подтверждения');
  expect(
    (
      await request.post(`/api/owner/bookings/${booking.id}/confirm`, {
        headers: { 'Idempotency-Key': 'confirm' },
        data: { expectedVersion: 1 },
      })
    ).status(),
  ).toBe(200);
  const agree = preview.getByRole('button', {
    name: 'Подтвердить последствия отпуска',
  });
  await agree.click();
  await expect(preview).toContainText('Подтверждено');
  expect((await (await request.get(path)).json()).items).toHaveLength(0);
  await agree.click();
  await expect(form).toHaveCount(0);
  expect(new Set(keys).size).toBe(4);
  const after = await (
    await request.get(`/api/owner/bookings/${booking.id}`)
  ).json();
  expect(after.status).toBe('CONFIRMED');
  expect(after.startsAt).toBe(booking.startsAt);
});

for (const kind of ['create', 'update', 'delete', 'warning'] as const) {
  test(`потеря ответа ${kind}: общая блокировка, восстановление только читает сохранённый исход`, async ({
    page,
    request,
  }) => {
    await seed(request, kind === 'warning');
    if (kind === 'warning') await book(request);
    if (kind === 'update' || kind === 'delete') {
      expect(
        (
          await request.post(path, {
            headers: { 'Idempotency-Key': 'vacation' },
            data: { startDate: '2026-11-03', endDate: '2026-11-03' },
          })
        ).status(),
      ).toBe(201);
      await page.goto('/admin');
      await page
        .getByRole('button', { name: 'Редактировать отпуск', exact: true })
        .click();
    } else await editor(page);
    const form = page.getByRole('form', { name: 'Отпуск' });
    if (kind === 'update')
      await form.getByLabel('Конечная дата').fill('2026-11-04');
    let writes = 0;
    await page.route('**/api/owner/out-of-office{,/*}', async (route) => {
      if (route.request().method() === 'GET') return route.continue();
      writes++;
      const response = await route.fetch();
      expect(response.status()).toBe(
        kind === 'delete'
          ? 204
          : kind === 'warning'
            ? 409
            : kind === 'create'
              ? 201
              : 200,
      );
      await route.abort('failed');
    });
    await form
      .getByRole('button', {
        name: kind === 'delete' ? 'Удалить отпуск' : 'Сохранить отпуск',
        exact: true,
      })
      .click();
    const recover = form.getByRole('button', {
      name: 'Выяснить результат изменения отпуска',
    });
    await expect(recover).toBeVisible();
    for (const name of [
      'Сохранить профиль',
      'Создать тип встречи',
      'Редактировать тип',
      'Архивировать тип',
      'Настроить рабочие часы',
      'Настроить исключения дат',
    ])
      await expect(
        page.getByRole('button', { name, exact: true }),
      ).toBeDisabled();
    await expect(
      page.getByRole('switch', { name: 'Публичная запись' }),
    ).toBeDisabled();
    await expect(form.getByLabel('Начальная дата')).toBeDisabled();
    await page.route('**/api/owner/operations/*', (route) =>
      route.abort('failed'),
    );
    await recover.click();
    await expect(recover).toBeEnabled();
    await expect(
      page.getByRole('button', { name: 'Сохранить профиль', exact: true }),
    ).toBeDisabled();
    await page.unroute('**/api/owner/operations/*');
    await recover.click();
    if (kind === 'warning') {
      const preview = form.getByRole('region', {
        name: 'Последствия изменения отпуска',
      });
      await expect(preview).toContainText('Ожидает подтверждения');
      await preview
        .getByRole('button', { name: 'Отменить изменение отпуска' })
        .click();
      expect((await (await request.get(path)).json()).items).toHaveLength(0);
      await expect(form.getByLabel('Начальная дата')).toHaveValue('2026-11-02');
    } else {
      await expect(form).toHaveCount(0);
      const items = (await (await request.get(path)).json()).items;
      expect(items).toHaveLength(kind === 'delete' ? 0 : 1);
      if (kind === 'update') expect(items[0].endDate).toBe('2026-11-04');
    }
    expect(writes).toBe(1);
    await expect(
      page.getByRole('button', { name: 'Сохранить профиль', exact: true }),
    ).toBeEnabled();
  });
}

test('сброс памяти требует явного чтения без повтора команды и сохраняет черновик', async ({
  page,
  request,
  restartServer,
}) => {
  await seed(request);
  const form = await editor(page);
  let writes = 0;
  await page.route('**/api/owner/out-of-office', async (route) => {
    if (route.request().method() === 'GET') return route.continue();
    writes++;
    await route.abort('failed');
  });
  await form
    .getByRole('button', { name: 'Сохранить отпуск', exact: true })
    .click();
  const recover = form.getByRole('button', {
    name: 'Выяснить результат изменения отпуска',
  });
  await expect(recover).toBeVisible();
  await restartServer();
  await recover.click();
  await expect(form).toContainText(
    'Результат операции недоступен. Возможен сброс данных.',
  );
  await form
    .getByRole('button', { name: 'Загрузить актуальные отпуска' })
    .click();
  await expect(form.getByLabel('Начальная дата')).toBeEnabled();
  await expect(form.getByLabel('Начальная дата')).toHaveValue('2026-11-02');
  await expect(form.getByLabel('Конечная дата')).toHaveValue('2026-11-02');
  expect((await (await request.get(path)).json()).items).toHaveLength(0);
  expect(writes).toBe(1);
});

test('конфликт версии показывает сохранённые даты отдельно от черновика', async ({
  page,
  request,
}) => {
  await seed(request);
  const created = await (
    await request.post(path, {
      headers: { 'Idempotency-Key': 'vacation' },
      data: { startDate: '2026-11-03', endDate: '2026-11-03' },
    })
  ).json();
  await page.goto('/admin');
  await page
    .getByRole('button', { name: 'Редактировать отпуск', exact: true })
    .click();
  const form = page.getByRole('form', { name: 'Отпуск' });
  await form.getByLabel('Конечная дата').fill('2026-11-04');
  expect(
    (
      await request.put(`${path}/${created.id}`, {
        headers: { 'Idempotency-Key': 'other-tab' },
        data: {
          startDate: '2026-11-05',
          endDate: '2026-11-05',
          expectedVersion: 1,
        },
      })
    ).status(),
  ).toBe(200);
  await form
    .getByRole('button', { name: 'Сохранить отпуск', exact: true })
    .click();
  await expect(form).toContainText('Сохранено: 05.11.2026 — 05.11.2026');
  await expect(form.getByLabel('Начальная дата')).toHaveValue('2026-11-03');
  await expect(form.getByLabel('Конечная дата')).toHaveValue('2026-11-04');
  expect((await (await request.get(path)).json()).items[0].version).toBe(2);
});

for (const stillValid of [true, false]) {
  test(`подготовленная гостевая попытка после отпуска: ${stillValid ? 'допустимое время сохранено' : 'закрытый день очищает выбор, но сохраняет контакты'}`, async ({
    page,
    request,
  }) => {
    await seed(request);
    const privateReads: string[] = [];
    page.on('request', (req) => {
      if (req.url().includes('/api/owner/')) privateReads.push(req.url());
    });
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
        await request.post(path, {
          headers: { 'Idempotency-Key': 'vacation' },
          data: {
            startDate: stillValid ? '2026-11-03' : '2026-11-02',
            endDate: stillValid ? '2026-11-03' : '2026-11-02',
          },
        })
      ).status(),
    ).toBe(201);
    await page.getByRole('button', { name: 'Записаться', exact: true }).click();
    if (stillValid) await expect(page).toHaveURL(/\/bookings\/[A-Za-z0-9_-]+$/);
    else {
      await page
        .getByRole('combobox', { name: 'Часовой пояс', exact: true })
        .selectOption('Europe/Moscow');
      await expect(
        page.getByRole('button', { name: '02.11.2026', exact: true }),
      ).toBeDisabled();
      await expect(
        page.getByRole('button', { name: 'Подготовить попытку' }),
      ).toHaveCount(0);
      await page
        .getByRole('button', { name: '03.11.2026', exact: true })
        .click();
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
      await expect(page.getByLabel('Комментарий')).toHaveValue(
        'Сохранить вопросы',
      );
      expect(
        (await (await request.get('/api/owner/bookings?view=upcoming')).json())
          .items,
      ).toHaveLength(0);
    }
    expect(privateReads).toEqual([]);
  });
}

test('пересекающиеся отпуска закрывают несколько типов поверх открытой даты; удаление одного не снимает второй', async ({
  page,
  request,
}) => {
  await seed(request);
  expect(
    (
      await request.post('/api/owner/meeting-types', {
        headers: { 'Idempotency-Key': 'other' },
        data: {
          id: 'Other',
          title: 'Другой тип',
          description: 'Ещё одно достаточно длинное описание',
          durationMinutes: 30,
        },
      })
    ).status(),
  ).toBe(201);
  expect(
    (
      await request.put(
        '/api/owner/meeting-types/Consult/date-overrides/2026-11-03',
        {
          headers: { 'Idempotency-Key': 'exception' },
          data: {
            expectedVersion: 1,
            intervals: [{ start: '00:00', end: '24:00' }],
          },
        },
      )
    ).status(),
  ).toBe(200);
  let form = await editor(page, '2026-11-02', '2026-11-03');
  await form
    .getByRole('button', { name: 'Сохранить отпуск', exact: true })
    .click();
  await expect(form).toHaveCount(0);
  await page
    .getByRole('button', { name: 'Добавить отпуск', exact: true })
    .click();
  form = page.getByRole('form', { name: 'Отпуск' });
  await form.getByLabel('Начальная дата').fill('2026-11-03');
  await form.getByLabel('Конечная дата').fill('2026-11-04');
  await form
    .getByRole('button', { name: 'Сохранить отпуск', exact: true })
    .click();
  await expect(form).toHaveCount(0);
  const section = page.getByRole('region', { name: 'Общие отпуска' });
  await section
    .getByRole('listitem')
    .filter({ hasText: '02.11.2026 — 03.11.2026' })
    .getByRole('button', { name: 'Редактировать отпуск', exact: true })
    .click();
  await page
    .getByRole('button', { name: 'Удалить отпуск', exact: true })
    .click();
  await expect(page.getByRole('form', { name: 'Отпуск' })).toHaveCount(0);
  for (const type of ['Consult', 'Other']) {
    const slots = (
      await (
        await request.get(`/api/public/anna-1/meeting-types/${type}/slots`)
      ).json()
    ).items;
    expect(
      slots.some((slot: { startsAt: string }) =>
        slot.startsAt.startsWith('2026-11-02'),
      ),
    ).toBe(true);
    expect(
      slots.some((slot: { startsAt: string }) =>
        slot.startsAt.startsWith('2026-11-03'),
      ),
    ).toBe(false);
    expect(
      slots.some((slot: { startsAt: string }) =>
        slot.startsAt.startsWith('2026-11-04'),
      ),
    ).toBe(false);
  }
});

test('закрытый отпуском ожидающий запрос не подтверждается и сохраняет статус и занятость', async ({
  page,
  request,
}) => {
  await seed(request, true);
  const pending = await book(request);
  const form = await editor(page);
  await form
    .getByRole('button', { name: 'Сохранить отпуск', exact: true })
    .click();
  await form
    .getByRole('button', { name: 'Подтвердить последствия отпуска' })
    .click();
  await expect(form).toHaveCount(0);
  const bookings = page.getByRole('region', { name: 'Бронирования владельца' });
  await bookings
    .getByRole('button', { name: 'Открыть Консультация', exact: true })
    .click();
  const details = bookings.getByRole('region', { name: 'Детали бронирования' });
  await details
    .getByRole('button', { name: 'Подтвердить запрос', exact: true })
    .click();
  await expect(bookings).toContainText(
    'Время не соответствует текущей доступности.',
  );
  await expect(
    details.getByText('Ожидает подтверждения', { exact: true }),
  ).toBeVisible();
  const after = await (
    await request.get(`/api/owner/bookings/${pending.id}`)
  ).json();
  expect(after).toEqual(pending);
});
