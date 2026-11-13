import { expect, test } from './real-server';

test('переносы строк не удаляются браузером молча и возвращаются как ошибка поля', async ({
  page,
  request,
}) => {
  await page.goto('/admin');
  const name = page.getByRole('textbox', { name: 'Имя', exact: true });
  await name.fill('Имя\nс переносом');
  await page
    .getByRole('button', { name: 'Сохранить профиль', exact: true })
    .click();
  await expect(name).toHaveAttribute('aria-invalid', 'true');
  await expect(name).toHaveValue('Имя\nс переносом');
  expect((await (await request.get('/api/owner/profile')).json()).version).toBe(
    1,
  );
});

test('конфликт показывает актуальные сведения, сохраняет ввод и требует нового осознанного сохранения', async ({
  page,
  context,
  request,
}) => {
  await page.goto('/admin');
  const other = await context.newPage();
  await other.goto('/admin');
  const name = page.getByRole('textbox', { name: 'Имя', exact: true });
  const username = page.getByRole('textbox', { name: 'Username', exact: true });
  await name.fill('Мой черновик');
  await username.fill('my-address');
  await other
    .getByRole('textbox', { name: 'Имя', exact: true })
    .fill('Из другой вкладки');
  await other
    .getByRole('textbox', { name: 'Username', exact: true })
    .fill('other-address');
  other.once('dialog', (dialog) => dialog.accept());
  await other
    .getByRole('button', { name: 'Сохранить профиль', exact: true })
    .click();
  await expect(other.locator('dd').first()).toHaveText('Из другой вкладки');
  const keys: string[] = [];
  page.on('request', (req) => {
    if (req.method() === 'PUT') keys.push(req.headers()['idempotency-key']);
  });
  page.once('dialog', (dialog) => dialog.accept());
  await page
    .getByRole('button', { name: 'Сохранить профиль', exact: true })
    .click();
  await expect(page.getByRole('alert')).toContainText('Профиль изменился');
  await expect(page.locator('dd').first()).toHaveText('Из другой вкладки');
  await expect(
    page.getByRole('link', { name: /\/booking\/other-address$/ }),
  ).toHaveAttribute('href', '/booking/other-address');
  await expect(name).toHaveValue('Мой черновик');
  await expect(username).toHaveValue('my-address');
  expect(keys).toHaveLength(1);
  expect((await (await request.get('/api/owner/profile')).json()).version).toBe(
    2,
  );
  page.once('dialog', async (dialog) => {
    expect(dialog.message()).toContain('/booking/other-address');
    await dialog.accept();
  });
  await page
    .getByRole('button', { name: 'Сохранить профиль', exact: true })
    .click();
  await expect(page.locator('dd').first()).toHaveText('Мой черновик');
  expect(keys).toHaveLength(2);
  expect(keys[0]).not.toBe(keys[1]);
  expect((await (await request.get('/api/owner/profile')).json()).version).toBe(
    3,
  );
});

test('потерянный ответ валидации восстанавливает ошибки полей и сохраняет ввод для исправления с новым ключом', async ({
  page,
  request,
}) => {
  const keys: string[] = [];
  await page.route('**/api/owner/profile', async (route) => {
    if (route.request().method() !== 'PUT') return route.continue();
    keys.push(route.request().headers()['idempotency-key']);
    expect((await route.fetch()).status()).toBe(422);
    await route.abort('connectionreset');
  });
  await page.goto('/admin');
  const name = page.getByRole('textbox', { name: 'Имя', exact: true });
  await name.fill('Имя\u034f');
  await page
    .getByRole('button', { name: 'Сохранить профиль', exact: true })
    .click();
  await expect(page.getByRole('alert')).toContainText(
    'Исход операции неизвестен',
  );
  await page.getByRole('button', { name: 'Выяснить результат' }).click();
  await expect(name).toHaveAttribute('aria-invalid', 'true');
  await expect(name).toHaveValue('Имя\u034f');
  await expect(page.getByRole('alert')).toContainText('Проверьте данные');
  expect(keys).toHaveLength(1);
  expect((await (await request.get('/api/owner/profile')).json()).version).toBe(
    1,
  );
  await page.unroute('**/api/owner/profile');
  await name.fill('Исправленное имя');
  const response = page.waitForResponse(
    (res) => res.request().method() === 'PUT',
  );
  await page
    .getByRole('button', { name: 'Сохранить профиль', exact: true })
    .click();
  const saved = await response;
  expect(saved.status()).toBe(200);
  expect(saved.request().headers()['idempotency-key']).not.toBe(keys[0]);
  await expect(page.getByRole('alert')).toHaveCount(0);
});

