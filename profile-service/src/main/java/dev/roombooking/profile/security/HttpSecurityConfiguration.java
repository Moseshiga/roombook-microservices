package dev.roombooking.profile.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class HttpSecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("keycloakJwtAuthenticationConverter")
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/profile/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }

    @Bean("keycloakJwtAuthenticationConverter")
    Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopeAuthorities = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<org.springframework.security.core.GrantedAuthority> authorities =
                    new ArrayList<>(scopeAuthorities.convert(jwt));
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roles)) {
                return authorities;
            }
            roles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(role -> "ROLE_" + role)
                    .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
            return authorities;
        });
        return converter;
    }
}
