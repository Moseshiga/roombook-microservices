# RoomBook: Booking Service

A learning project focused on reliable reservations and microservice communication.

## Current stage

Create a temporary room reservation and retrieve it by ID. PostgreSQL protects each
hourly slot against concurrent reservations, including requests from different JVMs.
Keycloak authenticates callers; the booking owner comes from the JWT `sub` claim.

Stack: Java 21, Spring Boot 4.1, Spring MVC, Spring Security OAuth2 Resource Server,
Bean Validation, JPA, PostgreSQL 17, Flyway, Keycloak and Actuator. Integration tests
use a real PostgreSQL Testcontainer.

## Run locally (PowerShell)

Requirements: JDK 21 and Docker with a running Linux container engine. Maven is
provided by the checked-in wrapper. From the project directory:

```powershell
Copy-Item .env.example .env
docker compose up -d postgres keycloak
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=-Xms64m -Xmx256m"
```

Alternatively run `BookingServiceApplication` in IntelliJ with VM options
`-Xms64m -Xmx256m`. Heap limits do not limit the entire JVM process.

The default `local` profile connects to `localhost:5433/bookings_db`, username
`booking`, password `booking_local`. Keycloak is available only on
`http://localhost:8081`; it has a separate `keycloak_db` in the same PostgreSQL
container. PostgreSQL, Keycloak and the HTTP server bind to loopback. The containers
are limited to 384 MiB and 768 MiB respectively. Docker engine memory is separate.

`.env` contains local-only secrets and is ignored by Git. Keep `.env.example` as a
template and replace its placeholders before the first start. The imported `roombook`
realm contains `USER` and `ADMIN` roles, a public `roombook-ui` client for a future
browser UI (Authorization Code + PKCE), and a confidential `roombook-service` client
for future service-to-service calls. Open the [Keycloak Admin Console](http://localhost:8081/admin/)
and sign in with `KEYCLOAK_ADMIN_USERNAME` and `KEYCLOAK_ADMIN_PASSWORD` from `.env`.
It also imports the local users `alice` (USER) and `admin-user` (USER, ADMIN).

## API

| Method | Path | Result |
| --- | --- | --- |
| POST | `/api/bookings` | `201 Created`, reservation JSON and `Location` header |
| GET | `/api/bookings/{id}` | `200 OK`, reservation JSON; `404` if absent |
| POST | `/api/bookings/{id}/confirm` | `200 OK` for a non-expired `PENDING` reservation |
| POST | `/api/bookings/{id}/cancel` | `200 OK` for a non-expired `PENDING` reservation |
| GET | `/actuator/health` | Application health |

All booking endpoints require a Bearer access token with the Keycloak realm role
`USER` or `ADMIN`. `GET /actuator/health` stays public. A browser client will obtain
the token through the `roombook-ui` Authorization Code + PKCE flow; we will add that
UI in a later step.

The JSON body deliberately has no `userId`: the service stores the `sub` claim from
the verified access token. The request shape is:

```powershell
$slotStart = [DateTime]::UtcNow.Date.AddDays(1).AddHours(12).ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
$body = @{
    roomId = '11111111-1111-1111-1111-111111111111'
    slotStart = $slotStart
} | ConvertTo-Json
```

The response contains `id`, `roomId`, `userId`, `slotStart`, `slotEnd`, `status`,
`createdAt` and `expiresAt`. Times represent UTC instants. Inputs must specify an
offset (`Z` is UTC). Slots start on whole UTC hours and last exactly one hour.

- Missing/invalid fields, past slots and non-hour-aligned times return `400`.
- A missing or invalid access token returns `401`; an authenticated caller without
  USER or ADMIN returns `403`.
- The owner may read, confirm and cancel their booking. An ADMIN may perform these
  operations on any booking; a different USER receives `403`.
- An occupied slot returns `409` with an `application/problem+json` response.
- A hold lasts ten minutes, capped at the slot start if it is less than ten minutes away.
- Confirmation and cancellation are competing atomic state transitions. Only an
  unexpired `PENDING` booking can change; all other states return `409`.
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

Time passing alone does not remove a row from the index. `ExpiredBookingCleanup` runs
every minute and persists `EXPIRED` for every overdue `PENDING` hold. A new reservation
attempt also expires an overdue hold for its requested slot, so it does not need to wait
for the next scheduled run. GET reports the effective EXPIRED status without writing to
the database, even before either cleanup runs. CONFIRMED rows remain active regardless
of hold expiry.

The scheduler is enabled with `booking.expiration.cleanup.enabled=true` and its fixed
delay is configured by `booking.expiration.cleanup-interval` (`PT1M` by default).
ShedLock stores a distributed lock in the `shedlock` table, so only one application
instance can execute this named cleanup task at one time. It uses PostgreSQL time rather
than an application server clock when deciding whether a lock is valid.

If another instance holds the lock, ShedLock skips this scheduled invocation instead of
waiting. `lockAtMostFor=PT5M` is a recovery ceiling: it frees the lock if the holder
crashes. It must remain longer than the worst expected cleanup duration; if it expires
while a task is still running, two instances could run concurrently. ShedLock is a lock,
not a durable distributed scheduler, so cleanup remains idempotent.

Expiry uses an injected UTC application clock. Multiple deployed instances require
synchronized clocks; database-authoritative timing is a topic for the distributed
deployment stage. Fixed hourly slots make an equality-based index sufficient;
arbitrary overlapping intervals would require a different constraint.

## Verify

With Docker running (the Compose database is not required):

```powershell
.\mvnw.cmd verify
```

Tests use an isolated, automatically removed PostgreSQL container, a fixed clock and
a deterministic test JWT decoder. They exercise the HTTP server, migrations, JWT role
mapping and real database constraints. The local development database is not touched.
Coverage includes concurrent requests, a deterministic blocked database insert,
expired-hold replacement, inactive history, confirmed reservations, input validation,
authentication, authorization and ownership. Tests run sequentially.

## Stop

Stop the application in IntelliJ or with Ctrl+C, then:

```powershell
docker compose stop
```

Database files remain in a named volume. Flyway applies migrations on application
startup; Hibernate uses `ddl-auto=validate` and does not modify the schema.

## Scope and next steps

There is no room catalog lookup yet. The public UI client and service client exist in
Keycloak, but neither is used by an application UI or another service yet. Room
catalog, messaging, idempotency, OpenFeign and gRPC are subsequent steps. Do not
expose this learning-stage API publicly.

## Configuration

Local application overrides: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and
`KEYCLOAK_ISSUER_URI`. Compose does not automatically pass environment variables to
an app launched separately from IntelliJ. For deployment, explicitly select another
`SPRING_PROFILES_ACTIVE` value and provide `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` and a production issuer.

## References

- [PostgreSQL partial unique indexes](https://www.postgresql.org/docs/17/indexes-partial.html)
- [Spring Boot Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
- [Spring Boot application properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html)
- [Docker Compose services](https://docs.docker.com/reference/compose-file/services/)
- [Keycloak server administration guide](https://www.keycloak.org/docs/latest/server_admin/)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
