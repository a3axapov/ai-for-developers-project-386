import { useEffect, useState } from 'react';
import { getBookingResult, type BookingDetails } from './api/generated';
import { browserZone, dateLabel, dayKey, timeLabel } from './displayTime';

export function BookingResultPage({ accessToken }: { accessToken: string }) {
  const [booking, setBooking] = useState<BookingDetails>();
  const [zone, setZone] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notFound, setNotFound] = useState(false);
  const [reload, setReload] = useState(0);
  const [copyMessage, setCopyMessage] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    void getBookingResult({ path: { accessToken }, signal: controller.signal })
      .then((result) => {
        if (controller.signal.aborted) return;
        if (result.data) {
          setBooking(result.data);
          setZone(
            (previous) => previous || browserZone(result.data.ownerTimeZone),
          );
        } else if (result.response?.status === 404) {
          setNotFound(true);
          setBooking(undefined);
        } else
          setError(
            'Не удалось обновить сведения. Показанные данные могут быть устаревшими.',
          );
      })
      .catch(() => {
        if (!controller.signal.aborted)
          setError(
            'Не удалось обновить сведения. Показанные данные могут быть устаревшими.',
          );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [accessToken, reload]);
  const label = (instant: string) =>
    `${dateLabel(dayKey(instant, zone))}, ${timeLabel(instant, zone)}`;
  return (
    <section className="placeholder">
      <h1>{notFound ? 'Бронирование не найдено' : 'Результат бронирования'}</h1>
      {loading && <p role="status">Загрузка сведений…</p>}
      {error && <p role="alert">{error}</p>}
      {booking && zone && (
        <>
          <p>{booking.ownerName}</p>
          <h2>{booking.title}</h2>
          <p className="meeting-description">{booking.description}</p>
          <p>{booking.durationMinutes} мин</p>
          <p>
            {booking.requiresApproval
              ? 'Требуется ручное подтверждение'
              : 'Автоматическое подтверждение'}
          </p>
          <p>
            {
              {
                CONFIRMED: 'Подтверждено',
                PENDING: 'Ожидает подтверждения',
                REJECTED: 'Отклонено',
                CANCELLED: 'Отменено',
              }[booking.status]
            }
          </p>
          {booking.rejectionReason === 'APPROVAL_EXPIRED' && (
            <p>Владелец не подтвердил запрос до начала встречи.</p>
          )}
          <label className="slot-zone">
            Часовой пояс
            <select
              value={zone}
              onChange={(event) => setZone(event.target.value)}
            >
              {[
                ...new Set([
                  zone,
                  booking.ownerTimeZone,
                  'UTC',
                  ...Intl.supportedValuesOf('timeZone'),
                ]),
              ]
                .sort()
                .map((value) => (
                  <option key={value} value={value}>
                    {value}
                  </option>
                ))}
            </select>
          </label>
          <p>
            {label(booking.startsAt)} — {label(booking.endsAt)}
          </p>
          <p>{booking.guestName}</p>
          <p>{booking.guestEmail}</p>
          <p className="meeting-description">{booking.comment}</p>
          <p>
            Любой обладатель ссылки получает доступ к сведениям бронирования. Не
            передавайте её посторонним.
          </p>
          <p>
            Уведомления не отправляются. Проверяйте актуальный статус
            самостоятельно. Восстановления ссылки по email нет.
          </p>
          <button
            className="button"
            onClick={async () => {
              try {
                await navigator.clipboard.writeText(window.location.href);
                setCopyMessage('Ссылка скопирована');
              } catch {
                setCopyMessage(
                  'Не удалось скопировать. Сохраните адрес из строки браузера.',
                );
              }
            }}
          >
            Скопировать ссылку
          </button>
          {copyMessage && <p role="status">{copyMessage}</p>}
        </>
      )}
      {!notFound && (
        <button
          className="button"
          disabled={loading}
          onClick={() => {
            setLoading(true);
            setError('');
            setReload((previous) => previous + 1);
          }}
        >
          Обновить сведения
        </button>
      )}
    </section>
  );
}
