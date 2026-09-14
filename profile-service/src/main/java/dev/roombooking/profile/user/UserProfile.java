package dev.roombooking.profile.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_profiles")
class UserProfile {
    @Id
    @Column(length = 255)
    private String userId;
    @Column(nullable = false, length = 320)
    private String email;
    @Column(nullable = false, length = 35)
    private String locale;
    @Column(nullable = false)
    private boolean notificationsEnabled;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected UserProfile() {
    }

    UserProfile(String userId, String email, String locale, boolean notificationsEnabled, Instant now) {
        this.userId = userId;
        this.email = email;
        this.locale = locale;
        this.notificationsEnabled = notificationsEnabled;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void update(String email, String locale, boolean notificationsEnabled, Instant now) {
        this.email = email;
        this.locale = locale;
        this.notificationsEnabled = notificationsEnabled;
        this.updatedAt = now;
    }

    String getUserId() { return userId; }
    String getEmail() { return email; }
    String getLocale() { return locale; }
    boolean isNotificationsEnabled() { return notificationsEnabled; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
