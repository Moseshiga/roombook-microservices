package dev.roombooking.notification.booking;

public interface NotificationSender {
    void sendBookingConfirmation(BookingConfirmedMessage message);
}
