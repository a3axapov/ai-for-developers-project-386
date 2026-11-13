import { expect, test } from './real-server';
import type { APIRequestContext } from '@playwright/test';

test.use({
  fixedNow: '2026-10-24T12:00:00Z',
  timezoneId: 'Europe/Berlin',
  serverArguments: [
    '--calendar.initial.time-zone=Europe/Berlin',
    '--calendar.initial.working-days=SUNDAY',
    '--calendar.initial.work-start=02:15',
    '--calendar.initial.work-end=02:45',
  ],
});

async function seed(request: APIRequestContext, durationMinutes = 30) {
  expect(
    (
      await request.post('/api/owner/meeting-types', {
        headers: { 'Idempotency-Key': 'seed' },
        data: {
          id: 'Consult',
          title: 'Консультация',
          description: 'Подробное описание нашей консультации',
          durationMinutes,
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
}

test('гость выбирает тип, дату и фактическое время в определённом браузером поясе', async ({
  page,
  request,
}) => {
  await seed(request);
  await page.goto('/booking/anna-1');
  await page.getByRole('link', { name: 'Выбрать тип «Консультация»' }).click();
  await expect(page.getByRole('group', { name: 'Календарь' })).toBeVisible();
  await expect(page.getByLabel('Часовой пояс')).toHaveValue('Europe/Berlin');
  const day = page.getByRole('button', { name: '25.10.2026', exact: true });
  await day.focus();
  await page.keyboard.press('Enter');
  const time = page.getByRole('button', {
    name: '02:15 UTC+02:00 — 02:45 UTC+02:00',
    exact: true,
  });
  await time.focus();
  await page.keyboard.press('Enter');
  await expect(time).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByRole('status')).toContainText('Выбрано: 25.10.2026');
  await expect(page.getByRole('status')).toContainText('02:15 UTC+02:00');
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});

test('повторяющиеся часы различимы смещениями; смена пояса сохраняет второй фактический момент', async ({
  page,
  request,
}) => {
  await seed(request);
  let reads = 0;
  page.on('request', (request) => {
    if (request.url().endsWith('/slots')) reads += 1;
  });
  await page.goto('/booking/anna-1/Consult');
  await page.getByRole('button', { name: '25.10.2026', exact: true }).click();
  await expect(
    page.getByRole('button', { name: '02:15 UTC+02:00 — 02:45 UTC+02:00' }),
  ).toBeVisible();
  const second = page.getByRole('button', {
    name: '02:15 UTC+01:00 — 02:45 UTC+01:00',
  });
  await second.click();
  await page.getByLabel('Часовой пояс').selectOption('America/Los_Angeles');
  const calendar = page.getByRole('group', { name: 'Календарь' });
  await expect(calendar.getByRole('button')).toHaveCount(15);
  await expect(
    calendar.getByRole('button', { name: '24.10.2026', exact: true }),
  ).toBeEnabled();
  await expect(
    calendar.getByRole('button', { name: '23.10.2026', exact: true }),
  ).toHaveCount(0);
  await expect(
    calendar.getByRole('button', { name: '08.11.2026', exact: true }),
  ).toHaveCount(0);
  await expect(page.getByRole('status')).toContainText('Выбрано: 24.10.2026');
  await expect(page.getByRole('status')).toContainText('18:15 UTC-07:00');
  await expect(
    page.getByRole('button', { name: '18:15 UTC-07:00 — 18:45 UTC-07:00' }),
  ).toHaveAttribute('aria-pressed', 'true');
  await page.getByLabel('Часовой пояс').selectOption('Asia/Katmandu');
  await expect(page.getByRole('status')).toContainText('Выбрано: 25.10.2026');
  await expect(page.getByRole('status')).toContainText('07:00 UTC+05:45');
  expect(reads).toBe(1);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});

test('другая дата снимает выбранное время без автоматической подстановки', async ({
  page,
  request,
}) => {
  await seed(request);
  await page.goto('/booking/anna-1/Consult');
  await page.getByRole('button', { name: '25.10.2026', exact: true }).click();
  await page
    .getByRole('button', { name: '02:15 UTC+02:00 — 02:45 UTC+02:00' })
    .click();
  await expect(page.getByRole('status')).toContainText('Выбрано:');
  await page.getByRole('button', { name: '01.11.2026', exact: true }).click();
  await expect(page.getByRole('status')).toHaveCount(0);
  await expect(
    page.getByRole('button', { name: '02:15 UTC+01:00 — 02:45 UTC+01:00' }),
  ).toHaveAttribute('aria-pressed', 'false');
});

test.describe('неопределённый пояс браузера', () => {
  test.use({ timezoneId: 'America/New_York' });
  for (const unavailable of ['missing', 'invalid', 'throws']) {
    test(`fallback на пояс владельца: ${unavailable}`, async ({
      page,
      request,
    }) => {
      await seed(request);
      await page.addInitScript((mode) => {
        const original = Intl.DateTimeFormat.prototype.resolvedOptions;
        Intl.DateTimeFormat.prototype.resolvedOptions = function () {
          if (mode === 'throws')
            throw new Error('Time zone detection unavailable');
          const options = original.call(this);
          Object.defineProperty(options, 'timeZone', {
            value: mode === 'invalid' ? 'Not/AZone' : undefined,
          });
          return options;
        };
      }, unavailable);
      await page.goto('/booking/anna-1/Consult');
      await expect(page.getByLabel('Часовой пояс')).toHaveValue(
        'Europe/Berlin',
      );
      await page
        .getByRole('button', { name: '25.10.2026', exact: true })
        .click();
      await expect(
        page.getByRole('button', { name: '02:15 UTC+02:00 — 02:45 UTC+02:00' }),
      ).toBeVisible();
      await page.getByLabel('Часовой пояс').selectOption('UTC');
      await expect(page.getByLabel('Часовой пояс')).toHaveValue('UTC');
    });
  }
});

test('пустое окно сохраняет календарь из 14 дат без выхода за границы и без выбора времени', async ({
  page,
  request,
}) => {
  await seed(request, 45);
  await page.goto('/booking/anna-1/Consult');
  const calendar = page.getByRole('group', { name: 'Календарь' });
  await expect(calendar).toBeVisible();
  await expect(calendar.getByRole('button')).toHaveCount(14);
  await expect(
    page.getByText('В ближайшие 14 дней нет свободного времени'),
  ).toBeVisible();
  await expect(
    calendar.getByRole('button', { name: '25.10.2026', exact: true }),
  ).toBeDisabled();
  await expect(
    calendar.getByRole('button', { name: '07.11.2026', exact: true }),
  ).toBeDisabled();
  await expect(
    calendar.getByRole('button', { name: '24.10.2026', exact: true }),
  ).toHaveCount(0);
  await expect(
    calendar.getByRole('button', { name: '08.11.2026', exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByText('В этот день нет свободного времени'),
  ).toHaveCount(0);
  await expect(
    page.getByRole('link', { name: 'Все типы встреч' }),
  ).toBeVisible();
  await expect(page.getByRole('button', { name: /UTC[+-]/ })).toHaveCount(0);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});

test.describe('пустой первый день, но не всё окно', () => {
  test.use({ fixedNow: '2026-10-23T12:00:00Z' });
  test('предлагает другие доступные даты; выбор пустого дня недоступен', async ({
    page,
    request,
  }) => {
    await seed(request);
    await page.goto('/booking/anna-1/Consult');
    await expect(
      page.getByText('В этот день нет свободного времени'),
    ).toBeVisible();
    await expect(
      page.getByText('Выберите другую доступную дату в календаре.'),
    ).toBeVisible();
    await expect(
      page.getByText('В ближайшие 14 дней нет свободного времени'),
    ).toHaveCount(0);
    await expect(
      page.getByRole('button', { name: '24.10.2026', exact: true }),
    ).toBeDisabled();
    await page.getByRole('button', { name: '25.10.2026', exact: true }).click();
    await expect(
      page.getByText('В этот день нет свободного времени'),
    ).toHaveCount(0);
    await expect(
      page.getByRole('button', { name: '02:15 UTC+01:00 — 02:45 UTC+01:00' }),
    ).toBeVisible();
  });
});

test('загрузка и ошибка чтения слотов различаются; повтор читает настоящий сервер', async ({
  page,
  request,
}) => {
  await seed(request);
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route('**/meeting-types/Consult/slots', async (route) => {
    await route.fetch();
    await gate;
    await route.abort('connectionreset');
  });
  try {
    await page.goto('/booking/anna-1/Consult');
    await expect(page.getByText('Загрузка свободного времени…')).toBeVisible();
    await expect(page.getByRole('alert')).toHaveCount(0);
  } finally {
    release();
  }
  await expect(page.getByRole('alert')).toContainText(
    'Не удалось загрузить свободное время.',
  );
  await page.unroute('**/meeting-types/Consult/slots');
  await page
    .getByRole('button', { name: 'Повторить загрузку времени' })
    .click();
  await expect(page.getByRole('group', { name: 'Календарь' })).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
});

for (const changed of ['disabled', 'username']) {
  test(`изменение публичного состояния перед чтением слотов: ${changed}`, async ({
    page,
    request,
  }) => {
    await seed(request);
    await page.route('**/meeting-types/Consult/slots', async (route) => {
      const response =
        changed === 'disabled'
          ? await request.put('/api/owner/public-booking', {
              headers: { 'Idempotency-Key': 'close' },
              data: { enabled: false, expectedVersion: 2 },
            })
          : await request.put('/api/owner/profile', {
              headers: { 'Idempotency-Key': 'rename' },
              data: {
                name: 'Анна Петрова',
                username: 'changed-owner',
                expectedVersion: 2,
              },
            });
      expect(response.status()).toBe(200);
      await route.continue();
    });
    await page.goto('/booking/anna-1/Consult');
    if (changed === 'disabled') {
      await expect(page.getByText('Запись временно недоступна')).toBeVisible();
    } else {
      await expect(
        page.getByRole('heading', { name: 'Страница не найдена' }),
      ).toBeVisible();
      await expect(page).toHaveURL('/booking/anna-1/Consult');
    }
    await expect(
      page.getByText('Подробное описание нашей консультации'),
    ).toHaveCount(0);
    await expect(page.getByRole('group', { name: 'Календарь' })).toHaveCount(0);
  });
}
