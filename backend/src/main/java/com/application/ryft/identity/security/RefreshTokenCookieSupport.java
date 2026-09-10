package com.application.ryft.identity.security;

import com.application.ryft.identity.config.RefreshTokenProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**sh token, used by
 * Shared Set-Cookie helper for the refre both {@link
 * com.application.ryft.identity.controller.AuthController} and {@link OAuth2LoginSuccessHandler}.
 * Clearing MUST reuse the exact same path/sameSite attributes the cookie was set with, or the
 * browser treats it as a different cookie and never actually deletes the original.
 */
@Component
public class RefreshTokenCookieSupport {

    private final RefreshTokenProperties properties;

    public RefreshTokenCookieSupport(RefreshTokenProperties properties) {
        this.properties = properties;
    }

    public void setCookie(HttpServletResponse response, String rawToken, Instant expiresAt) {
        Duration maxAge = Duration.between(Instant.now(), expiresAt);
        if (maxAge.isNegative()) {
            maxAge = Duration.ZERO;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookieBuilder(rawToken).maxAge(maxAge).build().toString());
    }

    public void clearCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookieBuilder("").maxAge(0).build().toString());
    }

    private ResponseCookie.ResponseCookieBuilder cookieBuilder(String value) {
        return ResponseCookie.from(properties.cookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path(properties.cookiePath());
    }
}
