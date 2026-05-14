# ── ЭТАП 1: СБОРКА (builder) ──────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder
# Используем Alpine-образ с JDK 17 (маленький, ~200 МБ). AS builder — именуем этап для многоступенчатой сборки
WORKDIR /workspace
# Рабочая директория внутри контейнера-сборщика

COPY gradlew .          # Копируем скрипт Gradle Wrapper (gradlew) — запускает нужную версию Gradle без установки
COPY gradle/ gradle/    # Копируем папку gradle/wrapper/ с настройками версии Gradle
COPY build.gradle.kts . # Корневой build-файл (общие зависимости, BOM)
COPY settings.gradle.kts . # Файл настроек — перечень подпроектов
COPY common-dtos/ common-dtos/ # Копируем common-dtos — service-discovery его не использует, но Gradle читает settings.gradle.kts
COPY service-discovery/ service-discovery/ # Исходный код этого сервиса

RUN chmod +x gradlew && \
    ./gradlew :service-discovery:bootJar -x test --no-daemon
# chmod +x gradlew        — даём права на выполнение скрипту
# :service-discovery:bootJar — собираем fat-JAR только для этого модуля
# -x test                 — пропускаем тесты (ускоряет Docker-сборку)
# --no-daemon             — не запускаем Gradle Daemon (в Docker daemon бесполезен)

# ── ЭТАП 2: ЗАПУСК (runtime) ──────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
# Финальный образ только с JRE (без JDK и исходников) — значительно меньше builder-образа
WORKDIR /app
COPY --from=builder /workspace/service-discovery/build/libs/*.jar app.jar
# Копируем только JAR из builder-этапа — весь JDK и исходники остаются в builder и не попадают в финальный образ
EXPOSE 8761
# Объявляем порт (документация для Docker Compose и оркестраторов)
ENTRYPOINT ["java", "-jar", "app.jar"]
# Команда запуска — запускает Spring Boot приложение
