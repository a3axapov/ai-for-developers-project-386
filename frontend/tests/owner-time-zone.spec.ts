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
          id: 'Old',
          title: 'Старый тип',
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
        headers: { 'Idempotency-Key': 'enable' },
        data: { enabled: true, expectedVersion: 1 },
      })
    ).status(),
  ).toBe(200);
}

async function consequences(page: Page, zone: string, apply = false) {
  const form = page.getByRole('form', { name: 'Часовой пояс владельца' });
  await form.getByLabel('Новый часовой пояс').fill(zone);
  await form
    .getByLabel('Применить также к существующим активным типам встреч')
    .setChecked(apply);
  await form
    .getByRole('button', { name: 'Показать последствия смены пояса' })
    .click();
  return form;
}

test('затронутый запрос: отмена без изменений и новое согласие после подтверждения в другой вкладке', async ({
  page,
  request,
}) => {
  await seed(request, true);
  const { meetingType } = await (
    await request.get('/api/public/anna-1/meeting-types/Old')
  ).json();
  const attempt = await (
    await request.post('/api/public/anna-1/booking-attempts', {
      data: {
        meetingTypeId: 'Old',
        conditionsToken: meetingType.conditionsToken,
        startsAt: '2026-11-02T09:00:00+03:00',
        guestName: 'Гость',
        guestEmail: 'guest@example.com',
      },
    })
  ).json();
  const booked = await (
    await request.post(`/api/booking-attempts/${attempt.attemptId}/submit`, {
      headers: { Authorization: `Bearer ${attempt.attemptSecret}` },
    })
  ).json();
  await page.goto('/admin');
  const form = await consequences(page, 'Europe/Berlin', true);
  const keys: string[] = [];
  page.on('request', (req) => {
    if (req.url().endsWith('/api/owner/time-zone') && req.method() === 'PUT')
      keys.push(req.headers()['idempotency-key']);
  });
  await form.getByRole('button', { name: 'Применить новый пояс' }).click();
  const list = form.getByRole('list', {
    name: 'Затронутые записи при смене пояса',
  });
  await expect(list).toContainText('PENDING');
  await expect(list).toContainText('Гость');
  await form.getByRole('button', { name: 'Отменить смену пояса' }).click();
  expect(
    (await (await request.get('/api/owner/profile')).json()).timeZone,
  ).toBe('Europe/Moscow');
  expect(
    (await (await request.get('/api/owner/meeting-types/Old')).json()).version,
  ).toBe(1);
  await form
    .getByRole('button', { name: 'Показать последствия смены пояса' })
    .click();
  await form.getByRole('button', { name: 'Применить новый пояс' }).click();
  await expect(list).toContainText('PENDING');
  expect(
    (
      await request.post(`/api/owner/bookings/${booked.booking.id}/confirm`, {
        headers: { 'Idempotency-Key': 'confirm' },
        data: { expectedVersion: 1 },
      })
    ).status(),
  ).toBe(200);
  await form
    .getByRole('button', { name: 'Подтвердить последствия смены пояса' })
    .click();
  await expect(list).toContainText('CONFIRMED');
  expect(
    (await (await request.get('/api/owner/profile')).json()).timeZone,
  ).toBe('Europe/Moscow');
  await form
    .getByRole('button', { name: 'Подтвердить последствия смены пояса' })
    .click();
  await expect(page.locator('.profile-details')).toContainText('Europe/Berlin');
  expect(new Set(keys).size).toBe(4);
  const after = await (
    await request.get(`/api/owner/bookings/${booked.booking.id}`)
  ).json();
  expect(after.startsAt).toBe(booked.booking.startsAt);
  expect(after.status).toBe('CONFIRMED');
  expect(after.version).toBe(2);
});

test('ошибка IANA связана с полем, клавиатура сохраняет черновик и выбор типов выключен', async ({
  page,
}) => {
  await page.goto('/admin');
  const form = await consequences(page, 'Not/AZone');
  await form.getByRole('button', { name: 'Применить новый пояс' }).click();
  const input = form.getByLabel('Новый часовой пояс');
  await expect(input).toHaveAttribute('aria-invalid', 'true');
  await expect(input).toHaveAttribute('aria-describedby', 'owner-zone-error');
  await expect(input).toHaveValue('Not/AZone');
  await input.focus();
  await input.fill('Europe/Berlin');
  await input.press('Enter');
  await expect(
    form.getByRole('region', { name: 'Последствия смены пояса' }),
  ).toBeVisible();
  await expect(
    form.getByLabel('Применить также к существующим активным типам встреч'),
  ).not.toBeChecked();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
});

