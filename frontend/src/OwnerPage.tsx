import { useEffect, useState } from 'react';
import {
  getOwnerProfile,
  getOwnerOperation,
  setPublicBooking,
  type ApiError,
  type OwnerProfile,
} from './api/generated';

type ProfileState =
  | { status: 'loading' }
  | { status: 'ready'; profile: OwnerProfile }
  | { status: 'error' };

export function OwnerPage() {
  const [state, setState] = useState<ProfileState>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);
  const [saving, setSaving] = useState(false);
  const [commandError, setCommandError] = useState<string>();
  const [copyMessage, setCopyMessage] = useState<string>();
  const [pendingKey, setPendingKey] = useState<string>();
  const [operationUnavailable, setOperationUnavailable] = useState(false);

  function definiteFailure(error: ApiError) {
    if (error.currentProfile)
      setState({ status: 'ready', profile: error.currentProfile });
    setPendingKey(undefined);
    setCommandError(error.message);
  }

  function unknownOutcome(key: string) {
    setPendingKey(key);
    setCommandError(
      'Исход операции неизвестен. Выясните результат перед следующим изменением.',
    );
  }

  async function recoverOperation() {
    if (!pendingKey || saving) return;
    setSaving(true);
    try {
      const result = await getOwnerOperation({
        path: { key: pendingKey },
        signal: AbortSignal.timeout(15_000),
      });
      if (result.data?.statusCode === 200 && result.data.profile) {
        setState({ status: 'ready', profile: result.data.profile });
        setPendingKey(undefined);
        setCommandError(undefined);
      } else if (result.data?.error) {
        definiteFailure(result.data.error);
      } else if (result.response?.status === 410) {
        setOperationUnavailable(true);
        setCommandError(
          'Результат операции недоступен. Возможен сброс данных.',
        );
      } else {
        unknownOutcome(pendingKey);
      }
    } catch {
      unknownOutcome(pendingKey);
    } finally {
      setSaving(false);
    }
  }

  async function copyBookingUrl() {
    if (state.status !== 'ready') return;
    try {
      await navigator.clipboard.writeText(
        new URL(state.profile.bookingUrl, window.location.origin).href,
      );
      setCopyMessage('Ссылка скопирована');
    } catch {
      setCopyMessage(
        'Не удалось скопировать. Выделите и скопируйте ссылку вручную.',
      );
    }
  }

  async function toggleBooking() {
    if (state.status !== 'ready' || saving || pendingKey) return;
    setSaving(true);
    setCommandError(undefined);
    const key = crypto.randomUUID();
    try {
      const result = await setPublicBooking({
        headers: { 'Idempotency-Key': key },
        signal: AbortSignal.timeout(15_000),
        body: {
          enabled: !state.profile.bookingEnabled,
          expectedVersion: state.profile.version,
        },
      });
      if (result.data) setState({ status: 'ready', profile: result.data });
      else if (
        result.response &&
        [400, 409, 422].includes(result.response.status) &&
        result.error?.code
      ) {
        definiteFailure(result.error);
      } else unknownOutcome(key);
    } catch {
      unknownOutcome(key);
    } finally {
      setSaving(false);
    }
  }

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
          <label className="booking-toggle">
            <input
              type="checkbox"
              role="switch"
              checked={state.profile.bookingEnabled}
              disabled={saving || Boolean(pendingKey)}
              onChange={() => {
                void toggleBooking();
              }}
              aria-describedby={commandError ? 'booking-error' : undefined}
            />
            Публичная запись
          </label>
          {saving && <p role="status">Сохранение…</p>}
          {commandError && (
            <p role="alert" id="booking-error">
              {commandError}
            </p>
          )}
          {pendingKey && (
            <>
              <p>Показано последнее подтверждённое состояние.</p>
              {operationUnavailable ? (
                <button
                  className="button"
                  onClick={() => {
                    setPendingKey(undefined);
                    setOperationUnavailable(false);
                    setCommandError(undefined);
                    setState({ status: 'loading' });
                    setAttempt((previous) => previous + 1);
                  }}
                >
                  Загрузить актуальный профиль
                </button>
              ) : (
                <button
                  className="button"
                  disabled={saving}
                  onClick={() => {
                    void recoverOperation();
                  }}
                >
                  Выяснить результат
                </button>
              )}
            </>
          )}
          <p>Общая ссылка для гостей:</p>
          <p className="booking-link">
            <a href={state.profile.bookingUrl}>
              {new URL(state.profile.bookingUrl, window.location.origin).href}
            </a>
          </p>
          <button
            className="button"
            onClick={() => {
              void copyBookingUrl();
            }}
          >
            Копировать ссылку
          </button>
          {copyMessage && <p role="status">{copyMessage}</p>}
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
