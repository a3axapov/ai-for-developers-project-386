import { expect, test } from './real-server';
import type { APIRequestContext, Page } from '@playwright/test';

test.use({
  fixedNow: '2026-11-01T09:00:00Z',
  serverArguments: ['--calendar.initial.time-zone=Europe/Berlin'],
});

const path = '/api/owner/time-blocks';
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
async function editor(page: Page) {
  await page.goto('/admin');
  await page
    .getByRole('button', { name: 'Добавить блокировку', exact: true })
    .click();
  const form = page.getByRole('form', {
    name: 'Блокировка времени',
    exact: true,
  });
  await form
    .getByLabel('Название блокировки', { exact: true })
    .fill('Личное секретное название');
  await form
    .getByLabel('Начало блокировки', { exact: true })
    .fill('2026-11-02T09:00');
  await form
    .getByLabel('Окончание блокировки', { exact: true })
    .fill('2026-11-02T11:00');
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
        startsAt: '2026-11-02T09:00:00+01:00',
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
  return result.json();
}

test('предупреждение без записи, отмена и явное отклонение; гость обновляет результат без утечки блокировки', async ({
  page,
  request,
  context,
}) => {
  await seed(request, true);
  const booked = await book(request);
  const guest = await context.newPage();
  await guest.goto(booked.resultUrl);
  await expect(
    guest.getByText('Ожидает подтверждения', { exact: true }),
  ).toBeVisible();
  const form = await editor(page);
  const keys: string[] = [];
  page.on('request', (req) => {
    if (req.url().endsWith(path) && req.method() === 'POST')
      keys.push(req.headers()['idempotency-key']);
  });
  await form
    .getByRole('button', { name: 'Сохранить блокировку', exact: true })
    .click();
  const preview = form.getByRole('region', { name: 'Последствия блокировки' });
  await expect(preview).toContainText('Гость');
  await expect(preview).toContainText('будут отклонены');
  await preview
    .getByRole('button', { name: 'Отменить изменение блокировки' })
    .click();
  await expect(
    form.getByLabel('Название блокировки', { exact: true }),
  ).toHaveValue('Личное секретное название');
  expect(
    (
      await (
        await request.get(`/api/owner/bookings/${booked.booking.id}`)
      ).json()
    ).version,
  ).toBe(1);
  await form
    .getByRole('button', { name: 'Сохранить блокировку', exact: true })
    .click();
  await preview
    .getByRole('button', {
      name: 'Подтвердить блокировку и отклонение запросов',
    })
    .click();
  await expect(form).toHaveCount(0);
  expect(new Set(keys).size).toBe(3);
  await guest
    .getByRole('button', { name: 'Обновить сведения', exact: true })
    .click();
  await expect(guest.getByText('Отклонено', { exact: true })).toBeVisible();
  await expect(guest.locator('body')).not.toContainText(
    'Личное секретное название',
  );
  await page.getByLabel('Представление бронирований').selectOption('calendar');
  await expect(
    page.getByRole('list', { name: 'Блокировки во внутреннем календаре' }),
  ).toContainText('Личное секретное название');
  const publicBodies: string[] = [];
  guest.on('response', async (response) => {
    if (response.url().includes('/api/public/'))
      publicBodies.push(await response.text());
  });
  await guest.goto('/booking/anna-1/Consult');
  await guest
    .getByRole('combobox', { name: 'Часовой пояс', exact: true })
    .selectOption('Europe/Berlin');
  await guest.getByRole('button', { name: '02.11.2026', exact: true }).click();
  await expect(
    guest.getByRole('button', {
      name: '09:00 UTC+01:00 — 09:30 UTC+01:00',
      exact: true,
    }),
  ).toHaveCount(0);
  await expect(guest.locator('body')).not.toContainText(
    'Личное секретное название',
  );
  expect(publicBodies.join('')).not.toContain('Личное секретное название');
});

