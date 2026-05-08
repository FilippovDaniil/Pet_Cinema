dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-server")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = true
}
tasks.getByName<Jar>("jar") {
    enabled = false
}
