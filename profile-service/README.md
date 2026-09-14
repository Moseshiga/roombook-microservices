# RoomBook: Profile Service

Profile-service owns notification contact data and preferences in `profiles_db`. Users
manage their own profile through REST on port `8084`. Notification-service reads a
profile through the internal protobuf/gRPC API on port `9090`.

| Protocol | Method | Access |
| --- | --- | --- |
| HTTP | `GET /api/profile` | USER or ADMIN, own JWT subject |
| HTTP | `PUT /api/profile` | USER or ADMIN, own JWT subject |
| gRPC | `roombook.profile.v1.UserProfileService/GetNotificationProfile` | Client Credentials token with PROFILE_READ |

The gRPC server uses plaintext HTTP/2 on loopback for local development. Production
deployment must use TLS or a trusted service-mesh transport in addition to OAuth2.
The REST API derives `userId` from the human user's JWT subject. The gRPC API accepts
that subject in the protobuf request because the authenticated caller is the
notification-service service account, not the human user.
