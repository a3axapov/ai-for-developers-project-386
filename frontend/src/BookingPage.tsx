import { useCallback, useEffect, useState } from 'react';
import { SlotCalendar } from './SlotCalendar';
import { GuestForm, type GuestContacts } from './GuestForm';
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
  const [contacts, setContacts] = useState<GuestContacts>({
    guestName: '',
    guestEmail: '',
    comment: '',
  });
  const shownType =
    state.status === 'ready' && 'meetingType' in state.page
      ? state.page.meetingType
      : undefined;
  const unavailable = useCallback((reason: 'not-found' | 'disabled') => {
    setState((previous) => {
      if (reason === 'not-found') return { status: 'not-found' };
      if (previous.status !== 'ready') return previous;
      return {
        status: 'ready',
        page: {
          ownerName: previous.page.ownerName,
          ownerTimeZone: previous.page.ownerTimeZone,
          state: 'BOOKING_DISABLED',
        },
      };
    });
  }, []);

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
    <section
      className="placeholder"
      aria-labelledby="page-title"
      onClick={(event) => {
        if (
          event.button !== 0 ||
          event.ctrlKey ||
          event.metaKey ||
          event.shiftKey ||
          event.altKey
        )
          return;
        const link =
          event.target instanceof Element ? event.target.closest('a') : null;
        if (
          !link ||
          link.target ||
          !link.pathname.startsWith(`/booking/${encodeURIComponent(username!)}`)
        )
          return;
        event.preventDefault();
        setState({ status: 'loading' });
        window.history.pushState(null, '', link.href);
        window.dispatchEvent(new PopStateEvent('popstate'));
      }}
    >
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
          <>
            <p>Запись временно недоступна</p>
            <button
              className="button"
              onClick={() => {
                setState({ status: 'loading' });
                setAttempt((previous) => previous + 1);
              }}
            >
              Обновить доступность
            </button>
          </>
        ) : 'meetingType' in state.page && state.page.meetingType ? (
          <>
            <h2>Выбранный тип встречи</h2>
            <article className="meeting-card">
              <Conditions item={state.page.meetingType} />
            </article>
            <SlotCalendar
              key={`${username}/${meetingTypeId}`}
              username={username!}
              id={meetingTypeId!}
              onUnavailable={unavailable}
              renderSelection={(slot) => (
                <GuestForm
                  key={`${slot.startsAt}/${shownType!.conditionsToken}`}
                  username={username!}
                  meetingTypeId={meetingTypeId!}
                  conditionsToken={shownType!.conditionsToken}
                  slot={slot}
                  contacts={contacts}
                  onChange={setContacts}
                  onUnavailable={unavailable}
                />
              )}
            />
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
