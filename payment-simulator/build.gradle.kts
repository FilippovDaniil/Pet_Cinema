dependencies {

    // spring-boot-starter-web — встроенный Tomcat + Spring MVC.
    // payment-simulator не имеет собственных контроллеров, но Web starter нужен для:
    //   1. HTTP сервера (Actuator /health endpoint для Docker healthcheck)
    //   2. RestTemplate (HTTP клиент для вызова order-service webhook)
    //   3. Jackson (JSON сериализация для RestTemplate запросов)
    implementation("org.springframework.boot:spring-boot-starter-web")

    // spring-cloud-starter-netflix-eureka-client — клиент Eureka Service Discovery.
    // Позволяет payment-simulator зарегистрироваться в Eureka Server.
    // Версия управляется Spring Cloud BOM из корневого build.gradle.kts.
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")

    // spring-kafka — Spring обёртка над Apache Kafka.
    // Предоставляет: @KafkaListener, KafkaListenerContainerFactory, Spring Kafka AutoConfiguration.
    // Версия управляется Spring Boot BOM (совместима с Spring Boot 3.2.5).
    implementation("org.springframework.kafka:spring-kafka")

    // jackson-databind — Jackson JSON библиотека (ObjectMapper, JsonNode).
    // Явная зависимость нужна т.к. ObjectMapper используется вручную в PaymentRequestConsumer
    // для парсинга через readTree() (JsonNode подход).
    // spring-boot-starter-web уже включает Jackson, но явное объявление делает зависимость очевидной.
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // lombok — compileOnly: нужен только при компиляции для генерации кода.
    // Не попадает в итоговый JAR (не нужен в runtime).
    // Используется: @Slf4j (логгер), @RequiredArgsConstructor (конструктор для DI).
    compileOnly("org.projectlombok:lombok")

    // annotationProcessor — обязательно для Lombok в Gradle:
    // Gradle запускает annotation processor во время компиляции для генерации кода Lombok.
    // Без этой строки @Slf4j и @RequiredArgsConstructor не работают.
    annotationProcessor("org.projectlombok:lombok")
}

// bootJar { enabled = true } — включает задачу сборки исполняемого fat JAR.
// Fat JAR (uber JAR) содержит все зависимости — можно запустить java -jar app.jar.
// Это стандартная задача Spring Boot Gradle plugin.
tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") { enabled = true }

// jar { enabled = false } — отключает стандартный Gradle JAR (без зависимостей).
// Без этой строки Gradle создаёт ДВА артефакта: plain JAR + fat JAR.
// Мы хотим только fat JAR для Docker запуска.
tasks.getByName<Jar>("jar") { enabled = false }
