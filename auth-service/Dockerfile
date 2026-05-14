# ── ЭТАП 1: СБОРКА ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY common-dtos/ common-dtos/  # common-dtos нужен — AuthService использует DTO из него
COPY auth-service/ auth-service/ # Исходный код auth-service

RUN chmod +x gradlew && \
    ./gradlew :auth-service:bootJar -x test --no-daemon
# Собираем только auth-service, пропускаем тесты

# ── ЭТАП 2: ЗАПУСК ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/auth-service/build/libs/*.jar app.jar
EXPOSE 8081  # Порт auth-service
ENTRYPOINT ["java", "-jar", "app.jar"]
