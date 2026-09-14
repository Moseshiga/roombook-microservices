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
Booking creation accepts an idempotency key, so a client can safely repeat a request
after a timeout without creating a second reservation. Before sending a confirmation,
notification-service obtains its own Client Credentials token and reads the user's
contact preferences from profile-service through a deadline-bound gRPC call.

Stack: Java 21, Spring Boot 4.1, Spring MVC, Spring Security OAuth2 Resource Server,
Bean Validation, JPA, PostgreSQL 17, Flyway, Keycloak, Spring Cloud OpenFeign,
Eureka, Spring Cloud LoadBalancer, RabbitMQ, Protocol Buffers, gRPC and Actuator.
Integration tests use real PostgreSQL and RabbitMQ Testcontainers.

The repository is a Maven multi-module project. Its root POM contains shared Java,
Spring Boot and Spring Cloud versions. The protobuf contract is a shared build module;
the other modules are independently runnable applications:

```text
roombook-parent
├── profile-grpc-contract
├── booking-service
├── room-service
├── notification-service
├── profile-service
└── discovery-server
```

## Run locally (PowerShell)

Requirements: JDK 21 and Docker with a running Linux container engine. Maven is
provided by the checked-in wrapper. From the project directory:

```powershell
Copy-Item .env.example .env
docker compose up -d postgres keycloak rabbitmq
.\mvnw.cmd -DskipTests install
.\mvnw.cmd -pl discovery-server spring-boot:run
.\mvnw.cmd -pl room-service spring-boot:run
.\mvnw.cmd -pl profile-service spring-boot:run
.\mvnw.cmd -pl notification-service spring-boot:run
.\mvnw.cmd -pl booking-service spring-boot:run
```

Run these in separate terminals, or start `DiscoveryServerApplication`,
`RoomServiceApplication`, `ProfileServiceApplication`, `NotificationServiceApplication` and
`BookingServiceApplication` in IntelliJ in that order.

The default `local` profiles use separate logical databases in PostgreSQL:
`bookings_db`, `rooms_db`, `notifications_db` and `profiles_db`, with username
`booking` and password `booking_local`. Keycloak is available only on
`http://localhost:8081`; it has a separate `keycloak_db` in the same PostgreSQL
container. PostgreSQL, Keycloak and the HTTP server bind to loopback. PostgreSQL and
RabbitMQ are limited to 384 MiB each; Keycloak is limited to 768 MiB. Docker engine
memory is separate.

