import { useEffect, useState } from 'react';
import {
  getBookingEntry,
  getPublicBookingPage,
  getPublicMeetingType,
  type PublicBookingPage,
  type PublicMeetingTypePage,
  type PublicMeetingType,
} from './api/generated';

type PageState =
  | { status: 'loading' }
  | { status: 'ready'; page: PublicBookingPage | PublicMeetingTypePage }
  | { status: 'error' }
  | { status: 'not-found' };

function Conditions({ item }: { item: PublicMeetingType }) {
  return (
    <>
      <h3>{item.title}</h3>
      <p className="meeting-description">{item.description}</p>
      <p>{item.durationMinutes} мин</p>
      <p>
        {item.requiresApproval
          ? 'Требуется ручное подтверждение'
          : 'Автоматическое подтверждение'}
      </p>
    </>
  );
}

export function BookingPage({
  username,
  meetingTypeId,
}: {
  username?: string;
  meetingTypeId?: string;
}) {
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
          const result =
            meetingTypeId !== undefined
              ? await getPublicMeetingType({
                  path: { username, id: meetingTypeId },
                  signal: controller.signal,
                })
              : await getPublicBookingPage({
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
  }, [username, meetingTypeId, attempt]);

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
      {state.status === 'ready' &&
        (state.page.state === 'BOOKING_DISABLED' ? (
          <p>Запись временно недоступна</p>
        ) : 'meetingType' in state.page && state.page.meetingType ? (
          <>
            <h2>Выбранный тип встречи</h2>
            <article className="meeting-card">
              <Conditions item={state.page.meetingType} />
            </article>
            <p>Выбор времени и запись появятся позже.</p>
          </>
        ) : 'items' in state.page && state.page.items?.length ? (
          <>
            <h2>Выберите тип встречи</h2>
            {state.page.items.map((item) => (
              <article className="meeting-card" key={item.id}>
                <Conditions item={item} />
                <a
                  className="button"
                  href={`/booking/${encodeURIComponent(username!)}/${encodeURIComponent(item.id)}`}
                >
                  Выбрать тип «{item.title}»
                </a>
              </article>
            ))}
          </>
        ) : (
          <p>Сейчас нет доступных типов встреч</p>
        ))}
      {meetingTypeId !== undefined && state.status === 'ready' && (
        <p>
          <a
            className="button"
            href={`/booking/${encodeURIComponent(username!)}`}
          >
            Все типы встреч
          </a>
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
