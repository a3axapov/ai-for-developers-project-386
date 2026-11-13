import { expect, test } from './real-server';
import type { Page, Route } from '@playwright/test';

test('создание ждёт загрузки исходного списка и не теряется из-за запоздалого чтения', async ({
  page,
}) => {
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route('**/api/owner/meeting-types', async (route) => {
    if (route.request().method() !== 'GET') return route.continue();
    const response = await route.fetch();
    await gate;
    await route.fulfill({ response });
  });
  try {
    await page.goto('/admin');
    await expect(page.getByText('Загрузка типов встреч…')).toBeVisible();
    await fillType(page);
    await expect(
      page.getByRole('button', { name: 'Создать тип встречи', exact: true }),
    ).toBeDisabled();
  } finally {
    release();
  }
  await expect(page.getByText('Типов встреч пока нет')).toBeVisible();
  await page
    .getByRole('button', { name: 'Создать тип встречи', exact: true })
    .click();
  await expect(page.getByRole('article')).toHaveCount(1);
});

async function fillType(page: Page, id = 'Consult') {
  await page.getByRole('textbox', { name: 'ID типа', exact: true }).fill(id);
  await page
    .getByRole('textbox', { name: 'Название типа', exact: true })
    .fill('  Консультация   по проекту  ');
  await page
    .getByRole('textbox', { name: 'Описание типа', exact: true })
    .fill('  Обсудим   вопросы проекта и следующие шаги  ');
  await page
    .getByRole('spinbutton', { name: 'Длительность, минут' })
    .fill('30');
}

test('владелец создаёт настоящий тип, видит условия и копирует текущую прямую ссылку', async ({
  page,
  context,
  request,
}) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await page.goto('/admin');
  await expect(page.getByText('Типов встреч пока нет')).toBeVisible();
  await fillType(page);
  const approval = page.getByRole('checkbox', { name: 'Ручное подтверждение' });
  await expect(approval).not.toBeChecked();
  await approval.check();
  const save = page.getByRole('button', {
    name: 'Создать тип встречи',
    exact: true,
  });
  await save.focus();
  await page.keyboard.press('Enter');
  const card = page.getByRole('article', { name: 'Консультация по проекту' });
  await expect(card).toContainText('Обсудим вопросы проекта и следующие шаги');
  await expect(card).toContainText('30 мин');
  await expect(card).toContainText('Требуется ручное подтверждение');
  await expect(card).toContainText('Pacific/Auckland');
  await expect(card).toContainText('09:00–18:00');
  await expect(card.getByRole('link')).toHaveAttribute(
    'href',
    '/booking/anna-1/Consult',
  );
  await card.getByRole('button', { name: 'Копировать прямую ссылку' }).click();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toMatch(
    /\/booking\/anna-1\/Consult$/,
  );
  const types = await (await request.get('/api/owner/meeting-types')).json();
  expect(types.items).toHaveLength(1);
  expect(types.items[0]).toMatchObject({
    id: 'Consult',
    requiresApproval: true,
    timeZone: 'Pacific/Auckland',
  });
  await page
    .getByRole('textbox', { name: 'Username', exact: true })
    .fill('new-owner');
  page.once('dialog', (dialog) => dialog.accept());
  await page
    .getByRole('button', { name: 'Сохранить профиль', exact: true })
    .click();
  await expect(card.getByRole('link')).toHaveAttribute(
    'href',
    '/booking/new-owner/Consult',
  );
  await card.getByRole('button', { name: 'Копировать прямую ссылку' }).click();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toMatch(
    /\/booking\/new-owner\/Consult$/,
  );
  await page.reload();
  await expect(card).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.goto('/booking/anna-1/Consult');
  await expect(
    page.getByRole('heading', { name: 'Страница не найдена' }),
  ).toBeVisible();
  await expect(page).toHaveURL('/booking/anna-1/Consult');
  await page.goto('/booking/new-owner/Consult');
  await expect(page.getByText('Запись временно недоступна')).toBeVisible();
});

