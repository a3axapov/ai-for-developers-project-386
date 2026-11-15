// Work with the literal wall time, never new Date(localInput) (which silently shifts gaps).
export function localTime(instant: string, zone: string) {
  const parts = new Intl.DateTimeFormat('sv-SE', {
    timeZone: zone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date(instant));
  const part = (name: Intl.DateTimeFormatPartTypes) =>
    parts.find((p) => p.type === name)!.value;
  const fraction = instant.match(/\.\d+/)?.[0] ?? '';
  return `${part('year')}-${part('month')}-${part('day')}T${part('hour')}:${part('minute')}:${part('second')}${fraction}`;
}

export function validOffsets(literal: string, zone: string): string[] {
  if (!/^\d{4}-\d\d-\d\dT\d\d:\d\d(:\d\d(\.\d{1,9})?)?$/.test(literal))
    return [];
  const seconds = literal.split('.')[0];
  const wall = seconds.length === 16 ? `${seconds}:00` : seconds;
  const naive = Date.parse(`${wall}Z`);
  if (!Number.isFinite(naive)) return [];
  const result: string[] = [];
  // Every RFC 3339 minute offset in IANA's modern range; compare the actual wall time.
  // Enumeration also handles half-hour folds and skipped dates, without guessing a shift.
  const formatter = new Intl.DateTimeFormat('sv-SE', {
    timeZone: zone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  });
  for (let minutes = -840; minutes <= 840; minutes++) {
    const date = new Date(naive - minutes * 60_000);
    if (formatter.format(date).replace(' ', 'T') !== wall) continue;
    const absolute = Math.abs(minutes);
    result.push(
      `${minutes < 0 ? '-' : '+'}${String(Math.floor(absolute / 60)).padStart(2, '0')}:${String(absolute % 60).padStart(2, '0')}`,
    );
  }
  return result;
}

export function literalOffset(instant: string) {
  return instant.endsWith('Z') ? '+00:00' : instant.slice(-6);
}
