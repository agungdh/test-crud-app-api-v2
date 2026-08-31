tasks.register<Exec>("composeUp") {
    group = "docker"
    description = "Start all docker-compose services"
    commandLine("docker", "compose", "up", "-d")
}

tasks.register<Exec>("composeDown") {
    group = "docker"
    description = "Stop all docker-compose services"
    commandLine("docker", "compose", "down")
}

tasks.register<Exec>("composeLogs") {
    group = "docker"
    description = "Follow docker-compose logs"
    commandLine("docker", "compose", "logs", "-f")
}

tasks.register<Exec>("minioInit") {
    group = "docker"
    description = "Create MinIO bucket 'crud' (manual, auto already via minio-init service)"
    commandLine("docker", "compose", "exec", "minio", "mcli", "mb", "--ignore-existing", "local/crud")
}

tasks.register<Exec>("minioInitLocal") {
    group = "docker"
    description = "Create MinIO bucket via one-off container (no need minio running via exec)"
    commandLine(
        "docker", "compose", "run", "--rm", "--no-deps",
        "-e", "MINIO_ROOT_USER=admin",
        "-e", "MINIO_ROOT_PASSWORD=admin123",
        "minio-init"
    )
}
