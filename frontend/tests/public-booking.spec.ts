import { expect, test } from './real-server';

test('владелец переключает запись, а гость видит выключенную и пустую страницы', async ({
  page,
  context,
}) => {
  await page.goto('/admin');
  const toggle = page.getByRole('switch', { name: 'Публичная запись' });
  await expect(toggle).not.toBeChecked();
  const guest = await context.newPage();
  await guest.goto('/booking/anna-1');
  await expect(guest.getByText('Запись временно недоступна')).toBeVisible();
  await toggle.focus();
  await page.keyboard.press('Space');
  await expect(toggle).toBeChecked();
  await expect(page.getByText('Публичная запись включена')).toBeVisible();
  await guest.reload();
  await expect(
    guest.getByRole('heading', { name: 'Анна Петрова' }),
  ).toBeVisible();
  await expect(
    guest.getByText('Сейчас нет доступных типов встреч'),
  ).toBeVisible();
  await expect(guest.getByRole('form')).toHaveCount(0);
  await toggle.click();
  await expect(toggle).not.toBeChecked();
  await guest.reload();
  await expect(guest.getByText('Запись временно недоступна')).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  expect(
    await guest.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
});

test('владелец копирует общую ссылку из текущего профиля', async ({
  page,
  context,
  baseURL,
}) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await page.goto('/admin');
  const link = page.getByRole('link', {
    name: `${baseURL}/booking/anna-1`,
    exact: true,
  });
  await expect(link).toHaveAttribute('href', '/booking/anna-1');
  const copy = page.getByRole('button', { name: 'Копировать ссылку' });
  await copy.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('status')).toHaveText('Ссылка скопирована');
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(
    `${baseURL}/booking/anna-1`,
  );
  await link.click();
  await expect(page).toHaveURL('/booking/anna-1');
  await expect(page.getByText('Запись временно недоступна')).toBeVisible();
});

test('устаревшая вкладка показывает актуальный профиль и требует нового действия', async ({
  page,
  context,
}) => {
  await page.goto('/admin');
  const toggle = page.getByRole('switch', { name: 'Публичная запись' });
  await expect(toggle).not.toBeChecked();
  const other = await context.newPage();
  await other.goto('/admin');
  await other.getByRole('switch', { name: 'Публичная запись' }).click();
  await expect(other.getByRole('switch')).toBeChecked();
  const conflict = page.waitForResponse((response) =>
    response.url().endsWith('/api/owner/public-booking'),
  );
  await toggle.click();
  expect((await conflict).status()).toBe(409);
  await expect(page.getByRole('alert')).toContainText('Профиль изменился');
  await expect(toggle).toBeChecked();
  await expect(toggle).toHaveAttribute('aria-describedby', 'booking-error');
  const retry = page.waitForResponse((response) =>
    response.url().endsWith('/api/owner/public-booking'),
  );
  await toggle.click();
  expect((await retry).status()).toBe(200);
  await expect(toggle).not.toBeChecked();
  await expect(page.getByRole('alert')).toHaveCount(0);
});

test('потерянный ответ не становится успехом, результат выясняется по прежнему ключу', async ({
  page,
  request,
}) => {
  const keys: string[] = [];
  await page.route('**/api/owner/public-booking', async (route) => {
    keys.push(route.request().headers()['idempotency-key']);
    const realResponse = await route.fetch();
    expect(realResponse.status()).toBe(200);
    await route.abort('connectionreset');
  });
  await page.goto('/admin');
  const toggle = page.getByRole('switch', { name: 'Публичная запись' });
  await toggle.click();
  await expect(page.getByRole('alert')).toContainText(
    'Исход операции неизвестен',
  );
  await expect(toggle).toBeDisabled();
  await expect(toggle).not.toBeChecked();
  await expect(page.getByText('Публичная запись включена')).toHaveCount(0);
  const actual = await request.get('/api/owner/profile');
  expect((await actual.json()).bookingEnabled).toBe(true);
  await page.route('**/api/owner/operations/*', (route) =>
    route.abort('connectionreset'),
  );
  await page.getByRole('button', { name: 'Выяснить результат' }).click();
  await expect(toggle).toBeDisabled();
  await expect(page.getByRole('alert')).toContainText(
    'Исход операции неизвестен',
  );
  await page.unroute('**/api/owner/operations/*');
  const lookup = page.waitForResponse((response) =>
    response.url().endsWith(`/api/owner/operations/${keys[0]}`),
  );
  await page.getByRole('button', { name: 'Выяснить результат' }).click();
  expect((await lookup).status()).toBe(200);
  await expect(toggle).toBeChecked();
  await expect(toggle).toBeEnabled();
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(keys).toHaveLength(1);
  expect((await (await request.get('/api/owner/profile')).json()).version).toBe(
    2,
  );
});

