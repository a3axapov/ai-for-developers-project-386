import { useState } from 'react';
import {
  updateOwnerTimeZone,
  getOwnerOperation,
  getOwnerProfile,
  listMeetingTypes,
  type ApiError,
  type OwnerProfile,
} from './api/generated';
import { dateLabel, dayKey, timeLabel } from './displayTime';

export function OwnerTimeZone({
  profile,
  blocked,
  onLockChange,
  onApplied,
}: {
  profile: OwnerProfile;
  blocked: boolean;
  onLockChange: (value: boolean) => void;
  onApplied: (value: OwnerProfile) => void;
}) {
  const [zone, setZone] = useState(profile.timeZone);
  const [apply, setApply] = useState(false);
  const [consequences, setConsequences] = useState(false);
  const [preview, setPreview] = useState<ApiError>();
  const [error, setError] = useState<ApiError>();
  const [saving, setSaving] = useState(false);
  const [pendingKey, setPendingKey] = useState<string>();
  const [unavailable, setUnavailable] = useState(false);
  const locked = blocked || saving || Boolean(pendingKey);

  function change() {
    setConsequences(false);
    setPreview(undefined);
    setError(undefined);
  }

  function failure(value: ApiError, retainedProfile = true) {
    setError(value);
    setPreview(
      value.code === 'IMPACT_CONFIRMATION_REQUIRED' ? value : undefined,
    );
    if (retainedProfile && value.currentProfile)
      onApplied(value.currentProfile);
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

  async function refresh(clear = true) {
    // A retained outcome proves the command, not the current profile or type set.
    // Keep the global lock until BOTH current reads succeed, including after reset.
    const [current] = await Promise.all([
      getOwnerProfile({
        throwOnError: true,
        signal: AbortSignal.timeout(15_000),
      }),
      listMeetingTypes({
        throwOnError: true,
        signal: AbortSignal.timeout(15_000),
      }),
    ]);
    onApplied(current.data);
    if (!clear) return;
    setPendingKey(undefined);
    setUnavailable(false);
    setError(undefined);
    setPreview(undefined);
    setConsequences(false);
    onLockChange(false);
  }

  async function save(confirmationToken?: string) {
    if (locked || !consequences) return;
    const key = crypto.randomUUID();
    setSaving(true);
    onLockChange(true);
    setError(undefined);
    try {
      const result = await updateOwnerTimeZone({
        headers: { 'Idempotency-Key': key },
        signal: AbortSignal.timeout(15_000),
        body: {
          timeZone: zone,
          applyToActiveTypes: apply,
          expectedVersion: profile.version,
          confirmationToken,
        },
      });
      if (result.data) await refresh();
      else if (
        result.response &&
        [400, 409, 422].includes(result.response.status) &&
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
      if (unavailable) {
        await refresh();
        return;
      }
      const result = await getOwnerOperation({
        path: { key: pendingKey },
        signal: AbortSignal.timeout(15_000),
      });
      if (result.data?.profile) await refresh();
      else if (result.data?.error) {
        await refresh(false);
        failure(result.data.error, false);
      } else if (result.response?.status === 410) {
        setUnavailable(true);
        setError({
          code: 'OPERATION_UNAVAILABLE',
          message:
            'Результат операции недоступен. Возможен сброс данных. Загрузите актуальные профиль и типы; команда не повторяется.',
        });
      } else unknown(pendingKey);
    } catch {
      unknown(pendingKey);
    } finally {
      setSaving(false);
    }
  }

  return (
    <form
      className="profile-editor"
      aria-label="Часовой пояс владельца"
      onSubmit={(event) => {
        event.preventDefault();
        if (!locked) {
          setConsequences(true);
          setPreview(undefined);
          setError(undefined);
        }
      }}
    >
      <h2>Изменить часовой пояс</h2>
      <label htmlFor="new-owner-zone">Новый часовой пояс</label>
      <input
        id="new-owner-zone"
        value={zone}
        disabled={locked}
        aria-invalid={
          error?.fieldErrors?.some((value) => value.field === 'timeZone') ||
          undefined
        }
        aria-describedby={
          error?.fieldErrors?.some((value) => value.field === 'timeZone')
            ? 'owner-zone-error'
            : undefined
        }
        onChange={(event) => {
          setZone(event.target.value);
          change();
        }}
      />
      {error?.fieldErrors
        ?.filter((value) => value.field === 'timeZone')
        .map((value) => (
          <p id="owner-zone-error" key={value.field}>
            {value.message}
          </p>
        ))}
      <label>
        <input
          type="checkbox"
          checked={apply}
          disabled={locked}
          onChange={(event) => {
            setApply(event.target.checked);
            change();
          }}
        />
        Применить также к существующим активным типам встреч
      </label>
      <button className="button" disabled={locked}>
        Показать последствия смены пояса
      </button>
      {consequences && (
        <section aria-label="Последствия смены пояса">
          <h3>Последствия смены пояса</h3>
          <p>Новый пояс владельца и новых типов: {zone}.</p>
          <p>
            {apply
              ? 'Активные типы сохранят местные часы и даты исключений в новом поясе: фактическая доступность сдвинется.'
              : 'Существующие типы сохранят свои пояса, часы и исключения.'}
          </p>
          <p>
            Гостевое окно изменится по текущей дате в новом поясе, даже без
            перевода типов.
          </p>
          <p>
            Бронирования и блокировки не перемещаются и не отменяются; изменится
            отображение. Отпуска сохранят собственные пояса и фактические
            границы. Архивные типы не меняются.
          </p>
          {preview && (
            <>
              <p>{preview.message}</p>
              <ul aria-label="Затронутые записи при смене пояса">
                {preview.affectedBookings?.map((booking) => (
                  <li key={booking.id}>
                    {booking.title} — {booking.guestName}, {booking.status},{' '}
                    {dateLabel(dayKey(booking.startsAt, profile.timeZone))},{' '}
                    {timeLabel(booking.startsAt, profile.timeZone)} —{' '}
                    {timeLabel(booking.endsAt, profile.timeZone)} (
                    {profile.timeZone})
                  </li>
                ))}
              </ul>
            </>
          )}
          <button
            className="button"
            type="button"
            disabled={locked}
            onClick={() => void save(preview?.confirmationToken)}
          >
            {preview
              ? 'Подтвердить последствия смены пояса'
              : 'Применить новый пояс'}
          </button>
          <button
            className="button"
            type="button"
            disabled={locked}
            onClick={change}
          >
            Отменить смену пояса
          </button>
        </section>
      )}
      {saving && <p role="status">Сохранение пояса…</p>}
      {error && <p role="alert">{error.message}</p>}
      {pendingKey && (
        <button
          className="button"
          type="button"
          disabled={saving}
          onClick={() => void recover()}
        >
          {unavailable
            ? 'Загрузить актуальные профиль и типы'
            : 'Выяснить результат смены пояса'}
        </button>
      )}
    </form>
  );
}
