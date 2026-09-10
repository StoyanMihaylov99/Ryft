package com.application.ryft.identity.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

/**
 * GitHub has no OIDC id_token, and its {@code /user} endpoint's {@code email} field is {@code null}
 * whenever the user has "keep my email private" enabled — even with {@code user:email} scope
 * granted. This delegates the base profile fetch to Spring's default user service, then separately
 * calls {@code GET /user/emails} to find the verified primary address and merges it in under a
 * {@code verified_email} attribute that {@link OAuthUserInfoExtractor} reads.
 */
@Service
public class GitHubOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final Logger log = LoggerFactory.getLogger(GitHubOAuth2UserService.class);

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
    private final RestClient githubRestClient;

    @Autowired
    public GitHubOAuth2UserService(RestClient githubRestClient) {
        this(githubRestClient, new DefaultOAuth2UserService());
    }

    /** Visible for tests, to substitute a stub delegate instead of hitting GitHub's real /user endpoint. */
    GitHubOAuth2UserService(RestClient githubRestClient, OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate) {
        this.githubRestClient = githubRestClient;
        this.delegate = delegate;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = delegate.loadUser(userRequest);
        String verifiedEmail = fetchVerifiedPrimaryEmail(userRequest.getAccessToken().getTokenValue());

        Map<String, Object> attributes = new LinkedHashMap<>(user.getAttributes());
        if (verifiedEmail != null) {
            attributes.put("verified_email", verifiedEmail);
        }

        String userNameAttribute = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        return new DefaultOAuth2User(user.getAuthorities(), attributes, userNameAttribute);
    }

    private String fetchVerifiedPrimaryEmail(String accessToken) {
        try {
            List<Map<String, Object>> emails = githubRestClient.get()
                    .uri("/user/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });
            if (emails == null) {
                return null;
            }
            return emails.stream()
                    .filter(e -> Boolean.TRUE.equals(e.get("verified")) && Boolean.TRUE.equals(e.get("primary")))
                    .map(e -> (String) e.get("email"))
                    .findFirst()
                    .or(() -> emails.stream()
                            .filter(e -> Boolean.TRUE.equals(e.get("verified")))
                            .map(e -> (String) e.get("email"))
                            .findFirst())
                    .orElse(null);
        } catch (RestClientException ex) {
            log.warn("Failed to fetch GitHub verified email list; falling back to /user email field", ex);
            return null;
        }
    }
}
