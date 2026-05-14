plugins {
    java // Подключает стандартный Java-плагин Gradle (компиляция, тесты, JAR)
}

dependencies {
    implementation("org.projectlombok:lombok:1.18.32")          // Lombok: генерация геттеров, сеттеров, Builder и т.д. в compile-time
    annotationProcessor("org.projectlombok:lombok:1.18.32")     // Lombok нужен и как annotationProcessor — обрабатывает аннотации во время компиляции
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1") // Jackson: сериализация/десериализация Java-объектов ↔ JSON
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")   // Jakarta Validation API: аннотации @NotBlank, @NotNull, @Size, @Email и т.д.
                                                                          // Сами аннотации — API. Реализацию (Hibernate Validator) подключают сервисы через Spring Boot.
}
