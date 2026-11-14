import { useState } from 'react';
import {
  listMeetingTypes,
  getOwnerProfile,
  getOwnerOperation,
  updateWeeklyHours,
  type ApiError,
  type MeetingType,
  type WorkingDay,
  type OwnerProfile,
} from './api/generated';
import { dateLabel, dayKey, timeLabel } from './displayTime';

const dayNames = {
  MONDAY: 'Пн',
  TUESDAY: 'Вт',
  WEDNESDAY: 'Ср',
  THURSDAY: 'Чт',
  FRIDAY: 'Пт',
  SATURDAY: 'Сб',
  SUNDAY: 'Вс',
};

export function WeeklyHours({
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
  const [days, setDays] = useState<WorkingDay[]>(
    item.days.map((day) => ({
      ...day,
      intervals: day.intervals.map((interval) => ({ ...interval })),
    })),
  );
  const [error, setError] = useState<ApiError>();
  const [preview, setPreview] = useState<ApiError>();
  const [saving, setSaving] = useState(false);
  const [pendingKey, setPendingKey] = useState<string>();
  const [unavailable, setUnavailable] = useState(false);
  const [missing, setMissing] = useState(false);
  const locked = blocked || saving || Boolean(pendingKey);

  function change(index: number, intervals: WorkingDay['intervals']) {
    setDays(days.map((day, i) => (i === index ? { ...day, intervals } : day)));
    setPreview(undefined);
    setError(undefined);
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

  function success(value: MeetingType) {
    setPendingKey(undefined);
    onLockChange(false);
    onSaved(value);
  }

  async function save(confirmationToken?: string) {
    if (locked || current.archived || missing) return;
    setSaving(true);
    onLockChange(true);
    setError(undefined);
    setPreview(undefined);
    const key = crypto.randomUUID();
    try {
      const result = await updateWeeklyHours({
        path: { id: current.id },
        headers: { 'Idempotency-Key': key },
        body: { days, expectedVersion: current.version, confirmationToken },
        signal: AbortSignal.timeout(15_000),
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
                'Тип больше недоступен. Возможен сброс данных. Черновик часов сохранён.',
            },
      );
      onReload(types.data.items, profile.data);
      onLockChange(false);
    } catch {
      setError({
        code: 'READ_FAILED',
        message:
          'Не удалось загрузить актуальный тип. Черновик часов сохранён.',
      });
    } finally {
      setSaving(false);
    }
  }

  return (
    <form
      className="profile-editor weekly-hours"
      aria-label="Недельные рабочие часы"
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void save();
      }}
    >
      <h3>Рабочие часы: {current.title}</h3>
      <p>
        Часовой пояс типа: {current.timeZone}. Пустой день закрыт. 24:00
        допустимо только как конец; ночь задаётся двумя днями.
      </p>
      <p>
        Пересекающиеся и соприкасающиеся интервалы объединяются. Существующие
        бронирования не отменяются и не переносятся.
      </p>
      <p>Сохранённые часы (версия {current.version}):</p>
      <ul>
        {current.days.map((day) => (
          <li key={day.day}>
            {dayNames[day.day]}:{' '}
            {day.intervals
              .map((value) => `${value.start}–${value.end}`)
              .join(', ') || 'Закрыто'}
          </li>
        ))}
      </ul>
      {current.archived && (
        <p role="alert">Архивный тип нельзя редактировать.</p>
      )}
      {days.map((day, i) => (
        <fieldset key={day.day} disabled={locked || current.archived}>
          <legend>{dayNames[day.day]}</legend>
          {day.intervals.length === 0 && <p>Закрыто</p>}
          {day.intervals.map((interval, j) => {
            const field = `days[${i}].intervals[${j}]`;
            const problem = error?.fieldErrors?.find((value) =>
              value.field.startsWith(field),
            );
            return (
              <div className="hours-interval" key={j}>
                {(['start', 'end'] as const).map((bound) => (
                  <label key={bound}>
                    {bound === 'start' ? 'Начало' : 'Конец'} {j + 1}
                    <input
                      type="text"
                      inputMode="text"
                      placeholder={bound === 'start' ? '09:00' : '24:00'}
                      value={interval[bound]}
                      aria-invalid={Boolean(problem)}
                      aria-describedby={
                        problem ? `hours-${i}-${j}-error` : undefined
                      }
                      onChange={(event) =>
                        change(
                          i,
                          day.intervals.map((value, index) =>
                            index === j
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
                  onClick={() =>
                    change(
                      i,
                      day.intervals.filter((_, index) => index !== j),
                    )
                  }
                >
                  Удалить интервал {j + 1}
                </button>
                {problem && (
                  <p role="alert" id={`hours-${i}-${j}-error`}>
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
              change(i, [...day.intervals, { start: '09:00', end: '17:00' }])
            }
          >
            Добавить интервал
          </button>
        </fieldset>
      ))}
      {error && <p role="alert">{error.message}</p>}
      {preview && (
        <section role="region" aria-label="Последствия изменения часов">
          <h4>Затронутые бронирования</h4>
          <p>
            Показываются только будущие ожидающие и подтверждённые записи,
            допустимые сейчас, но закрываемые новыми часами. Ранее закрытые,
            начавшиеся, отменённые и отклонённые записи не включаются. Ожидающие
            останутся занятыми, но их нельзя будет подтвердить.
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
            onClick={() => void save(preview.confirmationToken)}
          >
            Подтвердить последствия и сохранить часы
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
            Отменить изменение часов
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
          Выяснить результат изменения часов
        </button>
      )}
      {unavailable && (
        <button
          className="button"
          type="button"
          disabled={saving}
          onClick={() => void reload()}
        >
          Загрузить актуальные часы
        </button>
      )}
      <button
        className="button"
        disabled={locked || current.archived || missing || Boolean(preview)}
      >
        {saving ? 'Сохранение часов…' : 'Сохранить часы'}
      </button>
      <button
        className="button"
        type="button"
        disabled={locked}
        onClick={onClose}
      >
        Закрыть редактор часов
      </button>
    </form>
  );
}
