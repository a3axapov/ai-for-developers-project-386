import { useState } from 'react';
import {
  putDateOverride,
  deleteDateOverride,
  getOwnerOperation,
  listMeetingTypes,
  getOwnerProfile,
  type ApiError,
  type MeetingType,
  type WorkingInterval,
  type OwnerProfile,
} from './api/generated';
import { dateLabel, dayKey, timeLabel } from './displayTime';

export function DateOverrides({
  item,
  blocked,
  onLockChange,
  onSaved,
  onClose,
  onReload,
}: {
  item: MeetingType;
  blocked: boolean;
  onLockChange: (locked: boolean) => void;
  onSaved: (item: MeetingType) => void;
  onClose: () => void;
  onReload: (items: MeetingType[], profile: OwnerProfile) => void;
}) {
  const [current, setCurrent] = useState(item);
  const [date, setDate] = useState('');
  const [intervals, setIntervals] = useState<WorkingInterval[]>([]);
  const [error, setError] = useState<ApiError>();
  const [preview, setPreview] = useState<ApiError>();
  const [operation, setOperation] = useState<'PUT' | 'DELETE'>('PUT');
  const [saving, setSaving] = useState(false);
  const [pendingKey, setPendingKey] = useState<string>();
  const [unavailable, setUnavailable] = useState(false);
  const [missing, setMissing] = useState(false);
  const locked = blocked || saving || Boolean(pendingKey);

  function success(value: MeetingType) {
    setPendingKey(undefined);
    onLockChange(false);
    onSaved(value);
  }

  function failure(value: ApiError) {
    setError(value);
    setPreview(
      value.code === 'IMPACT_CONFIRMATION_REQUIRED' ? value : undefined,
    );
    if (value.currentMeetingType) setCurrent(value.currentMeetingType);
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

  function changeDate(value: string) {
    setDate(value);
    setIntervals(
      current.dateOverrides
        .find((override) => override.date === value)
        ?.intervals.map((interval) => ({ ...interval })) ?? [],
    );
    setError(undefined);
    setPreview(undefined);
  }

  function change(value: WorkingInterval[]) {
    setIntervals(value);
    setError(undefined);
    setPreview(undefined);
  }

  async function save(operation: 'PUT' | 'DELETE', confirmationToken?: string) {
    if (locked || current.archived || missing) return;
    setSaving(true);
    onLockChange(true);
    setError(undefined);
    setPreview(undefined);
    setOperation(operation);
    const key = crypto.randomUUID();
    try {
      const options = {
        path: { id: current.id, date },
        headers: { 'Idempotency-Key': key },
        signal: AbortSignal.timeout(15_000),
      };
      const result =
        operation === 'PUT'
          ? await putDateOverride({
              ...options,
              body: {
                intervals,
                expectedVersion: current.version,
                confirmationToken,
              },
            })
          : await deleteDateOverride({
              ...options,
              body: { expectedVersion: current.version, confirmationToken },
            });
      if (result.data) success(result.data);
      else if (
        result.response &&
        [400, 404, 409, 422].includes(result.response.status) &&
        result.error?.code
      )
        failure(result.error);
      else unknown(key);
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
      if (result.data?.statusCode === 200 && result.data.meetingType)
        success(result.data.meetingType);
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

  async function reload() {
    if (saving) return;
    setSaving(true);
    try {
      const [types, profile] = await Promise.all([
        listMeetingTypes({
          signal: AbortSignal.timeout(15_000),
          throwOnError: true,
        }),
        getOwnerProfile({
          signal: AbortSignal.timeout(15_000),
          throwOnError: true,
        }),
      ]);
      const item = types.data.items.find((value) => value.id === current.id);
      if (item) setCurrent(item);
      setMissing(!item);
      setPendingKey(undefined);
      setUnavailable(false);
      setPreview(undefined);
      setError(
        item
          ? undefined
          : {
              code: 'PAGE_NOT_FOUND',
              message:
                'Тип больше недоступен. Возможен сброс данных. Черновик исключения сохранён.',
            },
      );
      onReload(types.data.items, profile.data);
      onLockChange(false);
    } catch {
      setError({
        code: 'READ_FAILED',
        message:
          'Не удалось загрузить актуальный тип. Черновик исключения сохранён.',
      });
    } finally {
      setSaving(false);
    }
  }

  const dateError = error?.fieldErrors?.find((value) => value.field === 'date');

  return (
    <form
      className="profile-editor weekly-hours"
      aria-label="Исключение даты"
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void save('PUT');
      }}
    >
      <h3>Исключения дат: {current.title}</h3>
      <p>
        Часовой пояс типа: {current.timeZone}. Местная дата полностью заменяет
        недельные часы. Пустое исключение закрывает день; удаление возвращает
        текущие недельные часы. Можно открыть нерабочий день.
      </p>
      <p>
        24:00 допустимо только как конец; ночь задаётся двумя соседними датами.
        Бронирования не отменяются и не переносятся.
      </p>
      <p>Сохранённые исключения (версия {current.version}):</p>
      <ul>
        {current.dateOverrides.map((override) => (
          <li key={override.date}>
            {dateLabel(override.date)}:{' '}
            {override.intervals
              .map((value) => `${value.start}–${value.end}`)
              .join(', ') || 'Закрыто'}
          </li>
        ))}
      </ul>
      {current.archived && (
        <p role="alert">Архивный тип нельзя редактировать.</p>
      )}
      <fieldset disabled={locked || current.archived}>
        <legend>Черновик исключения</legend>
        <label>
          Местная дата
          <input
            type="date"
            value={date}
            aria-invalid={Boolean(dateError)}
            aria-describedby={dateError ? 'override-date-error' : undefined}
            onChange={(event) => changeDate(event.target.value)}
          />
        </label>
        {dateError && (
          <p role="alert" id="override-date-error">
            {dateError.message}
          </p>
        )}
        {intervals.length === 0 && (
          <p>Пустой массив: сохранить закрытый день</p>
        )}
        {intervals.map((interval, i) => {
          const problem = error?.fieldErrors?.find((value) =>
            value.field.startsWith(`intervals[${i}]`),
          );
          return (
            <div className="hours-interval" key={i}>
              {(['start', 'end'] as const).map((bound) => (
                <label key={bound}>
                  {bound === 'start' ? 'Начало' : 'Конец'} {i + 1}
                  <input
                    type="text"
                    placeholder={bound === 'start' ? '09:00' : '24:00'}
                    value={interval[bound]}
                    aria-invalid={Boolean(problem)}
                    aria-describedby={
                      problem ? `override-${i}-error` : undefined
                    }
                    onChange={(event) =>
                      change(
                        intervals.map((value, j) =>
                          j === i
                            ? { ...value, [bound]: event.target.value }
                            : value,
                        ),
                      )
                    }
                  />
                </label>
              ))}
              <button
                className="button"
                type="button"
                onClick={() => change(intervals.filter((_, j) => j !== i))}
              >
                Удалить интервал {i + 1}
              </button>
              {problem && (
                <p role="alert" id={`override-${i}-error`}>
                  {problem.message}
                </p>
              )}
            </div>
          );
        })}
        <button
          className="button"
          type="button"
          onClick={() =>
            change([...intervals, { start: '09:00', end: '17:00' }])
          }
        >
          Добавить интервал
        </button>
      </fieldset>
      {error && <p role="alert">{error.message}</p>}
      {saving && <p role="status">Обработка операции с исключением…</p>}
      {preview && (
        <section role="region" aria-label="Последствия изменения исключения">
          <h4>Затронутые бронирования</h4>
          <p>
            {operation === 'DELETE' ? 'Удаление' : 'Сохранение'} исключения{' '}
            {dateLabel(date)} ({current.timeZone}).
          </p>
          <p>
            Только будущие ожидающие и подтверждённые записи, допустимые сейчас,
            но закрываемые изменением. Ранее закрытые, начавшиеся, отменённые и
            отклонённые записи не включаются. Ожидающие останутся занятыми, но
            их нельзя будет подтвердить.
          </p>
          <ul>
            {preview.affectedBookings?.map((booking) => (
              <li key={booking.id}>
                {booking.title} · {booking.guestName} ·{' '}
                {booking.status === 'PENDING'
                  ? 'Ожидает подтверждения'
                  : 'Подтверждено'}{' '}
                · {dateLabel(dayKey(booking.startsAt, current.timeZone))},{' '}
                {timeLabel(booking.startsAt, current.timeZone)} —{' '}
                {dateLabel(dayKey(booking.endsAt, current.timeZone))},{' '}
                {timeLabel(booking.endsAt, current.timeZone)} (
                {current.timeZone})
              </li>
            ))}
          </ul>
          <button
            className="button"
            type="button"
            disabled={locked}
            onClick={() => void save(operation, preview.confirmationToken)}
          >
            Подтвердить последствия
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
            Отменить изменение исключения
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
          Выяснить результат изменения исключения
        </button>
      )}
      {unavailable && (
        <button
          className="button"
          type="button"
          disabled={saving}
          onClick={() => void reload()}
        >
          Загрузить актуальные исключения
        </button>
      )}
      <button
        className="button"
        disabled={
          locked || current.archived || missing || !date || Boolean(preview)
        }
      >
        Сохранить исключение
      </button>
      <button
        className="button"
        type="button"
        disabled={
          locked ||
          current.archived ||
          missing ||
          Boolean(preview) ||
          !current.dateOverrides.some((value) => value.date === date)
        }
        onClick={() => void save('DELETE')}
      >
        Удалить исключение
      </button>
      <button
        className="button"
        type="button"
        disabled={locked}
        onClick={onClose}
      >
        Закрыть редактор исключений
      </button>
    </form>
  );
}
