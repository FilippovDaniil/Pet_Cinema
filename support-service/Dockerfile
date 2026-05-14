# ============================================================
# Multi-stage Dockerfile для support-service
# Stage 1 (builder): компилируем JAR внутри Docker
# Stage 2 (runtime): запускаем JAR в лёгком JRE образе
# ============================================================

# Stage 1: BUILDER
# eclipse-temurin:17-jdk-alpine — официальный образ OpenJDK 17 на Alpine Linux (минимальный размер).
# AS builder — даём имя этому этапу для последующего COPY --from=builder.
FROM eclipse-temurin:17-jdk-alpine AS builder

# WORKDIR — рабочая директория внутри контейнера сборки.
# Все последующие команды выполняются в /workspace.
WORKDIR /workspace

# Копируем Gradle wrapper — скрипт запуска Gradle без установки Gradle на машину.
COPY gradlew .
# Копируем директорию gradle/wrapper/ — содержит gradle-wrapper.jar и properties файл.
# gradle-wrapper.jar автоматически скачивает нужную версию Gradle при первом запуске.
COPY gradle/ gradle/

# Копируем конфигурационные файлы Gradle:
# build.gradle.kts — зависимости корневого проекта и общие настройки
# settings.gradle.kts — регистрирует все подпроекты (включая support-service)
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Копируем исходники зависимостей:
# common-dtos/ — общие DTO (SupportTicketDto, SupportMessageDto, SupportMessageEvent и др.)
# support-service/ — исходники самого сервиса (src/main/java, src/main/resources)
COPY common-dtos/ common-dtos/
COPY support-service/ support-service/

# Сборка:
# chmod +x gradlew — делаем скрипт исполняемым (в Linux нет прав по умолчанию).
# ./gradlew :support-service:bootJar — собираем только support-service (не все модули).
#   :support-service: — путь к подпроекту в multi-module проекте.
#   bootJar — задача Spring Boot Gradle плагина: создаёт executable fat JAR.
# -x test — ПРОПУСКАЕМ тесты при сборке Docker образа (экономия времени ~2-5 мин).
# --no-daemon — не запускаем Gradle daemon в Docker (он нужен для интерактивных сессий, не CI).
RUN chmod +x gradlew && \
    ./gradlew :support-service:bootJar -x test --no-daemon

# ============================================================
# Stage 2: RUNTIME
# eclipse-temurin:17-jre-alpine — только JRE (без JDK компилятора).
# Размер образа: ~80MB vs ~300MB с JDK. Содержит только то что нужно для запуска.
FROM eclipse-temurin:17-jre-alpine

# WORKDIR /app — рабочая директория в runtime контейнере.
WORKDIR /app

# Копируем JAR из builder stage.
# /workspace/support-service/build/libs/*.jar — путь где bootJar создал файл.
# *.jar — glob паттерн (один JAR после сборки: support-service-0.0.1-SNAPSHOT.jar).
# app.jar — целевое имя (стандартизируем для ENTRYPOINT).
COPY --from=builder /workspace/support-service/build/libs/*.jar app.jar

# EXPOSE 8085 — документирует что контейнер слушает порт 8085.
# Не открывает порт автоматически — для этого нужен docker-compose ports: или -p флаг.
# Docker Compose использует EXPOSE как подсказку для связи между сервисами.
EXPOSE 8085

# ENTRYPOINT — команда запуска при старте контейнера.
# java -jar app.jar — запускает Spring Boot приложение.
# Форма JSON array (["java", "-jar", "app.jar"]) предпочтительнее строковой:
#   не запускает shell (/bin/sh -c), сигналы (SIGTERM) доходят напрямую до JVM.
ENTRYPOINT ["java", "-jar", "app.jar"]
