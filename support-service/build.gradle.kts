// build.gradle.kts — конфигурация сборки support-service (Kotlin DSL для Gradle).
// Этот файл описывает зависимости сервиса.
// Общие настройки (Spring Boot BOM, Lombok, плагины) унаследованы из корневого build.gradle.kts.

dependencies {

    // spring-boot-starter-web — Spring MVC: @RestController, @RequestMapping, встроенный Tomcat.
    // Включает Jackson для JSON сериализации/десериализации.
    implementation("org.springframework.boot:spring-boot-starter-web")

    // spring-boot-starter-data-jpa — JPA/Hibernate + Spring Data: @Entity, JpaRepository, @Transactional.
    // Hibernate подключается к PostgreSQL, управляет схемой БД (ddl-auto: update).
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // spring-boot-starter-security — Spring Security: SecurityFilterChain, @PreAuthorize, фильтры.
    // Необходим для JWT аутентификации и авторизации по ролям.
    implementation("org.springframework.boot:spring-boot-starter-security")

    // spring-boot-starter-validation — Bean Validation: @Valid, @NotBlank, @NotNull на DTO полях.
    // Hibernate Validator — реализация спецификации Jakarta Bean Validation.
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // spring-cloud-starter-netflix-eureka-client — Eureka Client для регистрации сервиса.
    // @EnableDiscoveryClient регистрирует support-service в Eureka Server (порт 8761).
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")

    // spring-kafka — Apache Kafka интеграция для Spring.
    // KafkaTemplate для публикации событий в топик "support-message".
    implementation("org.springframework.kafka:spring-kafka")

    // jjwt-api — Java JWT библиотека (API), версия 0.12.5.
    // Используется в JwtUtils: Jwts.parser(), verifyWith(), parseSignedClaims().
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")

    // jjwt-impl — реализация jjwt API. runtimeOnly: нужна только во время выполнения, не при компиляции.
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")

    // jjwt-jackson — интеграция jjwt с Jackson для JSON обработки claims.
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")

    // postgresql — JDBC драйвер PostgreSQL. runtimeOnly: нужен во время выполнения для подключения к БД.
    runtimeOnly("org.postgresql:postgresql")

    // lombok — кодогенерация: @Data, @Builder, @Slf4j, @RequiredArgsConstructor.
    // compileOnly: Lombok нужен только при компиляции, в JAR не включается.
    compileOnly("org.projectlombok:lombok")
    // annotationProcessor — запускает Lombok процессор аннотаций во время компиляции.
    // Генерирует геттеры, сеттеры, конструкторы и т.д.
    annotationProcessor("org.projectlombok:lombok")

    // testcontainers:postgresql — Docker контейнер PostgreSQL для интеграционных тестов.
    // SupportServiceIntegrationTest запускает реальный PostgreSQL 15-alpine.
    testImplementation("org.testcontainers:postgresql:1.19.8")

    // testcontainers:junit-jupiter — @Testcontainers, @Container аннотации для JUnit 5.
    // Управляет жизненным циклом Docker контейнеров в тестах.
    testImplementation("org.testcontainers:junit-jupiter:1.19.8")

    // testcontainers:kafka — Docker контейнер Kafka для интеграционных тестов.
    // Подключён но в SupportServiceIntegrationTest используется exclude KafkaAutoConfiguration
    // (тесты мокируют Kafka вместо реального брокера).
    testImplementation("org.testcontainers:kafka:1.19.8")

    // spring-kafka-test — тестовые утилиты Kafka: EmbeddedKafka и другие helper классы.
    testImplementation("org.springframework.kafka:spring-kafka-test")

    // spring-security-test — SecurityMockMvcRequestPostProcessors.authentication() для @WebMvcTest.
    // authentication() позволяет установить произвольный Authentication объект без реального токена.
    testImplementation("org.springframework.security:spring-security-test")
}

// bootJar: enabled = true — создаём исполняемый JAR (Spring Boot fat jar).
// В multi-module проекте нужно явно включить, т.к. корневой build.gradle.kts мог отключить.
tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") { enabled = true }

// jar: enabled = false — отключаем стандартный (тонкий) JAR.
// Нам нужен только fat JAR от Spring Boot, не обычный JAR без зависимостей.
tasks.getByName<Jar>("jar") { enabled = false }
