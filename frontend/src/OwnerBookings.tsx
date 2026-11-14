import { useEffect, useState } from 'react';
import {
  confirmOwnerBooking,
  rejectOwnerBooking,
  getOwnerBooking,
  getOwnerOperation,
  getOwnerCalendar,
  getOwnerProfile,
  listOwnerBookings,
  type ApiError,
  type BookingDetails,
} from './api/generated';
import { dateLabel, dayKey, timeLabel } from './displayTime';

const statusLabels = {
  PENDING: 'Ожидает подтверждения',
  CONFIRMED: 'Подтверждено',
  REJECTED: 'Отклонено',
  CANCELLED: 'Отменено',
};

function interval(booking: BookingDetails, zone: string) {
  const label = (instant: string) =>
    `${dateLabel(dayKey(instant, zone))}, ${timeLabel(instant, zone)}`;
  return `${label(booking.startsAt)} — ${label(booking.endsAt)}`;
}

function initialRange() {
  const now = new Date();
  return {
    from: new Date(
      Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1),
    ).toISOString(),
    to: new Date(
      Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 1),
    ).toISOString(),
  };
}

export function OwnerBookings({
  blocked,
  onLockChange,
}: {
  blocked: boolean;
  onLockChange: (locked: boolean) => void;
}) {
  const [view, setView] = useState('upcoming');
  const [status, setStatus] = useState('');
  const [range, setRange] = useState(initialRange);
  const [rangeDraft, setRangeDraft] = useState(range);
  const [reload, setReload] = useState(0);
  const [items, setItems] = useState<BookingDetails[]>([]);
  const [zone, setZone] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError>();
  const [selectedId, setSelectedId] = useState<string>();
  const [details, setDetails] = useState<BookingDetails>();
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');
  const [saving, setSaving] = useState(false);
  const [commandError, setCommandError] = useState('');
  const [pendingKey, setPendingKey] = useState<string>();
  const [operationUnavailable, setOperationUnavailable] = useState(false);
  const locked = saving || Boolean(pendingKey);

  function unknownOutcome(key: string) {
    setPendingKey(key);
    setCommandError(
      'Исход операции неизвестен. Выясните результат перед следующим изменением.',
    );
  }

  function decisionFinished(error?: ApiError) {
    setPendingKey(undefined);
    setOperationUnavailable(false);
    onLockChange(false);
    setCommandError(error?.message ?? '');
    // The saved operation is evidence of the command's outcome, not necessarily current details.
    refresh();
  }

  async function recoverDecision() {
    if (!pendingKey || saving) return;
    setSaving(true);
    try {
      const result = await getOwnerOperation({
        path: { key: pendingKey },
        signal: AbortSignal.timeout(15_000),
      });
      if (result.data?.statusCode === 200 && result.data.booking)
        decisionFinished();
      else if (result.data?.error) decisionFinished(result.data.error);
      else if (result.response?.status === 410) {
        setOperationUnavailable(true);
        setCommandError(
          'Результат операции недоступен. Возможен сброс данных. Загрузите актуальные бронирования; решение не будет отправлено заново.',
        );
      } else unknownOutcome(pendingKey);
    } catch {
      unknownOutcome(pendingKey);
    } finally {
      setSaving(false);
    }
  }

  async function reloadAfterUnavailable() {
    if (!selectedId || saving) return;
    setSaving(true);
    try {
      const result = await getOwnerBooking({
        path: { id: selectedId },
        signal: AbortSignal.timeout(15_000),
      });
      if (
        result.data ||
        (result.response?.status === 404 && result.error?.code)
      ) {
        decisionFinished(result.error);
        if (!result.data) {
          setDetails(undefined);
          setDetailError(result.error!.message);
        }
      } else
        setCommandError(
          'Не удалось загрузить актуальное бронирование. Повторите чтение.',
        );
    } catch {
      setCommandError(
        'Не удалось загрузить актуальное бронирование. Повторите чтение.',
      );
    } finally {
      setSaving(false);
    }
  }

  async function decideBooking(action: 'confirm' | 'reject') {
    if (!details || locked || blocked || detailLoading || loading) return;
    if (
      action === 'reject' &&
      !window.confirm(
        'Отклонение освободит занятость этого запроса. Восстановить его невозможно. Это не гарантирует свободный слот: другие ограничения продолжают действовать. Отклонить запрос?',
      )
    )
      return;
    setSaving(true);
    onLockChange(true);
    setCommandError('');
    const key = crypto.randomUUID();
    try {
      const result = await (
        action === 'confirm' ? confirmOwnerBooking : rejectOwnerBooking
      )({
        path: { id: details.id },
        headers: { 'Idempotency-Key': key },
        body: { expectedVersion: details.version },
        signal: AbortSignal.timeout(15_000),
      });
      if (result.data) decisionFinished();
      else if (
        result.response &&
        [400, 404, 409, 422].includes(result.response.status) &&
        result.error?.code
      )
        decisionFinished(result.error);
      else unknownOutcome(key);
    } catch {
      unknownOutcome(key);
    } finally {
      setSaving(false);
    }
  }

  function beginListRead() {
    setLoading(true);
    setError(undefined);
    setItems([]);
  }

  function beginDetailRead() {
    setDetailLoading(true);
    setDetails(undefined);
    setDetailError('');
  }

  function refresh() {
    beginListRead();
    if (selectedId) beginDetailRead();
    setReload((value) => value + 1);
  }

  useEffect(() => {
    const controller = new AbortController();
    const signal = controller.signal;
    const result =
      view === 'calendar'
        ? getOwnerCalendar({ query: range, signal })
        : listOwnerBookings({
            query: { view, ...(status ? { status } : {}) },
            signal,
          });
    void Promise.all([result, getOwnerProfile({ signal })])
      .then(([bookings, profile]) => {
        if (signal.aborted) return;
        if (bookings.data && profile.data) {
          setItems(bookings.data.items);
          setZone(profile.data.timeZone);
        } else {
          setError(
            bookings.response && bookings.error?.code
              ? bookings.error
              : {
                  code: 'READ_FAILED',
                  message:
                    'Не удалось загрузить бронирования. Обновите сведения.',
                },
          );
        }
      })
      .catch(() => {
        if (!signal.aborted)
          setError({
            code: 'READ_FAILED',
            message: 'Не удалось загрузить бронирования. Обновите сведения.',
          });
      })
      .finally(() => {
        if (!signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [view, status, range, reload]);

  useEffect(() => {
    if (!selectedId) return;
    const controller = new AbortController();
    void getOwnerBooking({
      path: { id: selectedId },
      signal: controller.signal,
    })
      .then((result) => {
        if (controller.signal.aborted) return;
        if (result.data) setDetails(result.data);
        else
          setDetailError(
            result.response && result.error?.code
              ? result.error.message
              : 'Не удалось загрузить детали. Обновите сведения.',
          );
      })
      .catch(() => {
        if (!controller.signal.aborted)
          setDetailError('Не удалось загрузить детали. Обновите сведения.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setDetailLoading(false);
      });
    return () => controller.abort();
  }, [selectedId, reload]);

  return (
    <section className="owner-bookings" aria-label="Бронирования владельца">
      <h2>Бронирования</h2>
      {zone && <p>Часовой пояс: {zone}</p>}
      <label>
        Представление бронирований
        <select
          disabled={locked}
          value={view}
          onChange={(event) => {
            if (event.target.value === view) return;
            beginListRead();
            setView(event.target.value);
          }}
        >
          <option value="upcoming">Предстоящие</option>
          <option value="history">История</option>
          <option value="calendar">Внутренний календарь</option>
        </select>
      </label>
      {view !== 'calendar' && (
        <label>
          Статус бронирований
          <select
            disabled={locked}
            value={status}
            onChange={(event) => {
              if (event.target.value === status) return;
              beginListRead();
              setStatus(event.target.value);
            }}
          >
            <option value="">Все статусы</option>
            {Object.entries(statusLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
      )}
      {view === 'calendar' && (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            beginListRead();
            setRange({ ...rangeDraft });
          }}
        >
          <h3>Внутренний календарь</h3>
          <p>
            Повестка в выбранном фактическом диапазоне. Это существующие
            бронирования, а не отдельные копии событий.
          </p>
          <p id="calendar-range-help">
            Укажите моменты RFC 3339 с UTC-смещением, например
            2026-11-02T09:00:00+03:00. Начало включено, конец исключён.
          </p>
          {(['from', 'to'] as const).map((field) => (
            <label key={field}>
              {field === 'from' ? 'Начало диапазона' : 'Конец диапазона'}
              <input
                disabled={locked}
                value={rangeDraft[field]}
                aria-describedby={`calendar-range-help${error?.fieldErrors?.some((item) => item.field === field) ? ` calendar-${field}-error` : ''}`}
                aria-invalid={
                  error?.fieldErrors?.some((item) => item.field === field) ||
                  undefined
                }
                onChange={(event) =>
                  setRangeDraft({ ...rangeDraft, [field]: event.target.value })
                }
              />
              {error?.fieldErrors
                ?.filter((item) => item.field === field)
                .map((item) => (
                  <span id={`calendar-${field}-error`} key={item.field}>
                    {item.message}
                  </span>
                ))}
            </label>
          ))}
          <button className="button" disabled={loading || locked}>
            Показать диапазон
          </button>
        </form>
      )}
      <button
        className="button"
        disabled={loading || detailLoading || locked}
        onClick={refresh}
      >
        Обновить бронирования
      </button>
      {loading && <p role="status">Загрузка бронирований…</p>}
      {error && <p role="alert">{error.message}</p>}
      {saving && <p role="status">Обработка решения…</p>}
      {commandError && <p role="alert">{commandError}</p>}
      {pendingKey && (
        <>
          <p>
            Показано последнее подтверждённое состояние. Новые решения
            заблокированы.
          </p>
          <button
            className="button"
            disabled={saving}
            onClick={() =>
              void (operationUnavailable
                ? reloadAfterUnavailable()
                : recoverDecision())
            }
          >
            {operationUnavailable
              ? 'Загрузить актуальные бронирования'
              : 'Выяснить результат решения'}
          </button>
        </>
      )}
      {!loading && !error && items.length === 0 && (
        <p>В выбранном представлении нет бронирований.</p>
      )}
      <ul aria-label="Список бронирований">
        {items.map((booking) => (
          <li key={booking.id}>
            <h3>{booking.title}</h3>
            <p>{interval(booking, zone)}</p>
            <p>{statusLabels[booking.status]}</p>
            <button
              className="button"
              disabled={locked}
              onClick={() => {
                beginDetailRead();
                setSelectedId(booking.id);
                refresh();
              }}
            >
              Открыть {booking.title}
            </button>
          </li>
        ))}
      </ul>
      {selectedId && (
        <section aria-label="Детали бронирования">
          <h3>Детали бронирования</h3>
          {detailLoading && <p role="status">Загрузка деталей…</p>}
          {detailError && <p role="alert">{detailError}</p>}
          {details && (
            <>
              <h4>{details.title}</h4>
              <p className="meeting-description">{details.description}</p>
              <p>{details.durationMinutes} мин</p>
              <p>
                {details.requiresApproval
                  ? 'Требуется ручное подтверждение'
                  : 'Автоматическое подтверждение'}
              </p>
              <p>Часовой пояс: {details.ownerTimeZone}</p>
              <p>{interval(details, details.ownerTimeZone)}</p>
              <p>{statusLabels[details.status]}</p>
              {details.rejectionReason === 'APPROVAL_EXPIRED' && (
                <p>Владелец не подтвердил запрос до начала встречи.</p>
              )}
              <p>{details.guestName}</p>
              <p>{details.guestEmail}</p>
              <p className="meeting-description">{details.comment}</p>
              <p>Версия: {details.version}</p>
              {details.status === 'PENDING' && (
                <>
                  <button
                    className="button"
                    disabled={locked || blocked || detailLoading || loading}
                    onClick={() => void decideBooking('confirm')}
                  >
                    Подтвердить запрос
                  </button>
                  <button
                    className="button"
                    disabled={locked || blocked || detailLoading || loading}
                    onClick={() => void decideBooking('reject')}
                  >
                    Отклонить запрос
                  </button>
                </>
              )}
            </>
          )}
        </section>
      )}
    </section>
  );
}