for (const kind of ['create', 'update', 'delete', 'warning'] as const) {
  test(`неизвестный исход ${kind}: глобальный замок и только чтение журнала после потери ответа`, async ({
    page,
    request,
  }) => {
    await seed(request, kind === 'warning');
    if (kind === 'warning') await book(request);
    if (kind === 'update' || kind === 'delete') {
      expect(
        (
          await request.post(path, {
            headers: { 'Idempotency-Key': 'block' },
            data: {
              title: 'Исходное',
              startsAt: '2026-11-02T09:00:00.123456789+01:00',
              endsAt: '2026-11-02T11:00:00+01:00',
            },
          })
        ).status(),
      ).toBe(201);
      await page.goto('/admin');
      await page
        .getByRole('button', { name: 'Редактировать блокировку', exact: true })
        .click();
    } else await editor(page);
    const form = page.getByRole('form', {
      name: 'Блокировка времени',
      exact: true,
    });
    if (kind === 'update')
      await form
        .getByLabel('Название блокировки', { exact: true })
        .fill('Изменено');
    let writes = 0;
    await page.route('**/api/owner/time-blocks{,/*}', async (route) => {
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
        name: kind === 'delete' ? 'Удалить блокировку' : 'Сохранить блокировку',
        exact: true,
      })
      .click();
    const recover = form.getByRole('button', {
      name: 'Выяснить результат изменения блокировки',
    });
    await expect(recover).toBeVisible();
    for (const name of [
      'Сохранить профиль',
      'Создать тип встречи',
      'Добавить отпуск',
      'Настроить рабочие часы',
      'Настроить исключения дат',
    ])
      await expect(
        page.getByRole('button', { name, exact: true }),
      ).toBeDisabled();
    await expect(
      page.getByRole('switch', { name: 'Публичная запись' }),
    ).toBeDisabled();
    await expect(
      form.getByLabel('Название блокировки', { exact: true }),
    ).toBeDisabled();
    await page.route('**/api/owner/operations/*', (route) =>
      route.abort('failed'),
    );
    await recover.click();
    await expect(recover).toBeEnabled();
    await expect(
      page.getByRole('button', { name: 'Сохранить профиль', exact: true }),
    ).toBeDisabled();
    await page.unroute('**/api/owner/operations/*');
    await recover.focus();
    await page.keyboard.press('Enter');
    if (kind === 'warning') {
      await expect(
        form.getByRole('region', { name: 'Последствия блокировки' }),
      ).toContainText('Гость');
      await form
        .getByRole('button', { name: 'Отменить изменение блокировки' })
        .click();
    } else await expect(form).toHaveCount(0);
    expect(writes).toBe(1);
    if (kind === 'update') {
      const calendar = await (
        await request.get('/api/owner/calendar', {
          params: {
            from: '2026-11-01T00:00:00Z',
            to: '2026-11-03T00:00:00Z',
          },
        })
      ).json();
      expect(calendar.timeBlocks[0].startsAt).toBe(
        '2026-11-02T09:00:00.123456789+01:00',
      );
    }
    await expect(
      page.getByRole('button', { name: 'Сохранить профиль', exact: true }),
    ).toBeEnabled();
  });
}

test('сброс при неизвестном удалении: явное чтение освобождает замок без повторной записи и сохраняет черновик', async ({
  page,
  request,
  restartServer,
}) => {
  await seed(request);
  await request.post(path, {
    headers: { 'Idempotency-Key': 'block' },
    data: {
      title: 'Исходное',
      startsAt: '2026-11-02T09:00:00+01:00',
      endsAt: '2026-11-02T11:00:00+01:00',
    },
  });
  await page.goto('/admin');
  await page
    .getByRole('button', { name: 'Редактировать блокировку', exact: true })
    .click();
  const form = page.getByRole('form', {
    name: 'Блокировка времени',
    exact: true,
  });
  await form
    .getByLabel('Название блокировки', { exact: true })
    .fill('Черновик');
  let writes = 0;
  await page.route('**/api/owner/time-blocks/*', (route) => {
    writes++;
    return route.abort('failed');
  });
  await form
    .getByRole('button', { name: 'Удалить блокировку', exact: true })
    .click();
  const recover = form.getByRole('button', {
    name: 'Выяснить результат изменения блокировки',
  });
  await expect(recover).toBeVisible();
  await restartServer();
  await recover.click();
  await form
    .getByRole('button', { name: 'Загрузить актуальные блокировки' })
    .click();
  await expect(form).toContainText('Блокировка больше недоступна');
  await expect(
    form.getByLabel('Название блокировки', { exact: true }),
  ).toHaveValue('Черновик');
  await expect(
    page.getByRole('button', { name: 'Сохранить профиль', exact: true }),
  ).toBeEnabled();
  expect(writes).toBe(1);
  await form
    .getByRole('button', { name: 'Закрыть редактор блокировки' })
    .click();
  await page
    .getByRole('button', { name: 'Добавить блокировку', exact: true })
    .click();
  await expect(
    form.getByLabel('Название блокировки', { exact: true }),
  ).toHaveValue('');
});

