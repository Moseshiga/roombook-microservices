package dev.roombooking.profile.user;

import dev.roombooking.profile.grpc.v1.GetNotificationProfileRequest;
import dev.roombooking.profile.grpc.v1.NotificationProfileResponse;
import dev.roombooking.profile.grpc.v1.UserProfileServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
class UserProfileGrpcService extends UserProfileServiceGrpc.UserProfileServiceImplBase {
    private final UserProfileService service;

    UserProfileGrpcService(UserProfileService service) {
        this.service = service;
    }

    @Override
    public void getNotificationProfile(GetNotificationProfileRequest request,
                                       StreamObserver<NotificationProfileResponse> responseObserver) {
        if (request.getUserId().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("user_id must not be blank")
                    .asRuntimeException());
            return;
        }

        service.find(request.getUserId()).ifPresentOrElse(profile -> {
            responseObserver.onNext(NotificationProfileResponse.newBuilder()
                    .setUserId(profile.userId())
                    .setEmail(profile.email())
                    .setLocale(profile.locale())
                    .setNotificationsEnabled(profile.notificationsEnabled())
                    .build());
            responseObserver.onCompleted();
        }, () -> responseObserver.onError(Status.NOT_FOUND
                .withDescription("User profile not found")
                .asRuntimeException()));
    }
}
