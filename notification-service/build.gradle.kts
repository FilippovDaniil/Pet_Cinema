// build.gradle.kts — зависимости notification-service (Kotlin DSL).
// Общие настройки унаследованы из корневого build.gradle.kts.

dependencies {

    // spring-boot-starter-web — Spring MVC + встроенный Tomcat + Jackson.
    // Нужен для NotificationController (@RestController, @GetMapping, @PatchMapping).
    implementation("org.springframework.boot:spring-boot-starter-web")

    // spring-boot-starter-data-jpa — JPA/Hibernate + Spring Data.
    // Нужен для Notification (@Entity), NotificationRepository (JpaRepository).
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // spring-boot-starter-security — Spring Security.
    // SecurityFilterChain, JwtAuthFilter, JWT аутентификация.
    implementation("org.springframework.boot:spring-boot-starter-security")

    // spring-cloud-starter-netflix-eureka-client — регистрация в Eureka Server.
    // @EnableDiscoveryClient в NotificationServiceApplication.
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")

    // spring-kafka — Apache Kafka интеграция.
    // @KafkaListener для consumption из "ticket-purchase" и "support-message" топиков.
    // StringDeserializer — получаем сырую JSON строку.
    implementation("org.springframework.kafka:spring-kafka")

    // jjwt-api — Java JWT 0.12.5 API.
    // JwtUtils использует: Jwts.parser(), verifyWith(), parseSignedClaims().
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    // jjwt-impl, jjwt-jackson — runtime зависимости для реализации jjwt.
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")

    // postgresql — JDBC драйвер для подключения к PostgreSQL.
    runtimeOnly("org.postgresql:postgresql")

    // lombok — кодогенерация: @Data, @Builder, @Slf4j, @RequiredArgsConstructor.
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // jackson-databind — ObjectMapper для ручного парсинга JSON.
    // NotificationConsumer: objectMapper.readValue(message, TicketPurchaseEvent.class)
    // Явная зависимость хотя spring-boot-starter-web уже включает jackson-databind.
    // Здесь explicit версия гарантирует что Jackson доступен в тестах.
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // testcontainers — Docker контейнеры для интеграционных тестов.
    // testcontainers:postgresql — PostgreSQL 15 контейнер для NotificationServiceIntegrationTest.
    // Нет явной версии — используется BOM из корневого build.gradle.kts.
    testImplementation("org.testcontainers:postgresql")
    // junit-jupiter — @Testcontainers, @Container аннотации для JUnit 5.
    testImplementation("org.testcontainers:junit-jupiter")
    // testcontainers:kafka — Kafka контейнер (в тестах исключаем KafkaAutoConfiguration,
    //   поэтому реальный контейнер не запускается, но зависимость нужна для компиляции).
    testImplementation("org.testcontainers:kafka")

    // spring-kafka-test — тестовые утилиты Kafka (EmbeddedKafkaBroker).
    testImplementation("org.springframework.kafka:spring-kafka-test")

    // spring-security-test — authentication() для @WebMvcTest.
    // NotificationControllerTest использует SecurityMockMvcRequestPostProcessors.authentication().
    testImplementation("org.springframework.security:spring-security-test")
}

// bootJar: enabled = true — создаём исполняемый Spring Boot fat JAR.
tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") { enabled = true }

// jar: enabled = false — отключаем стандартный тонкий JAR (нужен только fat JAR).
tasks.getByName<Jar>("jar") { enabled = false }
