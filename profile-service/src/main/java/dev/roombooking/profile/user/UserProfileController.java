package dev.roombooking.profile.user;

import jakarta.validation.Valid;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
class UserProfileController {
    private final UserProfileService service;

    UserProfileController(UserProfileService service) {
        this.service = service;
    }

    @GetMapping
    UserProfileResponse get(JwtAuthenticationToken authentication) {
        return service.getRequired(authentication.getToken().getSubject());
    }

    @PutMapping
    UserProfileResponse update(@Valid @RequestBody UpdateUserProfileRequest request,
                               JwtAuthenticationToken authentication) {
        return service.update(authentication.getToken().getSubject(), request);
    }
}
