package com.application.ryft.identity.service;

import com.application.ryft.identity.dto.LoginRequest;
import com.application.ryft.identity.dto.RegisterRequest;
import com.application.ryft.identity.exception.EmailAlreadyRegisteredException;
import com.application.ryft.identity.exception.InvalidCredentialsException;
import com.application.ryft.identity.exception.OAuthEmailConflictException;
import com.application.ryft.identity.exception.RefreshTokenReuseDetectedException;
import com.application.ryft.identity.repository.OAuthIdentityRepository;
import com.application.ryft.identity.repository.UserRepository;
import com.application.ryft.identity.repository.entity.OAuthIdentity;
import com.application.ryft.identity.repository.entity.User;
import com.application.ryft.identity.security.OAuthUserInfo;

import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OAuthIdentityRepository oAuthIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserRepository userRepository, OAuthIdentityRepository oAuthIdentityRepository,
                       PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userRepository = userRepository;
        this.oAuthIdentityRepository = oAuthIdentityRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }
        User user = new User(email, passwordEncoder.encode(request.password()), request.displayName(), null);
        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow(InvalidCredentialsException::new);
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(user);
    }

    /**
     * {@code noRollbackFor} must be repeated here even though {@link TokenService#rotateRefreshToken}
     * already declares it: with default REQUIRED propagation this method — not TokenService's, since
     * it's the outermost {@code @Transactional} boundary reached from the (non-transactional)
     * controller — owns the actual commit/rollback decision for the shared transaction. Without this,
     * this method's own default rollback-on-RuntimeException rule would still discard the family-wide
     * revocation TokenService just performed.
     */
    @Transactional(noRollbackFor = RefreshTokenReuseDetectedException.class)
    public AuthResult refresh(String rawRefreshToken) {
        RefreshTokenRotationResult rotation = tokenService.rotateRefreshToken(rawRefreshToken);
        IssuedAccessToken accessToken = tokenService.issueAccessToken(rotation.user());
        return new AuthResult(accessToken, rotation.refreshToken(), UserServiceImpl.toDTO(rotation.user()));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        tokenService.revokeRefreshToken(rawRefreshToken);
    }

    /**
     * Finds or creates the local account for a successful Google/GitHub login and issues our own
     * tokens exactly as register/login do. Matching an OAuth identity to an existing email/password
     * account only happens when the provider reports the email as verified (see
     * {@link OAuthEmailConflictException}).
     */
    @Transactional
    public AuthResult loginOrRegisterOAuthUser(OAuthUserInfo info) {
        Optional<OAuthIdentity> existingIdentity =
                oAuthIdentityRepository.findByProviderAndProviderUserId(info.provider(), info.providerUserId());

        User user = existingIdentity.isPresent()
                ? loginExistingOAuthUser(existingIdentity.get(), info)
                : registerNewOAuthUser(info);
        return issueTokens(user);
    }

    private User loginExistingOAuthUser(OAuthIdentity identity, OAuthUserInfo info) {
        User user = identity.getUser();
        refreshProfileFromProvider(user, info);
        return user;
    }

    private User registerNewOAuthUser(OAuthUserInfo info) {
        User user = resolveUserForNewIdentity(info);
        oAuthIdentityRepository.save(
                new OAuthIdentity(user, info.provider(), info.providerUserId(), normalizeEmail(info.email())));
        return user;
    }

    private User resolveUserForNewIdentity(OAuthUserInfo info) {
        String email = normalizeEmail(info.email());
        return findVerifiedUserForLinking(info, email)
                .orElseGet(() -> createUserForNewIdentity(info, email));
    }

    private Optional<User> findVerifiedUserForLinking(OAuthUserInfo info, String email) {
        Optional<User> existingUser = userRepository.findByEmailIgnoreCase(email);
        if (existingUser.isPresent() && !info.emailVerified()) {
            throw new OAuthEmailConflictException(email);
        }
        return existingUser;
    }

    private User createUserForNewIdentity(OAuthUserInfo info, String email) {
        User user = new User(email, null, info.displayName(), info.avatarUrl());
        userRepository.save(user);
        return user;
    }

    private void refreshProfileFromProvider(User user, OAuthUserInfo info) {
        if (info.displayName() != null && !info.displayName().isBlank()) {
            user.setDisplayName(info.displayName());
        }
        if (info.avatarUrl() != null && !info.avatarUrl().isBlank()) {
            user.setAvatarUrl(info.avatarUrl());
        }
    }

    private AuthResult issueTokens(User user) {
        IssuedAccessToken accessToken = tokenService.issueAccessToken(user);
        IssuedRefreshToken refreshToken = tokenService.issueRefreshToken(user);
        return new AuthResult(accessToken, refreshToken, UserServiceImpl.toDTO(user));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