`.env` contains local-only secrets and is ignored by Git. Keep `.env.example` as a
template and replace its placeholders before the first start. The imported `roombook`
realm contains `USER`, `ADMIN` and internal `PROFILE_READ` roles, a public `roombook-ui` client for a future
browser UI (Authorization Code + PKCE), and a confidential `roombook-service` client
for service calls. The confidential `notification-service` client uses Client
Credentials; its service account alone receives `PROFILE_READ`. Open the [Keycloak Admin Console](http://localhost:8081/admin/)
and sign in with `KEYCLOAK_ADMIN_USERNAME` and `KEYCLOAK_ADMIN_PASSWORD` from `.env`.
It also imports the local users `alice` (USER) and `admin-user` (USER, ADMIN).

Keycloak's `--import-realm` skips a realm that already exists. If `roombook` was
created before the gRPC stage, either re-create that disposable local realm from the
updated JSON or add the following in the Admin Console: realm role `PROFILE_READ`,
confidential OIDC client `notification-service` with **Client authentication** and
**Service accounts roles** enabled, then assign `PROFILE_READ` to its service account.
Copy the client secret from the Credentials tab to
`NOTIFICATION_SERVICE_CLIENT_SECRET` in `.env`. Restart notification-service after a
secret change. Spring imports the root `.env` only in the local profile; it is ignored
by Git.

[`discovery-server`](discovery-server/README.md) runs the Eureka registry and dashboard
on `http://localhost:8761`. [`room-service`](room-service/README.md) owns the independent
`rooms_db` catalog and starts on port `8082`. Once the application services have
registered, the dashboard shows `BOOKING-SERVICE`, `ROOM-SERVICE` and
`PROFILE-SERVICE` and `NOTIFICATION-SERVICE`. Eureka advertises profile-service's HTTP
port; this learning stage configures its gRPC endpoint separately as
`localhost:9090`.

RabbitMQ accepts AMQP connections on `localhost:5672`. Its management UI is available
at [http://localhost:15672](http://localhost:15672); sign in as `roombook` with the
`RABBITMQ_PASSWORD` value from `.env`. If the variable is absent, Compose and the local
Spring profiles use the learning-only fallback `roombook_local`.

## API

| Method | Path | Result |
| --- | --- | --- |
| POST | `/api/bookings` | `201 Created`; an idempotent replay returns `200 OK` and the same reservation |
| GET | `/api/bookings/{id}` | `200 OK`, reservation JSON; `404` if absent |
| POST | `/api/bookings/{id}/confirm` | `200 OK` for a non-expired `PENDING` reservation |
| POST | `/api/bookings/{id}/cancel` | `200 OK` for a non-expired `PENDING` reservation |
| GET | `/api/profile` | Current user's notification profile |
| PUT | `/api/profile` | Create or replace current user's notification profile |
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

$headers = @{
    Authorization = "Bearer $accessToken"
    'Idempotency-Key' = [guid]::NewGuid().ToString()
}

Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/bookings' `
    -Headers $headers -ContentType 'application/json' -Body $body
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

`POST /api/bookings` requires a visible ASCII `Idempotency-Key` of at most 128
characters. The client creates one key for one logical booking operation and reuses it
for every retry of that operation. The first successful request returns `201`; a retry
by the same authenticated user with the same key, room and slot returns `200`, the same
`bookingId` and the same `Location`. Reusing that key with another room or slot returns
`409`. Different users have independent key namespaces.

The service first claims `(user_id, idempotency_key)` with PostgreSQL `INSERT ... ON
CONFLICT DO NOTHING`. The claim, room hold and link to the resulting booking share one
database transaction. Concurrent retries therefore wait on the database uniqueness
constraint and observe one committed result. If room validation or booking creation
fails, the claim rolls back too and does not leave a stuck key. The active-slot unique
index remains separate: it protects the room schedule, while the idempotency record
identifies a repeated client command.

This learning version retains successful idempotency records indefinitely. A production
API should publish and enforce a retention period as part of its contract; once a key
expires, the client can no longer rely on replaying it to recover the original result.

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
pair of hourly ShedLock-protected maintenance tasks deletes them in bounded batches.

Published outbox rows are retained for 30 days. The cleanup query never selects an
unpublished row and repeats that condition in the delete, so events awaiting delivery
are preserved regardless of age. Processed inbox rows are retained for 90 days. After
an inbox row is removed, replaying that old `eventId` can invoke the consumer again;
therefore the inbox retention period defines the supported deduplication and DLQ replay
horizon. Both tables have retention indexes, and each invocation deletes at most 500
rows to avoid a large long-running transaction. The policies are configured through
`booking.outbox.cleanup.*` and `notification.inbox.cleanup.*`.

## Profile lookup over gRPC

[`profile-grpc-contract/src/main/proto/user_profile.proto`](profile-grpc-contract/src/main/proto/user_profile.proto)
is the language-neutral contract. Maven invokes `protoc` and generates immutable
message classes plus Java client and server stubs. The single unary RPC accepts the
Keycloak user subject and returns email, locale and the notification preference.
Existing protobuf field numbers are stable wire identifiers and must not be reused.

profile-service implements the generated server base class and listens on plaintext
HTTP/2 at `127.0.0.1:9090` for local development. A global Spring gRPC security
interceptor extracts the Bearer token from request metadata, validates its JWT with the
same Keycloak issuer as HTTP security, and requires `ROLE_PROFILE_READ` for
`GetNotificationProfile`. Missing credentials produce gRPC `UNAUTHENTICATED`; valid
credentials without the role produce `PERMISSION_DENIED`. Application failures use
protocol statuses such as `INVALID_ARGUMENT` and `NOT_FOUND`.

notification-service registers an OAuth2 client with the `client_credentials` grant.
Its `OAuth2AuthorizedClientManager` obtains and reuses a service access token until it
must be refreshed. A `BearerTokenAuthenticationInterceptor` adds that token to gRPC
metadata for every call. The generated blocking stub serializes the request with
protobuf and invokes profile-service through a reusable channel. Every lookup applies
the configured `notification.profile.grpc.deadline` (`PT2S` by default), because gRPC
otherwise waits without a deadline. Transport statuses are translated to the
notification adapter's `ProfileLookupException`, which lets the existing RabbitMQ
retry and DLQ policy handle a temporary profile-service failure.

The gRPC lookup currently runs inside the notification inbox transaction. That keeps
the event claim rollback behavior easy to observe, but holds a database connection
while waiting on the network. The short deadline bounds this cost. A higher-throughput
production design would narrow the transaction or introduce a notification dispatch
outbox while preserving idempotency.

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
retry exhaustion. Dedicated gRPC tests use an in-process server to verify response
mapping and deadlines. profile-service tests start a real Netty gRPC server and prove
the `UNAUTHENTICATED`, `PERMISSION_DENIED`, `NOT_FOUND` and successful authorization
paths. Tests run sequentially.

## Stop

Stop the application in IntelliJ or with Ctrl+C, then:

```powershell
docker compose stop
```

Database files remain in a named volume. Flyway applies migrations on application
startup; Hibernate uses `ddl-auto=validate` and does not modify the schema.

## Scope and next steps

Room catalog is reached through OpenFeign and Eureka; profile data is reached through
protobuf/gRPC; booking events cross RabbitMQ. This deliberately demonstrates three
communication styles with concrete reasons for each. The next infrastructure stages
are observability, an edge proxy and container orchestration. Do not expose this
learning-stage API publicly.

## Configuration

Local application overrides: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and
`KEYCLOAK_ISSUER_URI`. `EUREKA_URL` changes the registry address and
`EUREKA_INSTANCE_HOSTNAME` changes the hostname advertised by an application service.
RabbitMQ overrides are `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME` and
`RABBITMQ_PASSWORD`. notification-service datasource overrides are
`NOTIFICATION_DB_URL`, `NOTIFICATION_DB_USERNAME` and `NOTIFICATION_DB_PASSWORD`.
profile-service datasource overrides are `PROFILE_DB_URL`, `PROFILE_DB_USERNAME` and
`PROFILE_DB_PASSWORD`. `NOTIFICATION_SERVICE_CLIENT_SECRET` configures its Keycloak
client, `KEYCLOAK_TOKEN_URI` changes the token endpoint, and
`notification.profile.grpc.deadline` bounds each lookup.
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
- [Spring Boot gRPC](https://docs.spring.io/spring-boot/reference/io/grpc.html)
- [gRPC Java basics](https://grpc.io/docs/languages/java/basics/)
- [gRPC deadlines](https://grpc.io/docs/guides/deadlines/)
