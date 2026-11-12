import { expect, test } from '@playwright/test';

test('владелец видит исходный профиль из настоящего API', async ({ page }) => {
  const profileResponse = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/owner/profile' &&
      response.request().method() === 'GET',
  );
  await page.goto('/admin');
  const response = await profileResponse;
  expect(response.status()).toBe(200);
  expect(await response.json()).toEqual({
    name: 'Владелец календаря',
    username: 'owner',
    timeZone: 'Europe/Moscow',
    bookingEnabled: false,
    version: 1,
    bookingUrl: '/booking/owner',
  });
  await expect(
    page.getByRole('heading', { name: 'Профиль владельца' }),
  ).toBeVisible();
  await expect(
    page.getByText('Владелец календаря', { exact: true }),
  ).toBeVisible();
  await expect(page.getByText('owner', { exact: true })).toBeVisible();
  await expect(page.getByText('Europe/Moscow', { exact: true })).toBeVisible();
  await expect(page.getByText('Публичная запись выключена')).toBeVisible();
});
