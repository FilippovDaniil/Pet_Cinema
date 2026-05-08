dependencies {

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
}
tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") { enabled = true }
tasks.getByName<Jar>("jar") { enabled = false }
