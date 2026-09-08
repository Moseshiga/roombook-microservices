package dev.roombooking.room.catalog;

import java.time.Instant;
import java.util.UUID;

record RoomResponse(
        UUID id,
        String name,
        String location,
        int capacity,
        boolean active,
        Instant createdAt
) {
    static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getId(), room.getName(), room.getLocation(), room.getCapacity(),
                room.isActive(), room.getCreatedAt());
    }
}
