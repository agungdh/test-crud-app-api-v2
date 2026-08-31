tasks.register<Exec>("composeUp") {
    group = "docker"
    description = "Start all docker-compose services"
    commandLine("docker", "compose", "up", "-d", "--wait")
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
    description = "Create MinIO bucket 'crud'"
    commandLine("docker", "compose", "exec", "minio", "mcli", "mb", "--ignore-existing", "local/crud")
}
