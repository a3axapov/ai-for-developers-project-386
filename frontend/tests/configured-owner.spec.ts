import { expect, test } from './real-server';

test('общий вход получает действующий адрес владельца с сервера', async ({
  page,
}) => {
  await page.goto('/booking');
  await expect(page).toHaveURL('/booking/anna-1');
  await expect(
    page.getByRole('heading', { name: 'Анна Петрова' }),
  ).toBeVisible();
  await expect(page.getByText('Запись временно недоступна')).toBeVisible();
  await expect(page.getByRole('form')).toHaveCount(0);
  await expect(page.getByRole('list')).toHaveCount(0);
  await page.reload();
  await expect(page.getByText('Запись временно недоступна')).toBeVisible();
});

test('профиль показывает изменённую YAML-конфигурацию, а не константы интерфейса', async ({
  page,
}) => {
  await page.goto('/admin');
  await expect(
    page.getByRole('definition').filter({ hasText: /^Анна Петрова$/ }),
  ).toBeVisible();
  await expect(
    page.getByRole('definition').filter({ hasText: /^anna-1$/ }),
  ).toBeVisible();
  await expect(
    page.getByText('Pacific/Auckland', { exact: true }),
  ).toBeVisible();
  await page.reload();
  await expect(
    page.getByRole('definition').filter({ hasText: /^Анна Петрова$/ }),
  ).toBeVisible();
});
