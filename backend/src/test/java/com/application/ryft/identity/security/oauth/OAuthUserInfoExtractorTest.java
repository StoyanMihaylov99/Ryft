package com.application.ryft.identity.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.application.ryft.identity.oauth.exception.OAuthMissingEmailException;
import com.application.ryft.identity.oauth.entity.OAuthProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

class OAuthUserInfoExtractorTest {

    private final OAuthUserInfoExtractor extractor = new OAuthUserInfoExtractor();

    private OAuth2User user(String nameAttributeKey, Map<String, Object> attributes) {
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, nameAttributeKey);
    }

    @Test
    void extractsGoogleClaimsWithVerifiedEmail() {
        OAuth2User googleUser = user("sub", Map.of(
                "sub", "google-1",
                "email", "user@example.com",
                "email_verified", true,
                "name", "Ada Lovelace",
                "picture", "http://avatar"));

        OAuthUserInfo info = extractor.extract("google", googleUser);

        assertThat(info.provider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(info.providerUserId()).isEqualTo("google-1");
        assertThat(info.email()).isEqualTo("user@example.com");
        assertThat(info.emailVerified()).isTrue();
        assertThat(info.displayName()).isEqualTo("Ada Lovelace");
        assertThat(info.avatarUrl()).isEqualTo("http://avatar");
    }

    @Test
    void extractsGoogleClaimsWithUnverifiedEmail() {
        OAuth2User googleUser = user("sub", Map.of(
                "sub", "google-2", "email", "user2@example.com", "email_verified", false, "name", "Someone"));

        OAuthUserInfo info = extractor.extract("google", googleUser);

        assertThat(info.emailVerified()).isFalse();
    }

    @Test
    void extractsGithubEmailFromMergedVerifiedEmailAttribute() {
        OAuth2User githubUser = user("id", Map.of(
                "id", "12345",
                "email", "private@users.noreply.github.com",
                "verified_email", "real@example.com",
                "login", "octocat",
                "name", "The Octocat",
                "avatar_url", "http://avatar"));

        OAuthUserInfo info = extractor.extract("github", githubUser);

        assertThat(info.provider()).isEqualTo(OAuthProvider.GITHUB);
        assertThat(info.email()).isEqualTo("real@example.com");
        assertThat(info.emailVerified()).isTrue();
        assertThat(info.displayName()).isEqualTo("The Octocat");
    }

    @Test
    void extractsGithubFallsBackToPlainEmailWhenNoVerifiedEmailMerged() {
        OAuth2User githubUser = user("id", Map.of(
                "id", "12345", "email", "maybe-unverified@example.com", "login", "octocat"));

        OAuthUserInfo info = extractor.extract("github", githubUser);

        assertThat(info.email()).isEqualTo("maybe-unverified@example.com");
        assertThat(info.emailVerified()).isFalse();
    }

    @Test
    void extractsGithubFallsBackToLoginWhenNameMissing() {
        OAuth2User githubUser = user("id", Map.of(
                "id", "12345", "email", "user@example.com", "login", "octocat"));

        OAuthUserInfo info = extractor.extract("github", githubUser);

        assertThat(info.displayName()).isEqualTo("octocat");
    }

    @Test
    void unsupportedProviderThrows() {
        OAuth2User user = user("id", Map.of("id", "1", "email", "a@example.com"));

        assertThatThrownBy(() -> extractor.extract("facebook", user))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingEmailThrowsOAuthMissingEmailException() {
        OAuth2User githubUser = user("id", Map.of("id", "12345", "login", "octocat"));

        assertThatThrownBy(() -> extractor.extract("github", githubUser))
                .isInstanceOf(OAuthMissingEmailException.class);
    }
}
