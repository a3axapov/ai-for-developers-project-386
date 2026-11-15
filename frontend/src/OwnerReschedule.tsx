import { useEffect, useState } from 'react';
import {
  getOwnerRescheduleSlots,
  type ApiError,
  type BookingDetails,
  type Slot,
} from './api/generated';
import { dayKey, dateLabel, timeLabel } from './displayTime';

function shift(date: string, days: number) {
  const value = new Date(`${date}T12:00:00Z`);
  value.setUTCDate(value.getUTCDate() + days);
  return value.toISOString().slice(0, 10);
}

export function OwnerReschedule({
  booking,
  disabled,
  onSubmit,
}: {
  booking: BookingDetails;
  disabled: boolean;
  onSubmit: (slot: Slot) => void;
}) {
  const [from, setFrom] = useState(() =>
    dayKey(booking.startsAt, booking.ownerTimeZone),
  );
  const [reload, setReload] = useState(0);
  const [slots, setSlots] = useState<Slot[]>([]);
  const [selected, setSelected] = useState<Slot>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError>();
  const [zone, setZone] = useState(booking.ownerTimeZone);
  const to = shift(from, 7);

  useEffect(() => {
    const controller = new AbortController();
    void getOwnerRescheduleSlots({
      path: { id: booking.id },
      query: { fromDate: from, toDate: to },
      signal: controller.signal,
    })
      .then((result) => {
        if (controller.signal.aborted) return;
        if (result.data) {
          setSlots(result.data.items);
          setZone(result.data.ownerTimeZone);
        } else
          setError(
            result.error ?? {
              code: 'READ_FAILED',
              message: 'Не удалось загрузить время переноса. Повторите чтение.',
            },
          );
      })
      .catch(() => {
        if (!controller.signal.aborted)
          setError({
            code: 'READ_FAILED',
            message: 'Не удалось загрузить время переноса. Повторите чтение.',
          });
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [booking.id, from, to, reload]);

  function navigate(days: number) {
    setSelected(undefined);
    setSlots([]);
    setError(undefined);
    setLoading(true);
    setFrom(shift(from, days));
  }

  function label(slot: Pick<Slot, 'startsAt' | 'endsAt'>) {
    return `${dateLabel(dayKey(slot.startsAt, zone))}, ${timeLabel(slot.startsAt, zone)} — ${dateLabel(dayKey(slot.endsAt, zone))}, ${timeLabel(slot.endsAt, zone)}`;
  }

  return (
    <section aria-label="Перенос бронирования">
      <h4>Перенос бронирования</h4>
      <p>Сохранённые условия: {booking.title}</p>
      <p>{booking.durationMinutes} мин</p>
      <p>Часовой пояс: {zone}. Даты в этом поясе; конечная дата не включена.</p>
      <p>
        {from} — {to}
      </p>
      <button
        className="button"
        disabled={disabled || loading}
        onClick={() => navigate(-7)}
      >
        Предыдущие даты
      </button>
      <button
        className="button"
        disabled={disabled || loading}
        onClick={() => navigate(7)}
      >
        Следующие даты
      </button>
      <button
        className="button"
        disabled={disabled || loading}
        onClick={() => {
          setSelected(undefined);
          setSlots([]);
          setError(undefined);
          setLoading(true);
          setReload((v) => v + 1);
        }}
      >
        Обновить время переноса
      </button>
      {loading && <p role="status">Загрузка времени переноса…</p>}
      {error && <p role="alert">{error.message}</p>}
      {!loading && !error && slots.length === 0 && (
        <p>В этом диапазоне нет доступного времени. Выберите другие даты.</p>
      )}
      <div
        className="reschedule-slots"
        role="group"
        aria-label="Доступное время переноса"
      >
        {slots.map((slot) => (
          <button
            className="button"
            key={slot.startsAt}
            aria-label={`Выбрать ${slot.startsAt}`}
            aria-pressed={selected?.startsAt === slot.startsAt}
            disabled={disabled || loading}
            onClick={() => setSelected(slot)}
          >
            {label(slot)}
          </button>
        ))}
      </div>
      <p>
        Прежнее время: {label(booking)} ({zone})
      </p>
      {selected && (
        <p>
          Новое время: {label(selected)} ({zone})
        </p>
      )}
      <button
        className="button"
        disabled={disabled || loading || !selected || Boolean(error)}
        onClick={() => {
          if (selected) onSubmit(selected);
        }}
      >
        Подтвердить перенос
      </button>
    </section>
  );
}
