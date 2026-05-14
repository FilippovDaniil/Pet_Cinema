# Multi-stage Dockerfile для notification-service.
# Stage 1 (builder): сборка fat JAR внутри Docker.
# Stage 2 (runtime): запуск в лёгком JRE образе.

# Stage 1: BUILDER
# eclipse-temurin:17-jdk-alpine — OpenJDK 17 на Alpine Linux (минимальный размер для сборки).
FROM eclipse-temurin:17-jdk-alpine AS builder

# Рабочая директория внутри контейнера сборки.
WORKDIR /workspace

# Gradle wrapper — запускает Gradle без установки на машину.
COPY gradlew .
# gradle/wrapper/ — содержит gradle-wrapper.jar и properties (версия Gradle).
COPY gradle/ gradle/
# Конфигурационные файлы Gradle корневого проекта.
COPY build.gradle.kts .
COPY settings.gradle.kts .
# Исходники зависимостей:
# common-dtos/ — TicketPurchaseEvent, SupportMessageEvent, NotificationDto и др.
# notification-service/ — исходники самого сервиса.
COPY common-dtos/ common-dtos/
COPY notification-service/ notification-service/

# Сборка:
# chmod +x gradlew — права на исполнение (необходимо в Linux).
# ./gradlew :notification-service:bootJar — собираем fat JAR только для notification-service.
# -x test — пропускаем тесты (экономия ~2-4 мин на сборку Docker образа).
# --no-daemon — без Gradle daemon (не нужен в Docker CI).
RUN chmod +x gradlew && \
    ./gradlew :notification-service:bootJar -x test --no-daemon

# Stage 2: RUNTIME
# eclipse-temurin:17-jre-alpine — только JRE (~80MB vs ~300MB с JDK).
# Нет компилятора, нет инструментов разработки — меньше поверхность атаки.
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Копируем JAR из builder stage.
# /workspace/notification-service/build/libs/*.jar — путь генерации bootJar.
# app.jar — стандартизированное имя для ENTRYPOINT.
COPY --from=builder /workspace/notification-service/build/libs/*.jar app.jar

# EXPOSE 8086 — документирует порт контейнера (из application.yml: server.port: 8086).
EXPOSE 8086

# ENTRYPOINT в JSON array форме:
# Сигналы (SIGTERM от docker stop) доходят напрямую до JVM — корректное завершение.
ENTRYPOINT ["java", "-jar", "app.jar"]
