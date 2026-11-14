import { expect, test } from './real-server';
import type { APIRequestContext, Page } from '@playwright/test';

async function seed(request: APIRequestContext) {
  expect(
    (
      await request.post('/api/owner/meeting-types', {
        headers: { 'Idempotency-Key': 'seed' },
        data: {
          id: 'Consult',
          title: 'Консультация',
          description: 'Достаточно длинное описание встречи',
          durationMinutes: 30,
        },
      })
    ).status(),
  ).toBe(201);
}

async function edit(page: Page) {
  await page
    .getByRole('article', { name: 'Консультация', exact: true })
    .getByRole('button', { name: 'Редактировать тип' })
    .click();
  return page.getByRole('form', { name: 'Редактирование типа встречи' });
}

test('владелец редактирует условия с клавиатуры и осознанно архивирует без восстановления', async ({
  page,
  request,
}) => {
  await seed(request);
  await page.goto('/admin');
  const form = await edit(page);
  await expect(
    form.getByRole('textbox', { name: 'ID типа', exact: true }),
  ).toBeDisabled();
  await form
    .getByRole('textbox', { name: 'Название типа', exact: true })
    .fill('Новая консультация');
  await form
    .getByRole('textbox', { name: 'Описание типа', exact: true })
    .fill('short');
  await form
    .getByRole('button', { name: 'Сохранить тип встречи', exact: true })
    .click();
  await expect(
    form.getByRole('textbox', { name: 'Описание типа', exact: true }),
  ).toHaveAttribute('aria-invalid', 'true');
  await expect(
    form.getByRole('textbox', { name: 'Название типа', exact: true }),
  ).toHaveValue('Новая консультация');
  await form
    .getByRole('textbox', { name: 'Описание типа', exact: true })
    .fill('Новое достаточно подробное описание встречи');
  await form.getByRole('spinbutton').fill('45');
  await form.getByRole('checkbox', { name: 'Ручное подтверждение' }).check();
  await form
    .getByRole('button', { name: 'Сохранить тип встречи', exact: true })
    .focus();
  await page.keyboard.press('Enter');
  const card = page.getByRole('article', {
    name: 'Новая консультация',
    exact: true,
  });
  await expect(card).toContainText('45 мин');
  await expect(card).toContainText('Требуется ручное подтверждение');
  page.once('dialog', async (dialog) => {
    expect(dialog.message()).toContain('необратимо');
    await dialog.dismiss();
  });
  await card.getByRole('button', { name: 'Архивировать тип' }).click();
  await expect(card).toContainText('Активный');
  page.once('dialog', (dialog) => dialog.accept());
  await card.getByRole('button', { name: 'Архивировать тип' }).click();
  await expect(card).toContainText('Архивный');
  await expect(
    card.getByRole('button', { name: 'Редактировать тип' }),
  ).toHaveCount(0);
  await expect(
    card.getByRole('button', { name: 'Архивировать тип' }),
  ).toHaveCount(0);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.goto('/booking/anna-1/Consult');
  // Disabled public booking takes priority over archive.
  await expect(
    page.getByText('Запись временно недоступна', { exact: true }),
  ).toBeVisible();
  await request.put('/api/owner/public-booking', {
    headers: { 'Idempotency-Key': 'open' },
    data: { enabled: true, expectedVersion: 1 },
  });
  await page.reload();
  await expect(
    page.getByText('Запись на этот тип встречи прекращена', { exact: true }),
  ).toBeVisible();
  await page.getByRole('link', { name: 'Все типы встреч' }).click();
  await expect(
    page.getByText('Сейчас нет доступных типов встреч'),
  ).toBeVisible();
});

