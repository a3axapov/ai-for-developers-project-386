import { useState } from 'react';
import {
  prepareBookingAttempt,
  type PreparedBookingAttempt,
  type FieldError,
  type Slot,
} from './api/generated';

export type GuestContacts = {
  guestName: string;
  guestEmail: string;
  comment: string;
};

export function GuestForm({
  username,
  meetingTypeId,
  conditionsToken,
  slot,
  contacts,
  onChange,
  onUnavailable,
}: {
  username: string;
  meetingTypeId: string;
  conditionsToken: string;
  slot: Slot;
  contacts: GuestContacts;
  onChange: (contacts: GuestContacts) => void;
  onUnavailable: (reason: 'not-found' | 'disabled') => void;
}) {
  const [prepared, setPrepared] = useState<PreparedBookingAttempt>();
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState('');
  const [errors, setErrors] = useState<FieldError[]>([]);

  return (
    <form
      className="guest-form"
      noValidate
      onSubmit={async (event) => {
        event.preventDefault();
        if (pending || prepared) return;
        setPending(true);
        setErrors([]);
        setMessage('');
        try {
          const result = await prepareBookingAttempt({
            path: { username },
            body: {
              meetingTypeId,
              conditionsToken,
              startsAt: slot.startsAt,
              ...contacts,
            },
          });
          if (result.data) setPrepared(result.data);
          else if (!result.response || !result.error?.code) {
            setMessage(
              'Не удалось получить ответ подготовки. Бронирование не создавалось; можно повторить подготовку.',
            );
          } else if (result.response?.status === 404)
            onUnavailable('not-found');
          else if (result.error?.code === 'BOOKING_DISABLED')
            onUnavailable('disabled');
          else {
            setErrors(result.error?.fieldErrors ?? []);
            setMessage(
              result.error?.message ??
                'Не удалось подготовить попытку. Бронирование не создавалось; можно повторить подготовку.',
            );
          }
        } catch {
          setMessage(
            'Не удалось получить ответ подготовки. Бронирование не создавалось; можно повторить подготовку.',
          );
        } finally {
          setPending(false);
        }
      }}
    >
      <h2>Ваши контакты</h2>
      <p>
        Подготовка не бронирует и не удерживает время. Создание бронирования
        пока недоступно.
      </p>
      {(['guestName', 'guestEmail', 'comment'] as const).map((field) => {
        const error = errors.find((error) => error.field === field);
        const props = {
          id: `guest-${field}`,
          value: contacts[field],
          disabled: pending,
          'aria-invalid': Boolean(error),
          'aria-describedby': error ? `guest-${field}-error` : undefined,
          onChange: (
            event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
          ) => {
            onChange({ ...contacts, [field]: event.target.value });
            setPrepared(undefined);
          },
        };
        return (
          <div key={field}>
            <label htmlFor={props.id}>
              {field === 'guestName'
                ? 'Ваше имя'
                : field === 'guestEmail'
                  ? 'Email'
                  : 'Комментарий (необязательно)'}
            </label>
            {field === 'comment' ? (
              <textarea {...props} rows={4} />
            ) : (
              <input
                {...props}
                required
                type={field === 'guestEmail' ? 'email' : 'text'}
                autoComplete={field === 'guestEmail' ? 'email' : 'name'}
              />
            )}
            {error && <p id={`guest-${field}-error`}>{error.message}</p>}
          </div>
        );
      })}
      {message && <p role="alert">{message}</p>}
      {pending && <p role="status">Подготовка попытки…</p>}
      {prepared && (
        <p role="status">
          Попытка подготовлена. Бронирование ещё не создано; время не занято.
        </p>
      )}
      <button
        className="button"
        type="submit"
        disabled={pending || Boolean(prepared)}
      >
        Подготовить попытку
      </button>
    </form>
  );
}
