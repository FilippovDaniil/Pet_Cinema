# ============================================================
# STAGE 1: builder — сборка JAR файла
# ============================================================

# FROM eclipse-temurin:17-jdk-alpine AS builder — базовый образ для сборки.
# eclipse-temurin:17 — OpenJDK 17 (JDK нужен для компиляции Java кода).
# alpine — минималистичный Linux дистрибутив (~5 МБ vs ~100 МБ для debian).
# AS builder — именованный stage (используется в COPY --from=builder ниже).
FROM eclipse-temurin:17-jdk-alpine AS builder

# WORKDIR /workspace — рабочая директория внутри контейнера.
# Все последующие команды выполняются относительно /workspace.
# Директория создаётся автоматически если не существует.
WORKDIR /workspace

# COPY gradlew . — копируем Gradle Wrapper скрипт.
# gradlew позволяет собрать проект без установленного Gradle на машине.
# Контекст сборки — корень проекта (context: . в docker-compose.yml).
COPY gradlew .

# COPY gradle/ gradle/ — копируем директорию с Gradle Wrapper JAR и properties.
# gradle/wrapper/gradle-wrapper.jar — сам Wrapper, скачивает нужную версию Gradle.
# gradle/wrapper/gradle-wrapper.properties — указывает версию Gradle для скачивания.
COPY gradle/ gradle/

# COPY build.gradle.kts . — корневой build файл с общей конфигурацией.
# Содержит: Spring Boot BOM, общие зависимости (Lombok, Actuator), задачи тестов.
COPY build.gradle.kts .

# COPY settings.gradle.kts . — регистрирует все подпроекты Gradle.
# Без settings.gradle.kts Gradle не знает о существовании подпроектов (common-dtos, payment-simulator).
COPY settings.gradle.kts .

# COPY common-dtos/ common-dtos/ — копируем общие DTO.
# payment-simulator зависит от common-dtos (PaymentRequestEvent и другие DTO).
# Gradle должен видеть исходники common-dtos для компиляции payment-simulator.
COPY common-dtos/ common-dtos/

# COPY payment-simulator/ payment-simulator/ — копируем исходники этого сервиса.
# Только payment-simulator — не копируем другие сервисы (они не нужны для сборки).
# Это ускоряет сборку: Docker кешируется на уровне слоёв.
COPY payment-simulator/ payment-simulator/

# RUN chmod +x gradlew — делаем gradlew исполняемым (в Linux нужны права +x).
# && — выполняем следующую команду только если chmod успешен.
# ./gradlew :payment-simulator:bootJar — собираем fat JAR только для payment-simulator.
#   :payment-simulator:bootJar — Gradle task: сборка исполняемого JAR с зависимостями.
#   -x test — пропускаем тесты (ускоряет сборку, тесты запускаются в CI отдельно).
#   --no-daemon — не запускаем фоновый Gradle daemon (в Docker он не нужен, экономит память).
RUN chmod +x gradlew && \
    ./gradlew :payment-simulator:bootJar -x test --no-daemon

# ============================================================
# STAGE 2: runtime — минимальный образ для запуска
# ============================================================

# FROM eclipse-temurin:17-jre-alpine — JRE (Java Runtime Environment) для запуска.
# JRE меньше JDK: не содержит компилятор javac, jshell и другие инструменты разработки.
# alpine — минималистичный Linux (~75 МБ для JRE vs ~175 МБ для JDK).
# Итоговый образ содержит только JRE + JAR — безопаснее и легковеснее.
FROM eclipse-temurin:17-jre-alpine

# WORKDIR /app — рабочая директория в runtime контейнере.
WORKDIR /app

# COPY --from=builder — копируем JAR из stage "builder" в runtime образ.
# /workspace/payment-simulator/build/libs/*.jar — путь где Gradle создал JAR.
# app.jar — имя файла в runtime контейнере (используется в ENTRYPOINT).
# Wildcard *.jar безопасен: bootJar создаёт ровно один файл (payment-simulator-*.jar).
COPY --from=builder /workspace/payment-simulator/build/libs/*.jar app.jar

# EXPOSE 8087 — документирует что контейнер слушает порт 8087.
# EXPOSE не публикует порт автоматически — это только метаданные.
# Реальная публикация происходит в docker-compose.yml через ports: "8087:8087".
EXPOSE 8087

# ENTRYPOINT ["java", "-jar", "app.jar"] — команда запуска контейнера.
# exec form (JSON массив) — процесс java получает PID 1 (не shell).
# PID 1 важен: Docker SIGTERM (при остановке) идёт напрямую в JVM → корректный shutdown.
# java -jar app.jar — запускает Spring Boot приложение из fat JAR.
ENTRYPOINT ["java", "-jar", "app.jar"]
