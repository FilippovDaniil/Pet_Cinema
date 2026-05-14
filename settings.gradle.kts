// rootProject.name — имя корневого проекта Gradle.
// Отображается в: IDE, Gradle output, Maven артефактах (groupId:name:version).
// Используется как имя родительского проекта при сборке: "cinema-system".
rootProject.name = "cinema-system"

// Include only modules whose directories exist — allows partial Docker builds
// (each service Dockerfile copies only common-dtos + itself)
// Комментарий объясняет зачем нужен .filter { file(it).isDirectory }:
// Каждый Dockerfile при сборке копирует ТОЛЬКО common-dtos + свой сервис в контекст Docker.
// Остальные директории сервисов НЕ копируются → file(it).isDirectory вернёт false для них.
// Без этого фильтра Gradle упал бы с ошибкой "Project not found: :auth-service"
// при сборке image, скажем, movie-service (там нет auth-service в контексте).

// listOf(...) — Kotlin функция создания неизменяемого списка String.
// Перечислены ВСЕ подпроекты (subprojects) этого мультимодульного Gradle проекта.
listOf(
    "common-dtos",           // общие DTO и события — зависимость для всех сервисов
    "service-discovery",     // Eureka Server — реестр сервисов
    "api-gateway",           // Spring Cloud Gateway — единая точка входа
    "auth-service",          // аутентификация и JWT токены
    "movie-service",         // каталог фильмов, отзывы, комментарии
    "hall-service",          // залы, доп.услуги, сеансы
    "order-service",         // заказы, билеты, меню еды
    "support-service",       // чат технической поддержки
    "notification-service",  // уведомления через Kafka
    "payment-simulator"      // имитация платёжного шлюза
)
    // .filter { file(it).isDirectory } — фильтруем: включаем только существующие директории.
    // file(it) — создаёт java.io.File объект с путём относительно корня проекта.
    // isDirectory — возвращает true если директория существует в файловой системе.
    // В Docker builder context: при сборке payment-simulator скопированы только
    //   common-dtos/ и payment-simulator/ → остальные директории отсутствуют.
    .filter { file(it).isDirectory }

    // .forEach { include(it) } — регистрирует каждый найденный подпроект в Gradle.
    // include("auth-service") эквивалентно include(":auth-service") —
    //   создаёт подпроект с путём :auth-service.
    // После include() Gradle ищет build.gradle.kts в директории auth-service/
    //   и загружает его конфигурацию.
    .forEach { include(it) }
