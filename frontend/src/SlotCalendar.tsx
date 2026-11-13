import { useEffect, useState } from 'react';
import { getPublicSlots, type PublicSlots, type Slot } from './api/generated';
import { browserZone, dateLabel, dayKey, timeLabel } from './displayTime';

function Calendar({
  slots,
  renderSelection,
}: {
  slots: PublicSlots;
  renderSelection?: (slot: Slot) => React.ReactNode;
}) {
  const [zone, setZone] = useState(() => browserZone(slots.ownerTimeZone));
  const [date, setDate] = useState('');
  const [selected, setSelected] = useState<Slot>();
  const zones = [
    ...new Set([
      zone,
      slots.ownerTimeZone,
      'UTC',
      ...Intl.supportedValuesOf('timeZone'),
    ]),
  ].sort();
  const first = dayKey(slots.windowStart, zone);
  const last = dayKey(Date.parse(slots.windowEnd) - 1, zone);
  const days: string[] = [];
  for (
    let cursor = Date.parse(`${first}T00:00:00Z`);
    cursor <= Date.parse(`${last}T00:00:00Z`);
    cursor += 86_400_000
  ) {
    days.push(new Date(cursor).toISOString().slice(0, 10));
  }
  const activeDate = selected
    ? dayKey(selected.startsAt, zone)
    : days.includes(date)
      ? date
      : first;
  const byDay = new Map<string, Slot[]>();
  const formatter = new Intl.DateTimeFormat('sv-SE', {
    timeZone: zone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
  for (const slot of slots.items) {
    const day = formatter.format(new Date(slot.startsAt));
    const items = byDay.get(day) ?? [];
    items.push(slot);
    byDay.set(day, items);
  }
  const times = byDay.get(activeDate) ?? [];
  return (
    <>
      <h2>Выберите дату и время</h2>
      <label className="slot-zone">
        Часовой пояс
        <select value={zone} onChange={(event) => setZone(event.target.value)}>
          {zones.map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
      </label>
      <p>
        Окно записи: {dateLabel(first)} — {dateLabel(last)} ({zone}).
      </p>
      <fieldset className="slot-calendar">
        <legend>Календарь</legend>
        <div className="calendar-days">
          {days.map((day) => (
            <button
              className="button"
              key={day}
              disabled={!byDay.has(day)}
              aria-pressed={activeDate === day}
              onClick={() => {
                setDate(day);
                setSelected(undefined);
              }}
            >
              {dateLabel(day)}
            </button>
          ))}
        </div>
      </fieldset>
      {slots.items.length === 0 && (
        <>
          <p>В ближайшие 14 дней нет свободного времени</p>
          <p>Выберите другой тип встречи или проверьте позже.</p>
        </>
      )}
      <h3>{dateLabel(activeDate)}</h3>
      {slots.items.length > 0 && times.length === 0 && (
        <>
          <p>В этот день нет свободного времени</p>
          <p>Выберите другую доступную дату в календаре.</p>
        </>
      )}
      <div className="slot-times" aria-label="Свободное время">
        {times.map((slot) => (
          <button
            className="button"
            key={slot.startsAt}
            aria-pressed={selected?.startsAt === slot.startsAt}
            onClick={() => setSelected(slot)}
          >
            {timeLabel(slot.startsAt, zone)} — {timeLabel(slot.endsAt, zone)}
          </button>
        ))}
      </div>
      {selected && (
        <p role="status">
          Выбрано: {dateLabel(dayKey(selected.startsAt, zone))},{' '}
          {timeLabel(selected.startsAt, zone)} —{' '}
          {dateLabel(dayKey(selected.endsAt, zone))},{' '}
          {timeLabel(selected.endsAt, zone)} ({zone}). Выбор не удерживает
          время.
        </p>
      )}
      {selected && renderSelection?.(selected)}
    </>
  );
}

export function SlotCalendar({
  username,
  id,
  onUnavailable,
  renderSelection,
}: {
  username: string;
  id: string;
  onUnavailable: (reason: 'not-found' | 'disabled') => void;
  renderSelection?: (slot: Slot) => React.ReactNode;
}) {
  const [slots, setSlots] = useState<PublicSlots>();
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    void getPublicSlots({ path: { username, id }, signal: controller.signal })
      .then((result) => {
        if (controller.signal.aborted) return;
        if (result.data) setSlots(result.data);
        else if (result.response?.status === 404) onUnavailable('not-found');
        else if (result.error?.code === 'BOOKING_DISABLED')
          onUnavailable('disabled');
        else setFailed(true);
      })
      .catch(() => {
        if (!controller.signal.aborted) setFailed(true);
      });
    return () => controller.abort();
  }, [username, id, attempt, onUnavailable]);
  if (failed)
    return (
      <>
        <p role="alert">Не удалось загрузить свободное время.</p>
        <button
          className="button"
          onClick={() => {
            setFailed(false);
            setAttempt((previous) => previous + 1);
          }}
        >
          Повторить загрузку времени
        </button>
      </>
    );
  if (!slots) return <p role="status">Загрузка свободного времени…</p>;
  return <Calendar slots={slots} renderSelection={renderSelection} />;
}
