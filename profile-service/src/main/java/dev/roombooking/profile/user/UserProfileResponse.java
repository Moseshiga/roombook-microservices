package dev.roombooking.profile.user;

import java.time.Instant;

record UserProfileResponse(String userId, String email, String locale, boolean notificationsEnabled,
                           Instant createdAt, Instant updatedAt) {
    static UserProfileResponse from(UserProfile profile) {
        return new UserProfileResponse(profile.getUserId(), profile.getEmail(), profile.getLocale(),
                profile.isNotificationsEnabled(), profile.getCreatedAt(), profile.getUpdatedAt());
    }
}