test('восстановление недоставленного изменения находит блокировку, перемещённую другой вкладкой за диапазон', async ({
  page,
  request,
}) => {
  await seed(request);
  const created = await (
    await request.post(path, {
      headers: { 'Idempotency-Key': 'block' },
      data: {
        title: 'Исходное',
        startsAt: '2026-11-02T09:00:00+01:00',
        endsAt: '2026-11-02T11:00:00+01:00',
      },
    })
  ).json();
  await page.goto('/admin');
  await page
    .getByRole('button', { name: 'Редактировать блокировку', exact: true })
    .click();
  const form = page.getByRole('form', {
    name: 'Блокировка времени',
    exact: true,
  });
  await form
    .getByLabel('Название блокировки', { exact: true })
    .fill('Черновик');
  let writes = 0;
  await page.route('**/api/owner/time-blocks/*', (route) => {
    writes++;
    return route.abort('failed');
  });
  await form
    .getByRole('button', { name: 'Сохранить блокировку', exact: true })
    .click();
  const recover = form.getByRole('button', {
    name: 'Выяснить результат изменения блокировки',
  });
  await expect(recover).toBeVisible();
  expect(
    (
      await request.put(`${path}/${created.timeBlock.id}`, {
        headers: { 'Idempotency-Key': 'other-tab' },
        data: {
          title: 'Перемещено',
          startsAt: '2028-11-02T09:00:00+01:00',
          endsAt: '2028-11-02T11:00:00+01:00',
          expectedVersion: 1,
        },
      })
    ).status(),
  ).toBe(200);
  await recover.click();
  await form
    .getByRole('button', { name: 'Загрузить актуальные блокировки' })
    .click();
  await expect(form).toContainText('Сохранено: Перемещено');
  await expect(
    form.getByLabel('Название блокировки', { exact: true }),
  ).toHaveValue('Черновик');
  await expect(
    form.getByRole('button', { name: 'Сохранить блокировку', exact: true }),
  ).toBeEnabled();
  expect(writes).toBe(1);
});

test('подготовленная форма не обходит блокировку: время сбрасывается, контакты сохраняются, названия скрыты', async ({
  page,
  request,
}) => {
  await seed(request);
  await page.goto('/booking/anna-1/Consult');
  await page
    .getByRole('combobox', { name: 'Часовой пояс', exact: true })
    .selectOption('Europe/Berlin');
  await page.getByRole('button', { name: '02.11.2026', exact: true }).click();
  await page
    .getByRole('button', {
      name: '09:00 UTC+01:00 — 09:30 UTC+01:00',
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
        headers: { 'Idempotency-Key': 'block' },
        data: {
          title: 'Личное секретное название',
          startsAt: '2026-11-02T09:00:00+01:00',
          endsAt: '2026-11-02T09:30:00+01:00',
        },
      })
    ).status(),
  ).toBe(201);
  await page.getByRole('button', { name: 'Записаться', exact: true }).click();
  await expect(
    page.getByRole('button', { name: 'Подготовить попытку' }),
  ).toHaveCount(0);
  await expect(page.getByRole('alert')).toContainText('Время уже занято');
  await page
    .getByRole('combobox', { name: 'Часовой пояс', exact: true })
    .selectOption('Europe/Berlin');
  await page.getByRole('button', { name: '02.11.2026', exact: true }).click();
  await expect(
    page.getByRole('button', {
      name: '09:00 UTC+01:00 — 09:30 UTC+01:00',
      exact: true,
    }),
  ).toHaveCount(0);
  await page
    .getByRole('button', {
      name: '10:00 UTC+01:00 — 10:30 UTC+01:00',
      exact: true,
    })
    .click();
  await expect(page.getByLabel('Ваше имя')).toHaveValue('Гость');
  await expect(page.getByLabel('Email', { exact: true })).toHaveValue(
    'guest@example.com',
  );
  await expect(page.getByLabel('Комментарий')).toHaveValue('Сохранить вопросы');
  await expect(page.locator('body')).not.toContainText(
    'Личное секретное название',
  );
});