test('явно введённый неизвестный адрес не перенаправляется при любом состоянии записи', async ({
  page,
  request,
}) => {
  for (const enabled of [false, true]) {
    if (enabled) {
      const response = await request.put('/api/owner/public-booking', {
        headers: { 'Idempotency-Key': 'enable-for-unknown-page' },
        data: { enabled: true, expectedVersion: 1 },
      });
      expect(response.status()).toBe(200);
    }
    const response = page.waitForResponse((result) =>
      result.url().endsWith('/api/public/unknown-owner'),
    );
    await page.goto('/booking/unknown-owner');
    expect((await response).status()).toBe(404);
    await expect(
      page.getByRole('heading', { name: 'Страница не найдена' }),
    ).toBeVisible();
    await expect(page).toHaveURL('/booking/unknown-owner');
    await expect(page.getByText('Запись временно недоступна')).toHaveCount(0);
    await expect(
      page.getByText('Сейчас нет доступных типов встреч'),
    ).toHaveCount(0);
  }
});

test('потерянный ответ отказа восстанавливает отказ, а не успех', async ({
  page,
  request,
}) => {
  await page.goto('/admin');
  const toggle = page.getByRole('switch', { name: 'Публичная запись' });
  await expect(toggle).not.toBeChecked();
  expect(
    (
      await request.put('/api/owner/public-booking', {
        headers: { 'Idempotency-Key': 'another-tab' },
        data: { enabled: true, expectedVersion: 1 },
      })
    ).status(),
  ).toBe(200);
  let commands = 0;
  await page.route('**/api/owner/public-booking', async (route) => {
    commands++;
    expect((await route.fetch()).status()).toBe(409);
    await route.abort('connectionreset');
  });
  await toggle.click();
  await expect(page.getByRole('alert')).toContainText(
    'Исход операции неизвестен',
  );
  await page.getByRole('button', { name: 'Выяснить результат' }).click();
  await expect(page.getByRole('alert')).toContainText('Профиль изменился');
  await expect(toggle).toBeEnabled();
  await expect(toggle).toBeChecked();
  expect(commands).toBe(1);
  expect((await (await request.get('/api/owner/profile')).json()).version).toBe(
    2,
  );
});

test('утраченная операция сообщает о возможном сбросе и не повторяет изменение автоматически', async ({
  page,
  request,
}) => {
  let commands = 0;
  await page.route('**/api/owner/public-booking', async (route) => {
    commands++;
    await route.abort('connectionreset'); // The command never reached the server; lookup really returns 410.
  });
  await page.goto('/admin');
  const toggle = page.getByRole('switch', { name: 'Публичная запись' });
  await toggle.click();
  await expect(page.getByRole('alert')).toContainText(
    'Исход операции неизвестен',
  );
  await page.getByRole('button', { name: 'Выяснить результат' }).click();
  await expect(page.getByRole('alert')).toContainText(
    'Результат операции недоступен. Возможен сброс данных.',
  );
  await expect(toggle).toBeDisabled();
  expect(commands).toBe(1);
  expect((await (await request.get('/api/owner/profile')).json()).version).toBe(
    1,
  );
  await page
    .getByRole('button', { name: 'Загрузить актуальный профиль' })
    .click();
  await expect(toggle).toBeEnabled();
  await expect(toggle).not.toBeChecked();
  expect(commands).toBe(1);
});
