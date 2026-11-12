import { useEffect, useState } from 'react';
import { getOwnerProfile, type OwnerProfile } from './api/generated';

type ProfileState =
  | { status: 'loading' }
  | { status: 'ready'; profile: OwnerProfile }
  | { status: 'error' };

export function OwnerPage() {
  const [state, setState] = useState<ProfileState>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    getOwnerProfile({ signal: controller.signal, throwOnError: true }).then(
      ({ data }) => {
        if (!controller.signal.aborted) {
          setState({ status: 'ready', profile: data });
        }
      },
      () => {
        if (!controller.signal.aborted) {
          setState({ status: 'error' });
        }
      },
    );
    return () => controller.abort();
  }, [attempt]);

  return (
    <section className="placeholder" aria-labelledby="page-title">
      <p className="eyebrow">Владельцу календаря</p>
      <h1 id="page-title">Профиль владельца</h1>
      {state.status === 'loading' && <p role="status">Загрузка профиля…</p>}
      {state.status === 'error' && (
        <div>
          <p role="alert">Не удалось загрузить профиль. Попробуйте ещё раз.</p>
          <button
            className="button"
            onClick={() => {
              setState({ status: 'loading' });
              setAttempt((previous) => previous + 1);
            }}
          >
            Повторить загрузку
          </button>
        </div>
      )}
      {state.status === 'ready' && (
        <>
          <dl className="profile-details">
            <div>
              <dt>Имя</dt>
              <dd>{state.profile.name}</dd>
            </div>
            <div>
              <dt>Username</dt>
              <dd>{state.profile.username}</dd>
            </div>
            <div>
              <dt>Часовой пояс</dt>
              <dd>{state.profile.timeZone}</dd>
            </div>
          </dl>
          <p>
            {state.profile.bookingEnabled
              ? 'Публичная запись включена'
              : 'Публичная запись выключена'}
          </p>
          <p>
            Управление расписанием, типами встреч и бронированиями появится
            позже.
          </p>
        </>
      )}
      <a className="button" href="/">
        Вернуться на главную <span aria-hidden="true">↗</span>
      </a>
    </section>
  );
}
