# RoomBook: Booking Service

A learning project focused on reliable reservations and microservice communication.

## Current stage

Spring Boot application with a local PostgreSQL environment, Flyway integration,
Hibernate schema validation and an Actuator health endpoint. Booking endpoints,
entities and SQL migrations will be added in the next stage.

## Requirements

- JDK 21
- Docker with Docker Compose and a running Linux container engine

Maven is provided through the checked-in Maven Wrapper.

## Run locally (PowerShell)

From the project directory:

```powershell
docker compose up -d --wait postgres
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=-Xms64m -Xmx256m"
```

Alternatively, start `BookingServiceApplication` from IntelliJ IDEA. Optional VM
options: `-Xms64m -Xmx256m`. Heap limits do not limit the entire JVM process.

The `local` profile is used by default. It connects to `localhost:5433`, database
`bookings_db`, username `booking`, password `booking_local`. These are disposable
development credentials. PostgreSQL and the HTTP server bind to loopback only.
The database container has a 384 MiB memory limit; Docker engine memory is separate.

The PostgreSQL image creates the development user as a database superuser. This
configuration is for local learning, not a production deployment. Deployment will
need separate migration/application roles and externally supplied credentials.

Check the application:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Expected status: `UP`. There are no booking HTTP endpoints yet.

## Verify

With PostgreSQL running:

```powershell
.\mvnw.cmd verify
```

The generated context test currently uses the local database. Dedicated isolated
integration tests will be introduced with the booking behavior. Flyway may report
that no migrations were found at this initial stage; no business tables exist yet.
Hibernate validates mappings instead of creating or updating tables automatically.

## Stop

Stop the application with Ctrl+C or the IntelliJ Stop button, then:

```powershell
docker compose stop
```

Database files remain in a named volume and survive container recreation.

## Configuration

For local overrides, set `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` in the application's
environment. Docker Compose does not automatically pass shell settings to an
application launched separately from IntelliJ.

For another environment, explicitly set `SPRING_PROFILES_ACTIVE` and provide
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and
`SPRING_DATASOURCE_PASSWORD`. The local profile must not be used for deployment.

## References

- [Spring Boot application properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html)
- [Docker Compose services](https://docs.docker.com/reference/compose-file/services/)
