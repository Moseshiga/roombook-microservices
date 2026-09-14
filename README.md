# RoomBook Microservices

A learning project focused on reliable reservations and microservice communication.

## Current stage

Create a temporary room reservation and retrieve it by ID. PostgreSQL protects each
hourly slot against concurrent reservations, including requests from different JVMs.
Keycloak authenticates callers; the booking owner comes from the JWT `sub` claim.
Before creating a hold, booking-service finds room-service through Eureka and verifies
the requested room through an OpenFeign HTTP call. A successful confirmation stores a
versioned integration event in a transactional outbox. A scheduled publisher forwards
the event to RabbitMQ with retries; notification-service consumes it asynchronously.

Stack: Java 21, Spring Boot 4.1, Spring MVC, Spring Security OAuth2 Resource Server,
Bean Validation, JPA, PostgreSQL 17, Flyway, Keycloak, Spring Cloud OpenFeign,
Eureka, Spring Cloud LoadBalancer, RabbitMQ and Actuator. Integration tests use a real
PostgreSQL Testcontainer.

The repository is a Maven multi-module project. Its root POM contains shared Java,
Spring Boot and Spring Cloud versions and aggregates four independently runnable
applications:

```text
roombook-parent
├── booking-service
├── room-service
├── notification-service
└── discovery-server
```

## Run locally (PowerShell)

Requirements: JDK 21 and Docker with a running Linux container engine. Maven is
provided by the checked-in wrapper. From the project directory:

```powershell
Copy-Item .env.example .env
docker compose up -d postgres keycloak rabbitmq
.\mvnw.cmd -pl discovery-server spring-boot:run
.\mvnw.cmd -pl room-service spring-boot:run
.\mvnw.cmd -pl notification-service spring-boot:run `
  "-Dspring-boot.run.jvmArguments=-Xms64m -Xmx192m"
.\mvnw.cmd -pl booking-service spring-boot:run `
  "-Dspring-boot.run.jvmArguments=-Xms64m -Xmx256m"
```

Run these in separate terminals, or start `DiscoveryServerApplication`,
`RoomServiceApplication`, `NotificationServiceApplication` and
`BookingServiceApplication` in IntelliJ in that order.
For the application services, VM options `-Xms64m -Xmx256m` are sufficient locally.
Heap limits do not limit the entire JVM process.

The default `local` profile connects to `localhost:5433/bookings_db`, username
`booking`, password `booking_local`. Keycloak is available only on
`http://localhost:8081`; it has a separate `keycloak_db` in the same PostgreSQL
container. PostgreSQL, Keycloak and the HTTP server bind to loopback. PostgreSQL and
RabbitMQ are limited to 384 MiB each; Keycloak is limited to 768 MiB. Docker engine
memory is separate.