test('ручная блокировка: буквальный ввод, DST, связанные ошибки, клавиатура и CRUD без гостевой записи', async ({
  page,
  request,
}) => {
  await page.goto('/admin');
  await page
    .getByRole('button', { name: 'Добавить блокировку', exact: true })
    .click();
  const form = page.getByRole('form', {
    name: 'Блокировка времени',
    exact: true,
  });
  await expect(form).toContainText(
    'Текущий часовой пояс владельца: Europe/Berlin',
  );
  await form.getByLabel('Название блокировки', { exact: true }).fill('Личное');
  await form
    .getByLabel('Начало блокировки', { exact: true })
    .fill('2026-03-29T02:30');
  await form
    .getByLabel('Окончание блокировки', { exact: true })
    .fill('2026-03-29T04:00');
  await form
    .getByRole('button', { name: 'Сохранить блокировку', exact: true })
    .click();
  await expect(
    form.getByLabel('Начало блокировки', { exact: true }),
  ).toHaveValue('2026-03-29T02:30');
  await expect(
    form.getByLabel('Начало блокировки', { exact: true }),
  ).toHaveAttribute('aria-invalid', 'true');
  await expect(
    form.getByLabel('Начало блокировки', { exact: true }),
  ).toHaveAttribute('aria-describedby', 'block-startsAt-error');
  await form
    .getByLabel('Начало блокировки', { exact: true })
    .fill('2026-10-25T02:30');
  await form
    .getByLabel('Окончание блокировки', { exact: true })
    .fill('2026-10-25T02:30');
  await expect(
    form.getByLabel('UTC-смещение начала', { exact: true }),
  ).toHaveValue('');
  await form
    .getByRole('button', { name: 'Сохранить блокировку', exact: true })
    .click();
  await expect(form).toContainText('Выберите UTC-смещение');
  await form
    .getByLabel('UTC-смещение начала', { exact: true })
    .selectOption('+02:00');
  await form
    .getByLabel('UTC-смещение окончания', { exact: true })
    .selectOption('+01:00');
  await form
    .getByRole('button', { name: 'Сохранить блокировку', exact: true })
    .focus();
  await page.keyboard.press('Enter');
  await expect(form).toHaveCount(0);
  const region = page.getByRole('region', {
    name: 'Блокировки времени',
    exact: true,
  });
  await expect(region).toContainText('Личное');
  await region
    .getByRole('button', { name: 'Редактировать блокировку', exact: true })
    .click();
  await form
    .getByLabel('Название блокировки', { exact: true })
    .fill('Изменено');
  await form
    .getByRole('button', { name: 'Сохранить блокировку', exact: true })
    .click();
  await expect(form).toHaveCount(0);
  await expect(region).toContainText('Изменено');
  await region
    .getByRole('button', { name: 'Редактировать блокировку', exact: true })
    .click();
  await form
    .getByRole('button', { name: 'Удалить блокировку', exact: true })
    .click();
  await expect(form).toHaveCount(0);
  const calendar = await (
    await request.get('/api/owner/calendar', {
      params: { from: '2026-01-01T00:00:00Z', to: '2027-01-01T00:00:00Z' },
    })
  ).json();
  expect(calendar.timeBlocks).toHaveLength(0);
  expect(calendar.items).toHaveLength(0);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});
