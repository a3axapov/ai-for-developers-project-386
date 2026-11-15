import { useEffect, useState } from 'react';
import {
  listOutOfOffice,
  createOutOfOffice,
  updateOutOfOffice,
  deleteOutOfOffice,
  getOwnerOperation,
  getOwnerProfile,
  type OutOfOffice,
  type ApiError,
  type OwnerProfile,
} from './api/generated';
import { dateLabel, dayKey, timeLabel } from './displayTime';

export function Vacations({
  timeZone,
  blocked,
  onLockChange,
  onProfileLoaded,
}: {
  timeZone: string;
  blocked: boolean;
  onLockChange: (value: boolean) => void;
  onProfileLoaded: (value: OwnerProfile) => void;
}) {
  const [items, setItems] = useState<OutOfOffice[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [current, setCurrent] = useState<OutOfOffice>();
  const [draft, setDraft] = useState({ startDate: '', endDate: '' });
  const [error, setError] = useState<ApiError>();
  const [preview, setPreview] = useState<ApiError>();
  const [operation, setOperation] = useState<'save' | 'delete'>('save');
  const [saving, setSaving] = useState(false);
  const [pendingKey, setPendingKey] = useState<string>();
  const [unavailable, setUnavailable] = useState(false);
  const [missing, setMissing] = useState(false);
  const locked = blocked || saving || Boolean(pendingKey);
  const zone = current?.timeZone ?? timeZone;

  useEffect(() => {
    const controller = new AbortController();
    listOutOfOffice({ throwOnError: true, signal: controller.signal }).then(
      ({ data }) => {
        if (!controller.signal.aborted) {
          setItems(data.items);
          setLoading(false);
        }
      },
      () => {
        if (!controller.signal.aborted) {
          setError({
            code: 'READ_FAILED',
            message: 'Не удалось загрузить отпуска.',
          });
          setLoading(false);
        }
      },
    );
    return () => controller.abort();
  }, []);

  async function load(recovery = false) {
    setLoading(true);
    try {
      const [result, profile] = await Promise.all([
        listOutOfOffice({
          throwOnError: true,
          signal: AbortSignal.timeout(15_000),
        }),
        getOwnerProfile({
          throwOnError: true,
          signal: AbortSignal.timeout(15_000),
        }),
      ]);
      setItems(result.data.items);
      if (recovery) {
        const value = result.data.items.find((item) => item.id === current?.id);
        setMissing(Boolean(current && !value));
        if (value) setCurrent(value);
        setPendingKey(undefined);
        setUnavailable(false);
        setPreview(undefined);
        onLockChange(false);
        onProfileLoaded(profile.data);
      }
      setError(undefined);
    } catch {
      setError({
        code: 'READ_FAILED',
        message: 'Не удалось загрузить отпуска. Черновик сохранён.',
      });
    } finally {
      setLoading(false);
    }
  }

  function success(value?: OutOfOffice) {
    setItems((previous) =>
      value
        ? [...previous.filter((item) => item.id !== value.id), value]
        : previous.filter((item) => item.id !== current?.id),
    );
    setPendingKey(undefined);
    setPreview(undefined);
    setError(undefined);
    setEditing(false);
    onLockChange(false);
  }

  function failure(value: ApiError) {
    setError(value);
    setPreview(
      value.code === 'IMPACT_CONFIRMATION_REQUIRED' ? value : undefined,
    );
    if (value.currentOutOfOffice) setCurrent(value.currentOutOfOffice);
    setPendingKey(undefined);
    onLockChange(false);
  }

  function unknown(key: string) {
    setPendingKey(key);
    setError({
      code: 'UNKNOWN',
      message:
        'Исход операции неизвестен. Выясните результат перед следующим изменением.',
    });
  }

  async function save(kind: 'save' | 'delete', confirmationToken?: string) {
    if (locked || loading || missing) return;
    setSaving(true);
    onLockChange(true);
    setOperation(kind);
    setError(undefined);
    setPreview(undefined);
    const key = crypto.randomUUID();
    try {
      const options = {
        headers: { 'Idempotency-Key': key },
        signal: AbortSignal.timeout(15_000),
      };
      if (kind === 'delete' && current) {
        const result = await deleteOutOfOffice({
          ...options,
          path: { id: current.id },
          body: { expectedVersion: current.version, confirmationToken },
        });
        if (result.response?.status === 204) success();
        else if (
          result.response &&
          [400, 404, 409, 422].includes(result.response.status) &&
          result.error?.code
        )
          failure(result.error);
        else unknown(key);
      } else {
        const result = current
          ? await updateOutOfOffice({
              ...options,
              path: { id: current.id },
              body: {
                ...draft,
                expectedVersion: current.version,
                confirmationToken,
              },
            })
          : await createOutOfOffice({
              ...options,
              body: { ...draft, confirmationToken },
            });
        if (result.data) success(result.data);
        else if (
          result.response &&
          [400, 404, 409, 422].includes(result.response.status) &&
          result.error?.code
        )
          failure(result.error);
        else unknown(key);
      }
    } catch {
      unknown(key);
    } finally {
      setSaving(false);
    }
  }

  async function recover() {
    if (!pendingKey || saving) return;
    setSaving(true);
    try {
      const result = await getOwnerOperation({
        path: { key: pendingKey },
        signal: AbortSignal.timeout(15_000),
      });
      if (result.data?.statusCode === 204) success();
      else if (result.data?.outOfOffice) success(result.data.outOfOffice);
      else if (result.data?.error) failure(result.data.error);
      else if (result.response?.status === 410) {
        setUnavailable(true);
        setError({
          code: 'OPERATION_UNAVAILABLE',
          message: 'Результат операции недоступен. Возможен сброс данных.',
        });
      } else unknown(pendingKey);
    } catch {
      unknown(pendingKey);
    } finally {
      setSaving(false);
    }
  }

  function edit(value?: OutOfOffice) {
    setCurrent(value);
    setDraft({
      startDate: value?.startDate ?? '',
      endDate: value?.endDate ?? '',
    });
    setEditing(true);
    setMissing(false);
    setPreview(undefined);
    setError(undefined);
  }

  return (
    <section aria-label="Общие отпуска">
      <h2>Общие отпуска</h2>
      <p>
        Целые дни включительно для всех типов. Отпуск имеет приоритет над
        открытыми датами. Пересекающиеся отпуска действуют совместно; снятие
        одного запрета не отменяет остальные ограничения и занятость.
      </p>
      {loading && <p role="status">Загрузка отпусков…</p>}
      {!editing && error && <p role="alert">{error.message}</p>}
      {!editing && (
        <button
          className="button"
          disabled={locked || loading}
          onClick={() => void load()}
        >
          Обновить отпуска
        </button>
      )}
      <ul>
        {items.map((item) => (
          <li key={item.id}>
            {dateLabel(item.startDate)} — {dateLabel(item.endDate)} ·{' '}
            {item.timeZone} · версия {item.version}
            <button
              className="button"
              disabled={locked || loading || editing}
              onClick={() => edit(item)}
            >
              Редактировать отпуск
            </button>
          </li>
        ))}
      </ul>
      {!editing && (
        <button
          className="button"
          disabled={locked || loading}
          onClick={() => edit()}
        >
          Добавить отпуск
        </button>
      )}
      {editing && (
        <form
          className="profile-editor"
          aria-label="Отпуск"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            if (!preview) void save('save');
          }}
        >
          <h3>{current ? 'Изменение отпуска' : 'Новый отпуск'}</h3>
          <p>
            {current ? 'Сохранённый часовой пояс' : 'Часовой пояс создания'}:{' '}
            {zone}
          </p>
          {current && (
            <p>
              Сохранено: {dateLabel(current.startDate)} —{' '}
              {dateLabel(current.endDate)} · версия {current.version}
            </p>
          )}
          <p>Бронирования не отменяются и не переносятся.</p>
          <fieldset disabled={locked || missing || loading}>
            <legend>Черновик отпуска</legend>
            {(['startDate', 'endDate'] as const).map((field) => {
              const problem = error?.fieldErrors?.find(
                (value) => value.field === field,
              );
              return (
                <label key={field}>
                  {field === 'startDate' ? 'Начальная дата' : 'Конечная дата'}
                  <input
                    type="date"
                    value={draft[field]}
                    aria-invalid={Boolean(problem)}
                    aria-describedby={
                      problem ? `vacation-${field}-error` : undefined
                    }
                    onChange={(event) => {
                      setDraft({ ...draft, [field]: event.target.value });
                      setPreview(undefined);
                      setError(undefined);
                    }}
                  />
                  {problem && (
                    <span role="alert" id={`vacation-${field}-error`}>
                      {problem.message}
                    </span>
                  )}
                </label>
              );
            })}
          </fieldset>
          {error && !preview && <p role="alert">{error.message}</p>}
          {missing && (
            <p role="alert">
              Отпуск больше недоступен. Черновик сохранён. Закройте редактор для
              нового действия.
            </p>
          )}
          {preview && (
            <section aria-label="Последствия изменения отпуска">
              <h4>Проверьте последствия</h4>
              <p>
                {operation === 'delete' ? 'Удаление' : 'Сохранение'}:{' '}
                {dateLabel(draft.startDate)} — {dateLabel(draft.endDate)} (
                {zone})
              </p>
              <p>
                Только новые последствия для будущих активных записей. Встречи
                не отменяются и не переносятся. Ожидающие останутся занятыми, но
                их нельзя будет подтвердить.
              </p>
              <ul>
                {preview.affectedBookings?.map((booking) => (
                  <li key={booking.id}>
                    {booking.title} · {booking.guestName} ·{' '}
                    {booking.status === 'PENDING'
                      ? 'Ожидает подтверждения'
                      : 'Подтверждено'}{' '}
                    ·{dateLabel(dayKey(booking.startsAt, zone))},{' '}
                    {timeLabel(booking.startsAt, zone)} —
                    {dateLabel(dayKey(booking.endsAt, zone))},{' '}
                    {timeLabel(booking.endsAt, zone)} ({zone})
                  </li>
                ))}
              </ul>
              <button
                className="button"
                type="button"
                disabled={locked}
                onClick={() => void save(operation, preview.confirmationToken)}
              >
                Подтвердить последствия отпуска
              </button>
              <button
                className="button"
                type="button"
                disabled={locked}
                onClick={() => {
                  setPreview(undefined);
                  setError(undefined);
                }}
              >
                Отменить изменение отпуска
              </button>
            </section>
          )}
          {pendingKey && (
            <button
              className="button"
              type="button"
              disabled={saving}
              onClick={() => void recover()}
            >
              Выяснить результат изменения отпуска
            </button>
          )}
          {unavailable && (
            <button
              className="button"
              type="button"
              disabled={saving || loading}
              onClick={() => void load(true)}
            >
              Загрузить актуальные отпуска
            </button>
          )}
          <button
            className="button"
            disabled={locked || loading || missing || Boolean(preview)}
          >
            Сохранить отпуск
          </button>
          {current && (
            <button
              className="button"
              type="button"
              disabled={locked || loading || missing || Boolean(preview)}
              onClick={() => void save('delete')}
            >
              Удалить отпуск
            </button>
          )}
          <button
            className="button"
            type="button"
            disabled={locked}
            onClick={() => setEditing(false)}
          >
            Закрыть редактор отпуска
          </button>
        </form>
      )}
    </section>
  );
}