`.env` contains local-only secrets and is ignored by Git. Keep `.env.example` as a
template and replace its placeholders before the first start. The imported `roombook`
realm contains `USER` and `ADMIN` roles, a public `roombook-ui` client for a future
browser UI (Authorization Code + PKCE), and a confidential `roombook-service` client
for future service-to-service calls. Open the [Keycloak Admin Console](http://localhost:8081/admin/)
and sign in with `KEYCLOAK_ADMIN_USERNAME` and `KEYCLOAK_ADMIN_PASSWORD` from `.env`.
It also imports the local users `alice` (USER) and `admin-user` (USER, ADMIN).

[`discovery-server`](discovery-server/README.md) runs the Eureka registry and dashboard
on `http://localhost:8761`. [`room-service`](room-service/README.md) owns the independent
`rooms_db` catalog and starts on port `8082`. Once the application services have
registered, the dashboard shows `BOOKING-SERVICE`, `ROOM-SERVICE` and
`NOTIFICATION-SERVICE`.

RabbitMQ accepts AMQP connections on `localhost:5672`. Its management UI is available
at [http://localhost:15672](http://localhost:15672); sign in as `roombook` with the
`RABBITMQ_PASSWORD` value from `.env`. If the variable is absent, Compose and the local
Spring profiles use the learning-only fallback `roombook_local`.

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
- A requested room must exist and be active in room-service; otherwise creation returns
  `400`. booking-service relays the caller's Bearer token to this internal request.
- The owner may read, confirm and cancel their booking. An ADMIN may perform these
  operations on any booking; a different USER receives `403`.
- An occupied slot returns `409` with an `application/problem+json` response.
- A hold lasts ten minutes, capped at the slot start if it is less than ten minutes away.
- Confirmation and cancellation are competing atomic state transitions. Only an
  unexpired `PENDING` booking can change; all other states return `409`.
- Retrying an already successful POST currently returns `409`; idempotency keys are
  a separate future feature. The API does not yet recover a lost successful response.

## Transactional outbox and booking confirmation event

`BookingService.confirm` performs the guarded `PENDING -> CONFIRMED` update and inserts
an `outbox_events` row through the same transaction-bound datasource. PostgreSQL either
commits both changes or rolls both back. The request does not contact RabbitMQ.

The outbox row contains the `booking.confirmed.v1` event type, aggregate ID, occurrence
time, retry metadata and a JSON payload. The payload is the public integration contract:
it contains a unique `eventId`, booking and room IDs, the Keycloak subject and the
one-hour interval. It contains no access token or other authentication credentials.

`OutboxMessagePublisher` polls up to 50 due rows every five seconds. ShedLock permits
only one booking-service instance to run the publisher at a time. For each row it sends
the JSON contract to the durable `roombook.events` topic exchange using the event type
as routing key. Publisher confirms and mandatory returns are enabled; `published_at` is
set only after RabbitMQ acknowledges the message and confirms that it was routable.
Failures retain the row and move `next_attempt_at` using bounded exponential backoff.

notification-service owns the durable `notification.booking-confirmed.v1` queue,
binding and a separate `notifications_db`. Its `@RabbitListener` deserializes the JSON
into its own copy of the contract. A transactional handler atomically claims `eventId`
in `processed_messages` before invoking a replaceable `NotificationSender`; the current
adapter writes a structured log entry. The primary key makes duplicate delivery a
successful no-op, including when multiple consumer instances race for the same event.
If handling fails, the database claim rolls back. The consumer exception then rejects
the transaction and Spring AMQP retries the listener up to three times with exponential
backoff. A successful retry commits the claim and acknowledges the original delivery.
After the attempts are exhausted, `RejectAndDontRequeueRecoverer` rejects the delivery
and RabbitMQ routes it to `notification.booking-confirmed.v1.dlq`, preventing an invalid
message from forming an endless hot loop. These retries run in the consumer process;
the delivery stays unacknowledged and the listener thread waits during backoff.

The outbox closes the loss window between the PostgreSQL commit and RabbitMQ publish.
It deliberately provides at-least-once rather than exactly-once delivery: the process
can stop after RabbitMQ accepts a message but before `published_at` is stored, so the
same `eventId` may be published again. The notification inbox makes those repeated
deliveries safe for the current transactional logging adapter. A real email or SMS call
still needs provider-side idempotency keyed by `eventId`, or its own dispatch outbox,
because an external side effect and the local database commit cannot form one atomic
transaction. Published outbox and processed inbox rows are retained for inspection; a
later maintenance task will archive or delete them according to a retention policy.

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
authentication, authorization and ownership. The booking integration test verifies that
a successful confirmation creates an unpublished outbox row. Focused publisher tests
verify ACK and NACK handling. notification-service verifies duplicate suppression and
rollback of an inbox claim against a real PostgreSQL Testcontainer. Its RabbitMQ
integration tests verify recovery after transient failures and dead-letter routing after
retry exhaustion. Tests run sequentially.

## Stop

Stop the application in IntelliJ or with Ctrl+C, then:

```powershell
docker compose stop
```

Database files remain in a named volume. Flyway applies migrations on application
startup; Hibernate uses `ddl-auto=validate` and does not modify the schema.

## Scope and next steps

Room catalog is a separate service reached through OpenFeign. The client supplies only
the logical service ID `room-service`; Eureka resolves healthy instances and Spring
Cloud LoadBalancer chooses one. The public UI client and service client exist in
Keycloak, but neither is used by an application UI or another service yet. Outbox/inbox
retention and gRPC are subsequent steps. Do not expose this learning-stage API publicly.

## Configuration

Local application overrides: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and
`KEYCLOAK_ISSUER_URI`. `EUREKA_URL` changes the registry address and
`EUREKA_INSTANCE_HOSTNAME` changes the hostname advertised by an application service.
RabbitMQ overrides are `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME` and
`RABBITMQ_PASSWORD`. notification-service datasource overrides are
`NOTIFICATION_DB_URL`, `NOTIFICATION_DB_USERNAME` and `NOTIFICATION_DB_PASSWORD`.
Compose does not automatically pass environment variables to an app launched separately
from IntelliJ. For deployment, explicitly select another
`SPRING_PROFILES_ACTIVE` value and provide `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` and a production issuer.

## References

- [PostgreSQL partial unique indexes](https://www.postgresql.org/docs/17/indexes-partial.html)
- [Spring Boot Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
- [Spring Boot application properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html)
- [Docker Compose services](https://docs.docker.com/reference/compose-file/services/)
- [Keycloak server administration guide](https://www.keycloak.org/docs/latest/server_admin/)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Spring Cloud OpenFeign](https://docs.spring.io/spring-cloud-openfeign/reference/)
- [Spring Cloud Netflix Eureka](https://docs.spring.io/spring-cloud-netflix/reference/)
- [Spring Boot AMQP](https://docs.spring.io/spring-boot/reference/messaging/amqp.html)
- [Spring transaction-bound events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
