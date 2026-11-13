export function dayKey(instant: string | number, zone: string) {
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: zone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(instant));
}

export function dateLabel(day: string) {
  return `${day.slice(8, 10)}.${day.slice(5, 7)}.${day.slice(0, 4)}`;
}

export function timeLabel(instant: string, zone: string) {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: zone,
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
    timeZoneName: 'longOffset',
  }).formatToParts(new Date(instant));
  const value = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? '';
  const offset = value('timeZoneName').replace('GMT', 'UTC');
  return `${value('hour')}:${value('minute')} ${offset === 'UTC' ? 'UTC+00:00' : offset}`;
}

export function browserZone(fallback: string) {
  try {
    const zone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    if (zone) {
      new Intl.DateTimeFormat('en', { timeZone: zone }).format();
      return zone;
    }
  } catch {
    // Browser detection may be unavailable or return an unsupported identifier.
  }
  return fallback;
}
