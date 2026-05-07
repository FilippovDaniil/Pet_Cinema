import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot") version "3.2.5" apply false
    id("io.spring.dependency-management") version "1.1.5" apply false
}

val springCloudVersion = "2023.0.1"
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

    // Only apply Spring Boot plugin to services (not common-dtos)
    if (project.name != "common-dtos") {
        apply(plugin = "org.springframework.boot")
        apply(plugin = "io.spring.dependency-management")

        the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
            imports {
                mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
            }
        }

        dependencies {
            "implementation"(project(":common-dtos"))
            "implementation"("org.springframework.boot:spring-boot-starter-actuator")
            "compileOnly"("org.projectlombok:lombok")
            "annotationProcessor"("org.projectlombok:lombok")
            "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        }
    }
}
