dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-server") // Eureka Server — всё необходимое для запуска реестра сервисов
    compileOnly("org.projectlombok:lombok")        // Lombok нужен только на этапе компиляции (не попадает в JAR)
    annotationProcessor("org.projectlombok:lombok") // Обрабатывает аннотации Lombok во время компиляции
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = true  // Включаем создание исполняемого fat-JAR (содержит все зависимости)
}
tasks.getByName<Jar>("jar") {
    enabled = false // Отключаем создание обычного тонкого JAR (без зависимостей) — нам нужен только bootJar
}
