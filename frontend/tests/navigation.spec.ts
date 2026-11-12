import { expect, test } from '@playwright/test';

test('главная объясняет будущую запись без регистрации', async ({ page }) => {
  const apiRequests: string[] = [];
  page.on('request', (request) => {
    if (new URL(request.url()).pathname.startsWith('/api/')) {
      apiRequests.push(request.url());
    }
  });
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 })).toContainText(
    'Меньше переписки.',
  );
  await expect(
    page.getByRole('heading', { name: 'От выбора до встречи — три шага' }),
  ).toBeVisible();
  await expect(page.getByRole('listitem')).toHaveCount(3);
  await expect(
    page.getByText('Аккаунт не нужен.', { exact: false }),
  ).toBeVisible();
  await expect(page.getByText('Скоро — онлайн-запись.')).toBeVisible();
  await expect(
    page.getByText('Встреча в вашем календаре', { exact: true }),
  ).toBeVisible();
  await expect(page.getByRole('status')).toHaveCount(0);
  expect(apiRequests).toEqual([]);
});

for (const destination of [
  {
    path: '/booking',
    link: 'Записаться на встречу',
    title: 'Запись на встречи скоро появится',
  },
  {
    path: '/admin',
    link: 'Владельцу',
    title: 'Профиль владельца',
  },
]) {
  test(`переход на ${destination.path} и возврат на главную`, async ({
    page,
  }) => {
    await page.goto('/');
    await page
      .getByRole('link', { name: destination.link, exact: true })
      .click();
    await expect(page).toHaveURL(destination.path);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(
      destination.title,
    );
    await page.getByRole('link', { name: 'Вернуться на главную' }).click();
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { level: 1 })).toContainText(
      'Меньше переписки.',
    );
  });

  test(`прямое открытие и обновление ${destination.path}`, async ({ page }) => {
    await page.goto(destination.path);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(
      destination.title,
    );
    await page.reload();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(
      destination.title,
    );
  });
}

for (const path of ['/', '/booking', '/admin']) {
  test(`нет горизонтальной прокрутки на ${path}`, async ({ page }) => {
    await page.goto(path);
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
  });
}

test('основные переходы доступны с клавиатуры', async ({ page }) => {
  await page.goto('/');
  await page.keyboard.press('Tab');
  await expect(
    page.getByRole('link', { name: 'Перейти к содержимому' }),
  ).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('main')).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(
    page.getByRole('link', { name: 'Записаться на встречу' }),
  ).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL('/booking');
});