for (const command of ['edit', 'archive'] as const) {
  test(`потерянный ответ ${command} блокирует все изменения до чтения сохранённого исхода`, async ({
    page,
    request,
  }) => {
    await seed(request);
    await page.goto('/admin');
    await page
      .getByRole('textbox', { name: 'Имя', exact: true })
      .fill('Черновик профиля');
    await page
      .getByRole('textbox', { name: 'ID типа', exact: true })
      .fill('Draft');
    let writes = 0;
    await page.route(
      '**/api/owner/meeting-types/Consult{,/archive}',
      async (route) => {
        if (route.request().method() === 'GET') return route.continue();
        writes++;
        const response = await route.fetch();
        expect(response.status()).toBe(200);
        await route.abort('failed');
      },
    );
    if (command === 'edit') {
      const form = await edit(page);
      await form
        .getByRole('textbox', { name: 'Название типа', exact: true })
        .fill('Новый заголовок');
      await form
        .getByRole('button', { name: 'Сохранить тип встречи', exact: true })
        .click();
    } else {
      page.once('dialog', (dialog) => dialog.accept());
      await page.getByRole('button', { name: 'Архивировать тип' }).click();
    }
    const recovery = page.getByRole('button', {
      name: 'Выяснить результат изменения типа',
    });
    await expect(recovery).toBeVisible();
    await expect(
      page.getByRole('button', { name: 'Сохранить профиль', exact: true }),
    ).toBeDisabled();
    await expect(
      page.getByRole('switch', { name: 'Публичная запись' }),
    ).toBeDisabled();
    await expect(
      page.getByRole('button', { name: 'Архивировать тип' }),
    ).toBeDisabled();
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
    await expect(recovery).toHaveCount(0);
    await expect(
      page.getByRole('button', { name: 'Сохранить профиль', exact: true }),
    ).toBeEnabled();
    await expect(
      page.getByRole('textbox', { name: 'Имя', exact: true }),
    ).toHaveValue('Черновик профиля');
    await expect(
      page.getByRole('textbox', { name: 'ID типа', exact: true }),
    ).toHaveValue('Draft');
    const card = page.getByRole('article');
    await expect(card).toContainText(
      command === 'edit' ? 'Новый заголовок' : 'Архивный',
    );
    expect(writes).toBe(1);
  });
}

test('конфликт версии показывает текущий тип отдельно от черновика, архивный тип не сохраняется', async ({
  page,
  request,
}) => {
  await seed(request);
  await page.goto('/admin');
  const form = await edit(page);
  await form
    .getByRole('textbox', { name: 'Название типа', exact: true })
    .fill('Мой черновик');
  expect(
    (
      await request.put('/api/owner/meeting-types/Consult', {
        headers: { 'Idempotency-Key': 'other-tab' },
        data: {
          title: 'Другая вкладка',
          description: 'Достаточно длинное описание встречи',
          durationMinutes: 30,
          requiresApproval: false,
          expectedVersion: 1,
        },
      })
    ).status(),
  ).toBe(200);
  await form
    .getByRole('button', { name: 'Сохранить тип встречи', exact: true })
    .click();
  await expect(page.getByRole('article')).toContainText('Другая вкладка');
  await expect(form).toContainText('Актуальные данные: Другая вкладка');
  await expect(
    form.getByRole('textbox', { name: 'Название типа', exact: true }),
  ).toHaveValue('Мой черновик');
  await form
    .getByRole('button', { name: 'Сохранить тип встречи', exact: true })
    .click();
  await expect(page.getByRole('article')).toContainText('Мой черновик');
  await page.getByRole('button', { name: 'Редактировать тип' }).click();
  expect(
    (
      await request.post('/api/owner/meeting-types/Consult/archive', {
        headers: { 'Idempotency-Key': 'archive-tab' },
        data: { expectedVersion: 3 },
      })
    ).status(),
  ).toBe(200);
  await form
    .getByRole('button', { name: 'Сохранить тип встречи', exact: true })
    .click();
  await expect(form).toContainText(
    'Архивный тип нельзя редактировать или восстановить',
  );
  await expect(
    form.getByRole('button', { name: 'Сохранить тип встречи', exact: true }),
  ).toBeDisabled();
  await expect(page.getByRole('article')).toContainText('Архивный');
});

