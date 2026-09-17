package dev.roombooking.room.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Rooms", description = "Room catalog and administrator management")
class RoomController {
    private final RoomCatalogService roomCatalogService;

    RoomController(RoomCatalogService roomCatalogService) {
        this.roomCatalogService = roomCatalogService;
    }

    @GetMapping("/rooms")
    @Operation(summary = "List active rooms")
    List<RoomResponse> listActive() {
        return roomCatalogService.listActive();
    }

    @GetMapping("/rooms/{roomId}")
    @Operation(summary = "Get an active room")
    RoomResponse getActive(@PathVariable UUID roomId) {
        return roomCatalogService.getActive(roomId);
    }

    @PostMapping("/admin/rooms")
    @Operation(summary = "Create a room", description = "Requires the ADMIN realm role.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The room was created"),
            @ApiResponse(responseCode = "400", description = "The room definition is invalid"),
            @ApiResponse(responseCode = "403", description = "The caller lacks the ADMIN role")
    })
    ResponseEntity<RoomResponse> create(@Valid @RequestBody CreateRoomRequest request) {
        RoomResponse response = roomCatalogService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/rooms/{roomId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/admin/rooms/{roomId}")
    @Operation(summary = "Replace a room", description = "Updates room details and active status. Requires ADMIN.")
    RoomResponse update(@PathVariable UUID roomId, @Valid @RequestBody UpdateRoomRequest request) {
        return roomCatalogService.update(roomId, request);
    }
}
