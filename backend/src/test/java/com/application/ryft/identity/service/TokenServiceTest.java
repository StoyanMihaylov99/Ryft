package com.application.ryft.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.application.ryft.identity.config.JwtProperties;
import com.application.ryft.identity.config.RefreshTokenProperties;
import com.application.ryft.identity.exception.InvalidRefreshTokenException;
import com.application.ryft.identity.exception.RefreshTokenReuseDetectedException;
import com.application.ryft.identity.repository.RefreshTokenRepository;
import com.application.ryft.identity.repository.entity.RefreshToken;
import com.application.ryft.identity.repository.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtEncoder jwtEncoder;

    private RefreshTokenProperties refreshTokenProperties;
    private TokenService tokenService;
    private User user;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(Duration.ofMinutes(15));
        refreshTokenProperties = new RefreshTokenProperties(
                Duration.ofDays(30), "ryft_refresh_token", "/api/v1/auth", false, Duration.ofSeconds(10));
        tokenService = new TokenService(refreshTokenRepository, jwtEncoder, jwtProperties, refreshTokenProperties);
        user = new User("user@example.com", "hash", "Test User", null);
        // @UuidGenerator only assigns an id on a real Hibernate persist, which never happens in this
        // pure-Mockito test; set one directly since issueAccessToken() needs a non-null subject.
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        // lenient: not every test reaches a save() call (e.g. the pure-rejection paths), and a shared
        // @BeforeEach stub would otherwise trip Mockito's strict-stubbing check on those.
        lenient().when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void issueAccessTokenEncodesClaimsAndReturnsConfiguredTtl() {
        when(jwtEncoder.encode(any())).thenReturn(
                Jwt.withTokenValue("access-token-value").header("alg", "RS256").claim("sub", "irrelevant").build());

        IssuedAccessToken accessToken = tokenService.issueAccessToken(user);

        assertThat(accessToken.value()).isEqualTo("access-token-value");
        assertThat(accessToken.expiresInSeconds()).isEqualTo(Duration.ofMinutes(15).toSeconds());
    }

    @Test
    void issueRefreshTokenPersistsOnlyTheHashNeverTheRawValue() {
        IssuedRefreshToken issued = tokenService.issueRefreshToken(user);

        assertThat(issued.rawValue()).isNotBlank();

        var captor = org.mockito.ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(issued.rawValue()));
        assertThat(saved.getTokenHash()).isNotEqualTo(issued.rawValue());
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.isRevoked()).isFalse();
    }

    @Test
    void rotateRefreshTokenRevokesOldRowAndIssuesANewOneInTheSameFamily() {
        String rawToken = "current-raw-token";
        UUID familyId = UUID.randomUUID();
        RefreshToken current = new RefreshToken(user, sha256Hex(rawToken), familyId, Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(current));

        RefreshTokenRotationResult result = tokenService.rotateRefreshToken(rawToken);

        assertThat(current.isRevoked()).isTrue();
        assertThat(result.user()).isEqualTo(user);
        assertThat(result.refreshToken().rawValue()).isNotEqualTo(rawToken);
        verify(refreshTokenRepository, never()).revokeAllByFamilyId(any(), any());
    }

    @Test
    void rotateRefreshTokenWithUnknownTokenThrows() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.rotateRefreshToken("unknown"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotateRefreshTokenWithExpiredButNotRevokedTokenThrowsWithoutRevokingFamily() {
        String rawToken = "expired-raw-token";
        RefreshToken expired = new RefreshToken(user, sha256Hex(rawToken), UUID.randomUUID(), Instant.now().minusSeconds(60));
        when(refreshTokenRepository.findByTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> tokenService.rotateRefreshToken(rawToken))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository, never()).revokeAllByFamilyId(any(), any());
    }

    @Test
    void rotateRefreshTokenReusedLongAfterRotationIsTreatedAsTheftAndRevokesWholeFamily() {
        String rawToken = "stolen-raw-token";
        UUID familyId = UUID.randomUUID();
        RefreshToken alreadyRotatedAway = new RefreshToken(user, sha256Hex(rawToken), familyId, Instant.now().plusSeconds(3600));
        alreadyRotatedAway.revoke(Instant.now().minus(Duration.ofMinutes(5))); // well outside the 10s grace period
        when(refreshTokenRepository.findByTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(alreadyRotatedAway));

        assertThatThrownBy(() -> tokenService.rotateRefreshToken(rawToken))
                .isInstanceOf(RefreshTokenReuseDetectedException.class);
        verify(refreshTokenRepository, times(1)).revokeAllByFamilyId(org.mockito.ArgumentMatchers.eq(familyId), any());
    }

    @Test
    void rotateRefreshTokenReusedWithinGracePeriodRotatesFromCurrentLiveTokenInsteadOfRevokingFamily() {
        String staleRawToken = "stale-raw-token";
        UUID familyId = UUID.randomUUID();
        RefreshToken stale = new RefreshToken(user, sha256Hex(staleRawToken), familyId, Instant.now().plusSeconds(3600));
        stale.revoke(Instant.now().minus(Duration.ofSeconds(2))); // within the 10s grace period
        when(refreshTokenRepository.findByTokenHash(sha256Hex(staleRawToken))).thenReturn(Optional.of(stale));

        RefreshToken currentlyLive = new RefreshToken(user, sha256Hex("current-live-token"), familyId, Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByFamilyIdAndRevokedAtIsNull(familyId)).thenReturn(List.of(currentlyLive));

        RefreshTokenRotationResult result = tokenService.rotateRefreshToken(staleRawToken);

        assertThat(result.user()).isEqualTo(user);
        assertThat(currentlyLive.isRevoked()).isTrue();
        verify(refreshTokenRepository, never()).revokeAllByFamilyId(any(), any());
    }

    @Test
    void rotateRefreshTokenReusedWithinGracePeriodButFamilyAlreadyFullyRevokedStillTreatedAsTheft() {
        String staleRawToken = "stale-raw-token-2";
        UUID familyId = UUID.randomUUID();
        RefreshToken stale = new RefreshToken(user, sha256Hex(staleRawToken), familyId, Instant.now().plusSeconds(3600));
        stale.revoke(Instant.now().minus(Duration.ofSeconds(2)));
        when(refreshTokenRepository.findByTokenHash(sha256Hex(staleRawToken))).thenReturn(Optional.of(stale));
        when(refreshTokenRepository.findByFamilyIdAndRevokedAtIsNull(familyId)).thenReturn(List.of());

        assertThatThrownBy(() -> tokenService.rotateRefreshToken(staleRawToken))
                .isInstanceOf(RefreshTokenReuseDetectedException.class);
    }

    @Test
    void revokeRefreshTokenOnLogoutRevokesOnlyThatOneRow() {
        String rawToken = "logout-raw-token";
        RefreshToken token = new RefreshToken(user, sha256Hex(rawToken), UUID.randomUUID(), Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(token));

        tokenService.revokeRefreshToken(rawToken);

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository, never()).revokeAllByFamilyId(any(), any());
    }

    @Test
    void revokeRefreshTokenWithUnknownTokenIsANoOp() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        tokenService.revokeRefreshToken("unknown");

        verify(refreshTokenRepository, never()).revokeAllByFamilyId(any(), any());
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
