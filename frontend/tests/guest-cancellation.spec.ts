import { expect, test } from './real-server';
import type { APIRequestContext } from '@playwright/test';

test.use({
  fixedNow: '2026-11-01T00:00:00Z',
  serverArguments: [
    '--calendar.initial.time-zone=Europe/Moscow',
    '--calendar.initial.booking-enabled=true',
  ],
});

async function book(request: APIRequestContext, manual: boolean) {
  expect(
    (
      await request.post('/api/owner/meeting-types', {
        headers: { 'Idempotency-Key': 'seed' },
        data: {
          id: 'Meeting',
          title: 'Консультация',
          description: 'Сохранённое подробное описание консультации',
          durationMinutes: 30,
          requiresApproval: manual,
        },
      })
    ).status(),
  ).toBe(201);
  const slots = await request.get(
    '/api/public/anna-1/meeting-types/Meeting/slots',
  );
  const prepared = await request.post('/api/public/anna-1/booking-attempts', {
    data: {
      meetingTypeId: 'Meeting',
      conditionsToken: (await slots.json()).conditionsToken,
      startsAt: '2026-11-02T09:00:00+03:00',
      guestName: 'Гость',
      guestEmail: 'guest@example.com',
      comment: 'Сохранённый комментарий',
    },
  });
  expect(prepared.status()).toBe(201);
  const attempt = await prepared.json();
  const submitted = await request.post(
    `/api/booking-attempts/${attempt.attemptId}/submit`,
    { headers: { Authorization: `Bearer ${attempt.attemptSecret}` } },
  );
  expect(submitted.status()).toBe(201);
  return submitted.json();
}

for (const manual of [true, false]) {
  test(`гость явно ${manual ? 'отзывает запрос' : 'отменяет встречу'} клавиатурой без потери деталей`, async ({
    page,
    request,
  }) => {
    const saved = await book(request, manual);
    let commands = 0;
    page.on('request', (request) => {
      if (request.method() === 'POST' && request.url().endsWith('/cancel'))
        commands++;
    });
    await page.goto(saved.resultUrl);
    expect(commands).toBe(0);
    await page.getByLabel('Часовой пояс').selectOption('Europe/Moscow');
    const cancel = page.getByRole('button', {
      name: manual ? 'Отозвать запрос' : 'Отменить встречу',
      exact: true,
    });
    page.once('dialog', async (dialog) => {
      for (const text of [
        'Консультация',
        'Гость',
        '09:00',
        '09:30',
        'Europe/Moscow',
        'невозможно',
        'только',
        'не гарантирует',
      ])
        expect(dialog.message()).toContain(text);
      await dialog.dismiss();
    });
    await cancel.click();
    expect(commands).toBe(0);
    page.once('dialog', (dialog) => dialog.accept());
    await cancel.focus();
    await page.keyboard.press('Enter');
    await expect(page.getByText('Отменено', { exact: true })).toBeVisible();
    await expect(cancel).toHaveCount(0);
    expect(commands).toBe(1);
    await expect(
      page.getByText('guest@example.com', { exact: true }),
    ).toBeVisible();
    await expect(page.getByText('Сохранённый комментарий')).toBeVisible();
    await expect(page).toHaveURL(saved.resultUrl);
    await page.reload();
    await expect(page.getByText('Отменено', { exact: true })).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
  });
}

test('подтверждение владельцем в другой вкладке требует нового согласия гостя', async ({
  page,
  context,
  request,
}) => {
  const saved = await book(request, true);
  await page.goto(saved.resultUrl);
  const owner = await context.newPage();
  await owner.goto('/admin');
  const bookings = owner.getByRole('region', {
    name: 'Бронирования владельца',
  });
  await bookings.getByRole('button', { name: 'Открыть Консультация' }).click();
  await bookings.getByRole('button', { name: 'Подтвердить запрос' }).click();
  await expect(bookings.getByText('Версия: 2', { exact: true })).toBeVisible();
  let commands = 0;
  page.on('request', (request) => {
    if (request.method() === 'POST' && request.url().endsWith('/cancel'))
      commands++;
  });
  page.once('dialog', (dialog) => dialog.accept());
  await page
    .getByRole('button', { name: 'Отозвать запрос', exact: true })
    .click();
  await expect(page.getByRole('alert')).toContainText(
    'Бронирование изменилось',
  );
  await expect(page.getByText('Подтверждено', { exact: true })).toBeVisible();
  expect(commands).toBe(1);
  expect(
    (
      await (
        await request.get(`/api/owner/bookings/${saved.booking.id}`)
      ).json()
    ).version,
  ).toBe(2);
  page.once('dialog', (dialog) => dialog.dismiss());
  await page
    .getByRole('button', { name: 'Отменить встречу', exact: true })
    .click();
  expect(commands).toBe(1);
  page.once('dialog', (dialog) => dialog.accept());
  await page
    .getByRole('button', { name: 'Отменить встречу', exact: true })
    .click();
  await expect(page.getByText('Отменено', { exact: true })).toBeVisible();
  expect(commands).toBe(2);
  await expect(
    page.getByText('guest@example.com', { exact: true }),
  ).toBeVisible();
});