test('восстановление сохранённого конфликта читает текущий профиль, не подменяя его старым исходом', async ({
  page,
  request,
}) => {
  await seed(request);
  await page.goto('/admin');
  await page
    .getByRole('form', { name: 'Редактирование профиля' })
    .getByLabel('Имя', { exact: true })
    .fill('Мой черновик');
  const form = await consequences(page, 'Europe/Berlin', true);
  expect(
    (
      await request.put('/api/owner/profile', {
        headers: { 'Idempotency-Key': 'first-edit' },
        data: { expectedVersion: 2, name: 'Первое имя', username: 'anna-1' },
      })
    ).status(),
  ).toBe(200);
  await page.route('**/api/owner/time-zone', async (route) => {
    expect((await route.fetch()).status()).toBe(409);
    await route.abort('failed');
  });
  await form.getByRole('button', { name: 'Применить новый пояс' }).click();
  await expect(form.getByRole('alert')).toContainText(
    'Исход операции неизвестен',
  );
  expect(
    (
      await request.put('/api/owner/profile', {
        headers: { 'Idempotency-Key': 'second-edit' },
        data: {
          expectedVersion: 3,
          name: 'Актуальное имя',
          username: 'anna-1',
        },
      })
    ).status(),
  ).toBe(200);
  await page.unroute('**/api/owner/time-zone');
  await form
    .getByRole('button', { name: 'Выяснить результат смены пояса' })
    .click();
  await expect(page.locator('.profile-details')).toContainText(
    'Актуальное имя',
  );
  await expect(
    page
      .getByRole('form', { name: 'Редактирование профиля' })
      .getByLabel('Имя', { exact: true }),
  ).toHaveValue('Мой черновик');
  await expect(form.getByLabel('Новый часовой пояс')).toHaveValue(
    'Europe/Berlin',
  );
});

for (const outcome of ['delivered', 'not-delivered', 'reset'] as const) {
  test(`неизвестная смена пояса ${outcome}: глобальная блокировка, только чтение результата`, async ({
    page,
    request,
    restartServer,
  }) => {
    await seed(request);
    await page.goto('/admin');
    const form = await consequences(page, 'Europe/Berlin', true);
    let commands = 0;
    page.on('request', (req) => {
      if (req.url().endsWith('/api/owner/time-zone') && req.method() === 'PUT')
        commands++;
    });
    await page.route('**/api/owner/time-zone', async (route) => {
      if (outcome !== 'not-delivered') await route.fetch();
      await route.abort('failed');
    });
    await form.getByRole('button', { name: 'Применить новый пояс' }).click();
    await expect(form.getByRole('alert')).toContainText(
      'Исход операции неизвестен',
    );
    await expect(
      page.getByRole('button', { name: 'Сохранить профиль', exact: true }),
    ).toBeDisabled();
    await expect(
      page.getByRole('button', { name: 'Создать тип встречи', exact: true }),
    ).toBeDisabled();
    await expect(
      page.getByRole('button', { name: 'Добавить отпуск', exact: true }),
    ).toBeDisabled();
    await expect(
      page.getByRole('button', { name: 'Добавить блокировку', exact: true }),
    ).toBeDisabled();
    if (outcome === 'reset') await restartServer();
    await page.unroute('**/api/owner/time-zone');
    await form
      .getByRole('button', { name: 'Выяснить результат смены пояса' })
      .click();
    if (outcome !== 'delivered') {
      await expect(form.getByRole('alert')).toContainText(
        'Возможен сброс данных',
      );
      await expect(
        page.getByRole('button', { name: 'Сохранить профиль', exact: true }),
      ).toBeDisabled();
      await form
        .getByRole('button', { name: 'Загрузить актуальные профиль и типы' })
        .click();
    }
    await expect(
      page.getByRole('button', { name: 'Сохранить профиль', exact: true }),
    ).toBeEnabled();
    await expect(page.locator('.profile-details')).toContainText(
      outcome === 'delivered' ? 'Europe/Berlin' : 'Europe/Moscow',
    );
    if (outcome === 'reset')
      await expect(
        page.getByText('Типов встреч пока нет', { exact: true }),
      ).toBeVisible();
    else
      await expect(
        page.getByRole('article', { name: 'Старый тип' }),
      ).toContainText(
        outcome === 'delivered' ? 'Europe/Berlin' : 'Europe/Moscow',
      );
    expect(commands).toBe(1);
  });
}

