package dev.roombooking.notification.booking;

import dev.roombooking.notification.profile.NotificationProfile;

public interface NotificationSender {
    void sendBookingConfirmation(BookingConfirmedMessage message, NotificationProfile profile);
}
