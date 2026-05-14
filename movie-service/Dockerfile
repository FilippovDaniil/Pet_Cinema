# ─── Stage 1: builder ────────────────────────────────────────────────────────
# eclipse-temurin:17-jdk-alpine — образ с JDK 17 (нужен для компиляции).
# alpine — минималистичный Linux (~5MB вместо ~200MB у ubuntu). Итоговый образ меньше.
# AS builder — именуем этот stage "builder", чтобы во втором stage скопировать артефакт.
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace  # Создаём рабочую директорию /workspace и переходим в неё

# Копируем Gradle wrapper и скрипты — нужны для запуска ./gradlew.
# Копируем только нужные файлы (не весь репозиторий) — это оптимизирует кеширование слоёв Docker.
# Если меняется только код сервиса — Gradle wrapper-слой не пересобирается (кеш).
COPY gradlew .
COPY gradle/ gradle/

# Копируем корневые build-файлы (там объявлены общие зависимости для всех модулей)
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Копируем common-dtos — movie-service компилируется против этого модуля
COPY common-dtos/ common-dtos/

# Копируем исходники самого movie-service
COPY movie-service/ movie-service/

# Сборка:
# chmod +x gradlew — Gradle wrapper не имеет прав на выполнение в Linux после COPY
# :movie-service:bootJar — собрать fat JAR только для movie-service (не для всех модулей)
# -x test — пропустить тесты (они запускаются в CI, а не при сборке Docker образа)
# --no-daemon — не запускать Gradle daemon (в контейнере daemon бесполезен и тратит память)
RUN chmod +x gradlew && \
    ./gradlew :movie-service:bootJar -x test --no-daemon

# ─── Stage 2: runtime ────────────────────────────────────────────────────────
# eclipse-temurin:17-jre-alpine — только JRE (без компилятора).
# JRE ~70MB vs JDK ~200MB — образ runtime значительно меньше.
# Первый stage (builder) НЕ включается в финальный образ — только JAR файл копируется.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Копируем JAR из stage builder.
# --from=builder — берём из первого stage (не из хоста).
# *.jar — glob паттерн; должен совпасть ровно с одним файлом (movie-service-*.jar).
# build.gradle.kts: jar { enabled = false } гарантирует что только один JAR (bootJar).
COPY --from=builder /workspace/movie-service/build/libs/*.jar app.jar

EXPOSE 8082  # Документируем порт (не открывает его автоматически — только подсказка для docker run -P)

# ENTRYPOINT: команда запуска при старте контейнера.
# exec form ["java", ...] предпочтительнее shell form "java ..." — процесс получает PID 1,
# корректно обрабатывает SIGTERM при остановке контейнера (graceful shutdown).
ENTRYPOINT ["java", "-jar", "app.jar"]
