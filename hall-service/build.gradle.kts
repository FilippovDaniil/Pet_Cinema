// Зависимости hall-service.
// ВАЖНО: hall-service НЕ имеет зависимости на Kafka.
// Причина: hall-service не публикует и не потребляет Kafka-события.
// Он предоставляет данные (залы, сеансы) по REST другим сервисам.
dependencies {

    implementation("org.springframework.boot:spring-boot-starter-web")         // Tomcat + Spring MVC (REST контроллеры)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")    // Hibernate + Spring Data JPA
    implementation("org.springframework.boot:spring-boot-starter-security")    // Spring Security (JWT-фильтр, @PreAuthorize)
    implementation("org.springframework.boot:spring-boot-starter-validation")  // Bean Validation (@Valid, @NotBlank и т.д.)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")  // Redis (StringRedisTemplate, хотя hall-service его почти не использует)
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client") // Регистрация в Eureka
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")     // jjwt API для работы с JWT (Jwts.parser(), Keys и т.д.)
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")       // Реализация jjwt (только runtime — не нужен при компиляции)
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")    // Jackson-сериализатор для jjwt (JWT использует JSON внутри)
    runtimeOnly("org.postgresql:postgresql")               // PostgreSQL JDBC-драйвер (только runtime — интерфейс JDBC стандартный)
    compileOnly("org.projectlombok:lombok")                // Lombok (только компиляция — аннотации обрабатываются процессором)
    annotationProcessor("org.projectlombok:lombok")       // Процессор аннотаций Lombok (генерирует код во время компиляции)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")  // Swagger UI: /swagger-ui.html, /v3/api-docs
    testImplementation("org.testcontainers:postgresql:1.19.8")                 // Testcontainers — запуск PostgreSQL в Docker для тестов
    testImplementation("org.testcontainers:junit-jupiter:1.19.8")              // Интеграция Testcontainers с JUnit 5 (@Container, @Testcontainers)
    testImplementation("org.springframework.security:spring-security-test")   // MockMvc + Spring Security (WithMockUser, SecurityMockMvcRequestPostProcessors)
}

// bootJar enabled = true — Spring Boot Gradle Plugin создаёт исполняемый fat-JAR (с embedded Tomcat).
// Этот JAR используется в Dockerfile: java -jar app.jar
tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") { enabled = true }

// jar enabled = false — отключает создание обычного (не-boot) JAR.
// Без этого в build/libs/ появятся два JAR: hall-service.jar и hall-service-plain.jar.
// Dockerfile использует glob *.jar и упадёт если файлов больше одного (неоднозначность).
tasks.getByName<Jar>("jar") { enabled = false }
