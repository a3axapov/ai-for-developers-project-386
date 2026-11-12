import { expect, test } from '@playwright/test';

test('профиль показывает изменённую YAML-конфигурацию, а не константы интерфейса', async ({
  page,
}) => {
  await page.goto('/admin');
  await expect(page.getByText('Анна Петрова', { exact: true })).toBeVisible();
  await expect(page.getByText('anna-1', { exact: true })).toBeVisible();
  await expect(
    page.getByText('Pacific/Auckland', { exact: true }),
  ).toBeVisible();
  await page.reload();
  await expect(page.getByText('Анна Петрова', { exact: true })).toBeVisible();
});
