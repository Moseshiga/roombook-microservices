# RoomBook Notification Service

Consumes `booking.confirmed.v1` events from RabbitMQ and delegates them to a
notification adapter. The current adapter logs the intended notification so the
messaging path can be learned without an external email or SMS provider.

The service owns the durable `notification.booking-confirmed.v1` queue and its binding
to the durable `roombook.events` topic exchange. Rejected messages are routed to
`notification.booking-confirmed.v1.dlq` through `roombook.dead-letter` instead of being
requeued forever.

Run these commands from the repository root with Java 21:

```powershell
docker compose up -d rabbitmq
.\mvnw.cmd -pl notification-service spring-boot:run `
  "-Dspring-boot.run.jvmArguments=-Xms64m -Xmx192m"
```

The service starts on `http://localhost:8083` and registers in Eureka as
`NOTIFICATION-SERVICE`. RabbitMQ Management is available on
`http://localhost:15672`; use `roombook` and the `RABBITMQ_PASSWORD` value from `.env`.
