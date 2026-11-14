import { useEffect, useState } from 'react';
import { WeeklyHours } from './WeeklyHours';
import { DateOverrides } from './DateOverrides';
import { dateLabel } from './displayTime';
import {
  createMeetingType,
  updateMeetingType,
  archiveMeetingType,
  listMeetingTypes,
  getOwnerOperation,
  getOwnerProfile,
  type ApiError,
  type MeetingType,
  type OwnerProfile,
} from './api/generated';

const emptyDraft = {
  id: '',
  title: '',
  description: '',
  durationMinutes: '30',
  requiresApproval: false,
};
export type MeetingTypeDraft = typeof emptyDraft;
export type MeetingTypeEdit = { current: MeetingType; draft: MeetingTypeDraft };

const dayNames = {
  MONDAY: 'Пн',
  TUESDAY: 'Вт',
  WEDNESDAY: 'Ср',
  THURSDAY: 'Чт',
  FRIDAY: 'Пт',
  SATURDAY: 'Сб',
  SUNDAY: 'Вс',
};

export function MeetingTypes({
  bookingUrl,
  blocked,
  draft = emptyDraft,
  onDraftChange,
  onLockChange,
  onProfileLoaded,
  editing,
  onEditChange,
}: {
  bookingUrl: string;
  blocked: boolean;
  draft: MeetingTypeDraft | undefined;
  onDraftChange: (draft: MeetingTypeDraft) => void;
  onLockChange: (locked: boolean) => void;
  onProfileLoaded: (profile: OwnerProfile) => void;
  editing: MeetingTypeEdit | undefined;
  onEditChange: (editing: MeetingTypeEdit | undefined) => void;
}) {
  const [items, setItems] = useState<MeetingType[]>([]);
  const [hoursItem, setHoursItem] = useState<MeetingType>();
  const [overrideItem, setOverrideItem] = useState<MeetingType>();
  const [hoursBlocked, setHoursBlocked] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();
  const [fieldErrors, setFieldErrors] = useState<ApiError['fieldErrors']>([]);
  const [copyMessage, setCopyMessage] = useState<string>();
  const [pendingKey, setPendingKey] = useState<string>();
  const [unavailable, setUnavailable] = useState(false);
  const [operation, setOperation] = useState<'create' | 'edit' | 'archive'>(
    'create',
  );
  const activeDraft = editing?.draft ?? draft;
  const locked = blocked || saving || Boolean(pendingKey) || hoursBlocked;

  function changeDraft(value: MeetingTypeDraft) {
    if (editing) onEditChange({ ...editing, draft: value });
    else onDraftChange(value);
  }

  function success(item: MeetingType, kind = operation) {
    setItems((previous) => [
      ...previous.filter((type) => type.id !== item.id),
      item,
    ]);
    if (kind === 'create') onDraftChange(emptyDraft);
    else if (editing?.current.id === item.id) onEditChange(undefined);
    setPendingKey(undefined);
    setError(undefined);
    setFieldErrors([]);
    onLockChange(false);
  }

  function failure(error: ApiError) {
    setError(error.message);
    setFieldErrors(error.fieldErrors ?? []);
    if (error.currentMeetingType) {
      const current = error.currentMeetingType;
      setItems((previous) =>
        previous.map((item) => (item.id === current.id ? current : item)),
      );
      if (editing?.current.id === current.id)
        onEditChange({ ...editing, current });
    }
    setPendingKey(undefined);
    onLockChange(false);
  }

  function unknown(key: string) {
    setPendingKey(key);
    setError(
      'Исход операции неизвестен. Выясните результат перед следующим изменением.',
    );
  }

  useEffect(() => {
    const controller = new AbortController();
    listMeetingTypes({ signal: controller.signal, throwOnError: true }).then(
      ({ data }) => {
        if (!controller.signal.aborted) {
          setItems(data.items);
          setLoading(false);
          setLoadError(false);
        }
      },
      () => {
        if (!controller.signal.aborted) {
          setLoading(false);
          setLoadError(true);
        }
      },
    );
    return () => controller.abort();
  }, [attempt]);

  async function save() {
    if (locked || loading || loadError) return;
    if (editing?.current.archived) return;
    const duration = Number(activeDraft.durationMinutes);
    if (!Number.isInteger(duration)) {
      failure({
        code: 'VALIDATION_FAILED',
        message: 'Проверьте данные запроса.',
        fieldErrors: [
          {
            field: 'durationMinutes',
            code: 'INVALID_VALUE',
            message: 'Введите целое число от 1 до 480 минут.',
          },
        ],
      });
      return;
    }
    setSaving(true);
    setOperation(editing ? 'edit' : 'create');
    onLockChange(true);
    setError(undefined);
    setFieldErrors([]);
    const key = crypto.randomUUID();
    try {
      const options = {
        headers: { 'Idempotency-Key': key },
        signal: AbortSignal.timeout(15_000),
        body: {
          ...activeDraft,
          durationMinutes: Number(activeDraft.durationMinutes),
        },
      };
      const result = editing
        ? await updateMeetingType({
            ...options,
            path: { id: editing.current.id },
            body: {
              title: activeDraft.title,
              description: activeDraft.description,
              durationMinutes: duration,
              requiresApproval: activeDraft.requiresApproval,
              expectedVersion: editing.current.version,
            },
          })
        : await createMeetingType(options);
      if (result.data) {
        success(result.data, editing ? 'edit' : 'create');
      } else if (
        result.response &&
        [400, 404, 409, 422].includes(result.response.status) &&
        result.error?.code
      ) {
        failure(result.error);
      } else unknown(key);
    } catch {
      unknown(key);
    } finally {
      setSaving(false);
    }
  }

  async function archive(item: MeetingType) {
    if (locked || loading || loadError || item.archived) return;
    if (
      !window.confirm(
        'Архивирование необратимо: тип нельзя восстановить или редактировать. Новая запись прекратится, существующие бронирования сохранятся. Архивировать тип?',
      )
    )
      return;
    setOperation('archive');
    setSaving(true);
    onLockChange(true);
    setError(undefined);
    setFieldErrors([]);
    const key = crypto.randomUUID();
    try {
      const result = await archiveMeetingType({
        path: { id: item.id },
        headers: { 'Idempotency-Key': key },
        body: { expectedVersion: item.version },
        signal: AbortSignal.timeout(15_000),
      });
      if (result.data) {
        success(result.data, 'archive');
      } else if (
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
      if (
        result.data &&
        [200, 201].includes(result.data.statusCode) &&
        result.data.meetingType
      )
        success(result.data.meetingType);
      else if (result.data?.error) failure(result.data.error);
      else if (result.response?.status === 410) {
        setUnavailable(true);
        setError('Результат операции недоступен. Возможен сброс данных.');
      } else unknown(pendingKey);
    } catch {
      unknown(pendingKey);
    } finally {
      setSaving(false);
    }
  }

  async function reloadAfterUnavailable() {
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
      setItems(types.data.items);
      if (editing) {
        const current = types.data.items.find(
          (item) => item.id === editing.current.id,
        );
        if (current) onEditChange({ ...editing, current });
      }
      setPendingKey(undefined);
      setUnavailable(false);
      setError(undefined);
      setLoadError(false);
      onLockChange(false);
      onProfileLoaded(profile.data);
    } catch {
      setError(
        'Не удалось загрузить актуальные типы. Повторите загрузку; команда не отправлена заново.',
      );
    } finally {
      setSaving(false);
    }
  }

  async function copy(path: string) {
    try {
      await navigator.clipboard.writeText(
        new URL(path, window.location.origin).href,
      );
      setCopyMessage('Прямая ссылка скопирована');
    } catch {
      setCopyMessage(
        'Не удалось скопировать. Выделите и скопируйте ссылку вручную.',
      );
    }
  }

  return (
    <section aria-labelledby="meeting-types-title">
      <h2 id="meeting-types-title">Типы встреч</h2>
      {loading && <p role="status">Загрузка типов встреч…</p>}
      {loadError && (
        <>
          <p role="alert">Не удалось загрузить типы встреч.</p>
          <button
            className="button"
            onClick={() => {
              setLoading(true);
              setAttempt((n) => n + 1);
            }}
          >
            Повторить загрузку типов
          </button>
        </>
      )}
      {!loading && !loadError && items.length === 0 && (
        <p>Типов встреч пока нет</p>
      )}
      {items.map((item) => {
        const path = `${bookingUrl}/${encodeURIComponent(item.id)}`;
        return (
          <article
            className="meeting-card"
            aria-label={item.title}
            key={item.id}
          >
            <h3>{item.title}</h3>
            <p>ID: {item.id}</p>
            <p>{item.archived ? 'Архивный' : 'Активный'}</p>
            <p className="meeting-description">{item.description}</p>
            <p>
              {item.durationMinutes} мин ·{' '}
              {item.requiresApproval
                ? 'Требуется ручное подтверждение'
                : 'Автоматическое подтверждение'}
            </p>
            <p>Часовой пояс: {item.timeZone}</p>
            <ul>
              {item.days.map((day) => (
                <li key={day.day}>
                  {dayNames[day.day]}:{' '}
                  {day.intervals.length
                    ? day.intervals
                        .map((interval) => `${interval.start}–${interval.end}`)
                        .join(', ')
                    : 'Закрыто'}
                </li>
              ))}
            </ul>
            <ul aria-label="Исключения дат">
              {item.dateOverrides.map((override) => (
                <li key={override.date}>
                  {dateLabel(override.date)}:{' '}
                  {override.intervals
                    .map((value) => `${value.start}–${value.end}`)
                    .join(', ') || 'Закрыто'}
                </li>
              ))}
            </ul>
            <p className="booking-link">
              <a href={path}>{new URL(path, window.location.origin).href}</a>
            </p>
            <button
              className="button"
              onClick={() => {
                void copy(path);
              }}
            >
              Копировать прямую ссылку
            </button>
            {!item.archived && (
              <>
                <button
                  className="button"
                  disabled={locked}
                  onClick={() => setHoursItem(item)}
                >
                  Настроить рабочие часы
                </button>
                <button
                  className="button"
                  disabled={locked}
                  onClick={() => setOverrideItem(item)}
                >
                  Настроить исключения дат
                </button>
                <button
                  className="button"
                  disabled={locked}
                  onClick={() => {
                    onEditChange({
                      current: item,
                      draft: {
                        id: item.id,
                        title: item.title,
                        description: item.description,
                        durationMinutes: String(item.durationMinutes),
                        requiresApproval: item.requiresApproval,
                      },
                    });
                    setFieldErrors([]);
                    setError(undefined);
                  }}
                >
                  Редактировать тип
                </button>
                <button
                  className="button"
                  disabled={locked}
                  onClick={() => void archive(item)}
                >
                  Архивировать тип
                </button>
              </>
            )}
          </article>
        );
      })}
      {copyMessage && <p role="status">{copyMessage}</p>}
      {hoursItem && (
        <WeeklyHours
          key={hoursItem.id}
          item={hoursItem}
          blocked={locked}
          onLockChange={(value) => {
            setHoursBlocked(value);
            onLockChange(value);
          }}
          onClose={() => setHoursItem(undefined)}
          onReload={(items, profile) => {
            setItems(items);
            onProfileLoaded(profile);
          }}
          onSaved={(item) => {
            setItems((previous) =>
              previous.map((value) => (value.id === item.id ? item : value)),
            );
            setHoursItem(undefined);
          }}
        />
      )}
      {overrideItem && (
        <DateOverrides
          key={overrideItem.id}
          item={overrideItem}
          blocked={locked}
          onLockChange={(value) => {
            setHoursBlocked(value);
            onLockChange(value);
          }}
          onClose={() => setOverrideItem(undefined)}
          onReload={(items, profile) => {
            setItems(items);
            onProfileLoaded(profile);
          }}
          onSaved={(item) => {
            setItems((previous) =>
              previous.map((value) => (value.id === item.id ? item : value)),
            );
            setOverrideItem(undefined);
          }}
        />
      )}
      <form
        className="profile-editor"
        aria-label={
          editing ? 'Редактирование типа встречи' : 'Создание типа встречи'
        }
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          void save();
        }}
      >
        <h3>{editing ? 'Редактирование типа встречи' : 'Новый тип встречи'}</h3>
        {editing && (
          <p>Изменения условий действуют только для новых бронирований.</p>
        )}
        {editing?.current.archived && (
          <p role="alert">
            Архивный тип нельзя редактировать или восстановить.
          </p>
        )}
        {editing && error && (
          <p>
            Актуальные данные: {editing.current.title},{' '}
            {editing.current.durationMinutes} мин, версия{' '}
            {editing.current.version}. Черновик сохранён; проверьте данные перед
            новым действием.
          </p>
        )}
        <p>ID после создания изменить нельзя. Регистр значим.</p>
        {(['id', 'title', 'description', 'durationMinutes'] as const).map(
          (field) => {
            const labels = {
              id: 'ID типа',
              title: 'Название типа',
              description: 'Описание типа',
              durationMinutes: 'Длительность, минут',
            };
            const fieldError = fieldErrors?.find(
              (item) => item.field === field,
            );
            const props = {
              id: `meeting-${field}`,
              value: activeDraft[field],
              disabled:
                locked ||
                (field === 'id' && Boolean(editing)) ||
                Boolean(editing?.current.archived),
              'aria-invalid': fieldError ? true : undefined,
              'aria-describedby': fieldError
                ? `meeting-${field}-error`
                : undefined,
              onChange: (
                event: React.ChangeEvent<
                  HTMLInputElement | HTMLTextAreaElement
                >,
              ) => changeDraft({ ...activeDraft, [field]: event.target.value }),
            };
            return (
              <div key={field}>
                <label htmlFor={props.id}>{labels[field]}</label>
                {field === 'durationMinutes' ? (
                  <input {...props} type="number" min="1" max="480" step="1" />
                ) : (
                  <textarea {...props} rows={field === 'description' ? 4 : 1} />
                )}
                {fieldError && (
                  <p id={`meeting-${field}-error`}>{fieldError.message}</p>
                )}
              </div>
            );
          },
        )}
        <label className="booking-toggle">
          <input
            type="checkbox"
            checked={activeDraft.requiresApproval}
            disabled={locked || Boolean(editing?.current.archived)}
            onChange={(event) =>
              changeDraft({
                ...activeDraft,
                requiresApproval: event.target.checked,
              })
            }
          />
          Ручное подтверждение
        </label>
        <button
          className="button"
          disabled={
            locked || loading || loadError || Boolean(editing?.current.archived)
          }
        >
          {editing ? 'Сохранить тип встречи' : 'Создать тип встречи'}
        </button>
        {editing && (
          <button
            type="button"
            className="button"
            disabled={locked}
            onClick={() => {
              onEditChange(undefined);
              setFieldErrors([]);
              setError(undefined);
            }}
          >
            Отменить редактирование типа
          </button>
        )}
      </form>
      {saving && (
        <p role="status">
          {operation === 'create' ? 'Создание типа…' : 'Сохранение типа…'}
        </p>
      )}
      {error && <p role="alert">{error}</p>}
      {pendingKey &&
        (unavailable ? (
          <button
            className="button"
            disabled={saving}
            onClick={() => {
              void reloadAfterUnavailable();
            }}
          >
            Загрузить актуальные типы
          </button>
        ) : (
          <button
            className="button"
            disabled={saving}
            onClick={() => {
              void recover();
            }}
          >
            {operation === 'create'
              ? 'Выяснить результат создания'
              : 'Выяснить результат изменения типа'}
          </button>
        ))}
    </section>
  );
}
