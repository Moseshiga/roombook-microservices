package dev.roombooking.room;

import dev.roombooking.room.catalog.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(RoomServiceApplicationTests.JwtTestConfiguration.class)
@Testcontainers
class RoomServiceApplicationTests {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired RoomRepository roomRepository;
    @Autowired ObjectMapper objectMapper;
    @LocalServerPort int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void clearDatabase() {
        roomRepository.deleteAll();
    }

    @Test
    void onlyAdministratorsCanManageTheCatalog() throws Exception {
        String body = "{\"name\":\"Atlas\",\"location\":\"Floor 4\",\"capacity\":8}";
        assertThat(send("POST", "/api/admin/rooms", "user-token", body).statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());

        HttpResponse<String> created = send("POST", "/api/admin/rooms", "admin-token", body);
        assertThat(created.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(objectMapper.readTree(created.body()).get("name").asString()).isEqualTo("Atlas");
    }

    @Test
    void usersCanOnlySeeActiveRooms() throws Exception {
        HttpResponse<String> created = send("POST", "/api/admin/rooms", "admin-token",
                "{\"name\":\"Orion\",\"location\":\"Floor 2\",\"capacity\":6}");
        String roomId = objectMapper.readTree(created.body()).get("id").asString();

        assertThat(send("GET", "/api/rooms", "user-token", null).statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(send("PUT", "/api/admin/rooms/" + roomId, "admin-token",
                "{\"name\":\"Orion\",\"location\":\"Floor 2\",\"capacity\":6,\"active\":false}").statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(send("GET", "/api/rooms/" + roomId, "user-token", null).statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void catalogRequiresAnAuthenticatedUserWithTheCorrectRole() throws Exception {
        assertThat(send("GET", "/api/rooms", null, null).statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(send("GET", "/api/rooms", "guest-token", null).statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(send("GET", "/api/rooms", "user-token", null).statusCode()).isEqualTo(HttpStatus.OK.value());
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
        else request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtTestConfiguration {
        @Bean @Primary
        JwtDecoder jwtDecoder() {
            return token -> switch (token) {
                case "user-token" -> jwt("user-subject", List.of("USER"));
                case "admin-token" -> jwt("admin-subject", List.of("ADMIN"));
                case "guest-token" -> jwt("guest-subject", List.of("GUEST"));
                default -> throw new BadJwtException("Unknown test token");
            };
        }

        private Jwt jwt(String subject, List<String> roles) {
            Instant now = Instant.now();
            return new Jwt("test-token", now.minusSeconds(30), now.plusSeconds(300), Map.of("alg", "none"),
                    Map.of("sub", subject, "iss", "https://issuer.test", "realm_access", Map.of("roles", roles)));
        }
    }
}
