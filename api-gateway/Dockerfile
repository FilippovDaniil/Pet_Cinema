# ── ЭТАП 1: СБОРКА ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY common-dtos/ common-dtos/  # common-dtos нужен т.к. settings.gradle.kts включает его как подпроект
COPY api-gateway/ api-gateway/  # Исходный код api-gateway

RUN chmod +x gradlew && \
    ./gradlew :api-gateway:bootJar -x test --no-daemon
# Собираем fat-JAR только для api-gateway модуля, без тестов

# ── ЭТАП 2: ЗАПУСК ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/api-gateway/build/libs/*.jar app.jar
EXPOSE 8080  # Порт API Gateway (все клиентские запросы идут сюда)
ENTRYPOINT ["java", "-jar", "app.jar"]
