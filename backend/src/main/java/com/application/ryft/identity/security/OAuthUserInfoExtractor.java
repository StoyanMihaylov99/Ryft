 package com.application.ryft.identity.security;

import com.application.ryft.identity.exception.OAuthMissingEmailException;
import com.application.ryft.identity.repository.entity.OAuthProvider;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * Normalizes the attribute maps of Google's {@code OidcUser} (standard OIDC claims) and GitHub's
 * {@code OAuth2User} (GitHub's own {@code /user} shape, with a {@code verified_email} attribute
 * merged in by {@link GitHubOAuth2UserService}) into one {@link OAuthUserInfo} shape.
 */
@Component
public class OAuthUserInfoExtractor {

    public OAuthUserInfo extract(String registrationId, OAuth2User oauth2User) {
        OAuthUserInfo info = switch (registrationId.toLowerCase(Locale.ROOT)) {
            case "google" -> extractGoogle(oauth2User);
            case "github" -> extractGitHub(oauth2User);
            default -> throw new IllegalArgumentException("Unsupported OAuth2 provider: " + registrationId);
        };
        if (info.email() == null || info.email().isBlank()) {
            throw new OAuthMissingEmailException(info.provider());
        }
        return info;
    }

    private OAuthUserInfo extractGoogle(OAuth2User oauth2User) {
        Map<String, Object> attributes = oauth2User.getAttributes();
        String subjectId = String.valueOf(attributes.get("sub"));
        String email = (String) attributes.get("email");
        boolean emailVerified = Boolean.TRUE.equals(attributes.get("email_verified"));
        String displayName = (String) attributes.get("name");
        String avatarUrl = (String) attributes.get("picture");
        return new OAuthUserInfo(OAuthProvider.GOOGLE, subjectId, email, emailVerified, displayName, avatarUrl);
    }

    private OAuthUserInfo extractGitHub(OAuth2User oauth2User) {
        Map<String, Object> attributes = oauth2User.getAttributes();
        String subjectId = String.valueOf(attributes.get("id"));
        String verifiedEmail = (String) attributes.get("verified_email");
        boolean hasVerifiedEmail = verifiedEmail != null && !verifiedEmail.isBlank();
        String email = hasVerifiedEmail ? verifiedEmail : (String) attributes.get("email");
        String displayName = (String) attributes.get("name");
        if (displayName == null || displayName.isBlank()) {
            displayName = (String) attributes.get("login");
        }
        String avatarUrl = (String) attributes.get("avatar_url");
        return new OAuthUserInfo(OAuthProvider.GITHUB, subjectId, email, hasVerifiedEmail, displayName, avatarUrl);
    }
}