test('недоступный исход требует явной загрузки, которая не уничтожает черновик и не повторяет команду', async ({
  page,
  request,
}) => {
  let commands = 0;
  await page.route('**/api/owner/profile', async (route) => {
    if (route.request().method() !== 'PUT') return route.continue();
    commands++;
    await route.abort('connectionreset');
  });
  await page.goto('/admin');
  const name = page.getByRole('textbox', { name: 'Имя', exact: true });
  await name.fill('Несохранённое имя');
  await page
    .getByRole('button', { name: 'Сохранить профиль', exact: true })
    .click();
  await page.getByRole('button', { name: 'Выяснить результат' }).click();
  await expect(page.getByRole('alert')).toContainText('Возможен сброс данных');
  await expect(page.getByRole('switch')).toBeDisabled();
  await page
    .getByRole('button', { name: 'Загрузить актуальный профиль' })
    .click();
  await expect(name).toHaveValue('Несохранённое имя');
  await expect(
    page.getByRole('button', { name: 'Сохранить профиль', exact: true }),
  ).toBeEnabled();
  await expect(page.locator('dd').first()).toHaveText('Анна Петрова');
  expect(commands).toBe(1);
  expect((await (await request.get('/api/owner/profile')).json()).version).toBe(
    1,
  );
});

test('потерянный ответ профиля восстанавливает нормализованный успех по тому же ключу без повтора', async ({
  page,
  request,
}) => {
  const keys: string[] = [];
  await page.route('**/api/owner/profile', async (route) => {
    if (route.request().method() !== 'PUT') return route.continue();
    keys.push(route.request().headers()['idempotency-key']);
    expect((await route.fetch()).status()).toBe(200);
    await route.abort('connectionreset');
  });
  await page.goto('/admin');
  const name = page.getByRole('textbox', { name: 'Имя', exact: true });
  await name.fill('  Новое   имя  ');
  const save = page.getByRole('button', {
    name: 'Сохранить профиль',
    exact: true,
  });
  await save.click();
  await expect(page.getByRole('alert')).toContainText(
    'Исход операции неизвестен',
  );
  await expect(save).toBeDisabled();
  await expect(page.getByRole('switch')).toBeDisabled();
  await expect(name).toHaveValue('  Новое   имя  ');
  await page.getByRole('button', { name: 'Выяснить результат' }).click();
  await expect(name).toHaveValue('Новое имя');
  await expect(save).toBeEnabled();
  expect(keys).toHaveLength(1);
  expect((await (await request.get('/api/owner/profile')).json()).version).toBe(
    2,
  );
});

test('ошибки полей доступны с клавиатуры, ввод и действующие ссылки сохраняются', async ({
  page,
  request,
}) => {
  await page.goto('/admin');
  const name = page.getByRole('textbox', { name: 'Имя', exact: true });
  const username = page.getByRole('textbox', { name: 'Username', exact: true });
  await name.fill('Невидимый\u200b');
  await username.fill('-invalid');
  page.once('dialog', (dialog) => dialog.accept());
  await page
    .getByRole('button', { name: 'Сохранить профиль', exact: true })
    .focus();
  await page.keyboard.press('Enter');
  await expect(name).toHaveAttribute('aria-invalid', 'true');
  await expect(name).toHaveAttribute('aria-describedby', 'name-error');
  await expect(username).toHaveAttribute('aria-invalid', 'true');
  await expect(username).toHaveAttribute('aria-describedby', 'username-error');
  await expect(page.locator('#name-error')).toContainText('1–100');
  await expect(page.locator('#username-error')).toContainText('3–30');
  await expect(name).toHaveValue('Невидимый\u200b');
  await expect(username).toHaveValue('-invalid');
  expect((await (await request.get('/api/owner/profile')).json()).version).toBe(
    1,
  );
  await name.fill('😀'.repeat(100));
  await username.fill(' ANNA-1 ');
  await page
    .getByRole('button', { name: 'Сохранить профиль', exact: true })
    .click();
  await expect(name).toHaveValue('😀'.repeat(100));
  await expect(username).toHaveValue('anna-1');
  await expect(name).not.toHaveAttribute('aria-invalid', 'true');
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});

