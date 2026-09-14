package dev.roombooking.profile.user;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
class UserProfileService {
    private final UserProfileRepository repository;
    private final Clock clock;

    UserProfileService(UserProfileRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    UserProfileResponse getRequired(String userId) {
        return find(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found"));
    }

    @Transactional(readOnly = true)
    Optional<UserProfileResponse> find(String userId) {
        return repository.findById(userId).map(UserProfileResponse::from);
    }

    @Transactional
    UserProfileResponse update(String userId, UpdateUserProfileRequest request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        String locale = java.util.Locale.forLanguageTag(request.locale()).toLanguageTag();
        UserProfile profile = repository.findById(userId)
                .map(existing -> {
                    existing.update(email, locale, request.notificationsEnabled(), now);
                    return existing;
                })
                .orElseGet(() -> new UserProfile(userId, email, locale, request.notificationsEnabled(), now));
        return UserProfileResponse.from(repository.save(profile));
    }
}
