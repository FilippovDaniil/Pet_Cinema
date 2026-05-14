# Dockerfile — многоэтапная сборка (multi-stage build) фронтенда.
# Этап 1: сборка React-приложения (builder).
# Этап 2: продакшн-образ на базе Nginx (только статика, без Node.js).
# Итоговый образ в 5-10 раз меньше, чем если бы оставить Node в нём.

# ---- Stage 1: builder ----
# node:20-alpine — Node.js 20 на базе Alpine Linux (минимальный образ ~50MB).
FROM node:20-alpine AS builder

# WORKDIR — устанавливаем рабочую директорию внутри контейнера.
# Все последующие команды выполняются в /app.
WORKDIR /app

# Сначала копируем только package.json (без исходников).
# Это использует слои Docker-кеша: npm install не перезапускается,
# если изменились только исходники (только package.json вызывает пересборку зависимостей).
COPY package.json ./

# npm install — устанавливаем все зависимости из package.json в /app/node_modules.
RUN npm install

# Копируем исходный код фронтенда (всё кроме node_modules, если .dockerignore настроен).
COPY . .

# npm run build — запускает Vite сборку (vite build).
# Результат: /app/dist/ — оптимизированные статические файлы (HTML, JS, CSS).
# Vite минифицирует JS/CSS, разбивает на чанки, добавляет хеши в имена файлов.
RUN npm run build

# ---- Stage 2: production image ----
# nginx:alpine — Nginx на Alpine (минимальный образ ~25MB).
# Node.js больше не нужен: только Nginx для раздачи статики.
FROM nginx:alpine

# Копируем результат сборки из stage 1 (папку dist) в директорию Nginx.
# --from=builder — берём файлы из именованного предыдущего этапа.
# /usr/share/nginx/html — стандартная директория для статики в официальном образе Nginx.
COPY --from=builder /app/dist /usr/share/nginx/html

# Копируем кастомную конфигурацию Nginx.
# nginx.conf настраивает SPA-роутинг (try_files) и проксирование /api/.
COPY nginx.conf /etc/nginx/conf.d/default.conf

# EXPOSE 80 — декларирует что контейнер слушает на порту 80.
# Это документация; реальный маппинг портов делается в docker-compose.yml.
EXPOSE 80
