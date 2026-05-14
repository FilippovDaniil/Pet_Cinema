// plugins {} — блок объявления Gradle плагинов для корневого проекта.
// Плагины здесь применяются к ROOT проекту.
// apply false — плагин ЗАГРУЖАЕТСЯ в classpath, но НЕ применяется к root проекту.
//   Применение к конкретным подпроектам происходит в блоке subprojects ниже.
plugins {
    // java — стандартный Gradle Java плагин: компиляция, тесты, JAR.
    // Здесь применяется к root (нужно для общих настроек java {} блока).
    java

    // org.springframework.boot — Spring Boot Gradle плагин.
    // Предоставляет: bootJar задачу, bootRun, processTestAops и т.д.
    // version "3.2.5" — версия Spring Boot (применяется ко всем подпроектам через BOM).
    // apply false — НЕ применяем к root, только загружаем. Применяем в subprojects ниже.
    id("org.springframework.boot") version "3.2.5" apply false

    // io.spring.dependency-management — управление версиями зависимостей через BOM.
    // Позволяет писать implementation("org.springframework.boot:spring-boot-starter-web")
    //   БЕЗ явного указания версии — версия берётся из BOM.
    // Примечание: в этом проекте используется нативный Gradle platform() вместо этого плагина
    //   (см. комментарий в subprojects блоке) — плагин загружен, но используется минимально.
    id("io.spring.dependency-management") version "1.1.5" apply false
}

// Версии вынесены в val переменные для единого управления.
// Изменение версии здесь обновляет ВСЕ сервисы одновременно.
val springBootVersion = "3.2.5"         // Spring Boot основная версия
val springCloudVersion = "2023.0.1"     // Spring Cloud (Eureka, Gateway, LoadBalancer)
val testcontainersVersion = "1.19.8"    // Testcontainers (PostgreSQL, Redis в тестах)
val lombokVersion = "1.18.32"           // Lombok (генерация кода)
val javaVersion = JavaVersion.VERSION_17 // Java 17 LTS

// allprojects {} — конфигурация применяется ко ВСЕМ проектам (root + все subprojects).
allprojects {
    // group — Maven groupId: идентифицирует организацию/проект.
    // В Maven артефактах: com.cinema:auth-service:1.0.0
    group = "com.cinema"

    // version — версия всех артефактов.
    version = "1.0.0"

    repositories {
        // mavenCentral() — основной репозиторий Maven артефактов.
        // Gradle скачивает зависимости из Maven Central (search.maven.org).
        // Кешируется локально в ~/.gradle/caches/ после первого скачивания.
        mavenCentral()
    }
}

