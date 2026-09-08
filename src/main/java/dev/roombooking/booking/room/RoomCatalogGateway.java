package dev.roombooking.booking.room;

import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class RoomCatalogGateway {
    private final RoomCatalogClient roomCatalogClient;

    public RoomCatalogGateway(RoomCatalogClient roomCatalogClient) {
        this.roomCatalogClient = roomCatalogClient;
    }

    public void requireActiveRoom(UUID roomId) {
        try {
            RoomCatalogResponse room = roomCatalogClient.getActiveRoom(roomId);
            if (!roomId.equals(room.id()) || !room.active()) {
                throw roomUnavailable(roomId);
            }
        } catch (FeignException.NotFound exception) {
            throw roomUnavailable(roomId);
        } catch (FeignException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Room catalog is unavailable", exception);
        }
    }

    private ResponseStatusException roomUnavailable(UUID roomId) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Room %s does not exist or is inactive".formatted(roomId));
    }
}
