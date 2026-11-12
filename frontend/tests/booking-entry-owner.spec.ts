import { expect, test } from './real-server';

test.use({ ownerUsername: 'booking-entry' });

test('booking-entry остаётся допустимым именем владельца', async ({ page }) => {
  await page.goto('/booking');
  await expect(page).toHaveURL('/booking/booking-entry');
  await expect(
    page.getByRole('heading', { name: 'Анна Петрова' }),
  ).toBeVisible();
  await expect(page.getByText('Запись временно недоступна')).toBeVisible();
  await page.reload();
  await expect(
    page.getByRole('heading', { name: 'Анна Петрова' }),
  ).toBeVisible();
});