test('сброс журнала неизвестного изменения требует явной загрузки, сохраняет черновики и не повторяет команду', async ({
  page,
  request,
  restartServer,
}) => {
  await seed(request);
  await page.goto('/admin');
  await page
    .getByRole('textbox', { name: 'Имя', exact: true })
    .fill('Черновик профиля');
  await page
    .getByRole('textbox', { name: 'ID типа', exact: true })
    .fill('CreateDraft');
  const form = await edit(page);
  await form
    .getByRole('textbox', { name: 'Название типа', exact: true })
    .fill('Черновик изменения');
  let writes = 0;
  await page.route('**/api/owner/meeting-types/Consult', async (route) => {
    if (route.request().method() !== 'PUT') return route.continue();
    writes++;
    await route.fetch();
    await route.abort('failed');
  });
  await form
    .getByRole('button', { name: 'Сохранить тип встречи', exact: true })
    .click();
  const recovery = page.getByRole('button', {
    name: 'Выяснить результат изменения типа',
  });
  await expect(recovery).toBeVisible();
  await restartServer();
  await recovery.click();
  await expect(
    page.getByText('Результат операции недоступен. Возможен сброс данных.', {
      exact: true,
    }),
  ).toBeVisible();
  await expect(
    page.getByRole('button', { name: 'Сохранить профиль', exact: true }),
  ).toBeDisabled();
  await page.getByRole('button', { name: 'Загрузить актуальные типы' }).click();
  await expect(
    page.getByRole('button', { name: 'Сохранить профиль', exact: true }),
  ).toBeEnabled();
  await expect(
    form.getByRole('textbox', { name: 'Название типа', exact: true }),
  ).toHaveValue('Черновик изменения');
  await expect(
    page.getByRole('textbox', { name: 'Имя', exact: true }),
  ).toHaveValue('Черновик профиля');
  await page
    .getByRole('button', { name: 'Отменить редактирование типа' })
    .click();
  await expect(
    page.getByRole('textbox', { name: 'ID типа', exact: true }),
  ).toHaveValue('CreateDraft');
  expect(writes).toBe(1);
  expect(
    (await (await request.get('/api/owner/meeting-types')).json()).items,
  ).toHaveLength(0);
});

