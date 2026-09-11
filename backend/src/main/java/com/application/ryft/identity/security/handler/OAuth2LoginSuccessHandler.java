package com.application.ryft.identity.security.handler;

import com.application.ryft.identity.oauth.exception.OAuthEmailConflictException;
import com.application.ryft.identity.oauth.exception.OAuthMissingEmailException;
import com.application.ryft.identity.auth.service.AuthResult;
import com.application.ryft.identity.auth.service.AuthService;
import com.application.ryft.identity.security.OAuth2RedirectSupport;
import com.application.ryft.identity.security.RefreshTokenCookieSupport;
import com.application.ryft.identity.security.oauth.OAuthUserInfo;
import com.application.ryft.identity.security.oauth.OAuthUserInfoExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Runs after Spring Security's own OAuth2/OIDC authentication already succeeded. Note: an exception
 * thrown from here does NOT get routed to {@link OAuth2LoginFailureHandler} automatically (that only
 * happens for exceptions raised during the authentication filter itself) — so provisioning failures
 * are caught explicitly below and redirected the same way the failure handler would.
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final OAuthUserInfoExtractor userInfoExtractor;
    private final AuthService authService;
    private final RefreshTokenCookieSupport cookieSupport;
    private final OAuth2RedirectSupport redirectSupport;

    public OAuth2LoginSuccessHandler(OAuthUserInfoExtractor userInfoExtractor, AuthService authService,
            RefreshTokenCookieSupport cookieSupport, OAuth2RedirectSupport redirectSupport) {
        this.userInfoExtractor = userInfoExtractor;
        this.authService = authService;
        this.cookieSupport = cookieSupport;
        this.redirectSupport = redirectSupport;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        try {
            String registrationId = registrationIdOf(authentication);
            OAuth2User principal = (OAuth2User) authentication.getPrincipal();
            OAuthUserInfo userInfo = userInfoExtractor.extract(registrationId, principal);

            AuthResult result = authService.loginOrRegisterOAuthUser(userInfo);
            cookieSupport.setCookie(response, result.refreshToken().rawValue(), result.refreshToken().expiresAt());
            // No tokens in this redirect URL — the frontend calls /auth/refresh next, using the
            // cookie just set above, to mint an access token.
            redirectSupport.redirectToSuccess(response);
        } catch (OAuthEmailConflictException ex) {
            log.warn("OAuth2 login rejected: {}", ex.getMessage());
            redirectSupport.redirectToFailure(response, "oauth_email_conflict");
        } catch (OAuthMissingEmailException ex) {
            log.warn("OAuth2 login rejected: {}", ex.getMessage());
            redirectSupport.redirectToFailure(response, "oauth_missing_email");
        } catch (RuntimeException ex) {
            log.error("Unexpected error provisioning OAuth2 user", ex);
            redirectSupport.redirectToFailure(response, "oauth_error");
        }
    }

    private String registrationIdOf(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken token) {
            return token.getAuthorizedClientRegistrationId();
        }
        throw new IllegalStateException("Expected OAuth2AuthenticationToken, got " + authentication.getClass());
    }
}