test('существующая блокировка после смены пояса редактируется в новом поясе без сдвига и потери наносекунд', async ({
  page,
  request,
}) => {
  const response = await request.post('/api/owner/time-blocks', {
    headers: { 'Idempotency-Key': 'block' },
    data: {
      title: 'Личное',
      startsAt: '2026-10-25T04:30:00.123456789+03:00',
      endsAt: '2026-10-25T05:00:00.987654321+03:00',
    },
  });
  expect(response.status()).toBe(201);
  const original = (await response.json()).timeBlock;
  await page.goto('/admin');
  const blocks = page.getByRole('region', {
    name: 'Блокировки времени',
    exact: true,
  });
  await blocks
    .getByRole('button', { name: 'Редактировать блокировку' })
    .click();
  const form = page.getByRole('form', {
    name: 'Блокировка времени',
    exact: true,
  });
  await form
    .getByLabel('Название блокировки', { exact: true })
    .fill('Новое название');
  const zone = await consequences(page, 'Europe/Berlin');
  await zone.getByRole('button', { name: 'Применить новый пояс' }).click();
  await expect(page.locator('.profile-details')).toContainText('Europe/Berlin');
  await expect(
    form.getByLabel('Начало блокировки', { exact: true }),
  ).toHaveValue('2026-10-25T02:30:00.123456789');
  await expect(
    form.getByLabel('Название блокировки', { exact: true }),
  ).toHaveValue('Новое название');
  await form
    .getByRole('button', { name: 'Сохранить блокировку', exact: true })
    .click();
  await expect(form).toHaveCount(0);
  const after = (
    await (
      await request.get(
        '/api/owner/calendar?from=2026-10-25T00:00:00Z&to=2026-10-26T00:00:00Z',
      )
    ).json()
  ).timeBlocks[0];
  expect(Date.parse(after.startsAt)).toBe(Date.parse(original.startsAt));
  expect(after.startsAt).toContain('.123456789');
  expect(after.endsAt).toContain('.987654321');
  await blocks
    .getByRole('button', { name: 'Редактировать блокировку' })
    .click();
  await expect(
    form.getByLabel('Начало блокировки', { exact: true }),
  ).toHaveValue('2026-10-25T02:30:00.123456789');
  await form
    .getByRole('button', { name: 'Сохранить блокировку', exact: true })
    .click();
  await expect(form).toHaveCount(0);
});

