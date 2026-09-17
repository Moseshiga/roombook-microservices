package dev.roombooking.booking.openapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info = @Info(
                title = "RoomBook Booking API",
                version = "v1",
                description = "Creates one-hour room holds and manages their confirmation lifecycle."),
        security = @SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH))
@SecurityScheme(
        name = OpenApiConfiguration.BEARER_AUTH,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Keycloak access token containing the USER or ADMIN realm role.")
public class OpenApiConfiguration {
    public static final String BEARER_AUTH = "bearerAuth";
}
