# RoomBook: Room Service

The room catalog owns room names, locations, capacities and activation state. It has
its own `rooms_db` database and never reads the booking-service schema.

## API

All endpoints use the `roombook` Keycloak realm.

| Method | Path | Access |
| --- | --- | --- |
| GET | `/api/rooms` | USER or ADMIN |
| GET | `/api/rooms/{id}` | USER or ADMIN |
| POST | `/api/admin/rooms` | ADMIN |
| PUT | `/api/admin/rooms/{id}` | ADMIN |
| GET | `/actuator/health` | public |

Run from the repository root after starting the Compose infrastructure:

```powershell
.\mvnw.cmd -f room-service\pom.xml spring-boot:run
```

The local profile uses `localhost:5433/rooms_db` and starts on port `8082`.