test('гость явно выбирает даже единственный тип, открывает прямую ссылку и возвращается к списку', async ({
  page,
  request,
}) => {
  expect(
    (
      await request.post('/api/owner/meeting-types', {
        headers: { 'Idempotency-Key': 'seed' },
        data: {
          id: 'Only',
          title: 'Единственный тип',
          description: 'Достаточно подробное описание для гостя',
          durationMinutes: 45,
          requiresApproval: true,
        },
      })
    ).status(),
  ).toBe(201);
  expect(
    (
      await request.put('/api/owner/public-booking', {
        headers: { 'Idempotency-Key': 'open' },
        data: { enabled: true, expectedVersion: 1 },
      })
    ).status(),
  ).toBe(200);
  await page.goto('/booking/anna-1');
  await expect(
    page.getByRole('heading', { name: 'Анна Петрова', exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText('Достаточно подробное описание для гостя'),
  ).toBeVisible();
  await expect(page.getByText('45 мин', { exact: false })).toBeVisible();
  await expect(
    page.getByRole('heading', { name: 'Выбранный тип встречи' }),
  ).toHaveCount(0);
  const choose = page.getByRole('link', {
    name: 'Выбрать тип «Единственный тип»',
  });
  await choose.focus();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL('/booking/anna-1/Only');
  await expect(
    page.getByRole('heading', { name: 'Выбранный тип встречи' }),
  ).toBeVisible();
  await expect(page.getByText('Требуется ручное подтверждение')).toBeVisible();
  await page.reload();
  await expect(
    page.getByRole('heading', { name: 'Единственный тип', exact: true }),
  ).toBeVisible();
  await page.getByRole('link', { name: 'Все типы встреч' }).click();
  await expect(page).toHaveURL('/booking/anna-1');
  await expect(choose).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await request.put('/api/owner/public-booking', {
    headers: { 'Idempotency-Key': 'close' },
    data: { enabled: false, expectedVersion: 2 },
  });
  await page.goto('/booking/anna-1/Only');
  await expect(page.getByText('Запись временно недоступна')).toBeVisible();
  await expect(
    page.getByText('Достаточно подробное описание для гостя'),
  ).toHaveCount(0);
  await page.goto('/booking/anna-1/only');
  await expect(
    page.getByRole('heading', { name: 'Страница не найдена' }),
  ).toBeVisible();
  await expect(page).toHaveURL('/booking/anna-1/only');
});

test('потерянный ответ создания блокирует все изменения и восстанавливает тип без повтора команды', async ({
  page,
  request,
}) => {
  let commands = 0;
  await page.route('**/api/owner/meeting-types', async (route) => {
    if (route.request().method() !== 'POST') return route.continue();
    commands++;
    expect((await route.fetch()).status()).toBe(201);
    await route.abort('connectionreset');
  });
  await page.goto('/admin');
  await fillType(page);
  const create = page.getByRole('button', {
    name: 'Создать тип встречи',
    exact: true,
  });
  await create.click();
  await expect(page.getByRole('alert')).toContainText(
    'Исход операции неизвестен',
  );
  await expect(create).toBeDisabled();
  await expect(page.getByRole('switch')).toBeDisabled();
  await expect(
    page.getByRole('button', { name: 'Сохранить профиль', exact: true }),
  ).toBeDisabled();
  await expect(
    page.getByRole('textbox', { name: 'ID типа', exact: true }),
  ).toHaveValue('Consult');
  await page
    .getByRole('button', { name: 'Выяснить результат создания' })
    .click();
  await expect(
    page.getByRole('article', { name: 'Консультация по проекту' }),
  ).toBeVisible();
  await expect(create).toBeEnabled();
  await expect(page.getByRole('switch')).toBeEnabled();
  expect(commands).toBe(1);
  expect(
    (await (await request.get('/api/owner/meeting-types')).json()).items,
  ).toHaveLength(1);
});

test('ошибки полей связаны с вводом, дробная длительность не теряет черновик, исправление использует новый ключ', async ({
  page,
  request,
}) => {
  await page.goto('/admin');
  await fillType(page, 'invalid_id');
  const description = page.getByRole('textbox', {
    name: 'Описание типа',
    exact: true,
  });
  const duration = page.getByRole('spinbutton', {
    name: 'Длительность, минут',
  });
  const create = page.getByRole('button', {
    name: 'Создать тип встречи',
    exact: true,
  });
  await description.fill('Коротко');
  await duration.fill('0');
  await create.focus();
  await page.keyboard.press('Enter');
  for (const field of ['id', 'description', 'durationMinutes']) {
    await expect(page.locator(`#meeting-${field}`)).toHaveAttribute(
      'aria-invalid',
      'true',
    );
    await expect(page.locator(`#meeting-${field}`)).toHaveAttribute(
      'aria-describedby',
      `meeting-${field}-error`,
    );
    await expect(page.locator(`#meeting-${field}-error`)).not.toBeEmpty();
  }
  await expect(description).toHaveValue('Коротко');
  expect(
    (await (await request.get('/api/owner/meeting-types')).json()).items,
  ).toHaveLength(0);
  await fillType(page);
  await duration.fill('1.5');
  await create.click();
  await expect(duration).toHaveAttribute('aria-invalid', 'true');
  await expect(duration).toHaveValue('1.5');
  const keys: string[] = [];
  page.on('request', (req) => {
    if (req.method() === 'POST') keys.push(req.headers()['idempotency-key']);
  });
  await duration.fill('30');
  await create.click();
  await expect(page.getByRole('article')).toHaveCount(1);
  await fillType(page, 'Other');
  await create.click();
  await expect(page.getByRole('alert')).toContainText('уже существует');
  await expect(
    page.getByRole('textbox', { name: 'ID типа', exact: true }),
  ).toHaveValue('Other');
  await duration.fill('31');
  await create.click();
  await expect(page.getByRole('article')).toHaveCount(2);
  expect(new Set(keys).size).toBe(3);
  await request.put('/api/owner/public-booking', {
    headers: { 'Idempotency-Key': 'open' },
    data: { enabled: true, expectedVersion: 1 },
  });
  await page.goto('/booking/anna-1');
  const cards = page.getByRole('article');
  await expect(cards).toHaveCount(2);
  await expect(cards.nth(0)).toContainText('30 мин');
  await expect(cards.nth(1)).toContainText('31 мин');
  await expect(cards.nth(0).getByRole('link')).toHaveAttribute(
    'href',
    '/booking/anna-1/Consult',
  );
  await expect(cards.nth(1).getByRole('link')).toHaveAttribute(
    'href',
    '/booking/anna-1/Other',
  );
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});

test('потерянный отказ создания восстанавливает ошибки без потери данных', async ({
  page,
  request,
}) => {
  let commands = 0;
  await page.route('**/api/owner/meeting-types', async (route) => {
    if (route.request().method() !== 'POST') return route.continue();
    commands++;
    expect((await route.fetch()).status()).toBe(422);
    await route.abort('connectionreset');
  });
  await page.goto('/admin');
  await fillType(page, 'bad_id');
  await page
    .getByRole('button', { name: 'Создать тип встречи', exact: true })
    .click();
  await page
    .getByRole('button', { name: 'Выяснить результат создания' })
    .click();
  const id = page.getByRole('textbox', { name: 'ID типа', exact: true });
  await expect(id).toHaveValue('bad_id');
  await expect(id).toHaveAttribute('aria-invalid', 'true');
  await expect(page.getByRole('switch')).toBeEnabled();
  expect(commands).toBe(1);
  expect(
    (await (await request.get('/api/owner/meeting-types')).json()).items,
  ).toHaveLength(0);
});

test('недоступный исход создания требует явной загрузки и не отправляет новую команду', async ({
  page,
  request,
}) => {
  let commands = 0;
  await page.route('**/api/owner/meeting-types', async (route) => {
    if (route.request().method() !== 'POST') return route.continue();
    commands++;
    await route.abort('connectionreset');
  });
  await page.goto('/admin');
  await fillType(page);
  const create = page.getByRole('button', {
    name: 'Создать тип встречи',
    exact: true,
  });
  await create.click();
  await page
    .getByRole('button', { name: 'Выяснить результат создания' })
    .click();
  await expect(page.getByRole('alert')).toContainText('Возможен сброс данных');
  await expect(create).toBeDisabled();
  await expect(page.getByRole('switch')).toBeDisabled();
  await page.getByRole('button', { name: 'Загрузить актуальные типы' }).click();
  await expect(create).toBeEnabled();
  await expect(
    page.getByRole('textbox', { name: 'ID типа', exact: true }),
  ).toHaveValue('Consult');
  await expect(
    page.getByRole('textbox', { name: 'Описание типа', exact: true }),
  ).toHaveValue('  Обсудим   вопросы проекта и следующие шаги  ');
  expect(commands).toBe(1);
  expect(
    (await (await request.get('/api/owner/meeting-types')).json()).items,
  ).toHaveLength(0);
});

test('ошибка обновления профиля после недоступного исхода не уничтожает черновик и блокировку', async ({
  page,
}) => {
  await page.route('**/api/owner/meeting-types', async (route) => {
    if (route.request().method() !== 'POST') return route.continue();
    await route.abort('connectionreset');
  });
  await page.goto('/admin');
  await fillType(page);
  await page
    .getByRole('button', { name: 'Создать тип встречи', exact: true })
    .click();
  await page
    .getByRole('button', { name: 'Выяснить результат создания' })
    .click();
  await page.route('**/api/owner/profile', (route) =>
    route.abort('connectionreset'),
  );
  await page.getByRole('button', { name: 'Загрузить актуальные типы' }).click();
  await expect(page.getByRole('alert')).toContainText(
    'Не удалось загрузить актуальные типы',
  );
  await expect(
    page.getByRole('textbox', { name: 'ID типа', exact: true }),
  ).toHaveValue('Consult');
  await expect(page.getByRole('switch')).toBeDisabled();
  await page.unroute('**/api/owner/profile');
  await page.getByRole('button', { name: 'Загрузить актуальные типы' }).click();
  await expect(page.getByRole('switch')).toBeEnabled();
  await expect(
    page.getByRole('textbox', { name: 'ID типа', exact: true }),
  ).toHaveValue('Consult');
});

test('неизвестный исход профиля блокирует создание типа до выяснения', async ({
  page,
}) => {
  await page.route('**/api/owner/profile', async (route) => {
    if (route.request().method() !== 'PUT') return route.continue();
    expect((await route.fetch()).status()).toBe(200);
    await route.abort('connectionreset');
  });
  await page.goto('/admin');
  await fillType(page);
  await page
    .getByRole('button', { name: 'Сохранить профиль', exact: true })
    .click();
  await expect(
    page.getByRole('button', { name: 'Создать тип встречи', exact: true }),
  ).toBeDisabled();
  await page
    .getByRole('button', { name: 'Выяснить результат', exact: true })
    .click();
  await expect(
    page.getByRole('button', { name: 'Создать тип встречи', exact: true }),
  ).toBeEnabled();
  await expect(
    page.getByRole('textbox', { name: 'ID типа', exact: true }),
  ).toHaveValue('Consult');
});

for (const command of ['public-booking', 'profile'] as const) {
  for (const failedReload of [false, true]) {
    test(`черновик типа целиком сохраняется: ${command}, ${failedReload ? 'ошибка загрузки и повтор' : 'успешная загрузка'} после 410`, async ({
      page,
      request,
    }) => {
      let commands = 0;
      let creations = 0;
      let key = '';
      page.on('request', (req) => {
        if (req.method() === 'PUT') commands++;
        if (
          req.method() === 'POST' &&
          new URL(req.url()).pathname === '/api/owner/meeting-types'
        )
          creations++;
      });
      await page.route(`**/api/owner/${command}`, async (route) => {
        if (route.request().method() !== 'PUT') return route.continue();
        key = route.request().headers()['idempotency-key'];
        await route.abort('connectionreset');
      });
      await page.goto('/admin');
      await expect(page.getByText('Типов встреч пока нет')).toBeVisible();
      await fillType(page, 'Unsaved-Draft');
      await page
        .getByRole('spinbutton', { name: 'Длительность, минут' })
        .fill('45');
      await page
        .getByRole('checkbox', { name: 'Ручное подтверждение' })
        .check();
      const create = page.getByRole('button', {
        name: 'Создать тип встречи',
        exact: true,
      });
      const expectDraft = async () => {
        await expect
          .poll(async () => ({
            id: await page
              .getByRole('textbox', { name: 'ID типа', exact: true })
              .inputValue(),
            title: await page
              .getByRole('textbox', { name: 'Название типа', exact: true })
              .inputValue(),
            description: await page
              .getByRole('textbox', { name: 'Описание типа', exact: true })
              .inputValue(),
            duration: await page
              .getByRole('spinbutton', { name: 'Длительность, минут' })
              .inputValue(),
            approval: await page
              .getByRole('checkbox', { name: 'Ручное подтверждение' })
              .isChecked(),
          }))
          .toEqual({
            id: 'Unsaved-Draft',
            title: '  Консультация   по проекту  ',
            description: '  Обсудим   вопросы проекта и следующие шаги  ',
            duration: '45',
            approval: true,
          });
      };
      if (command === 'profile') {
        await page
          .getByRole('textbox', { name: 'Имя', exact: true })
          .fill('Несохранённое имя');
        await page
          .getByRole('button', { name: 'Сохранить профиль', exact: true })
          .click();
      } else {
        await page.getByRole('switch').click();
      }
      await expect(page.getByRole('alert')).toContainText(
        'Исход операции неизвестен',
      );
      await expect(create).toBeDisabled();
      await expectDraft();
      const lookup = page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === `/api/owner/operations/${key}`,
      );
      await page
        .getByRole('button', { name: 'Выяснить результат', exact: true })
        .click();
      const outcome = await lookup;
      expect(outcome.status()).toBe(410);
      expect(await outcome.json()).toMatchObject({
        code: 'OPERATION_UNAVAILABLE',
      });
      await expect(page.getByRole('alert')).toContainText(
        'Возможен сброс данных',
      );
      await expect(create).toBeDisabled();
      await expectDraft();

      let release!: () => void;
      const gate = new Promise<void>((resolve) => {
        release = resolve;
      });
      const reload = async (route: Route) => {
        if (route.request().method() !== 'GET') return route.fallback();
        await gate;
        if (failedReload) return route.abort('connectionreset');
        return route.continue();
      };
      await page.route('**/api/owner/profile', reload);
      try {
        await page
          .getByRole('button', { name: 'Загрузить актуальный профиль' })
          .click();
        await expect(page.getByText('Загрузка профиля…')).toBeVisible();
        await expect(create).toHaveCount(0);
        expect(creations).toBe(0);
        expect(commands).toBe(1);
      } finally {
        release();
      }
      if (failedReload) {
        await expect(page.getByRole('alert')).toContainText(
          'Не удалось загрузить профиль',
        );
        await expect(create).toHaveCount(0);
        expect(creations).toBe(0);
        expect(commands).toBe(1);
        await page.unroute('**/api/owner/profile', reload);
        await page
          .getByRole('button', { name: 'Повторить загрузку', exact: true })
          .click();
      }
      await expect(create).toBeEnabled();
      await expectDraft();
      if (command === 'profile') {
        await expect(
          page.getByRole('textbox', { name: 'Имя', exact: true }),
        ).toHaveValue('Несохранённое имя');
      }
      expect(creations).toBe(0);
      expect(commands).toBe(1);
      expect(
        (await (await request.get('/api/owner/meeting-types')).json()).items,
      ).toEqual([]);
      const profile = await (await request.get('/api/owner/profile')).json();
      expect(profile).toMatchObject({
        name: 'Анна Петрова',
        bookingEnabled: false,
        version: 1,
      });
    });
  }
}

test('ошибки загрузки типов и прямой страницы отличаются от пустого списка и допускают повтор', async ({
  page,
  request,
}) => {
  await page.route('**/api/owner/meeting-types', (route) =>
    route.abort('connectionreset'),
  );
  await page.goto('/admin');
  await expect(page.getByRole('alert')).toContainText(
    'Не удалось загрузить типы',
  );
  await expect(page.getByText('Типов встреч пока нет')).toHaveCount(0);
  await fillType(page);
  await page.unroute('**/api/owner/meeting-types');
  await page.getByRole('button', { name: 'Повторить загрузку типов' }).click();
  await expect(page.getByText('Типов встреч пока нет')).toBeVisible();
  await expect(
    page.getByRole('textbox', { name: 'ID типа', exact: true }),
  ).toHaveValue('Consult');
  await page
    .getByRole('button', { name: 'Создать тип встречи', exact: true })
    .click();
  await expect(page.getByRole('article')).toHaveCount(1);
  await request.put('/api/owner/public-booking', {
    headers: { 'Idempotency-Key': 'open' },
    data: { enabled: true, expectedVersion: 1 },
  });
  await page.route('**/api/public/anna-1/meeting-types/Consult', (route) =>
    route.abort('connectionreset'),
  );
  await page.goto('/booking/anna-1/Consult');
  await expect(page.getByRole('alert')).toContainText(
    'Не удалось загрузить страницу',
  );
  await expect(page.getByText('Сейчас нет доступных типов встреч')).toHaveCount(
    0,
  );
  await page.unroute('**/api/public/anna-1/meeting-types/Consult');
  await page
    .getByRole('button', { name: 'Повторить загрузку', exact: true })
    .click();
  await expect(
    page.getByRole('heading', { name: 'Консультация по проекту', exact: true }),
  ).toBeVisible();
});