test('смена username требует подтверждения, отмена сохраняет ввод, старый адрес не перенаправляется', async ({
  page,
  context,
  request,
}) => {
  await page.goto('/admin');
  const username = page.getByRole('textbox', { name: 'Username', exact: true });
  await username.fill(' BOOKING-ENTRY ');
  let commands = 0;
  page.on('request', (req) => {
    if (req.method() === 'PUT') commands++;
  });
  const warning = page.waitForEvent('dialog');
  const save = page.getByRole('button', {
    name: 'Сохранить профиль',
    exact: true,
  });
  const clicking = save.click();
  const dialog = await warning;
  expect(dialog.message()).toContain(
    'Опубликованные ссылки потребуется заменить',
  );
  expect(dialog.message()).toContain('/booking/anna-1');
  expect(dialog.message()).toContain('/booking/booking-entry');
  await dialog.dismiss();
  await clicking;
  expect(commands).toBe(0);
  await expect(username).toHaveValue(' BOOKING-ENTRY ');
  expect(
    (await (await request.get('/api/owner/profile')).json()).username,
  ).toBe('anna-1');
  const guest = await context.newPage();
  await guest.goto('/booking/anna-1');
  page.once('dialog', (next) => next.accept());
  await save.focus();
  await page.keyboard.press('Enter');
  await expect(username).toHaveValue('booking-entry');
  expect(commands).toBe(1);
  await guest.reload();
  await expect(
    guest.getByRole('heading', { name: 'Страница не найдена' }),
  ).toBeVisible();
  await expect(guest).toHaveURL('/booking/anna-1');
  await guest.goto('/booking');
  await expect(guest).toHaveURL('/booking/booking-entry');
  await expect(
    guest.getByRole('heading', { name: 'Анна Петрова' }),
  ).toBeVisible();
  await username.fill('anna-1');
  page.once('dialog', (next) => next.accept());
  await save.click();
  await expect(username).toHaveValue('anna-1');
  await guest.reload();
  await expect(
    guest.getByRole('heading', { name: 'Страница не найдена' }),
  ).toBeVisible();
  await guest.goto('/booking/anna-1');
  await expect(
    guest.getByRole('heading', { name: 'Анна Петрова' }),
  ).toBeVisible();
});

test('имя сохраняется с клавиатуры без предупреждения и без смены ссылки или выключателя', async ({
  page,
  context,
}) => {
  await page.goto('/admin');
  const toggle = page.getByRole('switch', { name: 'Публичная запись' });
  await toggle.click();
  await expect(toggle).toBeChecked();
  await page
    .getByRole('textbox', { name: 'Имя', exact: true })
    .fill('  Анна   李 😀  ');
  await page
    .getByRole('button', { name: 'Сохранить профиль', exact: true })
    .focus();
  await page.keyboard.press('Enter');
  await expect(
    page.getByRole('textbox', { name: 'Имя', exact: true }),
  ).toHaveValue('Анна 李 😀');
  await expect(page.getByRole('switch')).toBeChecked();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  const guest = await context.newPage();
  await guest.goto('/booking/anna-1');
  await expect(
    guest.getByRole('heading', { name: 'Анна 李 😀' }),
  ).toBeVisible();
  await expect(
    guest.getByText('Сейчас нет доступных типов встреч'),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});
