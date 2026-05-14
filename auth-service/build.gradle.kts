dependencies {

    implementation("org.springframework.boot:spring-boot-starter-web")              // REST API: Tomcat + Jackson + Spring MVC
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")         // JPA/Hibernate + Spring Data репозитории
    implementation("org.springframework.boot:spring-boot-starter-security")         // Spring Security: AuthenticationManager, SecurityFilterChain и т.д.
    implementation("org.springframework.boot:spring-boot-starter-validation")       // Bean Validation: @Valid, @NotBlank, @Size и т.д. (Hibernate Validator)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")       // Spring Data Redis: StringRedisTemplate для blacklist токенов
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client") // Eureka Client: регистрация в реестре сервисов
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")    // jjwt API
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")      // jjwt реализация (runtime)
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")   // jjwt + Jackson (runtime)
    runtimeOnly("org.postgresql:postgresql")              // JDBC драйвер PostgreSQL (runtime — нужен только при запуске)

    // Тестовые зависимости:
    testImplementation("org.springframework.security:spring-security-test") // SecurityMockMvcRequestPostProcessors.authentication()
    testImplementation("org.testcontainers:postgresql")    // PostgreSQL контейнер для интеграционных тестов
    testImplementation("org.testcontainers:junit-jupiter") // @Testcontainers, @Container аннотации
    testImplementation("org.testcontainers:testcontainers") // Базовые классы Testcontainers
    testAnnotationProcessor("org.projectlombok:lombok:1.18.32") // Lombok в тестах (обработка аннотаций)
    testCompileOnly("org.projectlombok:lombok:1.18.32")         // Lombok в тестах (компиляция)
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = true
}
tasks.getByName<Jar>("jar") {
    enabled = false
}
