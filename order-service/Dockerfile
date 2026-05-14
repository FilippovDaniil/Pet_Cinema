# ============================================================
# STAGE 1: Builder — компилируем и собираем JAR
# eclipse-temurin:17-jdk-alpine — JDK 17 на Alpine Linux (минимальный образ ~200MB)
# ============================================================
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace

# Копируем Gradle wrapper и скрипты (нужны для запуска ./gradlew)
COPY gradlew .
COPY gradle/ gradle/

# Копируем корневые Gradle файлы (build.gradle.kts, settings.gradle.kts — определяют проект)
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Копируем общие DTO (common-dtos — зависимость order-service)
COPY common-dtos/ common-dtos/

# Копируем исходники самого order-service
COPY order-service/ order-service/

# Сборка fat JAR:
# -x test — пропускаем тесты (ускоряет build, тесты запускаются в CI отдельно)
# --no-daemon — не запускаем Gradle daemon (в Docker он не нужен, экономим память)
RUN chmod +x gradlew && \
    ./gradlew :order-service:bootJar -x test --no-daemon

# ============================================================
# STAGE 2: Runtime — минимальный образ для запуска
# eclipse-temurin:17-jre-alpine — только JRE (без компилятора), ~80MB
# Финальный образ значительно меньше builder образа
# ============================================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Копируем только JAR из builder stage (все зависимости уже внутри fat JAR)
COPY --from=builder /workspace/order-service/build/libs/*.jar app.jar

# Документируем порт который использует сервис (8084)
# EXPOSE не открывает порт — это только метаданные. Реальный маппинг в docker-compose.yml.
EXPOSE 8084

# Запускаем Spring Boot приложение
ENTRYPOINT ["java", "-jar", "app.jar"]
