const steps = [
  {
    title: 'Выберите встречу',
    description: 'Найдите подходящий тип встречи по описанию и длительности.',
  },
  {
    title: 'Найдите удобное время',
    description:
      'Посмотрите свободные слоты на ближайшие 14 дней в своём часовом поясе.',
  },
  {
    title: 'Оставьте свои контакты',
    description:
      'Укажите имя, email и комментарий, затем подтвердите запись. Аккаунт не нужен.',
  },
];

function HomePage() {
  return (
    <>
      <section className="hero" aria-labelledby="hero-title">
        <div>
          <p className="eyebrow">Сервис бронирования встреч</p>
          <h1 id="hero-title">
            Меньше переписки.
            <br />
            Больше времени <span>на встречу.</span>
          </h1>
          <p className="lead">
            Выберите встречу и удобное время в календаре владельца — без долгих
            согласований и регистрации.
          </p>
          <a className="button" href="/booking" aria-describedby="booking-note">
            Записаться на встречу <span aria-hidden="true">↗</span>
          </a>
          <p className="availability-note" id="booking-note">
            Скоро — онлайн-запись. Сервис в разработке.
          </p>
        </div>
        <aside className="intro-card" aria-labelledby="intro-title">
          <span className="card-symbol" aria-hidden="true">
            ↗
          </span>
          <p className="eyebrow">Место для ваших планов</p>
          <h2 id="intro-title">Одна ссылка. Понятный следующий шаг.</h2>
          <p>
            Здесь появится запись на встречи с владельцем календаря. Вы сможете
            выбрать время самостоятельно, а не искать его в переписке.
          </p>
          <div className="card-detail">
            <span aria-hidden="true">◷</span> В вашем часовом поясе
          </div>
        </aside>
      </section>

      <section className="steps-section" aria-labelledby="steps-title">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Как это будет работать</p>
            <h2 id="steps-title">От выбора до встречи — три шага</h2>
          </div>
          <p>Онлайн-запись ещё готовится. Вот что станет доступно.</p>
        </div>
        <ol className="steps">
          {steps.map((step, index) => (
            <li key={step.title}>
              <span className="step-number" aria-hidden="true">
                0{index + 1}
              </span>
              <h3>{step.title}</h3>
              <p>{step.description}</p>
            </li>
          ))}
        </ol>
      </section>

      <section className="features-section" aria-labelledby="features-title">
        <div>
          <p className="eyebrow">Продумано заранее</p>
          <h2 id="features-title">Всё важное — в одном месте</h2>
          <p>Эти возможности появятся вместе с онлайн-записью.</p>
        </div>
        <dl className="features">
          <div>
            <dt>Только свободное время</dt>
            <dd>
              Сервис учтёт расписание владельца и занятые часы, чтобы встречи не
              пересекались.
            </dd>
          </div>
          <div>
            <dt>Без лишних аккаунтов</dt>
            <dd>
              Для записи гостю понадобятся только имя, email и комментарий.
            </dd>
          </div>
          <div>
            <dt>Встреча в вашем календаре</dt>
            <dd>
              После записи можно будет скачать файл .ics и самостоятельно
              добавить встречу в свой календарь.
            </dd>
          </div>
        </dl>
      </section>
    </>
  );
}

function PlaceholderPage({ owner = false }: { owner?: boolean }) {
  return (
    <section className="placeholder" aria-labelledby="page-title">
      <p className="eyebrow">
        {owner ? 'Владельцу календаря' : 'Онлайн-запись'}
      </p>
      <h1 id="page-title">
        {owner
          ? 'Раздел владельца в разработке'
          : 'Запись на встречи скоро появится'}
      </h1>
      <p className="lead">
        {owner
          ? 'Здесь появится управление расписанием, типами встреч и бронированиями.'
          : 'Онлайн-запись пока недоступна. Мы готовим удобный выбор встречи и свободного времени.'}
      </p>
      <a className="button" href="/">
        Вернуться на главную <span aria-hidden="true">↗</span>
      </a>
    </section>
  );
}

export function App() {
  const path = window.location.pathname;

  return (
    <div className="site-shell">
      <a className="skip-link" href="#main">
        Перейти к содержимому
      </a>
      <header className="site-header">
        <a className="brand" href="/" aria-label="Сервис встреч — главная">
          <span className="brand-mark" aria-hidden="true">
            ↗
          </span>
          Сервис встреч
        </a>
        <nav aria-label="Главное меню">
          <a
            className="owner-link"
            href="/admin"
            aria-current={path === '/admin' ? 'page' : undefined}
          >
            Владельцу <span aria-hidden="true">↗</span>
          </a>
        </nav>
      </header>
      <main id="main" tabIndex={-1}>
        {path === '/' ? (
          <HomePage />
        ) : path === '/booking' ? (
          <PlaceholderPage />
        ) : path === '/admin' ? (
          <PlaceholderPage owner />
        ) : (
          <section className="placeholder">
            <h1>Страница не найдена</h1>
            <a className="button" href="/">
              Вернуться на главную
            </a>
          </section>
        )}
      </main>
      <footer className="site-footer">
        <span>Сервис бронирования встреч</span>
        <span>Меньше согласований. Больше общения.</span>
      </footer>
    </div>
  );
}
