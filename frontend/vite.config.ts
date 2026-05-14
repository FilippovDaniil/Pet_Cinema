// defineConfig — вспомогательная функция Vite для автодополнения TypeScript типов конфига.
import { defineConfig } from 'vite'

// @vitejs/plugin-react — официальный плагин React для Vite.
// Включает Fast Refresh (горячая замена компонентов без перезагрузки страницы),
// поддержку JSX, Babel-трансформации для React-специфичных оптимизаций.
import react from '@vitejs/plugin-react'

export default defineConfig({
  // plugins — массив плагинов Vite; react() добавляет поддержку JSX и Fast Refresh.
  plugins: [react()],

  // server — настройки dev-сервера (vite dev, localhost:5173 по умолчанию).
  server: {
    // proxy — перенаправление запросов в режиме разработки.
    // Решает проблему CORS: браузер отправляет /api/* на тот же origin (localhost:5173),
    // Vite прозрачно переадресует на api-gateway (localhost:8080).
    proxy: {
      '/api': {
        // target — адрес api-gateway в режиме разработки (Spring Cloud Gateway).
        // В Docker env запросы идут через Nginx (nginx.conf), не через этот прокси.
        target: 'http://localhost:8080',

        // changeOrigin: true — Vite подменяет заголовок Host на host целевого сервера.
        // Необходимо если api-gateway валидирует Host header (Spring Security).
        changeOrigin: true
      }
    }
  }
})
