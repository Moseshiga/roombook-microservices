package dev.roombooking.booking;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(BookingServiceApplicationTests.TimeFixture.class)
@Testcontainers
class BookingServiceApplicationTests {
    private static final Instant NOW = Instant.parse("2030-01-01T10:55:00Z");
    private static final String SLOT = "2030-01-02T12:00:00Z";
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

    @BeforeEach
    void clearTestDatabase() {
        jdbc.update("DELETE FROM bookings");
    }

    @AfterAll
    static void closeClient() {
        HTTP.close();
    }

    @Test
    void createsAndReadsTenMinuteHold() throws Exception {
        UUID user = UUID.randomUUID();
        var response = post(body(ROOM, user, SLOT));
        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode booking = mapper.readTree(response.body());
        assertThat(booking.get("status").asText()).isEqualTo("PENDING");
        assertThat(booking.get("userId").asText()).isEqualTo(user.toString());
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
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO bookings (id, room_id, user_id, slot_start, status, created_at, expires_at)
                VALUES (?, ?, ?, CAST(? AS timestamptz), ?, '2030-01-01T10:45:00Z', CAST(? AS timestamptz))
                """, id, ROOM, UUID.randomUUID(), SLOT, status, expiresAt);
        return id;
    }

    private String body(UUID room, UUID user, String slot) {
        return """
                {"roomId":"%s", "userId":"%s", "slotStart":"%s"}
                """.formatted(room, user, slot);
    }

    private HttpRequest postRequest(String body) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/bookings"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private HttpResponse<String> post(String body) throws Exception {
        return HTTP.send(postRequest(body), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeFixture {
        @Bean
        @Primary
        Clock testClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
}
