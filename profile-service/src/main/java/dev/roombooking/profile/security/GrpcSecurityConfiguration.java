package dev.roombooking.profile.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor;
import org.springframework.grpc.server.security.GrpcSecurity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

@Configuration(proxyBeanMethods = false)
class GrpcSecurityConfiguration {
    private static final String GET_NOTIFICATION_PROFILE =
            "roombook.profile.v1.UserProfileService/GetNotificationProfile";

    @Bean
    @GlobalServerInterceptor
    AuthenticationProcessInterceptor grpcSecurityFilterChain(
            GrpcSecurity grpc,
            @Qualifier("keycloakJwtAuthenticationConverter")
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter) throws Exception {
        return grpc
                .authorizeRequests(authorize -> authorize
                        .methods(GET_NOTIFICATION_PROFILE).hasAuthority("ROLE_PROFILE_READ")
                        .methods("grpc.health.v1.Health/*", "grpc.reflection.v1.ServerReflection/*",
                                "grpc.reflection.v1alpha.ServerReflection/*").permitAll()
                        .allRequests().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }
}
