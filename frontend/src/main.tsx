// import React — базовая библиотека React.
// В React 17+ с автоматическим JSX transform не нужен явный import в каждом файле.
// Здесь нужен для React.StrictMode компонента.
import React from 'react'

// ReactDOM — DOM-специфичная часть React для браузера.
// React разделён на react (общая логика) и react-dom (рендеринг в DOM).
// В React Native: react-native вместо react-dom.
import ReactDOM from 'react-dom/client'

// App — корневой компонент приложения (определяет маршрутизацию и провайдеры).
import App from './App'

// index.css — глобальные стили (сброс стилей браузера, тёмная тема, шрифты).
// Импортируется здесь — применяется ко всему приложению.
import './index.css'

// ReactDOM.createRoot — React 18 API для создания корневого узла рендеринга.
// Отличие от React 17 ReactDOM.render(): поддерживает Concurrent Mode (параллельный рендеринг).
// document.getElementById('root')! — находим элемент <div id="root"> из index.html.
//   ! (non-null assertion) — сообщаем TypeScript что результат не null (мы уверены что div существует).
ReactDOM.createRoot(document.getElementById('root')!).render(
  // React.StrictMode — режим строгой проверки для разработки.
  // В development режиме:
  //   - Выводит предупреждения об устаревших API
  //   - Намеренно вызывает эффекты (useEffect) дважды для обнаружения побочных эффектов
  //   - НЕ влияет на production сборку (StrictMode отключается в build)
  <React.StrictMode>
    {/* App — точка входа: AuthProvider + BrowserRouter + все Routes */}
    <App />
  </React.StrictMode>
)
