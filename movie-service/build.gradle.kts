dependencies {

    // Spring Boot Starters — автоконфигурируют соответствующие подсистемы:
    implementation("org.springframework.boot:spring-boot-starter-web")         // Tomcat + Spring MVC + Jackson
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")    // Hibernate + Spring Data JPA
    implementation("org.springframework.boot:spring-boot-starter-security")    // Spring Security
    implementation("org.springframework.boot:spring-boot-starter-validation")  // Bean Validation (Jakarta @Valid)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")  // Lettuce Redis клиент

    // Eureka Client: регистрируется в service-discovery, получает адреса других сервисов
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")

    // Kafka Producer: отправляет MovieUpdateEvent в топик "movie-update"
    implementation("org.springframework.kafka:spring-kafka")

    // JWT библиотека jjwt 0.12.5 (разбита на 3 артефакта):
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")    // API (интерфейсы): Jwts, Claims и т.д.
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")      // Реализация (нужна только в runtime, не для компиляции)
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")   // Jackson-сериализация payload JWT

    // PostgreSQL JDBC драйвер — нужен только в runtime (Hibernate обращается через него к БД)
    runtimeOnly("org.postgresql:postgresql")

    // Lombok: генерирует код во время компиляции.
    // compileOnly: не попадает в финальный JAR (только для компилятора)
    // annotationProcessor: обрабатывает аннотации @Data, @Builder и т.д.
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // SpringDoc OpenAPI: генерирует Swagger UI по /swagger-ui.html
    // webmvc-ui — для обычного (Servlet-based) Spring MVC (не reactive)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")

    // Тестовые зависимости:
    testImplementation("org.testcontainers:postgresql:1.19.8") // Запускает реальный PostgreSQL в Docker для интеграционных тестов
    testImplementation("org.testcontainers:junit-jupiter:1.19.8") // Интеграция Testcontainers с JUnit 5 (@Container, @Testcontainers)
    testImplementation("org.testcontainers:kafka:1.19.8")      // Kafka в Docker для тестов (не используется напрямую, но резервирует)
    testImplementation("org.springframework.kafka:spring-kafka-test") // EmbeddedKafka для unit-тестов (не нужен реальный брокер)
    testImplementation("org.springframework.security:spring-security-test") // @WithMockUser, SecurityMockMvcRequestPostProcessors.authentication()
}

// bootJar: задача Spring Boot Gradle Plugin для сборки "fat JAR" — включает все зависимости.
// enabled = true: явно включаем (по умолчанию true для модулей с main-классом).
tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") { enabled = true }

// jar: стандартная Gradle задача для обычного JAR без зависимостей.
// enabled = false: отключаем, чтобы Gradle не создавал два JAR файла.
// В Dockerfile: COPY --from=builder /workspace/movie-service/build/libs/*.jar app.jar
// Маска *.jar должна совпадать ровно с одним файлом — поэтому plain JAR отключаем.
tasks.getByName<Jar>("jar") { enabled = false }
