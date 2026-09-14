package dev.roombooking.booking;

import dev.roombooking.booking.reservation.BookingService;
import dev.roombooking.booking.reservation.BookingConfirmedEvent;
import dev.roombooking.booking.reservation.BookingConfirmedEventStore;
import dev.roombooking.booking.messaging.OutboxRetentionService;
import dev.roombooking.booking.room.RoomCatalogClient;
import dev.roombooking.booking.room.RoomCatalogResponse;
import feign.FeignException;
import feign.Request;
import feign.Response;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "booking.expiration.cleanup.enabled=false",
                "booking.outbox.publisher.enabled=false",
                "booking.outbox.cleanup.enabled=false",
                "eureka.client.enabled=false"
        })
@ActiveProfiles("test")
@Import(BookingServiceApplicationTests.TimeFixture.class)
@Testcontainers
class BookingServiceApplicationTests {
    private static final Instant NOW = Instant.parse("2030-01-01T10:55:00Z");
    private static final String SLOT = "2030-01-02T12:00:00Z";
    private static final String ALICE_SUBJECT = "alice-subject";
    private static final String BOB_SUBJECT = "bob-subject";
    private static final UUID ROOM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(384L * 1024 * 1024));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired BookingService bookingService;
    @Autowired BookingConfirmedEventStore bookingConfirmedEventStore;
    @Autowired OutboxRetentionService outboxRetentionService;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired LockProvider lockProvider;
    @MockitoBean RoomCatalogClient roomCatalogClient;
    @MockitoBean RabbitTemplate rabbitTemplate;

    @BeforeEach
    void clearTestDatabase() {
        jdbc.update("DELETE FROM outbox_events");
        jdbc.update("DELETE FROM bookings");
        given(roomCatalogClient.getActiveRoom(any())).willAnswer(invocation -> {
            UUID roomId = invocation.getArgument(0);
            return new RoomCatalogResponse(roomId, true);
        });
    }

    @AfterAll
    static void closeClient() {
        HTTP.close();
    }

    @Test
    void createsAndReadsTenMinuteHold() throws Exception {
        var response = post(body(ROOM, UUID.randomUUID(), SLOT));
        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode booking = mapper.readTree(response.body());
        assertThat(booking.get("status").asText()).isEqualTo("PENDING");
        assertThat(booking.get("userId").asText()).isEqualTo(ALICE_SUBJECT);
        assertThat(booking.get("createdAt").asText()).isEqualTo(NOW.toString());
        assertThat(booking.get("expiresAt").asText()).isEqualTo("2030-01-01T11:05:00Z");
        assertThat(booking.get("slotEnd").asText()).isEqualTo("2030-01-02T13:00:00Z");
        String location = response.headers().firstValue("Location").orElseThrow();
        assertThat(location).isEqualTo("/api/bookings/" + booking.get("id").asText());
        var read = get(location);
        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(read.body())).isEqualTo(booking);
    }

    @Test
    void requiresBearerTokenAndUserOrAdminRole() throws Exception {
        assertThat(postAs(null, body(ROOM, SLOT)).statusCode()).isEqualTo(401);
        assertThat(postAs("guest-token", body(ROOM, SLOT)).statusCode()).isEqualTo(403);
        assertThat(postAs("alice-token", body(ROOM, SLOT)).statusCode()).isEqualTo(201);
    }

    @Test
    void rejectsBookingForARoomThatTheCatalogDoesNotExpose() throws Exception {
        UUID missingRoom = UUID.randomUUID();
        given(roomCatalogClient.getActiveRoom(missingRoom)).willThrow(FeignException.errorStatus(
                "RoomCatalogClient#getActiveRoom",
                Response.builder()
                        .status(404)
                        .reason("Not Found")
                        .request(Request.create(Request.HttpMethod.GET, "http://room-service/api/rooms/" + missingRoom,
                                Map.of(), null, StandardCharsets.UTF_8, null))
                        .build()));

        assertThat(post(body(missingRoom, UUID.randomUUID(), SLOT)).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings", Integer.class)).isZero();
    }

    @Test
    void onlyOwnerOrAdministratorCanReadAndChangeBooking() throws Exception {
        UUID bookingId = seedForUser(ROOM, BOB_SUBJECT, "PENDING", "2030-01-01T11:05:00Z");
        assertThat(getAs("alice-token", "/api/bookings/" + bookingId).statusCode()).isEqualTo(403);
        assertThat(postWithoutBody("/api/bookings/" + bookingId + "/cancel", "alice-token").statusCode())
                .isEqualTo(403);
        assertThat(getAs("admin-token", "/api/bookings/" + bookingId).statusCode()).isEqualTo(200);
        assertThat(postWithoutBody("/api/bookings/" + bookingId + "/cancel", "admin-token").statusCode())
                .isEqualTo(200);
    }

    @Test
    void confirmsPendingBookingAndPreventsLaterCancellation() throws Exception {
        UUID id = createBooking();
        var confirmation = postWithoutBody("/api/bookings/" + id + "/confirm");
        assertThat(confirmation.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(confirmation.body()).get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(postWithoutBody("/api/bookings/" + id + "/cancel").statusCode()).isEqualTo(409);
        assertThat(mapper.readTree(get("/api/bookings/" + id).body()).get("status").asText())
                .isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT aggregate_id FROM outbox_events", UUID.class)).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT event_type FROM outbox_events", String.class))
                .isEqualTo("booking.confirmed.v1");
        assertThat(jdbc.queryForObject("SELECT published_at IS NULL FROM outbox_events", Boolean.class)).isTrue();
        JsonNode payload = mapper.readTree(
                jdbc.queryForObject("SELECT payload::text FROM outbox_events", String.class));
        assertThat(payload.get("bookingId").asText()).isEqualTo(id.toString());
        assertThat(payload.get("eventId").asText()).isNotBlank();
    }

    @Test
    void rollsBackOutboxEventTogetherWithTheBusinessTransaction() {
        var event = new BookingConfirmedEvent(
                UUID.randomUUID(), NOW, UUID.randomUUID(), ROOM, ALICE_SUBJECT,
                Instant.parse(SLOT), Instant.parse("2030-01-02T13:00:00Z"));
        var transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            bookingConfirmedEventStore.append(event);
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isZero();
    }

    @Test
    void outboxRetentionDeletesOnlyPublishedEventsStrictlyBeforeTheCutoffInBatches() {
        Instant cutoff = NOW.minus(Duration.ofDays(30));
        UUID firstExpired = insertOutboxEvent(cutoff.minusSeconds(2), cutoff.minusSeconds(2));
        UUID secondExpired = insertOutboxEvent(cutoff.minusSeconds(1), cutoff.minusSeconds(1));
        UUID exactlyAtCutoff = insertOutboxEvent(cutoff.minusSeconds(10), cutoff);
        UUID recent = insertOutboxEvent(cutoff, NOW.minus(Duration.ofDays(1)));
        UUID unpublished = insertOutboxEvent(cutoff.minus(Duration.ofDays(1)), null);

        assertThat(outboxRetentionService.deletePublishedBefore(cutoff, 1)).isOne();
        assertThat(outboxRetentionService.deletePublishedBefore(cutoff, 1)).isOne();
        assertThat(outboxRetentionService.deletePublishedBefore(cutoff, 1)).isZero();

        assertThat(outboxEventExists(firstExpired)).isFalse();
        assertThat(outboxEventExists(secondExpired)).isFalse();
        assertThat(outboxEventExists(exactlyAtCutoff)).isTrue();
        assertThat(outboxEventExists(recent)).isTrue();
        assertThat(outboxEventExists(unpublished)).isTrue();
    }

    @Test
    void cancelsPendingBookingAndReleasesItsSlot() throws Exception {
        UUID id = createBooking();
        var cancellation = postWithoutBody("/api/bookings/" + id + "/cancel");
        assertThat(cancellation.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(cancellation.body()).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(post(body(ROOM, UUID.randomUUID(), SLOT)).statusCode()).isEqualTo(201);
        assertThat(postWithoutBody("/api/bookings/" + id + "/confirm").statusCode()).isEqualTo(409);
    }

    @Test
    void concurrentConfirmationAndCancellationHaveOneWinner() throws Exception {
        UUID id = createBooking();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var confirmation = workers.submit(() -> transitionAfterStart(ready, start, id, "confirm"));
            var cancellation = workers.submit(() -> transitionAfterStart(ready, start, id, "cancel"));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(new Integer[]{confirmation.get(10, TimeUnit.SECONDS), cancellation.get(10, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(200, 409);
        }
        String finalStatus = jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, id);
        assertThat(finalStatus).isIn("CONFIRMED", "CANCELLED");
    }

    @Test
    void cannotConfirmOrCancelExpiredHold() throws Exception {
        UUID id = seed("PENDING", NOW.toString());
        assertThat(postWithoutBody("/api/bookings/" + id + "/confirm").statusCode()).isEqualTo(409);
        assertThat(postWithoutBody("/api/bookings/" + id + "/cancel").statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, id))
                .isEqualTo("PENDING");
    }

    @Test
    void cleanupExpiresEveryOverduePendingBookingButLeavesOthersUntouched() {
        UUID expired = seed("PENDING", NOW.toString());
        UUID active = seedForRoom(UUID.randomUUID(), "PENDING", "2030-01-01T11:05:00Z");
        UUID confirmed = seedForRoom(UUID.randomUUID(), "CONFIRMED", NOW.toString());

        assertThat(bookingService.expireOverduePending()).isEqualTo(1);
        assertThat(statusOf(expired)).isEqualTo("EXPIRED");
        assertThat(statusOf(active)).isEqualTo("PENDING");
        assertThat(statusOf(confirmed)).isEqualTo("CONFIRMED");
        assertThat(bookingService.expireOverduePending()).isZero();
    }

    @Test
    void databaseLockAllowsOnlyOneInstanceToHoldTheSameTaskLock() {
        String lockName = "test-expire-overdue-bookings";
        LockConfiguration configuration = new LockConfiguration(
                Instant.now(), lockName, Duration.ofMinutes(1), Duration.ZERO);
        var otherInstanceProvider = new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());

        var firstLock = lockProvider.lock(configuration);
        assertThat(firstLock).isPresent();
        assertThat(otherInstanceProvider.lock(configuration)).isEmpty();

        firstLock.orElseThrow().unlock();
        assertThat(otherInstanceProvider.lock(configuration)).isPresent();
    }

    @Test
    void transitionsMissingBookingReturn404() throws Exception {
        UUID id = UUID.randomUUID();
        assertThat(postWithoutBody("/api/bookings/" + id + "/confirm").statusCode()).isEqualTo(404);
        assertThat(postWithoutBody("/api/bookings/" + id + "/cancel").statusCode()).isEqualTo(404);
    }

    @Test
    void holdNeverExtendsBeyondSlotStart() throws Exception {
        var response = post(body(ROOM, UUID.randomUUID(), "2030-01-01T11:00:00Z"));
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(mapper.readTree(response.body()).get("expiresAt").asText())
                .isEqualTo("2030-01-01T11:00:00Z");
    }

    @Test
    void simultaneousHttpRequestsHaveExactlyOneWinner() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return post(body(ROOM, UUID.randomUUID(), SLOT)).statusCode();
            });
            var second = workers.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return post(body(ROOM, UUID.randomUUID(), SLOT)).statusCode();
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(new Integer[]{first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings", Integer.class)).isEqualTo(1);
    }

    @Test
    void databaseBlocksCompetingInsertUntilFirstTransactionCommits() throws Exception {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO bookings VALUES (
                        gen_random_uuid(), '11111111-1111-1111-1111-111111111111', gen_random_uuid(),
                        '2030-01-02T12:00:00Z', 'PENDING', '2030-01-01T10:55:00Z', '2030-01-01T11:05:00Z')
                        """);
            }
            var competing = HTTP.sendAsync(postRequest(body(ROOM, UUID.randomUUID(), SLOT)),
                    HttpResponse.BodyHandlers.ofString());
            boolean waitingForLock = false;
            for (int attempt = 0; attempt < 100; attempt++) {
                waitingForLock = Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM pg_stat_activity
                        WHERE datname = current_database() AND wait_event_type = 'Lock'
                        AND query LIKE 'insert into bookings%')
                        """, Boolean.class));
                if (waitingForLock) { break; }
                Thread.sleep(50);
            }
            try {
                assertThat(waitingForLock).as("competing INSERT waits on PostgreSQL, not a Java lock").isTrue();
                assertThat(competing.isDone()).isFalse();
                connection.commit();
                assertThat(competing.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(409);
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void duplicateReturnsProblemDetailAndDoesNotPoisonLaterRequests() throws Exception {
        String request = body(ROOM, UUID.randomUUID(), SLOT);
        assertThat(post(request).statusCode()).isEqualTo(201);
        var duplicate = post(request);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.headers().firstValue("Content-Type").orElseThrow())
                .startsWith("application/problem+json");
        assertThat(mapper.readTree(duplicate.body()).get("title").asText()).isEqualTo("Slot unavailable");
        assertThat(post(body(ROOM, UUID.randomUUID(), "2030-01-02T13:00:00Z")).statusCode()).isEqualTo(201);
        assertThat(post(body(UUID.randomUUID(), UUID.randomUUID(), SLOT)).statusCode()).isEqualTo(201);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "EXPIRED"})
    void inactiveHistoryDoesNotBlockSlot(String status) throws Exception {
        seed(status, "2030-01-01T11:05:00Z");
        assertThat(post(body(ROOM, UUID.randomUUID(), SLOT)).statusCode()).isEqualTo(201);
    }

    @Test
    void confirmedBookingDoesNotExpireWhenHoldDeadlinePasses() throws Exception {
        seed("CONFIRMED", "2030-01-01T10:55:00Z");
        assertThat(post(body(ROOM, UUID.randomUUID(), SLOT)).statusCode()).isEqualTo(409);
    }

    @Test
    void expiredHoldCanBeReadAndReplacedAtExactDeadline() throws Exception {
        UUID oldId = seed("PENDING", NOW.toString());
        assertThat(mapper.readTree(get("/api/bookings/" + oldId).body()).get("status").asText())
                .isEqualTo("EXPIRED");
        assertThat(post(body(ROOM, UUID.randomUUID(), SLOT)).statusCode()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, oldId))
                .isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings WHERE status = 'PENDING'", Integer.class))
                .isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2029-01-01T12:00:00Z", "2030-01-01T10:55:00Z",
            "2030-01-02T12:30:00Z", "2030-01-02T12:00:00.000001Z"})
    void rejectsPastAndMisalignedSlots(String slot) throws Exception {
        assertThat(post(body(ROOM, UUID.randomUUID(), slot)).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings", Integer.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "{\"roomId\":\"invalid\"}"})
    void rejectsMissingFieldsAndMalformedJson(String request) throws Exception {
        assertThat(post(request).statusCode()).isEqualTo(400);
    }

    @Test
    void unknownBookingReturns404AndInvalidIdReturns400() throws Exception {
        assertThat(get("/api/bookings/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
        assertThat(get("/api/bookings/invalid").statusCode()).isEqualTo(400);
    }

    private UUID seed(String status, String expiresAt) {
        return seedForRoom(ROOM, status, expiresAt);
    }

    private UUID seedForRoom(UUID room, String status, String expiresAt) {
        return seedForUser(room, ALICE_SUBJECT, status, expiresAt);
    }

    private UUID seedForUser(UUID room, String userId, String status, String expiresAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO bookings (id, room_id, user_id, slot_start, status, created_at, expires_at)
                VALUES (?, ?, ?, CAST(? AS timestamptz), ?, '2030-01-01T10:45:00Z', CAST(? AS timestamptz))
                """, id, room, userId, SLOT, status, expiresAt);
        return id;
    }

    private String statusOf(UUID id) {
        return jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, id);
    }

    private UUID insertOutboxEvent(Instant occurredAt, Instant publishedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO outbox_events (
                            id, aggregate_type, aggregate_id, event_type, payload,
                            occurred_at, published_at, attempts, next_attempt_at
                        ) VALUES (?, 'BOOKING', ?, 'booking.confirmed.v1', CAST('{}' AS jsonb),
                                  CAST(? AS timestamptz), CAST(? AS timestamptz), 0, CAST(? AS timestamptz))
                        """,
                id, UUID.randomUUID(), occurredAt.toString(),
                publishedAt == null ? null : publishedAt.toString(), occurredAt.toString());
        return id;
    }

    private boolean outboxEventExists(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM outbox_events WHERE id = ?)", Boolean.class, id));
    }

    private String body(UUID room, UUID user, String slot) {
        return body(room, slot);
    }

    private String body(UUID room, String slot) {
        return """
                {"roomId":"%s", "slotStart":"%s"}
                """.formatted(room, slot);
    }

    private UUID createBooking() throws Exception {
        var response = post(body(ROOM, UUID.randomUUID(), SLOT));
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(mapper.readTree(response.body()).get("id").asText());
    }

    private int transitionAfterStart(CountDownLatch ready, CountDownLatch start, UUID id, String action)
            throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return postWithoutBody("/api/bookings/" + id + "/" + action).statusCode();
    }

    private HttpRequest postRequest(String body) {
        return postRequest(body, "alice-token");
    }

    private HttpRequest postRequest(String body, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/bookings"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request.build();
    }

    private HttpResponse<String> post(String body) throws Exception {
        return HTTP.send(postRequest(body), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postAs(String token, String body) throws Exception {
        return HTTP.send(postRequest(body, token), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postWithoutBody(String path) throws Exception {
        return postWithoutBody(path, "alice-token");
    }

    private HttpResponse<String> postWithoutBody(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).POST(HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return getAs("alice-token", path);
    }

    private HttpResponse<String> getAs(String token, String path) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeFixture {
        @Bean
        @Primary
        Clock testClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }

        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            return token -> switch (token) {
                case "alice-token" -> jwt(token, ALICE_SUBJECT, "USER");
                case "bob-token" -> jwt(token, BOB_SUBJECT, "USER");
                case "admin-token" -> jwt(token, "admin-subject", "ADMIN");
                case "guest-token" -> jwt(token, "guest-subject", "GUEST");
                default -> throw new org.springframework.security.oauth2.jwt.BadJwtException("Unknown test token");
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
