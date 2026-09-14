package dev.roombooking.profile;

import dev.roombooking.profile.grpc.v1.GetNotificationProfileRequest;
import dev.roombooking.profile.grpc.v1.NotificationProfileResponse;
import dev.roombooking.profile.grpc.v1.UserProfileServiceGrpc;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.grpc.server.port=0",
        "server.port=0",
        "eureka.client.enabled=false"
})
@Import(ProfileGrpcSecurityIntegrationTests.JwtFixture.class)
@Testcontainers
class ProfileGrpcSecurityIntegrationTests {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static ManagedChannel channel;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalGrpcServerPort
    int grpcPort;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void prepareDatabaseAndChannel() throws Exception {
        jdbc.update("DELETE FROM user_profiles");
        jdbc.update("""
                INSERT INTO user_profiles (
                    user_id, email, locale, notifications_enabled, created_at, updated_at
                ) VALUES (?, ?, ?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz))
                """, "alice-subject", "alice@example.com", "en-US", true, NOW.toString(), NOW.toString());
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        channel = ManagedChannelBuilder.forAddress("127.0.0.1", grpcPort).usePlaintext().build();
    }

    @AfterAll
    static void closeChannel() throws Exception {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void serviceTokenWithProfileReadRoleCanCallGrpcMethod() {
        NotificationProfileResponse response = stubWithToken("service-token")
                .getNotificationProfile(request("alice-subject"));

        assertThat(response.getUserId()).isEqualTo("alice-subject");
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getNotificationsEnabled()).isTrue();
    }

    @Test
    void missingTokenIsUnauthenticated() {
        assertThatThrownBy(() -> UserProfileServiceGrpc.newBlockingStub(channel)
                .getNotificationProfile(request("alice-subject")))
                .isInstanceOfSatisfying(StatusRuntimeException.class, exception ->
                        assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
    }

    @Test
    void tokenWithoutRequiredRoleIsPermissionDenied() {
        assertThatThrownBy(() -> stubWithToken("user-token")
                .getNotificationProfile(request("alice-subject")))
                .isInstanceOfSatisfying(StatusRuntimeException.class, exception ->
                        assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED));
    }

    @Test
    void missingProfileUsesGrpcNotFoundStatus() {
        assertThatThrownBy(() -> stubWithToken("service-token")
                .getNotificationProfile(request("missing-user")))
                .isInstanceOfSatisfying(StatusRuntimeException.class, exception ->
                        assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND));
    }

    private UserProfileServiceGrpc.UserProfileServiceBlockingStub stubWithToken(String token) {
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
        return UserProfileServiceGrpc.newBlockingStub(ClientInterceptors.intercept(
                channel, MetadataUtils.newAttachHeadersInterceptor(headers)));
    }

    private GetNotificationProfileRequest request(String userId) {
        return GetNotificationProfileRequest.newBuilder().setUserId(userId).build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtFixture {
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            return token -> switch (token) {
                case "service-token" -> jwt(token, "service-account-notification-service", "PROFILE_READ");
                case "user-token" -> jwt(token, "alice-subject", "USER");
                default -> throw new BadJwtException("Unknown test token");
            };
        }

        private Jwt jwt(String token, String subject, String role) {
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject(subject)
                    .issuedAt(NOW.minusSeconds(60))
                    .expiresAt(NOW.plusSeconds(3600))
                    .claim("realm_access", Map.of("roles", List.of(role)))
                    .build();
        }
    }
}
