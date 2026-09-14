package dev.roombooking.profile.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record UpdateUserProfileRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 35)
        @Pattern(regexp = "[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*", message = "must be a BCP 47 language tag")
        String locale,
        boolean notificationsEnabled
) {
}
