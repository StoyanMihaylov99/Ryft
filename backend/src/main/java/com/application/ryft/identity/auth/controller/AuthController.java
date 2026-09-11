package com.application.ryft.identity.auth.controller;

import com.application.ryft.identity.auth.dto.AuthResponse;
import com.application.ryft.identity.auth.dto.LoginRequest;
import com.application.ryft.identity.auth.dto.RegisterRequest;
import com.application.ryft.identity.user.dto.UserDTO;
import com.application.ryft.identity.auth.exception.InvalidRefreshTokenException;
import com.application.ryft.identity.security.RefreshTokenCookieSupport;
import com.application.ryft.identity.auth.service.AuthResult;
import com.application.ryft.identity.auth.service.AuthService;
import com.application.ryft.identity.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final RefreshTokenCookieSupport cookieSupport;

    public AuthController(AuthService authService, UserService userService, RefreshTokenCookieSupport cookieSupport) {
        this.authService = authService;
        this.userService = userService;
        this.cookieSupport = cookieSupport;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        AuthResult result = authService.register(request);
        cookieSupport.setCookie(response, result.refreshToken().rawValue(), result.refreshToken().expiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(toAuthResponse(result));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthResult result = authService.login(request);
        cookieSupport.setCookie(response, result.refreshToken().rawValue(), result.refreshToken().expiresAt());
        return ResponseEntity.ok(toAuthResponse(result));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "${app.refresh-token.cookie-name}", required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null) {
            throw new InvalidRefreshTokenException("Missing refresh token cookie");
        }
        AuthResult result = authService.refresh(refreshToken);
        cookieSupport.setCookie(response, result.refreshToken().rawValue(), result.refreshToken().expiresAt());
        return ResponseEntity.ok(toAuthResponse(result));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${app.refresh-token.cookie-name}", required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        cookieSupport.clearCookie(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserDTO> me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(userService.getById(userId));
    }

    private AuthResponse toAuthResponse(AuthResult result) {
        return new AuthResponse(result.accessToken().value(), result.accessToken().expiresInSeconds(), result.user());
    }
}
