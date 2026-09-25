package com.application.ryft.seed.data;

/** One demo account to be registered via {@code AuthService.register}. */
public record SeedUser(
        String email,
        String displayName
) {
}
