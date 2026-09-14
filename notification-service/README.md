# RoomBook Notification Service

Consumes `booking.confirmed.v1` events from RabbitMQ and delegates them to a
notification adapter. The current adapter logs the intended notification so the
messaging path can be learned without an external email or SMS provider.

The service owns `notifications_db`. Before invoking the adapter, it atomically inserts
the event ID into `processed_messages`. PostgreSQL's primary key and
`ON CONFLICT DO NOTHING` allow only one transaction to claim an event, even when two
service instances receive duplicate deliveries concurrently. A duplicate is treated as
success and acknowledged without sending another notification. If the adapter throws,
the transaction rolls back the claim so a later DLQ replay or retry can try again.

The service owns the durable `notification.booking-confirmed.v1` queue and its binding
to the durable `roombook.events` topic exchange. Rejected messages are routed to
`notification.booking-confirmed.v1.dlq` through `roombook.dead-letter` instead of being
requeued forever.

Listener retry is enabled for handler failures. After the initial delivery, Spring AMQP
makes up to three in-process retries with exponential delays of one, two and four
seconds. Each failed call rolls back the `processed_messages` claim. A successful retry
commits the claim and the container acknowledges the original RabbitMQ delivery. When
all attempts fail, the default `RejectAndDontRequeueRecoverer` rejects the message and
the broker dead-letters it.

This stateless retry keeps the original delivery unacknowledged and occupies one
consumer thread during each delay. With local concurrency set to one, later messages
wait behind the failing message. If the process stops during retry, RabbitMQ requeues
the unacknowledged delivery and the in-memory retry count starts again in the next
consumer. Broker-based delayed retry queues are a possible future scaling improvement.

Run these commands from the repository root with Java 21:

```powershell
docker compose up -d postgres keycloak-database-init rabbitmq
.\mvnw.cmd -pl notification-service spring-boot:run `
  "-Dspring-boot.run.jvmArguments=-Xms64m -Xmx192m"
```

The service starts on `http://localhost:8083` and registers in Eureka as
`NOTIFICATION-SERVICE`. RabbitMQ Management is available on
`http://localhost:15672`; use `roombook` and the `RABBITMQ_PASSWORD` value from `.env`.
The local datasource is `jdbc:postgresql://localhost:5433/notifications_db` with the
same learning-only PostgreSQL credentials as booking-service. It is a separate logical
database in the existing container, so it does not require another PostgreSQL process.

The database transaction cannot be atomic with a real email or SMS provider. A provider
adapter should pass `eventId` as the provider's idempotency key when that feature is
available. Otherwise a later notification-dispatch outbox would be needed to close the
remaining database/external-side-effect failure window.
