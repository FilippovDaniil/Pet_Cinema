plugins {
    java
    id("org.springframework.boot") version "3.2.5" apply false
    id("io.spring.dependency-management") version "1.1.5" apply false
}

val springBootVersion = "3.2.5"
val springCloudVersion = "2023.0.1"
val testcontainersVersion = "1.19.8"
val lombokVersion = "1.18.32"
val javaVersion = JavaVersion.VERSION_17

allprojects {
    group = "com.cinema"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    if (project.name != "common-dtos") {
        apply(plugin = "org.springframework.boot")

        // Use native Gradle platform() BOM imports — avoids Spring DM plugin
        // ExclusionConfiguringAction incompatibility with Gradle 8 immutable configs
        dependencies {
            "implementation"(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
            "implementation"(platform("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion"))
            "testImplementation"(platform("org.testcontainers:testcontainers-bom:$testcontainersVersion"))

            "implementation"(project(":common-dtos"))
            "implementation"("org.springframework.boot:spring-boot-starter-actuator")
            "compileOnly"("org.projectlombok:lombok:$lombokVersion")
            "annotationProcessor"("org.projectlombok:lombok:$lombokVersion")
            "testImplementation"("org.springframework.boot:spring-boot-starter-test")
            "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        }
    }
}
