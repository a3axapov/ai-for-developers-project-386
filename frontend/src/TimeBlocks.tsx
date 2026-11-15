import { useEffect, useMemo, useRef, useState } from 'react';
import {
  createTimeBlock,
  updateTimeBlock,
  deleteTimeBlock,
  getOwnerCalendar,
  getOwnerOperation,
  getOwnerProfile,
  type ApiError,
  type TimeBlock,
  type TimeBlockResult,
  type OwnerProfile,
} from './api/generated';
import { localTime, validOffsets, offsetInZone } from './manualTime';
import { dateLabel, dayKey, timeLabel } from './displayTime';

export function TimeBlocks({
  timeZone,
  blocked,
  onLockChange,
  onProfileLoaded,
}: {
  timeZone: string;
  blocked: boolean;
  onLockChange: (value: boolean) => void;
  onProfileLoaded: (profile: OwnerProfile) => void;
}) {
  const [initialRange] = useState(() => {
    const year = new Date().getFullYear();
    return {
      from: `${year}-01-01T00:00:00Z`,
      to: `${year + 1}-01-01T00:00:00Z`,
    };
  });
  const [range, setRange] = useState(initialRange);
  const [items, setItems] = useState<TimeBlock[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [current, setCurrent] = useState<TimeBlock>();
  const [draft, setDraft] = useState({
    title: '',
    startsAt: '',
    endsAt: '',
    startOffset: '',
    endOffset: '',
  });
  const [error, setError] = useState<ApiError>();
  const [preview, setPreview] = useState<ApiError>();
  const [saving, setSaving] = useState(false);
  const [pendingKey, setPendingKey] = useState<string>();
  const [unavailable, setUnavailable] = useState(false);
  const [missing, setMissing] = useState(false);
  const locked = blocked || saving || Boolean(pendingKey);
  const previousZone = useRef(timeZone);
  useEffect(() => {
    const oldZone = previousZone.current;
    if (oldZone === timeZone) return;
    previousZone.current = timeZone;
    setPreview(undefined);
    setDraft((value) => {
      function rebase(wall: string, offset: string) {
        if (!current) return { wall, offset: '' };
        const choices = validOffsets(wall, oldZone);
        const selected = choices.includes(offset)
          ? offset
          : choices.length === 1
            ? choices[0]
            : '';
        if (!selected) return { wall, offset: '' };
        const instant = `${wall}${selected}`;
        return {
          wall: localTime(instant, timeZone),
          offset: offsetInZone(instant, timeZone),
        };
      }
      const start = rebase(value.startsAt, value.startOffset);
      const end = rebase(value.endsAt, value.endOffset);
      return {
        ...value,
        startsAt: start.wall,
        startOffset: start.offset,
        endsAt: end.wall,
        endOffset: end.offset,
      };
    });
  }, [timeZone, current]);
  const startOffsets = useMemo(
    () => validOffsets(draft.startsAt, timeZone),
    [draft.startsAt, timeZone],
  );
  const endOffsets = useMemo(
    () => validOffsets(draft.endsAt, timeZone),
    [draft.endsAt, timeZone],
  );
  const startOffset =
    draft.startOffset || (startOffsets.length === 1 ? startOffsets[0] : '');
  const endOffset =
    draft.endOffset || (endOffsets.length === 1 ? endOffsets[0] : '');
  const label = (instant: string) =>
    `${dateLabel(dayKey(instant, timeZone))}, ${timeLabel(instant, timeZone)}`;

  useEffect(() => {
    const controller = new AbortController();
    getOwnerCalendar({ query: initialRange, signal: controller.signal }).then(
      (result) => {
        if (controller.signal.aborted) return;
        if (result.data) setItems(result.data.timeBlocks);
        else
          setError({
            code: 'READ_FAILED',
            message: 'Не удалось загрузить блокировки.',
          });
        setLoading(false);
      },
      () => {
        if (!controller.signal.aborted) {
          setError({
            code: 'READ_FAILED',
            message: 'Не удалось загрузить блокировки.',
          });
          setLoading(false);
        }
      },
    );
    return () => controller.abort();
  }, [initialRange]);

  async function load(recovery = false) {
    setLoading(true);
    try {
      // Recovery must find an ID moved outside the displayed range by another tab.
      // Cover every four-digit RFC 3339 year, including boundary offsets.
      const query = recovery
        ? {
            from: '0000-01-01T00:00:00+18:00',
            to: '9999-12-31T23:59:59.999999999-18:00',
          }
        : range;
      const [result, profile] = await Promise.all([
        getOwnerCalendar({
          query,
          throwOnError: true,
          signal: AbortSignal.timeout(15_000),
        }),
        getOwnerProfile({
          throwOnError: true,
          signal: AbortSignal.timeout(15_000),
        }),
      ]);
      setItems(result.data.timeBlocks);
      onProfileLoaded(profile.data);
      if (recovery) {
        const value = result.data.timeBlocks.find(
          (item) => item.id === current?.id,
        );
        setMissing(Boolean(current && !value));
        if (value) setCurrent(value);
        setPendingKey(undefined);
        setUnavailable(false);
        setPreview(undefined);
        onLockChange(false);
      }
      setError(undefined);
    } catch {
      setError({
        code: 'READ_FAILED',
        message: 'Не удалось загрузить блокировки. Черновик сохранён.',
      });
    } finally {
      setLoading(false);
    }
  }

  function success(result?: TimeBlockResult) {
    setItems((previous) =>
      result
        ? [
            ...previous.filter((item) => item.id !== result.timeBlock.id),
            result.timeBlock,
          ]
        : previous.filter((item) => item.id !== current?.id),
    );
    setPendingKey(undefined);
    setUnavailable(false);
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
    if (value.currentTimeBlock) setCurrent(value.currentTimeBlock);
    if (value.code === 'TIME_BLOCK_NOT_FOUND') setMissing(true);
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
  function validate() {
    const errors = [];
    for (const [field, offsets, choice] of [
      ['startsAt', startOffsets, startOffset],
      ['endsAt', endOffsets, endOffset],
    ] as const) {
      if (!offsets.includes(choice))
        errors.push({
          field,
          code: 'INVALID_VALUE',
          message: offsets.length
            ? 'Выберите UTC-смещение.'
            : 'Укажите существующее местное время ГГГГ-ММ-ДДTчч:мм. Несуществующее время не сдвигается.',
        });
    }
    if (errors.length) {
      setError({
        code: 'VALIDATION_FAILED',
        message: 'Проверьте время блокировки.',
        fieldErrors: errors,
      });
      return false;
    }
    return true;
  }
  async function save(deleting = false, confirmationToken?: string) {
    if (locked || loading || missing || (!deleting && !validate())) return;
    setSaving(true);
    onLockChange(true);
    setError(undefined);
    setPreview(undefined);
    const key = crypto.randomUUID();
    const options = {
      headers: { 'Idempotency-Key': key },
      signal: AbortSignal.timeout(15_000),
    };
    try {
      if (deleting && current) {
        const result = await deleteTimeBlock({
          ...options,
          path: { id: current.id },
          body: { expectedVersion: current.version },
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
        const body = {
          title: draft.title,
          startsAt: `${draft.startsAt}${startOffset}`,
          endsAt: `${draft.endsAt}${endOffset}`,
          confirmationToken,
        };
        const result = current
          ? await updateTimeBlock({
              ...options,
              path: { id: current.id },
              body: { ...body, expectedVersion: current.version },
            })
          : await createTimeBlock({ ...options, body });
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
      else if (result.data?.timeBlockResult)
        success(result.data.timeBlockResult);
      else if (result.data?.error) failure(result.data.error);
      else if (result.response?.status === 410) {
        setUnavailable(true);
        setError({
          code: 'OPERATION_UNAVAILABLE',
          message:
            'Результат операции недоступен. Возможен сброс данных. Загрузите актуальное состояние; команда не повторится.',
        });
      } else unknown(pendingKey);
    } catch {
      unknown(pendingKey);
    } finally {
      setSaving(false);
    }
  }
  function edit(value?: TimeBlock) {
    setCurrent(value);
    setDraft({
      title: value?.title ?? '',
      startsAt: value ? localTime(value.startsAt, timeZone) : '',
      endsAt: value ? localTime(value.endsAt, timeZone) : '',
      startOffset: value ? offsetInZone(value.startsAt, timeZone) : '',
      endOffset: value ? offsetInZone(value.endsAt, timeZone) : '',
    });
    setEditing(true);
    setMissing(false);
    setPreview(undefined);
    setError(undefined);
  }

  return (
    <section aria-label="Блокировки времени">
      <h2>Блокировки времени</h2>
      <p>
        Личная занятость для всех типов, без гостевой записи. Названия не видны
        гостям. Прошедшее время, ночь и несколько дней допустимы.
      </p>
      {!editing && (
        <form
          className="profile-editor"
          aria-label="Диапазон блокировок"
          onSubmit={(event) => {
            event.preventDefault();
            void load();
          }}
        >
          <label>
            Блокировки с (RFC 3339)
            <input
              value={range.from}
              disabled={locked}
              onChange={(event) =>
                setRange({ ...range, from: event.target.value })
              }
            />
          </label>
          <label>
            Блокировки до (RFC 3339)
            <input
              value={range.to}
              disabled={locked}
              onChange={(event) =>
                setRange({ ...range, to: event.target.value })
              }
            />
          </label>
          <button className="button" disabled={locked || loading}>
            Обновить блокировки
          </button>
        </form>
      )}
      {loading && <p role="status">Загрузка блокировок…</p>}
      {!editing && error && <p role="alert">{error.message}</p>}
      <ul>
        {items.map((item) => (
          <li key={item.id}>
            Блокировка: {item.title} · {label(item.startsAt)} —{' '}
            {label(item.endsAt)} ({timeZone}) · версия {item.version}
            <button
              className="button"
              disabled={locked || loading || editing}
              onClick={() => edit(item)}
            >
              Редактировать блокировку
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
          Добавить блокировку
        </button>
      )}
      {editing && (
        <form
          className="profile-editor"
          aria-label="Блокировка времени"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            if (!preview) void save();
          }}
        >
          <h3>{current ? 'Изменение блокировки' : 'Новая блокировка'}</h3>
          <p>Текущий часовой пояс владельца: {timeZone}</p>
          {current && (
            <p>
              Сохранено: {current.title} · {label(current.startsAt)} —{' '}
              {label(current.endsAt)} · версия {current.version}
            </p>
          )}
          <fieldset disabled={locked || missing || loading}>
            <legend>Черновик блокировки</legend>
            {(['title', 'startsAt', 'endsAt'] as const).map((field) => {
              const problem = error?.fieldErrors?.find(
                (value) => value.field === field,
              );
              return (
                <div key={field}>
                  <label htmlFor={`block-${field}`}>
                    {field === 'title'
                      ? 'Название блокировки'
                      : field === 'startsAt'
                        ? 'Начало блокировки'
                        : 'Окончание блокировки'}
                  </label>
                  <input
                    id={`block-${field}`}
                    type="text"
                    value={draft[field]}
                    placeholder={field === 'title' ? '' : '2026-11-02T09:00'}
                    aria-invalid={Boolean(problem)}
                    aria-describedby={
                      problem ? `block-${field}-error` : undefined
                    }
                    onChange={(event) => {
                      setDraft({
                        ...draft,
                        [field]: event.target.value,
                        ...(field === 'startsAt'
                          ? { startOffset: '' }
                          : field === 'endsAt'
                            ? { endOffset: '' }
                            : {}),
                      });
                      setPreview(undefined);
                      setError(undefined);
                    }}
                  />
                  {problem && (
                    <span role="alert" id={`block-${field}-error`}>
                      {problem.message}
                    </span>
                  )}
                </div>
              );
            })}
            {(['start', 'end'] as const).map((which) => {
              const offsets = which === 'start' ? startOffsets : endOffsets;
              const field = which === 'start' ? 'startsAt' : 'endsAt';
              const problem = error?.fieldErrors?.some(
                (value) => value.field === field,
              );
              return (
                <div key={which}>
                  <label htmlFor={`block-${which}-offset`}>
                    {which === 'start'
                      ? 'UTC-смещение начала'
                      : 'UTC-смещение окончания'}
                  </label>
                  <select
                    id={`block-${which}-offset`}
                    value={which === 'start' ? startOffset : endOffset}
                    aria-invalid={problem}
                    aria-describedby={
                      problem ? `block-${field}-error` : undefined
                    }
                    onChange={(event) => {
                      setDraft({
                        ...draft,
                        [which === 'start' ? 'startOffset' : 'endOffset']:
                          event.target.value,
                      });
                      setPreview(undefined);
                      setError(undefined);
                    }}
                  >
                    <option value="">Выберите UTC-смещение</option>
                    {offsets.map((offset) => (
                      <option key={offset} value={offset}>
                        UTC{offset}
                      </option>
                    ))}
                  </select>
                </div>
              );
            })}
          </fieldset>
          {error && !preview && <p role="alert">{error.message}</p>}
          {missing && (
            <p role="alert">
              Блокировка больше недоступна. Черновик сохранён. Закройте редактор
              для нового действия.
            </p>
          )}
          {preview && (
            <section aria-label="Последствия блокировки">
              <h4>Проверьте последствия</h4>
              <p>
                {draft.title} · {draft.startsAt} UTC{startOffset} —{' '}
                {draft.endsAt} UTC{endOffset} ({timeZone})
              </p>
              <p>
                Все показанные ожидающие запросы будут отклонены без
                восстановления. Подтверждённая встреча запрещает блокировку.
              </p>
              <ul>
                {preview.affectedBookings?.map((booking) => (
                  <li key={booking.id}>
                    {booking.title} · {booking.guestName} · Ожидает
                    подтверждения · {label(booking.startsAt)} —{' '}
                    {label(booking.endsAt)} ({timeZone}) · версия{' '}
                    {booking.version}
                  </li>
                ))}
              </ul>
              <button
                className="button"
                type="button"
                disabled={locked}
                onClick={() => void save(false, preview.confirmationToken)}
              >
                Подтвердить блокировку и отклонение запросов
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
                Отменить изменение блокировки
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
              Выяснить результат изменения блокировки
            </button>
          )}
          {unavailable && (
            <button
              className="button"
              type="button"
              disabled={saving || loading}
              onClick={() => void load(true)}
            >
              Загрузить актуальные блокировки
            </button>
          )}
          <button
            className="button"
            disabled={locked || loading || missing || Boolean(preview)}
          >
            Сохранить блокировку
          </button>
          {current && (
            <button
              className="button"
              type="button"
              disabled={locked || loading || missing || Boolean(preview)}
              onClick={() => void save(true)}
            >
              Удалить блокировку
            </button>
          )}
          <button
            className="button"
            type="button"
            disabled={locked}
            onClick={() => setEditing(false)}
          >
            Закрыть редактор блокировки
          </button>
        </form>
      )}
    </section>
  );
}
