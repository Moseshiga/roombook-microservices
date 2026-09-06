# RoomBook: Booking Service

A learning project focused on reliable reservations and microservice communication.

## Current stage

Create a temporary room reservation and retrieve it by ID. PostgreSQL protects each
hourly slot against concurrent reservations, including requests from different JVMs.

Stack: Java 21, Spring Boot 4.1, Spring MVC, Bean Validation, JPA, PostgreSQL 17,
Flyway and Actuator. Integration tests use a real PostgreSQL Testcontainer.

## Run locally (PowerShell)

Requirements: JDK 21 and Docker with a running Linux container engine. Maven is
provided by the checked-in wrapper. From the project directory:

```powershell
docker compose up -d --wait postgres
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=-Xms64m -Xmx256m"
```

Alternatively run `BookingServiceApplication` in IntelliJ with VM options
`-Xms64m -Xmx256m`. Heap limits do not limit the entire JVM process.

The default `local` profile connects to `localhost:5433/bookings_db`, username
`booking`, password `booking_local`. PostgreSQL and the HTTP server bind to loopback.
The database container has a 384 MiB memory limit; Docker engine memory is separate.
Credentials and the database superuser are for local development only. Deployments
will need external secrets, restricted database roles and authentication.

## API

| Method | Path | Result |
| --- | --- | --- |
| POST | `/api/bookings` | `201 Created`, reservation JSON and `Location` header |
| GET | `/api/bookings/{id}` | `200 OK`, reservation JSON; `404` if absent |
| GET | `/actuator/health` | Application health |

Create and read a reservation for tomorrow at 12:00 UTC:

```powershell
$slotStart = [DateTime]::UtcNow.Date.AddDays(1).AddHours(12).ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
$body = @{
    roomId = '11111111-1111-1111-1111-111111111111'
    userId = '22222222-2222-2222-2222-222222222222'
    slotStart = $slotStart
} | ConvertTo-Json

$booking = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/bookings `
    -ContentType 'application/json' -Body $body
$booking
Invoke-RestMethod "http://localhost:8080/api/bookings/$($booking.id)"
```

The response contains `id`, `roomId`, `userId`, `slotStart`, `slotEnd`, `status`,
`createdAt` and `expiresAt`. Times represent UTC instants. Inputs must specify an
offset (`Z` is UTC). Slots start on whole UTC hours and last exactly one hour.

- Missing/invalid fields, past slots and non-hour-aligned times return `400`.
- An occupied slot returns `409` with an `application/problem+json` response.
- A hold lasts ten minutes, capped at the slot start if it is less than ten minutes away.
- Retrying an already successful POST currently returns `409`; idempotency keys are
  a separate future feature. The API does not yet recover a lost successful response.

## Concurrency and expiration

The migration creates a partial unique index:

```sql
CREATE UNIQUE INDEX bookings_active_slot_uq ON bookings (room_id, slot_start)
WHERE status IN ('PENDING', 'CONFIRMED');
```

`BookingService.create` expires any overdue PENDING hold for the requested slot and
inserts the new booking in one transaction. There is no check-then-insert availability
query or JVM lock. PostgreSQL arbitrates concurrent inserts. The losing transaction
rolls back; the exception handler translates only this named index violation to 409.

Time passing alone does not remove a row from the index. A new reservation attempt
explicitly persists `EXPIRED` for an overdue hold. GET reports the effective EXPIRED
status without writing to the database, even before this cleanup. No background
expiration job exists yet. CONFIRMED rows remain active regardless of hold expiry.

Expiry uses an injected UTC application clock. Multiple deployed instances require
synchronized clocks; database-authoritative timing is a topic for the distributed
deployment stage. Fixed hourly slots make an equality-based index sufficient;
arbitrary overlapping intervals would require a different constraint.

## Verify

With Docker running (the Compose database is not required):

```powershell
.\mvnw.cmd verify
```

Tests use an isolated, automatically removed PostgreSQL container and a fixed clock.
They exercise the HTTP server, migrations and real database constraints. Coverage
includes concurrent requests, a deterministic blocked database insert, expired-hold
replacement, inactive history, confirmed reservations, input validation and retrieval.
The local development database is not touched by tests. Tests run sequentially.

## Stop

Stop the application in IntelliJ or with Ctrl+C, then:

```powershell
docker compose stop
```

Database files remain in a named volume. Flyway applies migrations on application
startup; Hibernate uses `ddl-auto=validate` and does not modify the schema.

## Scope and next steps

Room and user UUIDs are currently caller-supplied demo identifiers. There is no room
catalog lookup, authentication or ownership enforcement yet. Do not expose this
learning-stage API publicly. Keycloak integration will replace caller-supplied user
identity with the authenticated subject. Confirmation, cancellation, background
expiration, messaging and idempotency are subsequent steps.

## Configuration

Local application overrides: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Compose does not
automatically pass environment variables to an app launched separately from IntelliJ.
For deployment, explicitly select another `SPRING_PROFILES_ACTIVE` value and provide
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.

## References

- [PostgreSQL partial unique indexes](https://www.postgresql.org/docs/17/indexes-partial.html)
- [Spring Boot Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
- [Spring Boot application properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html)
- [Docker Compose services](https://docs.docker.com/reference/compose-file/services/)
