package dev.roombooking.room.catalog;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
class RoomController {
    private final RoomCatalogService roomCatalogService;

    RoomController(RoomCatalogService roomCatalogService) {
        this.roomCatalogService = roomCatalogService;
    }

    @GetMapping("/rooms")
    List<RoomResponse> listActive() {
        return roomCatalogService.listActive();
    }

    @GetMapping("/rooms/{roomId}")
    RoomResponse getActive(@PathVariable UUID roomId) {
        return roomCatalogService.getActive(roomId);
    }

    @PostMapping("/admin/rooms")
    ResponseEntity<RoomResponse> create(@Valid @RequestBody CreateRoomRequest request) {
        RoomResponse response = roomCatalogService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/rooms/{roomId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/admin/rooms/{roomId}")
    RoomResponse update(@PathVariable UUID roomId, @Valid @RequestBody UpdateRoomRequest request) {
        return roomCatalogService.update(roomId, request);
    }
}
