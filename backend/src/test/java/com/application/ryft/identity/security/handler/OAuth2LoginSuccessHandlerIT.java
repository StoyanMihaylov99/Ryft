package com.application.ryft.identity.security.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.application.ryft.AbstractIntegrationTest;
import com.application.ryft.identity.oauth.repository.OAuthIdentityRepository;
import com.application.ryft.identity.user.repository.UserRepository;
import com.application.ryft.identity.oauth.entity.OAuthIdentity;
import com.application.ryft.identity.oauth.entity.OAuthProvider;
import com.application.ryft.identity.user.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

/**
 * Exercises the OAuth2 user-provisioning logic directly (no real Google/GitHub call, per this
 * project's coding standard of testing behavior over real network calls) by feeding the handler a
 * hand-built {@code OAuth2AuthenticationToken}, exactly as Spring Security would after a real
 * provider round-trip.
 */
class OAuth2LoginSuccessHandlerIT extends AbstractIntegrationTest {

    @Autowired
    private OAuth2LoginSuccessHandler successHandler;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OAuthIdentityRepository oAuthIdentityRepository;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void newGoogleLoginCreatesUserAndIdentityAndSetsCookie() throws Exception {
        String email = uniqueEmail();
        String googleSubject = "google-" + UUID.randomUUID();
        OAuth2AuthenticationToken authentication = googleAuthentication(googleSubject, email, true, "New User");

        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:4200/oauth2/callback");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNotNull();

        Optional<User> user = userRepository.findByEmailIgnoreCase(email);
        assertThat(user).isPresent();
        Optional<OAuthIdentity> identity =
                oAuthIdentityRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, googleSubject);
        assertThat(identity).isPresent();
        assertThat(identity.get().getUser().getId()).isEqualTo(user.get().getId());
    }

    @Test
    void verifiedGithubEmailLinksToExistingPasswordAccountInsteadOfCreatingANewOne() throws Exception {
        String email = uniqueEmail();
        User existing = userRepository.save(new User(email, "dummy-hash", "Existing User", null));

        String githubId = "github-" + UUID.randomUUID();
        OAuth2AuthenticationToken authentication = githubAuthentication(githubId, email, true, "GitHub Name");

        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:4200/oauth2/callback");
        Optional<OAuthIdentity> identity =
                oAuthIdentityRepository.findByProviderAndProviderUserId(OAuthProvider.GITHUB, githubId);
        assertThat(identity).isPresent();
        assertThat(identity.get().getUser().getId()).isEqualTo(existing.getId());
        // Linking onto the existing account, not creating a second one for the same email.
        assertThat(userRepository.findByEmailIgnoreCase(email)).map(User::getId).contains(existing.getId());
    }

    @Test
    void unverifiedEmailConflictingWithExistingAccountIsRejectedWithoutLinking() throws Exception {
        String email = uniqueEmail();
        userRepository.save(new User(email, "dummy-hash", "Existing User", null));

        String githubId = "github-" + UUID.randomUUID();
        OAuth2AuthenticationToken authentication = githubAuthentication(githubId, email, false, "Impersonator");

        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:4200/login?error=oauth_email_conflict");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(oAuthIdentityRepository.findByProviderAndProviderUserId(OAuthProvider.GITHUB, githubId)).isEmpty();
    }

    private OAuth2AuthenticationToken googleAuthentication(String subject, String email, boolean emailVerified,
            String name) {
        Map<String, Object> claims = Map.of(
                "sub", subject,
                "email", email,
                "email_verified", emailVerified,
                "name", name,
                "picture", "https://example.com/avatar.png");
        OidcIdToken idToken = new OidcIdToken("id-token-value", Instant.now(), Instant.now().plusSeconds(3600), claims);
        OidcUserInfo userInfo = new OidcUserInfo(claims);
        DefaultOidcUser oidcUser = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken, userInfo, "sub");
        return new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "google");
    }

    private OAuth2AuthenticationToken githubAuthentication(String id, String email, boolean verified, String name) {
        Map<String, Object> attributes = verified
                ? Map.of("id", id, "email", email, "verified_email", email, "login", "octocat", "name", name)
                : Map.of("id", id, "email", email, "login", "octocat", "name", name);
        DefaultOAuth2User oauth2User = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes, "id");
        return new OAuth2AuthenticationToken(oauth2User, oauth2User.getAuthorities(), "github");
    }
}
