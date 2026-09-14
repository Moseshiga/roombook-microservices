package dev.roombooking.notification.profile;

import dev.roombooking.profile.grpc.v1.GetNotificationProfileRequest;
import dev.roombooking.profile.grpc.v1.NotificationProfileResponse;
import dev.roombooking.profile.grpc.v1.UserProfileServiceGrpc;
import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrpcNotificationProfileGatewayTests {
    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void shutdownGrpc() throws Exception {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void mapsSuccessfulProtobufResponseToDomainObject() throws Exception {
        var gateway = gateway(new UserProfileServiceGrpc.UserProfileServiceImplBase() {
            @Override
            public void getNotificationProfile(GetNotificationProfileRequest request,
                                               StreamObserver<NotificationProfileResponse> observer) {
                observer.onNext(NotificationProfileResponse.newBuilder()
                        .setUserId(request.getUserId())
                        .setEmail("alice@example.com")
                        .setLocale("en-US")
                        .setNotificationsEnabled(true)
                        .build());
                observer.onCompleted();
            }
        }, Duration.ofSeconds(1));

        assertThat(gateway.getRequired("alice-subject"))
                .isEqualTo(new NotificationProfile("alice-subject", "alice@example.com", "en-US", true));
    }

    @Test
    void translatesGrpcNotFoundStatus() throws Exception {
        var gateway = gateway(new UserProfileServiceGrpc.UserProfileServiceImplBase() {
            @Override
            public void getNotificationProfile(GetNotificationProfileRequest request,
                                               StreamObserver<NotificationProfileResponse> observer) {
                observer.onError(Status.NOT_FOUND.withDescription("missing").asRuntimeException());
            }
        }, Duration.ofSeconds(1));

        assertThatThrownBy(() -> gateway.getRequired("missing-user"))
                .isInstanceOf(ProfileLookupException.class)
                .hasMessage("Notification profile not found for user missing-user")
                .hasCauseInstanceOf(io.grpc.StatusRuntimeException.class);
    }

    @Test
    void stopsWaitingWhenDeadlineExpires() throws Exception {
        var gateway = gateway(new UserProfileServiceGrpc.UserProfileServiceImplBase() {
            @Override
            public void getNotificationProfile(GetNotificationProfileRequest request,
                                               StreamObserver<NotificationProfileResponse> observer) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }, Duration.ofMillis(30));

        assertThatThrownBy(() -> gateway.getRequired("slow-user"))
                .isInstanceOf(ProfileLookupException.class)
                .hasMessage("Profile service deadline exceeded");
    }

    private GrpcNotificationProfileGateway gateway(BindableService service, Duration deadline) throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName).addService(service).build().start();
        channel = InProcessChannelBuilder.forName(serverName).build();
        return new GrpcNotificationProfileGateway(
                UserProfileServiceGrpc.newBlockingStub(channel), deadline);
    }
}
