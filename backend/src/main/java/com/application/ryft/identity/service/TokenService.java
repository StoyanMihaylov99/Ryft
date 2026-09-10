package com.application.ryft.identity.service;

import com.application.ryft.identity.config.JwtProperties;
import com.application.ryft.identity.config.RefreshTokenProperties;
import com.application.ryft.identity.exception.InvalidRefreshTokenException;
import com.application.ryft.identity.exception.RefreshTokenReuseDetectedException;
import com.application.ryft.identity.repository.RefreshTokenRepository;
import com.application.ryft.identity.repository.entity.RefreshToken;
import com.application.ryft.identity.repository.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {

    private static final String ISSUER = "ryft";
    private static final int RAW_TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenProperties refreshTokenProperties;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(RefreshTokenRepository refreshTokenRepository, JwtEncoder jwtEncoder,
            JwtProperties jwtProperties, RefreshTokenProperties refreshTokenProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.refreshTokenProperties = refreshTokenProperties;
    }

    public IssuedAccessToken issueAccessToken(User user) {
        Instant now = Instant.now();
        Duration ttl = jwtProperties.accessTokenTtl();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new IssuedAccessToken(value, ttl.toSeconds());
    }

    /** Starts a brand-new rotation family — used at login/register/OAuth provisioning. */
    @Transactional
    public IssuedRefreshToken issueRefreshToken(User user) {
        RefreshTokenRow row = createRow(user, UUID.randomUUID());
        return row.toIssuedRefreshToken();
    }

    /**
     * Validates the presented raw refresh token and rotates it: the presented token is revoked and
     * a new one is issued in its place (same family). If the presented token was already revoked —
     * i.e. it's being reused — the whole family is revoked and re-login is forced, UNLESS this looks
     * like two browser tabs racing the same legitimate rotation (see the grace-period branch below).
     *
     * <p>{@code noRollbackFor} matters here: the theft-detected path both mutates the DB (revokes the
     * whole family) AND throws, and Spring's default behavior is to roll back the transaction on any
     * unchecked exception — which would silently undo the very revocation this method exists to
     * perform. The revocation must commit even though the caller still sees the exception.
     */
    @Transactional(noRollbackFor = RefreshTokenReuseDetectedException.class)
    public RefreshTokenRotationResult rotateRefreshToken(String rawToken) {
        Instant now = Instant.now();
        RefreshToken presented = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Unknown refresh token"));

        if (presented.isRevoked()) {
            return handleReuse(presented, now);
        }
        if (presented.isExpired(now)) {
            throw new InvalidRefreshTokenException("Refresh token expired");
        }
        return rotate(presented, now);
    }

    /** Revokes a single token (logout) — leaves other devices/tabs in the same family untouched. */
    @Transactional
    public void revokeRefreshToken(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.revoke(Instant.now()));
    }

    private RefreshTokenRotationResult handleReuse(RefreshToken presented, Instant now) {
        boolean withinGracePeriod = presented.getRevokedAt() != null
                && !Duration.between(presented.getRevokedAt(), now).isNegative()
                && Duration.between(presented.getRevokedAt(), now).compareTo(refreshTokenProperties.reuseGracePeriod()) <= 0;

        if (withinGracePeriod) {
            // Most likely a second tab presenting a token that another tab already rotated a moment
            // ago, not real theft. Rotate from whatever is currently live in the family instead of
            // treating it as an attack, so neither tab gets logged out.
            List<RefreshToken> stillActive = refreshTokenRepository.findByFamilyIdAndRevokedAtIsNull(presented.getFamilyId());
            if (!stillActive.isEmpty()) {
                return rotate(stillActive.get(0), now);
            }
        }

        refreshTokenRepository.revokeAllByFamilyId(presented.getFamilyId(), now);
        throw new RefreshTokenReuseDetectedException();
    }

    private RefreshTokenRotationResult rotate(RefreshToken current, Instant now) {
        current.revoke(now);
        RefreshTokenRow next = createRow(current.getUser(), current.getFamilyId());
        current.markReplacedBy(next.entity().getId());
        return new RefreshTokenRotationResult(current.getUser(), next.toIssuedRefreshToken());
    }

    private RefreshTokenRow createRow(User user, UUID familyId) {
        String rawToken = generateRawToken();
        Instant expiresAt = Instant.now().plus(refreshTokenProperties.ttl());
        RefreshToken entity = new RefreshToken(user, hash(rawToken), familyId, expiresAt);
        refreshTokenRepository.save(entity);
        return new RefreshTokenRow(entity, rawToken);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private record RefreshTokenRow(RefreshToken entity, String rawValue) {
        IssuedRefreshToken toIssuedRefreshToken() {
            return new IssuedRefreshToken(rawValue, entity.getExpiresAt());
        }
    }
}