// subprojects {} — конфигурация применяется только к ПОДПРОЕКТАМ (не к root).
// Все настройки здесь автоматически наследуются каждым сервисом.
subprojects {
    // apply(plugin = "java") — применяем Java плагин к каждому подпроекту.
    // Даёт: sourceCompatibility, targetCompatibility, compile tasks.
    apply(plugin = "java")

    // java {} — настройки Java компилятора для всех подпроектов.
    java {
        // sourceCompatibility — версия Java синтаксиса в исходниках.
        // VERSION_17 — разрешает использовать Java 17 фичи (records, sealed classes, text blocks).
        sourceCompatibility = javaVersion

        // targetCompatibility — версия JVM байткода.
        // VERSION_17 — скомпилированный байткод запускается на JVM 17+.
        targetCompatibility = javaVersion
    }

    // tasks.withType<JavaCompile> — конфигурация всех JavaCompile задач (compileJava, compileTestJava).
    tasks.withType<JavaCompile> {
        // encoding = "UTF-8" — кодировка исходных файлов.
        // Важно для Русских комментариев и строк в коде — без этого могут быть ошибки компиляции
        // на системах с другой default кодировкой (Windows часто использует CP1251).
        options.encoding = "UTF-8"
    }

    // tasks.withType<Test> — конфигурация всех Test задач (test).
    tasks.withType<Test> {
        // useJUnitPlatform() — ОБЯЗАТЕЛЬНО для JUnit 5 (Jupiter).
        // Без этой строки Gradle запускает тесты через JUnit 4 Vintage engine
        //   и JUnit 5 аннотации (@Test, @BeforeEach) просто игнорируются.
        // Требование Gradle 9.1: явное указание платформы.
        useJUnitPlatform()
    }

    // Применяем Spring Boot плагин и зависимости ТОЛЬКО к Spring Boot сервисам.
    // common-dtos — обычная Java библиотека (нет Spring Boot, нет main класса).
    //   Spring Boot плагин создаёт исполняемый JAR — для библиотеки это не нужно.
    if (project.name != "common-dtos") {
        // apply(plugin = "org.springframework.boot") — подключаем Spring Boot Gradle плагин.
        // Добавляет: bootJar (fat JAR), bootRun (запуск), processAot (native образы).
        apply(plugin = "org.springframework.boot")

        // Use native Gradle platform() BOM imports — avoids Spring DM plugin
        // ExclusionConfiguringAction incompatibility with Gradle 8 immutable configs
        //
        // ПОЯСНЕНИЕ: зачем platform() вместо io.spring.dependency-management плагина?
        // В Gradle 8+ конфигурации стали immutable (неизменяемыми) после резолюции.
        // Плагин io.spring.dependency-management пытается добавить exclusions ПОСЛЕ резолюции →
        //   ExclusionConfiguringAction конфликт → ошибки сборки в некоторых конфигурациях.
        // platform() — нативный Gradle механизм BOM import, работает корректно с Gradle 8+.
        dependencies {
            // platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
            // Импортирует Spring Boot BOM — содержит согласованные версии ~300 зависимостей.
            // Пример: spring-boot-starter-web → jackson 2.15.x, tomcat 10.x.x и т.д.
            // "implementation" — BOM применяется к implementation + api конфигурациям.
            "implementation"(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))

            // Spring Cloud BOM — версии Eureka, Gateway, LoadBalancer, Config.
            // 2023.0.1 совместим с Spring Boot 3.2.x (матрица совместимости Spring Cloud).
            "implementation"(platform("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion"))

            // Testcontainers BOM — версии postgresql, kafka, redis, junit-jupiter контейнеров.
            // "testImplementation" — только для тестовых конфигураций.
            "testImplementation"(platform("org.testcontainers:testcontainers-bom:$testcontainersVersion"))

            // project(":common-dtos") — зависимость от подпроекта common-dtos.
            // : — путь разделитель в Gradle (аналог / в файловых путях).
            // Все сервисы используют общие DTO (AuthRequest, MovieDto, NotificationDto и т.д.)
            "implementation"(project(":common-dtos"))

            // spring-boot-starter-actuator — мониторинг и управление:
            //   /actuator/health — healthcheck (используется в Docker healthcheck и depends_on)
            //   /actuator/metrics — метрики (Prometheus/Grafana если нужно)
            //   /actuator/info — информация о приложении
            // Добавлен ВСЕМ сервисам автоматически здесь.
            "implementation"("org.springframework.boot:spring-boot-starter-actuator")

            // Lombok — compileOnly: код генерируется при компиляции, в runtime не нужен.
            // lombokVersion — явная версия, т.к. Lombok не входит в Spring Boot BOM.
            "compileOnly"("org.projectlombok:lombok:$lombokVersion")

            // annotationProcessor — annotation processor Lombok для Gradle.
            // Без этой строки @Data, @Builder, @Slf4j и другие аннотации Lombok не работают.
            "annotationProcessor"("org.projectlombok:lombok:$lombokVersion")

            // spring-boot-starter-test — тестовые зависимости Spring Boot:
            //   JUnit 5 (Jupiter), Mockito, AssertJ, MockMvc, Hamcrest, JSONPath.
            // Версия берётся из Spring Boot BOM.
            "testImplementation"("org.springframework.boot:spring-boot-starter-test")

            // junit-platform-launcher — НЕОБХОДИМ для Gradle 9.1 + JUnit 5.
            // testRuntimeOnly — нужен только при запуске тестов (не при компиляции).
            // Без этой зависимости Gradle не может запустить JUnit 5 тесты.
            // Исторически: раньше он включался транзитивно через junit-jupiter-engine,
            //   в новых версиях нужно указывать явно.
            "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        }
    }
}
