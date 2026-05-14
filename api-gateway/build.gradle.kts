dependencies {

    implementation("org.springframework.cloud:spring-cloud-starter-gateway")              // Spring Cloud Gateway: реактивный reverse-proxy + маршрутизация
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client") // Eureka Client: регистрирует gateway в реестре + lb:// URI
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")    // Реактивный Redis-клиент (WebFlux-совместимый, нужен для CacheInvalidationConsumer)
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")       // jjwt API: классы Jwts, Claims, Keys
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")         // jjwt реализация: только в runtime, не нужна при компиляции
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")      // jjwt интеграция с Jackson (сериализация claims в JSON)
    implementation("org.springframework.kafka:spring-kafka") // Spring Kafka: @KafkaListener, KafkaTemplate
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = true  // Создаём исполняемый fat-JAR
}
tasks.getByName<Jar>("jar") {
    enabled = false // Отключаем обычный тонкий JAR
}
