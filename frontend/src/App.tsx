import { useEffect, useState } from 'react';
import { getHealth } from './api/generated';

export function App() {
  const [status, setStatus] = useState('Проверяем соединение…');

  useEffect(() => {
    const controller = new AbortController();

    getHealth({ signal: controller.signal, throwOnError: true })
      .then(({ data }) => {
        setStatus(
          data.status === 'UP' ? 'Сервис доступен' : 'Сервис недоступен',
        );
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setStatus('Не удалось подключиться к API');
        }
      });

    return () => controller.abort();
  }, []);

  return (
    <main>
      <h1>Сервис бронирования встреч</h1>
      <p>Каркас приложения готов. Здесь появится запись на встречи.</p>
      <p role="status">{status}</p>
    </main>
  );
}
