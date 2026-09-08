package dev.roombooking.room.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rooms")
class Room {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(nullable = false, length = 120)
    private String location;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Room() {
    }

    Room(UUID id, String name, String location, int capacity, boolean active, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.capacity = capacity;
        this.active = active;
        this.createdAt = createdAt;
    }

    void update(String name, String location, int capacity, boolean active) {
        this.name = name;
        this.location = location;
        this.capacity = capacity;
        this.active = active;
    }

    UUID getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getLocation() {
        return location;
    }

    int getCapacity() {
        return capacity;
    }

    boolean isActive() {
        return active;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
