rootProject.name = "cinema-system"

// Include only modules whose directories exist — allows partial Docker builds
// (each service Dockerfile copies only common-dtos + itself)
listOf(
    "common-dtos",
    "service-discovery",
    "api-gateway",
    "auth-service",
    "movie-service",
    "hall-service",
    "order-service",
    "support-service",
    "notification-service",
    "payment-simulator"
).filter { file(it).isDirectory }.forEach { include(it) }