for (const delivered of [true, false]) {
  test(`потеря ${delivered ? 'ответа' : 'запроса'} отмены не показывает ложный успех и требует обновления`, async ({
    page,
    request,
  }) => {
    const saved = await book(request, false);
    await page.goto(saved.resultUrl);
    let commands = 0;
    await page.route('**/api/bookings/*/cancel', async (route) => {
      commands++;
      if (delivered) expect((await route.fetch()).status()).toBe(200);
      await route.abort('failed');
    });
    page.once('dialog', (dialog) => dialog.accept());
    const cancel = page.getByRole('button', {
      name: 'Отменить встречу',
      exact: true,
    });
    await cancel.click();
    await expect(page.getByRole('alert')).toContainText(
      'Исход отмены неизвестен',
    );
    await expect(page.getByText('Отменено', { exact: true })).toHaveCount(0);
    await expect(cancel).toBeDisabled();
    await expect(page.getByText('Сохранённый комментарий')).toBeVisible();
    await page.route('**/api/bookings/*', (route) => route.abort('failed'));
    await page.getByRole('button', { name: 'Обновить сведения' }).click();
    await expect(
      page.getByText('Исход отмены неизвестен.', { exact: false }),
    ).toBeVisible();
    await expect(cancel).toBeDisabled();
    await page.unroute('**/api/bookings/*');
    await page.getByRole('button', { name: 'Обновить сведения' }).click();
    if (delivered) {
      await expect(page.getByText('Отменено', { exact: true })).toBeVisible();
      await expect(cancel).toHaveCount(0);
    } else {
      await expect(cancel).toBeEnabled();
      await page.unroute('**/api/bookings/*/cancel');
      page.once('dialog', (dialog) => dialog.accept());
      await cancel.click();
      await expect(page.getByText('Отменено', { exact: true })).toBeVisible();
    }
    expect(commands).toBe(1);
    expect(
      (
        await (
          await request.get(`/api/owner/bookings/${saved.booking.id}`)
        ).json()
      ).version,
    ).toBe(2);
    await expect(page).toHaveURL(saved.resultUrl);
  });
}

for (const manual of [true, false]) {
  test(`граница начала запрещает ${manual ? 'отзыв' : 'отмену'} без частичных изменений`, async ({
    page,
    request,
    setServerNow,
  }) => {
    const saved = await book(request, manual);
    await page.goto(saved.resultUrl);
    await expect(
      page.getByRole('button', {
        name: manual ? 'Отозвать запрос' : 'Отменить встречу',
        exact: true,
      }),
    ).toBeVisible();
    await setServerNow('2026-11-02T06:00:00Z');
    page.once('dialog', (dialog) => dialog.accept());
    await page
      .getByRole('button', {
        name: manual ? 'Отозвать запрос' : 'Отменить встречу',
        exact: true,
      })
      .click();
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(
      page.getByText(manual ? 'Отклонено' : 'Подтверждено', { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole('button', { name: /Отозвать запрос|Отменить встречу/ }),
    ).toHaveCount(0);
    await expect(page.getByText('Сохранённый комментарий')).toBeVisible();
    const actual = await (
      await request.get(`/api/owner/bookings/${saved.booking.id}`)
    ).json();
    expect(actual.version).toBe(manual ? 2 : 1);
    expect(actual.startsAt).toBe(saved.booking.startsAt);
    expect(actual.guestEmail).toBe(saved.booking.guestEmail);
  });
}

test('сброс настоящего процесса после потери ответа делает ссылку нейтрально недоступной', async ({
  page,
  request,
  restartServer,
}) => {
  const saved = await book(request, false);
  await page.goto(saved.resultUrl);
  await page.route('**/api/bookings/*/cancel', async (route) => {
    expect((await route.fetch()).status()).toBe(200);
    await route.abort('failed');
  });
  page.once('dialog', (dialog) => dialog.accept());
  await page
    .getByRole('button', { name: 'Отменить встречу', exact: true })
    .click();
  await expect(page.getByRole('alert')).toContainText(
    'Исход отмены неизвестен',
  );
  await restartServer();
  await page.getByRole('button', { name: 'Обновить сведения' }).click();
  await expect(
    page.getByRole('heading', { name: 'Бронирование не найдено' }),
  ).toBeVisible();
  await expect(
    page.getByText('guest@example.com', { exact: true }),
  ).toHaveCount(0);
  await expect(page.getByRole('alert')).toHaveCount(0);
  await expect(
    page.getByRole('button', { name: 'Отменить встречу', exact: true }),
  ).toHaveCount(0);
  expect((await request.get('/api/owner/bookings?view=history')).status()).toBe(
    200,
  );
  expect(
    (await (await request.get('/api/owner/bookings?view=history')).json())
      .items,
  ).toEqual([]);
});
