package com.application.ryft.identity.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class GitHubOAuth2UserServiceTest {

    private RestClient restClient;
    private RestClient.RequestHeadersUriSpec uriSpec;
    private RestClient.RequestHeadersSpec headersSpec;
    private RestClient.ResponseSpec responseSpec;
    private OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
    private GitHubOAuth2UserService service;
    private OAuth2UserRequest userRequest;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        headersSpec = mock(RestClient.RequestHeadersSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);
        delegate = mock(OAuth2UserService.class);

        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(headersSpec);
        when(headersSpec.header(anyString(), anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);

        service = new GitHubOAuth2UserService(restClient, delegate);

        ClientRegistration registration = ClientRegistration.withRegistrationId("github")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("id")
                .clientName("GitHub")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "test-access-token", Instant.now(), Instant.now().plusSeconds(3600));
        userRequest = new OAuth2UserRequest(registration, accessToken);
    }

    private OAuth2User baseGithubUser() {
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("id", "12345", "login", "octocat", "name", "The Octocat", "avatar_url", "http://avatar"), "id");
    }

    @SuppressWarnings("unchecked")
    @Test
    void mergesPrimaryVerifiedEmailIntoAttributes() {
        when(delegate.loadUser(userRequest)).thenReturn(baseGithubUser());
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of(
                Map.of("email", "secondary@example.com", "primary", false, "verified", true),
                Map.of("email", "primary@example.com", "primary", true, "verified", true)));

        OAuth2User result = service.loadUser(userRequest);

        assertThat(result.getAttributes()).containsEntry("verified_email", "primary@example.com");
    }

    @SuppressWarnings("unchecked")
    @Test
    void fallsBackToAnyVerifiedEmailWhenNoPrimaryVerifiedFound() {
        when(delegate.loadUser(userRequest)).thenReturn(baseGithubUser());
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of(
                Map.of("email", "unverified@example.com", "primary", true, "verified", false),
                Map.of("email", "verified-not-primary@example.com", "primary", false, "verified", true)));

        OAuth2User result = service.loadUser(userRequest);

        assertThat(result.getAttributes()).containsEntry("verified_email", "verified-not-primary@example.com");
    }

    @SuppressWarnings("unchecked")
    @Test
    void leavesAttributesUnchangedWhenNoEmailIsVerified() {
        when(delegate.loadUser(userRequest)).thenReturn(baseGithubUser());
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of(
                Map.of("email", "unverified@example.com", "primary", true, "verified", false)));

        OAuth2User result = service.loadUser(userRequest);

        assertThat(result.getAttributes()).doesNotContainKey("verified_email");
    }

    @SuppressWarnings("unchecked")
    @Test
    void gracefullyDegradesWhenGithubEmailsCallFails() {
        when(delegate.loadUser(userRequest)).thenReturn(baseGithubUser());
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenThrow(new RestClientException("network error"));

        OAuth2User result = service.loadUser(userRequest);

        assertThat(result.getAttributes()).doesNotContainKey("verified_email");
        assertThat(result.getAttributes()).containsEntry("login", "octocat");
    }

    @Test
    void preservesOriginalAttributesAndNameKey() {
        when(delegate.loadUser(userRequest)).thenReturn(baseGithubUser());
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of());

        OAuth2User result = service.loadUser(userRequest);

        assertThat(result.getName()).isEqualTo("12345");
        assertThat(result.getAttributes()).containsEntry("name", "The Octocat");
    }
}
