import { useEffect, useState } from 'react';
import {
  getBookingEntry,
  getPublicBookingPage,
  type PublicBookingPage,
} from './api/generated';

type PageState =
  | { status: 'loading' }
  | { status: 'ready'; page: PublicBookingPage }
  | { status: 'error' }
  | { status: 'not-found' };

export function BookingPage({ username }: { username?: string }) {
  const [state, setState] = useState<PageState>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      try {
        if (username === undefined) {
          const { data } = await getBookingEntry({
            signal: controller.signal,
            throwOnError: true,
          });
          if (!controller.signal.aborted)
            window.location.replace(data.bookingUrl);
        } else {
          const result = await getPublicBookingPage({
            path: { username },
            signal: controller.signal,
          });
          if (!controller.signal.aborted) {
            setState(
              result.data
                ? { status: 'ready', page: result.data }
                : {
                    status:
                      result.response?.status === 404 ? 'not-found' : 'error',
                  },
            );
          }
        }
      } catch {
        if (!controller.signal.aborted) setState({ status: 'error' });
      }
    }
    void load();
    return () => controller.abort();
  }, [username, attempt]);

  return (
    <section className="placeholder" aria-labelledby="page-title">
      <p className="eyebrow">Онлайн-запись</p>
      <h1 id="page-title">
        {state.status === 'ready'
          ? state.page.ownerName
          : state.status === 'not-found'
            ? 'Страница не найдена'
            : 'Страница бронирования'}
      </h1>
      {state.status === 'loading' && <p role="status">Загрузка страницы…</p>}
      {state.status === 'ready' && (
        <p>
          {state.page.state === 'BOOKING_DISABLED'
            ? 'Запись временно недоступна'
            : 'Сейчас нет доступных типов встреч'}
        </p>
      )}
      {state.status === 'error' && (
        <>
          <p role="alert">Не удалось загрузить страницу. Попробуйте ещё раз.</p>
          <button
            className="button"
            onClick={() => {
              setState({ status: 'loading' });
              setAttempt((previous) => previous + 1);
            }}
          >
            Повторить загрузку
          </button>
        </>
      )}
      <a className="button" href="/">
        Вернуться на главную <span aria-hidden="true">↗</span>
      </a>
    </section>
  );
}
