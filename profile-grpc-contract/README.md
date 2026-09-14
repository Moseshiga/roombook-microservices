# RoomBook profile gRPC contract

`user_profile.proto` is the versioned language-neutral contract shared by the gRPC
server in profile-service and the client in notification-service. Maven generates
protobuf message classes and Java client/server stubs under `target/generated-sources`.

Field numbers are wire identifiers. Once published, an existing number must never be
reused for another meaning; removed fields should be marked `reserved` in the proto.
