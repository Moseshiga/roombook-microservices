package dev.roombooking.notification.profile;

import dev.roombooking.profile.grpc.v1.GetNotificationProfileRequest;
import dev.roombooking.profile.grpc.v1.NotificationProfileResponse;
import dev.roombooking.profile.grpc.v1.UserProfileServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
class GrpcNotificationProfileGateway implements NotificationProfileGateway {
    private final UserProfileServiceGrpc.UserProfileServiceBlockingStub profileStub;
    private final Duration deadline;

    GrpcNotificationProfileGateway(UserProfileServiceGrpc.UserProfileServiceBlockingStub profileStub,
                                   @Value("${notification.profile.grpc.deadline:PT2S}") Duration deadline) {
        this.profileStub = profileStub;
        this.deadline = deadline;
    }

    @Override
    public NotificationProfile getRequired(String userId) {
        try {
            NotificationProfileResponse response = profileStub
                    .withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS)
                    .getNotificationProfile(GetNotificationProfileRequest.newBuilder().setUserId(userId).build());
            if (!userId.equals(response.getUserId())) {
                throw new ProfileLookupException("Profile service returned another user", null);
            }
            return new NotificationProfile(response.getUserId(), response.getEmail(), response.getLocale(),
                    response.getNotificationsEnabled());
        } catch (StatusRuntimeException exception) {
            Status.Code code = exception.getStatus().getCode();
            String message = switch (code) {
                case NOT_FOUND -> "Notification profile not found for user " + userId;
                case DEADLINE_EXCEEDED -> "Profile service deadline exceeded";
                case UNAVAILABLE -> "Profile service is unavailable";
                case UNAUTHENTICATED -> "Profile service rejected the service token";
                case PERMISSION_DENIED -> "Notification service may not read profiles";
                default -> "Profile lookup failed with gRPC status " + code;
            };
            throw new ProfileLookupException(message, exception);
        }
    }
}
