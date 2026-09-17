package dev.roombooking.profile.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@Tag(name = "Profile", description = "Notification preferences for the authenticated user")
class UserProfileController {
    private final UserProfileService service;

    UserProfileController(UserProfileService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Get the current user's profile")
    UserProfileResponse get(@Parameter(hidden = true) JwtAuthenticationToken authentication) {
        return service.getRequired(authentication.getToken().getSubject());
    }

    @PutMapping
    @Operation(summary = "Create or replace the current user's profile")
    UserProfileResponse update(@Valid @RequestBody UpdateUserProfileRequest request,
                               @Parameter(hidden = true) JwtAuthenticationToken authentication) {
        return service.update(authentication.getToken().getSubject(), request);
    }
}
