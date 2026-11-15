import { readFile } from 'node:fs/promises';
import type { APIRequestContext, Download } from '@playwright/test';
import { expect, test } from './real-server';

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
          title: 'Консультация 😀, тест; \\',
          description: 'Сохранённое подробное описание\nВторая строка 李',
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
      guestName: 'GuestSecretSentinel',
      guestEmail: 'private@example.com',
      comment: 'CommentSecretSentinel',
    },
  });
  expect(prepared.status()).toBe(201);
  const attempt = await prepared.json();
  const response = await request.post(
    `/api/booking-attempts/${attempt.attemptId}/submit`,
    {
      headers: { Authorization: `Bearer ${attempt.attemptSecret}` },
    },
  );
  expect(response.status()).toBe(201);
  return response.json();
}

async function file(download: Download) {
  expect(download.suggestedFilename()).toBe('booking.ics');
  expect(await download.failure()).toBeNull();
  return readFile((await download.path())!);
}

for (const manual of [true, false]) {
  test(`предупреждение и настоящий ${manual ? 'предварительный' : 'подтверждённый'} файл: клавиатура, приватность, отмена`, async ({
    page,
    request,
    baseURL,
  }) => {
    const saved = await book(request, manual);
    let exports = 0;
    let downloads = 0;
    page.on('request', (request) => {
      if (request.url().endsWith('/calendar.ics')) exports++;
    });
    page.on('download', () => downloads++);
    await page.goto(saved.resultUrl);
    const button = page.getByRole('button', {
      name: 'Скачать .ics',
      exact: true,
    });
    await expect(button).toBeVisible();
    expect(exports).toBe(0);
    expect(downloads).toBe(0);
    page.once('dialog', async (dialog) => {
      expect(dialog.message()).toContain(
        'Файл содержит ссылку на детали и отмену бронирования',
      );
      expect(dialog.message()).toContain('Не передавайте его посторонним');
      if (manual)
        expect(dialog.message()).toContain(
          'не означает подтверждения владельцем',
        );
      await dialog.dismiss();
    });
    await button.click();
    expect(exports).toBe(0);
    expect(downloads).toBe(0);
    page.once('dialog', (dialog) => dialog.accept());
    const downloaded = page.waitForEvent('download');
    await button.focus();
    await page.keyboard.press('Enter');
    const bytes = await file(await downloaded);
    const actual = await request.get(`/api${saved.resultUrl}/calendar.ics`);
    expect(actual.status()).toBe(200);
    expect(bytes).toEqual(await actual.body());
    const unfolded = bytes.toString('utf8').replace(/\r\n[ \t]/g, '');
    expect(unfolded).toContain(
      `STATUS:${manual ? 'TENTATIVE' : 'CONFIRMED'}\r\n`,
    );
    expect(unfolded).toContain(`URL:${baseURL}${saved.resultUrl}\r\n`);
    for (const secret of [
      'GuestSecretSentinel',
      'private@example.com',
      'CommentSecretSentinel',
    ])
      expect(unfolded).not.toContain(secret);
    await expect(
      page.getByText(/вручную.*обновляйте|обновляйте.*вручную/i),
    ).toBeVisible();
    await expect(page.getByText(/дубликат/i)).toBeVisible();
    page.once('dialog', (dialog) => dialog.accept());
    await page
      .getByRole('button', {
        name: manual ? 'Отозвать запрос' : 'Отменить встречу',
        exact: true,
      })
      .click();
    await expect(page.getByText('Отменено', { exact: true })).toBeVisible();
    await expect(button).toHaveCount(0);
    expect(downloads).toBe(1);
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
  });
}

test('прежняя ссылка скачивает актуальный снимок после подтверждения и переноса', async ({
  page,
  request,
}) => {
  const saved = await book(request, true);
  await page.goto(saved.resultUrl);
  const button = page.getByRole('button', {
    name: 'Скачать .ics',
    exact: true,
  });
  await expect(button).toBeVisible();
  const download = async () => {
    page.once('dialog', (dialog) => dialog.accept());
    const pending = page.waitForEvent('download');
    await button.click();
    return (await file(await pending))
      .toString('utf8')
      .replace(/\r\n[ \t]/g, '');
  };
  const tentative = await download();
  expect(tentative).toContain('STATUS:TENTATIVE\r\n');
  const ownerPath = `/api/owner/bookings/${saved.booking.id}`;
  expect(
    (
      await request.post(`${ownerPath}/confirm`, {
        headers: { 'Idempotency-Key': 'confirm' },
        data: { expectedVersion: 1 },
      })
    ).status(),
  ).toBe(200);
  expect(await download()).toContain('STATUS:CONFIRMED\r\n');
  expect(
    (
      await request.post(`${ownerPath}/reschedule`, {
        headers: { 'Idempotency-Key': 'move' },
        data: { expectedVersion: 2, startsAt: '2026-12-07T10:00:00+03:00' },
      })
    ).status(),
  ).toBe(200);
  const current = await download();
  expect(current).toContain('DTSTART:20261207T070000Z\r\n');
  expect(current).toContain('DTEND:20261207T073000Z\r\n');
  expect(current.match(/^UID:.*$/m)?.[0]).toBe(
    tentative.match(/^UID:.*$/m)?.[0],
  );
  expect(current.match(/^URL:.*$/m)?.[0]).toBe(
    tentative.match(/^URL:.*$/m)?.[0],
  );
  await page.getByRole('button', { name: 'Обновить сведения' }).click();
  await expect(page.getByText('Подтверждено', { exact: true })).toBeVisible();
  await expect(page).toHaveURL(saved.resultUrl);
});

