package dev.roombooking.room.catalog;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
class RoomCatalogService {
    private final RoomRepository roomRepository;
    private final Clock clock;

    RoomCatalogService(RoomRepository roomRepository, Clock clock) {
        this.roomRepository = roomRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    List<RoomResponse> listActive() {
        return roomRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(RoomResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    RoomResponse getActive(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .filter(Room::isActive)
                .orElseThrow(() -> notFound(roomId));
        return RoomResponse.from(room);
    }

    @Transactional
    RoomResponse create(CreateRoomRequest request) {
        Room room = new Room(
                UUID.randomUUID(), request.name().trim(), request.location().trim(),
                request.capacity(), true, clock.instant());
        try {
            return RoomResponse.from(roomRepository.saveAndFlush(room));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A room with this name already exists");
        }
    }

    @Transactional
    RoomResponse update(UUID roomId, UpdateRoomRequest request) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> notFound(roomId));
        room.update(request.name().trim(), request.location().trim(), request.capacity(), request.active());
        try {
            return RoomResponse.from(roomRepository.saveAndFlush(room));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A room with this name already exists");
        }
    }

    private ResponseStatusException notFound(UUID roomId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Room %s was not found".formatted(roomId));
    }
}
