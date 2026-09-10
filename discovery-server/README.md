# Discovery Server

Eureka registry for local development. Start this application before `room-service`
and `booking-service`, then open `http://localhost:8761` to inspect registered instances.

The server runs in standalone mode: it neither registers itself nor fetches a registry
from another Eureka peer.