for (const action of ['cancel', 'reject', 'expire', 'reset']) {
  test(`устаревшая страница: ${action} не скачивает JSON вместо календаря и обновляет детали`, async ({
    page,
    request,
    setServerNow,
    restartServer,
  }) => {
    const saved = await book(
      request,
      action === 'reject' || action === 'expire',
    );
    await page.goto(saved.resultUrl);
    const button = page.getByRole('button', {
      name: 'Скачать .ics',
      exact: true,
    });
    await expect(button).toBeVisible();
    if (action === 'expire') await setServerNow('2026-11-02T06:00:00Z');
    else if (action === 'reset') await restartServer();
    else
      expect(
        (
          await request.post(
            `/api/owner/bookings/${saved.booking.id}/${action}`,
            {
              headers: { 'Idempotency-Key': action },
              data: { expectedVersion: 1 },
            },
          )
        ).status(),
      ).toBe(200);
    let downloads = 0;
    page.on('download', () => downloads++);
    page.once('dialog', (dialog) => dialog.accept());
    const response = page.waitForResponse((response) =>
      response.url().endsWith('/calendar.ics'),
    );
    await button.click();
    expect((await response).status()).toBe(action === 'reset' ? 404 : 409);
    if (action === 'reset')
      await expect(
        page.getByRole('heading', { name: 'Бронирование не найдено' }),
      ).toBeVisible();
    else {
      await expect(page.getByRole('alert')).toContainText('Экспорт недоступен');
      await expect(
        page.getByText(action === 'cancel' ? 'Отменено' : 'Отклонено', {
          exact: true,
        }),
      ).toBeVisible();
      await expect(
        page.getByText('private@example.com', { exact: true }),
      ).toBeVisible();
    }
    await expect(button).toHaveCount(0);
    expect(downloads).toBe(0);
  });
}

test('потеря доставки настоящего файла даёт связанную ошибку и безопасное повторное скачивание', async ({
  page,
  request,
}) => {
  const saved = await book(request, false);
  await page.goto(saved.resultUrl);
  await page.route('**/api/bookings/*/calendar.ics', async (route) => {
    const actual = await route.fetch();
    expect(actual.status()).toBe(200);
    expect((await actual.body()).toString('utf8')).toMatch(
      /^BEGIN:VCALENDAR\r\n/,
    );
    await route.abort('failed');
  });
  let downloads = 0;
  page.on('download', () => downloads++);
  const button = page.getByRole('button', {
    name: 'Скачать .ics',
    exact: true,
  });
  page.once('dialog', (dialog) => dialog.accept());
  await button.click();
  await expect(page.getByRole('alert')).toHaveText(
    'Не удалось скачать .ics. Повторите скачивание.',
  );
  await expect(button).toHaveAttribute('aria-describedby', 'result-error');
  await expect(button).toBeEnabled();
  expect(downloads).toBe(0);
  await page.unroute('**/api/bookings/*/calendar.ics');
  page.once('dialog', (dialog) => dialog.accept());
  const downloaded = page.waitForEvent('download');
  await button.click();
  const bytes = await file(await downloaded);
  expect(bytes).toEqual(
    await (await request.get(`/api${saved.resultUrl}/calendar.ics`)).body(),
  );
  expect(downloads).toBe(1);
  await expect(page.getByRole('alert')).toHaveCount(0);
});

test('прошедшая подтверждённая встреча остаётся доступной, неизвестная ссылка нейтральна', async ({
  page,
  request,
  setServerNow,
}) => {
  const saved = await book(request, false);
  await setServerNow('2026-11-03T00:00:00Z');
  await page.goto(saved.resultUrl);
  const button = page.getByRole('button', {
    name: 'Скачать .ics',
    exact: true,
  });
  page.once('dialog', (dialog) => dialog.accept());
  const downloaded = page.waitForEvent('download');
  await button.click();
  expect((await file(await downloaded)).toString('utf8')).toContain(
    'STATUS:CONFIRMED\r\n',
  );
  await expect(
    page.getByRole('button', { name: 'Отменить встречу', exact: true }),
  ).toHaveCount(0);
  await page.goto('/bookings/unknown');
  await expect(
    page.getByRole('heading', { name: 'Бронирование не найдено' }),
  ).toBeVisible();
  await expect(button).toHaveCount(0);
  await expect(
    page.getByText('private@example.com', { exact: true }),
  ).toHaveCount(0);
});
