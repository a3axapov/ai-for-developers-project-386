import { useEffect, useState } from 'react';
import {
  createMeetingType,
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
  onLockChange,
  onProfileLoaded,
}: {
  bookingUrl: string;
  blocked: boolean;
  onLockChange: (locked: boolean) => void;
  onProfileLoaded: (profile: OwnerProfile) => void;
}) {
  const [items, setItems] = useState<MeetingType[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [draft, setDraft] = useState(emptyDraft);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();
  const [fieldErrors, setFieldErrors] = useState<ApiError['fieldErrors']>([]);
  const [copyMessage, setCopyMessage] = useState<string>();
  const [pendingKey, setPendingKey] = useState<string>();
  const [unavailable, setUnavailable] = useState(false);

  function success(item: MeetingType) {
    setItems((previous) => [
      ...previous.filter((type) => type.id !== item.id),
      item,
    ]);
    setDraft(emptyDraft);
    setPendingKey(undefined);
    setError(undefined);
    setFieldErrors([]);
    onLockChange(false);
  }

  function failure(error: ApiError) {
    setError(error.message);
    setFieldErrors(error.fieldErrors ?? []);
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
    if (blocked || saving || pendingKey || loading || loadError) return;
    const duration = Number(draft.durationMinutes);
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
    onLockChange(true);
    setError(undefined);
    setFieldErrors([]);
    const key = crypto.randomUUID();
    try {
      const result = await createMeetingType({
        headers: { 'Idempotency-Key': key },
        signal: AbortSignal.timeout(15_000),
        body: { ...draft, durationMinutes: Number(draft.durationMinutes) },
      });
      if (result.data) {
        success(result.data);
      } else if (
        result.response &&
        [400, 409, 422].includes(result.response.status) &&
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

  async function recover() {
    if (!pendingKey || saving) return;
    setSaving(true);
    try {
      const result = await getOwnerOperation({
        path: { key: pendingKey },
        signal: AbortSignal.timeout(15_000),
      });
      if (result.data?.statusCode === 201 && result.data.meetingType)
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
          </article>
        );
      })}
      {copyMessage && <p role="status">{copyMessage}</p>}
      <form
        className="profile-editor"
        aria-label="Создание типа встречи"
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          void save();
        }}
      >
        <h3>Новый тип встречи</h3>
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
              value: draft[field],
              disabled: blocked || saving || Boolean(pendingKey),
              'aria-invalid': fieldError ? true : undefined,
              'aria-describedby': fieldError
                ? `meeting-${field}-error`
                : undefined,
              onChange: (
                event: React.ChangeEvent<
                  HTMLInputElement | HTMLTextAreaElement
                >,
              ) => setDraft({ ...draft, [field]: event.target.value }),
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
            checked={draft.requiresApproval}
            disabled={blocked || saving || Boolean(pendingKey)}
            onChange={(event) =>
              setDraft({ ...draft, requiresApproval: event.target.checked })
            }
          />
          Ручное подтверждение
        </label>
        <button
          className="button"
          disabled={
            blocked || saving || Boolean(pendingKey) || loading || loadError
          }
        >
          Создать тип встречи
        </button>
      </form>
      {saving && <p role="status">Создание типа…</p>}
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
            Выяснить результат создания
          </button>
        ))}
    </section>
  );
}
