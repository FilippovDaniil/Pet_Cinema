dependencies {

    // Spring Boot Web — встроенный Tomcat + Spring MVC (@RestController, @RequestMapping, etc.)
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Spring Data JPA + Hibernate — ORM для работы с PostgreSQL
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Spring Security — JWT фильтр, @PreAuthorize, SecurityFilterChain
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Bean Validation — @NotNull, @Valid, @Min на DTO
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Eureka Client — регистрация в Service Discovery, @LoadBalanced RestTemplate (lb://hall-service)
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")

    // Spring Kafka — KafkaTemplate, @KafkaListener, NewTopic beans
    // (в hall-service этой зависимости нет — hall-service не использует Kafka)
    implementation("org.springframework.kafka:spring-kafka")

    // jjwt — JWT токены. API отдельно от реализации (позволяет менять реализацию без изменения кода)
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    // jjwt-impl — реализация парсинга/генерации JWT (загружается в runtime через ServiceLoader)
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    // jjwt-jackson — Jackson интеграция для сериализации claims в JSON
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")

    // PostgreSQL JDBC драйвер — нужен в runtime, не при компиляции
    runtimeOnly("org.postgresql:postgresql")

    // Lombok — генерация @Data, @Builder, @Slf4j и т.д. только при компиляции
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok") // apt процессор для обработки Lombok аннотаций

    // SpringDoc OpenAPI (Swagger UI) — документация API на /swagger-ui.html
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")

    // ---- TEST зависимости ----

    // Testcontainers PostgreSQL — реальная PostgreSQL в Docker контейнере для интеграционных тестов
    testImplementation("org.testcontainers:postgresql:1.19.8")
    // Testcontainers JUnit 5 — @Container, @Testcontainers аннотации
    testImplementation("org.testcontainers:junit-jupiter:1.19.8")
    // Testcontainers Kafka — EmbeddedKafka или реальный Kafka контейнер для тестов
    testImplementation("org.testcontainers:kafka:1.19.8")
    // spring-kafka-test — EmbeddedKafkaBroker для тестов без реального Kafka
    testImplementation("org.springframework.kafka:spring-kafka-test")
    // spring-security-test — @WithMockUser, SecurityMockMvcRequestPostProcessors.authentication()
    testImplementation("org.springframework.security:spring-security-test")
}

// bootJar — исполняемый fat JAR (включает все зависимости). Нужен для Dockerfile.
tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") { enabled = true }

// jar — обычный "thin" JAR без зависимостей. Отключаем чтобы избежать двух JAR файлов в build/libs/.
// Без этого Dockerfile COPY *.jar app.jar упадёт из-за неоднозначности (два файла совпадают по маске).
tasks.getByName<Jar>("jar") { enabled = false }
