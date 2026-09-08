package dev.roombooking.booking.reservation;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

record BookingActor(String subject, boolean administrator) {
    static BookingActor from(JwtAuthenticationToken authentication) {
        boolean administrator = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        return new BookingActor(authentication.getToken().getSubject(), administrator);
    }
}
