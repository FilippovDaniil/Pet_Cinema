# ─── Стадия 1: Сборка JAR ────────────────────────────────────────────────────
# eclipse-temurin:17-jdk-alpine — компактный образ JDK 17 на Alpine Linux.
# JDK нужен для компиляции (javac) и сборки Gradle.
# AS builder — именуем стадию для последующего COPY --from=builder.
FROM eclipse-temurin:17-jdk-alpine AS builder

# WORKDIR — устанавливает рабочую директорию для всех последующих команд (RUN, COPY и т.д.).
# Если директории нет — Docker создаёт её автоматически.
WORKDIR /workspace

# Копируем файлы Gradle для сборки проекта.
# Порядок важен: сначала файлы конфигурации, потом исходный код.
# Docker кеширует слои — если Gradle-файлы не изменились, зависимости не скачиваются заново.
COPY gradlew .                  # Gradle Wrapper скрипт
COPY gradle/ gradle/            # Gradle Wrapper JAR и properties
COPY build.gradle.kts .         # Корневой build-файл (общие зависимости для всех сервисов)
COPY settings.gradle.kts .      # Регистрирует подпроекты (включает :hall-service)
COPY common-dtos/ common-dtos/  # Общие DTO — hall-service зависит от них (SessionDto и т.д.)
COPY hall-service/ hall-service/ # Исходный код hall-service

# Делаем gradlew исполняемым и собираем JAR.
# :hall-service:bootJar — собирает только hall-service (не все подпроекты).
# -x test — пропускаем тесты (они запускаются в CI, не при сборке образа).
# --no-daemon — не запускаем фоновый Gradle daemon (он бесполезен в одноразовом контейнере).
RUN chmod +x gradlew && \
    ./gradlew :hall-service:bootJar -x test --no-daemon

# ─── Стадия 2: Runtime образ ─────────────────────────────────────────────────
# eclipse-temurin:17-jre-alpine — только JRE (Java Runtime Environment), без JDK.
# Намного меньше, чем JDK-образ (~100 МБ vs ~350 МБ).
# JRE достаточно для запуска JAR (компилятор не нужен в production).
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# COPY --from=builder — копируем JAR из стадии builder в runtime-образ.
# *.jar — glob-паттерн. Работает потому что jar{enabled=false} в build.gradle.kts
# гарантирует что в build/libs/ только один JAR (bootJar без суффикса -plain).
COPY --from=builder /workspace/hall-service/build/libs/*.jar app.jar

# Документируем порт, который сервис слушает (из application.yml: server.port: 8083).
# EXPOSE не публикует порт автоматически — это делает docker-compose ports: или -p флаг.
EXPOSE 8083

# Команда запуска контейнера: java -jar app.jar
# Используем exec-форму (JSON array) вместо shell-формы ("java -jar app.jar"):
# exec-форма: java получает PID 1 → корректно получает SIGTERM при docker stop
# shell-форма: /bin/sh получает PID 1 → SIGTERM не дойдёт до java (graceful shutdown не работает)
ENTRYPOINT ["java", "-jar", "app.jar"]