for (const stillValid of [true, false]) {
  test(`открытая попытка при переводе типов: ${stillValid ? 'допустимый слот не теряется от версии пояса' : 'закрытое время сброшено, контакты сохранены'}`, async ({
    page,
    request,
  }) => {
    await seed(request);
    await page.goto('/booking/anna-1/Old');
    await page
      .getByRole('combobox', { name: 'Часовой пояс', exact: true })
      .selectOption('Europe/Moscow');
    await page.getByRole('button', { name: '02.11.2026', exact: true }).click();
    await page
      .getByRole('button', {
        name: stillValid
          ? '12:00 UTC+03:00 — 12:30 UTC+03:00'
          : '09:00 UTC+03:00 — 09:30 UTC+03:00',
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
        await request.put('/api/owner/time-zone', {
          headers: { 'Idempotency-Key': 'zone' },
          data: {
            timeZone: 'Europe/Berlin',
            applyToActiveTypes: true,
            expectedVersion: 2,
          },
        })
      ).status(),
    ).toBe(200);
    await page.getByRole('button', { name: 'Записаться', exact: true }).click();
    if (stillValid) await expect(page).toHaveURL(/\/bookings\/[A-Za-z0-9_-]+$/);
    else {
      await expect(
        page.getByRole('button', { name: 'Подготовить попытку' }),
      ).toHaveCount(0);
      await page
        .getByRole('combobox', { name: 'Часовой пояс', exact: true })
        .selectOption('Europe/Moscow');
      await page
        .getByRole('button', { name: '02.11.2026', exact: true })
        .click();
      await page.getByRole('button', { name: /^11:00/ }).click();
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

test.describe('текущее окно владельца отдельно от пояса типа', () => {
  test.use({
    fixedNow: '2026-11-01T22:30:00Z',
    serverArguments: ['--calendar.initial.time-zone=America/New_York'],
  });
  test('смена только владельца закрывает открытую форму у полуночи, не стирая контакты', async ({
    page,
    request,
  }) => {
    await seed(request);
    expect(
      (
        await request.put(
          '/api/owner/meeting-types/Old/date-overrides/2026-11-02',
          {
            headers: { 'Idempotency-Key': 'date' },
            data: {
              expectedVersion: 1,
              intervals: [{ start: '00:00', end: '24:00' }],
            },
          },
        )
      ).status(),
    ).toBe(200);
    await page.goto('/booking/anna-1/Old');
    await page
      .getByRole('combobox', { name: 'Часовой пояс', exact: true })
      .selectOption('America/New_York');
    await page.getByRole('button', { name: '02.11.2026', exact: true }).click();
    await page
      .getByRole('button', {
        name: '00:00 UTC-05:00 — 00:30 UTC-05:00',
        exact: true,
      })
      .click();
    await page.getByLabel('Ваше имя').fill('Гость');
    await page.getByLabel('Email', { exact: true }).fill('guest@example.com');
    await page.getByLabel('Комментарий').fill('Вопросы');
    await page.getByRole('button', { name: 'Подготовить попытку' }).click();
    await expect(
      page.getByRole('button', { name: 'Записаться', exact: true }),
    ).toBeVisible();
    expect(
      (
        await request.put('/api/owner/time-zone', {
          headers: { 'Idempotency-Key': 'zone' },
          data: {
            timeZone: 'Asia/Tokyo',
            applyToActiveTypes: false,
            expectedVersion: 2,
          },
        })
      ).status(),
    ).toBe(200);
    await page.getByRole('button', { name: 'Записаться', exact: true }).click();
    await expect(
      page.getByRole('button', { name: 'Подготовить попытку' }),
    ).toHaveCount(0);
    await page
      .getByRole('combobox', { name: 'Часовой пояс', exact: true })
      .selectOption('America/New_York');
    await page.getByRole('button', { name: '03.11.2026', exact: true }).click();
    await page.getByRole('button', { name: /^09:00/ }).click();
    await expect(page.getByLabel('Ваше имя')).toHaveValue('Гость');
    await expect(page.getByLabel('Email', { exact: true })).toHaveValue(
      'guest@example.com',
    );
    await expect(page.getByLabel('Комментарий')).toHaveValue('Вопросы');
    expect(
      (await (await request.get('/api/owner/meeting-types/Old')).json())
        .timeZone,
    ).toBe('America/New_York');
  });
});

test('пояс владельца: явные последствия, выключенный выбор типов и перевод местных правил', async ({
  page,
  request,
}) => {
  expect(
    (
      await request.post('/api/owner/meeting-types', {
        headers: { 'Idempotency-Key': 'seed' },
        data: {
          id: 'Old',
          title: 'Старый тип',
          description: 'Достаточно длинное описание встречи',
          durationMinutes: 30,
        },
      })
    ).status(),
  ).toBe(201);
  await page.goto('/admin');
  const form = page.getByRole('form', { name: 'Часовой пояс владельца' });
  const toggle = form.getByLabel(
    'Применить также к существующим активным типам встреч',
  );
  await expect(toggle).not.toBeChecked();
  await form.getByLabel('Новый часовой пояс').fill('Europe/Berlin');
  await form
    .getByRole('button', { name: 'Показать последствия смены пояса' })
    .click();
  const consequences = form.getByRole('region', {
    name: 'Последствия смены пояса',
  });
  await expect(consequences).toContainText('Europe/Berlin');
  await expect(consequences).toContainText('Существующие типы сохранят');
  await expect(consequences).toContainText(
    'Бронирования и блокировки не перемещаются',
  );
  await consequences
    .getByRole('button', { name: 'Применить новый пояс' })
    .click();
  await expect(page.locator('.profile-details')).toContainText('Europe/Berlin');
  expect(
    (await (await request.get('/api/owner/meeting-types/Old')).json()).timeZone,
  ).toBe('Europe/Moscow');
  await form.getByLabel('Новый часовой пояс').fill('America/New_York');
  await toggle.check();
  await form
    .getByRole('button', { name: 'Показать последствия смены пояса' })
    .click();
  await expect(consequences).toContainText('местные часы и даты');
  await consequences
    .getByRole('button', { name: 'Применить новый пояс' })
    .click();
  await expect(page.locator('.profile-details')).toContainText(
    'America/New_York',
  );
  expect(
    (await (await request.get('/api/owner/meeting-types/Old')).json()).timeZone,
  ).toBe('America/New_York');
  await expect(page.getByRole('article', { name: 'Старый тип' })).toContainText(
    'America/New_York',
  );
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
});
