package dev.roombooking.notification.profile;

public record NotificationProfile(String userId, String email, String locale, boolean notificationsEnabled) {
}