test.describe('открытая гостевая форма', () => {
  test.use({
    fixedNow: '2026-11-01T00:00:00Z',
    timezoneId: 'Europe/Moscow',
    serverArguments: ['--calendar.initial.time-zone=Europe/Moscow'],
  });

  async function fillGuest(page: Page, request: APIRequestContext) {
    await seed(request);
    await request.post('/api/owner/meeting-types', {
      headers: { 'Idempotency-Key': 'other' },
      data: {
        id: 'Other',
        title: 'Другой тип',
        description: 'Достаточно подробное описание другого типа',
        durationMinutes: 30,
      },
    });
    await request.put('/api/owner/public-booking', {
      headers: { 'Idempotency-Key': 'open' },
      data: { enabled: true, expectedVersion: 1 },
    });
    await page.goto('/booking/anna-1/Consult');
    await page.getByRole('button', { name: '02.11.2026', exact: true }).click();
    await page
      .getByRole('button', {
        name: '09:00 UTC+03:00 — 09:30 UTC+03:00',
        exact: true,
      })
      .click();
    await page.getByLabel('Ваше имя').fill('Гость');
    await page.getByLabel('Email', { exact: true }).fill('guest@example.com');
    await page.getByLabel('Комментарий').fill('Мои вопросы');
  }

  test('архивирование между загрузкой условий и календаря показывает прекращение записи', async ({
    page,
    request,
  }) => {
    await seed(request);
    await request.put('/api/owner/public-booking', {
      headers: { 'Idempotency-Key': 'open' },
      data: { enabled: true, expectedVersion: 1 },
    });
    await page.route(
      '**/api/public/anna-1/meeting-types/Consult',
      async (route) => {
        const response = await route.fetch();
        expect(
          (
            await request.post('/api/owner/meeting-types/Consult/archive', {
              headers: { 'Idempotency-Key': 'archive' },
              data: { expectedVersion: 1 },
            })
          ).status(),
        ).toBe(200);
        await route.fulfill({ response });
      },
    );
    await page.goto('/booking/anna-1/Consult');
    await expect(
      page.getByText('Запись на этот тип встречи прекращена', { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole('link', { name: 'Все типы встреч' }),
    ).toBeVisible();
    await expect(
      page.getByRole('button', { name: 'Подготовить попытку' }),
    ).toHaveCount(0);
  });

  test('снимок записи и позднее восстановление успеха переживают правку, архив, смену адреса и выключение записи', async ({
    page,
    request,
    setServerNow,
    context,
  }) => {
    await fillGuest(page, request);
    await page.getByRole('button', { name: 'Подготовить попытку' }).click();
    await page.route('**/api/booking-attempts/*/submit', async (route) => {
      const response = await route.fetch();
      expect(response.status()).toBe(201);
      await route.abort('failed');
    });
    await page.getByRole('button', { name: 'Записаться', exact: true }).click();
    const recover = page.getByRole('button', {
      name: 'Выяснить результат записи',
    });
    await expect(recover).toBeVisible();
    expect(
      (
        await request.put('/api/owner/meeting-types/Consult', {
          headers: { 'Idempotency-Key': 'change' },
          data: {
            title: 'Новые условия',
            description: 'Другое достаточно подробное описание встречи',
            durationMinutes: 45,
            requiresApproval: true,
            expectedVersion: 1,
          },
        })
      ).status(),
    ).toBe(200);
    expect(
      (
        await request.post('/api/owner/meeting-types/Consult/archive', {
          headers: { 'Idempotency-Key': 'archive' },
          data: { expectedVersion: 2 },
        })
      ).status(),
    ).toBe(200);
    const owner = await context.newPage();
    await owner.goto('/admin');
    await expect(
      owner.getByRole('article', { name: 'Новые условия' }),
    ).toContainText('Архивный');
    const upcoming = owner.getByRole('region', {
      name: 'Бронирования владельца',
    });
    await expect(upcoming).toContainText('Консультация');
    await owner
      .getByLabel('Представление бронирований')
      .selectOption('calendar');
    await owner.getByLabel('Начало диапазона').fill('2026-11-02T00:00:00Z');
    await owner.getByLabel('Конец диапазона').fill('2026-11-03T00:00:00Z');
    await owner.getByRole('button', { name: 'Показать диапазон' }).click();
    await expect(
      owner.getByRole('list', { name: 'Список бронирований' }),
    ).toContainText('Консультация');
    await owner.close();
    await request.put('/api/owner/profile', {
      headers: { 'Idempotency-Key': 'rename' },
      data: {
        name: 'Другое имя',
        username: 'changed-owner',
        expectedVersion: 2,
      },
    });
    await request.put('/api/owner/public-booking', {
      headers: { 'Idempotency-Key': 'off' },
      data: { enabled: false, expectedVersion: 3 },
    });
    await setServerNow('2027-01-01T00:00:00Z');
    await recover.click();
    await expect(
      page.getByRole('heading', { name: 'Результат бронирования' }),
    ).toBeVisible();
    await expect(page.getByText('Консультация', { exact: true })).toBeVisible();
    await expect(
      page.getByText('Достаточно длинное описание встречи', { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByText('Автоматическое подтверждение', { exact: true }),
    ).toBeVisible();
    await expect(page.getByText('Подтверждено', { exact: true })).toBeVisible();
    await expect(page.getByText('Новые условия', { exact: true })).toHaveCount(
      0,
    );
  });

  for (const prepared of [false, true]) {
    test(`смена условий снимает время и сохраняет контакты; подготовлена: ${prepared}`, async ({
      page,
      request,
    }) => {
      await fillGuest(page, request);
      if (prepared)
        await page.getByRole('button', { name: 'Подготовить попытку' }).click();
      expect(
        (
          await request.put('/api/owner/meeting-types/Consult', {
            headers: { 'Idempotency-Key': 'edit' },
            data: {
              title: 'Новые условия',
              description: 'Новое достаточно длинное описание встречи',
              durationMinutes: 45,
              requiresApproval: true,
              expectedVersion: 1,
            },
          })
        ).status(),
      ).toBe(200);
      const failure = page.waitForResponse(
        (response) => response.status() === 409,
      );
      await page
        .getByRole('button', {
          name: prepared ? 'Записаться' : 'Подготовить попытку',
          exact: true,
        })
        .click();
      expect((await (await failure).json()).code).toBe('CONDITIONS_CHANGED');
      await expect(
        page.getByRole('heading', { name: 'Новые условия', exact: true }),
      ).toBeVisible();
      await expect(page.getByText('45 мин', { exact: true })).toBeVisible();
      await expect(
        page.getByText('Требуется ручное подтверждение', { exact: true }),
      ).toBeVisible();
      await expect(page.getByLabel('Ваше имя')).toHaveCount(0);
      await expect(
        page.getByRole('button', { name: 'Записаться', exact: true }),
      ).toHaveCount(0);
      expect(
        (await (await request.get('/api/owner/bookings?view=upcoming')).json())
          .items,
      ).toHaveLength(0);
      await page
        .getByRole('button', { name: '02.11.2026', exact: true })
        .click();
      await page
        .getByRole('button', {
          name: '09:00 UTC+03:00 — 09:45 UTC+03:00',
          exact: true,
        })
        .click();
      await expect(page.getByLabel('Ваше имя')).toHaveValue('Гость');
      await expect(page.getByLabel('Email', { exact: true })).toHaveValue(
        'guest@example.com',
      );
      await expect(page.getByLabel('Комментарий')).toHaveValue('Мои вопросы');
      await page.getByRole('button', { name: 'Подготовить попытку' }).click();
      await page
        .getByRole('button', { name: 'Записаться', exact: true })
        .click();
      await expect(
        page.getByRole('heading', { name: 'Результат бронирования' }),
      ).toBeVisible();
      await expect(
        page.getByText('Новые условия', { exact: true }),
      ).toBeVisible();
      await expect(
        page.getByText('Ожидает подтверждения', { exact: true }),
      ).toBeVisible();
    });

    test(`архивирование открытой формы предлагает другой тип с сохранёнными контактами; подготовлена: ${prepared}`, async ({
      page,
      request,
    }) => {
      await fillGuest(page, request);
      if (prepared)
        await page.getByRole('button', { name: 'Подготовить попытку' }).click();
      expect(
        (
          await request.post('/api/owner/meeting-types/Consult/archive', {
            headers: { 'Idempotency-Key': 'archive' },
            data: { expectedVersion: 1 },
          })
        ).status(),
      ).toBe(200);
      await page
        .getByRole('button', {
          name: prepared ? 'Записаться' : 'Подготовить попытку',
          exact: true,
        })
        .click();
      await expect(
        page
          .getByText('Запись на этот тип встречи прекращена', { exact: true })
          .first(),
      ).toBeVisible();
      await expect(
        page.getByRole('button', { name: 'Подготовить попытку' }),
      ).toHaveCount(0);
      await page.getByRole('link', { name: 'Все типы встреч' }).click();
      await expect(
        page.getByRole('link', { name: 'Выбрать тип «Консультация»' }),
      ).toHaveCount(0);
      await page
        .getByRole('link', { name: 'Выбрать тип «Другой тип»' })
        .click();
      await page
        .getByRole('button', { name: '02.11.2026', exact: true })
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
      await expect(page.getByLabel('Комментарий')).toHaveValue('Мои вопросы');
      expect(
        (await (await request.get('/api/owner/bookings?view=upcoming')).json())
          .items,
      ).toHaveLength(0);
    });
  }
});
