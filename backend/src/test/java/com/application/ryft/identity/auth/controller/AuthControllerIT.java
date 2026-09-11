package com.application.ryft.identity.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.application.ryft.AbstractIntegrationTest;
import com.application.ryft.identity.auth.dto.LoginRequest;
import com.application.ryft.identity.auth.dto.RegisterRequest;
import com.application.ryft.identity.auth.repository.RefreshTokenRepository;
import com.application.ryft.identity.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Grace period is forced to zero for this class so refresh-token reuse always takes the "real
 * theft" branch deterministically — with the default 10s grace period, an integration test that
 * immediately re-presents a rotated-away token would hit the lenient multi-tab-race branch instead
 * (that branch is exercised separately in TokenServiceTest, where timing can be controlled exactly).
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.refresh-token.reuse-grace-period=PT0S")
class AuthControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Value("${app.refresh-token.cookie-name}")
    private String cookieName;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void registerCreatesUserAndSetsRefreshCookie() throws Exception {
        String email = uniqueEmail();
        RegisterRequest request = new RegisterRequest(email, "password123", "Ada Lovelace");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn();

        assertThat(extractCookieValue(result)).isNotBlank();
        assertThat(userRepository.existsByEmailIgnoreCase(email)).isTrue();
        assertThat(refreshTokenRepository.count()).isGreaterThan(0);
    }

    @Test
    void registerWithDuplicateEmailReturns409() throws Exception {
        String email = uniqueEmail();
        RegisterRequest request = new RegisterRequest(email, "password123", "Ada Lovelace");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void registerWithInvalidPayloadReturns400() throws Exception {
        RegisterRequest request = new RegisterRequest("not-an-email", "short", "");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithCorrectPasswordSucceeds() throws Exception {
        String email = uniqueEmail();
        registerUser(email, "password123", "Grace Hopper");

        LoginRequest request = new LoginRequest(email, "password123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        String email = uniqueEmail();
        registerUser(email, "password123", "Grace Hopper");

        LoginRequest request = new LoginRequest(email, "wrong-password");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithUnknownEmailReturns401() throws Exception {
        LoginRequest request = new LoginRequest(uniqueEmail(), "password123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTokenAndOldCookieCanNeverBeUsedAgain() throws Exception {
        String email = uniqueEmail();
        MvcResult registerResult = registerUser(email, "password123", "Katherine Johnson");
        String firstRefreshToken = extractCookieValue(registerResult);

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(cookieName, firstRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn();
        String secondRefreshToken = extractCookieValue(refreshResult);
        assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);

        // Reusing the already-rotated-away first token is theft-shaped: rejected, and it revokes the
        // whole family, so even the legitimately-latest token stops working afterwards.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(cookieName, firstRefreshToken)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(cookieName, secondRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCookieReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsCookieAndInvalidatesItForFutureRefresh() throws Exception {
        String email = uniqueEmail();
        MvcResult registerResult = registerUser(email, "password123", "Margaret Hamilton");
        String refreshToken = extractCookieValue(registerResult);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie(cookieName, refreshToken)))
                .andExpect(status().isNoContent())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(cookieName, refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsCurrentUserWithValidBearerToken() throws Exception {
        String email = uniqueEmail();
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, "password123", "Ada Lovelace"))))
                .andExpect(status().isCreated())
                .andReturn();
        String accessToken = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    private MvcResult registerUser(String email, String password, String displayName) throws Exception {
        RegisterRequest request = new RegisterRequest(email, password, displayName);
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String extractCookieValue(MvcResult result) {
        String setCookieHeader = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeader).isNotNull();
        String prefix = cookieName + "=";
        assertThat(setCookieHeader).startsWith(prefix);
        int semicolon = setCookieHeader.indexOf(';');
        return semicolon >= 0
                ? setCookieHeader.substring(prefix.length(), semicolon)
                : setCookieHeader.substring(prefix.length());
    }
}
