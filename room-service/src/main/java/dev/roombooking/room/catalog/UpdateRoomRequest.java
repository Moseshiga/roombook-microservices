package dev.roombooking.room.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

record UpdateRoomRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120) String location,
        @Positive int capacity,
        boolean active
) {
}
