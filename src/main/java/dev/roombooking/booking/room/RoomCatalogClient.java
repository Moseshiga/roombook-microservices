package dev.roombooking.booking.room;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "room-service",
        url = "${room-service.url:http://localhost:8082}",
        configuration = RoomCatalogClientConfiguration.class
)
public interface RoomCatalogClient {
    @GetMapping("/api/rooms/{roomId}")
    RoomCatalogResponse getActiveRoom(@PathVariable UUID roomId);
}
