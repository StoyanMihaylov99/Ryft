package com.application.ryft.identity.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.application.ryft.identity.auth.dto.LoginRequest;
import com.application.ryft.identity.auth.dto.RegisterRequest;
import com.application.ryft.identity.auth.exception.EmailAlreadyRegisteredException;
import com.application.ryft.identity.auth.exception.InvalidCredentialsException;
import com.application.ryft.identity.oauth.exception.OAuthEmailConflictException;
import com.application.ryft.identity.oauth.repository.OAuthIdentityRepository;
import com.application.ryft.identity.user.repository.UserRepository;
import com.application.ryft.identity.oauth.entity.OAuthIdentity;
import com.application.ryft.identity.oauth.entity.OAuthProvider;
import com.application.ryft.identity.user.entity.User;
import com.application.ryft.identity.security.oauth.OAuthUserInfo;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OAuthIdentityRepository oAuthIdentityRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, oAuthIdentityRepository, passwordEncoder, tokenService);
        // lenient: several tests (login, logout, refresh, conflict/rejection paths) never reach a
        // save() call, and a shared @BeforeEach stub would otherwise trip Mockito's strict-stubbing check.
        lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(oAuthIdentityRepository.save(any(OAuthIdentity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubTokenIssuance() {
        when(tokenService.issueAccessToken(any())).thenReturn(new IssuedAccessToken("access-tok", 900));
        when(tokenService.issueRefreshToken(any()))
                .thenReturn(new IssuedRefreshToken("refresh-tok", Instant.now().plusSeconds(2_592_000)));
    }

    @Test
    void registerNormalizesEmailAndHashesPassword() {
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-hash");
        stubTokenIssuance();

        AuthResult result = authService.register(new RegisterRequest("New@Example.com", "password123", "New User"));

        assertThat(result.user().email()).isEqualTo("new@example.com");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("encoded-hash");
    }

    @Test
    void registerWithExistingEmailThrowsAndNeverSaves() {
        when(userRepository.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("taken@example.com", "password123", "Name")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithCorrectPasswordSucceeds() {
        User user = new User("user@example.com", "encoded-hash", "Name", null);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded-hash")).thenReturn(true);
        stubTokenIssuance();

        AuthResult result = authService.login(new LoginRequest("user@example.com", "password123"));

        assertThat(result.user().email()).isEqualTo("user@example.com");
    }

    @Test
    void loginWithWrongPasswordThrows() {
        User user = new User("user@example.com", "encoded-hash", "Name", null);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginWithUnknownEmailThrowsSameExceptionAsWrongPassword() {
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "password123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginForOAuthOnlyAccountWithNoPasswordThrowsWithoutCallingPasswordEncoder() {
        User oauthOnlyUser = new User("oauth@example.com", null, "OAuth User", null);
        when(userRepository.findByEmailIgnoreCase("oauth@example.com")).thenReturn(Optional.of(oauthOnlyUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("oauth@example.com", "whatever")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void refreshDelegatesToTokenServiceAndBuildsNewAuthResult() {
        User user = new User("refresh@example.com", "hash", "Name", null);
        IssuedRefreshToken newRefresh = new IssuedRefreshToken("new-raw", Instant.now().plusSeconds(100));
        when(tokenService.rotateRefreshToken("old-raw"))
                .thenReturn(new RefreshTokenRotationResult(user, newRefresh));
        when(tokenService.issueAccessToken(user)).thenReturn(new IssuedAccessToken("new-access", 900));

        AuthResult result = authService.refresh("old-raw");

        assertThat(result.refreshToken().rawValue()).isEqualTo("new-raw");
        assertThat(result.accessToken().value()).isEqualTo("new-access");
    }

    @Test
    void logoutDelegatesToTokenService() {
        authService.logout("raw-token");

        verify(tokenService).revokeRefreshToken("raw-token");
    }

    @Test
    void oAuthLoginForBrandNewUserCreatesUserAndIdentity() {
        when(oAuthIdentityRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-123"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("new.user@example.com")).thenReturn(Optional.empty());
        stubTokenIssuance();

        OAuthUserInfo info = new OAuthUserInfo(OAuthProvider.GOOGLE, "google-123", "New.User@Example.com", true,
                "New User", "http://avatar");
        authService.loginOrRegisterOAuthUser(info);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("new.user@example.com");
        assertThat(userCaptor.getValue().getPasswordHash()).isNull();

        ArgumentCaptor<OAuthIdentity> identityCaptor = ArgumentCaptor.forClass(OAuthIdentity.class);
        verify(oAuthIdentityRepository).save(identityCaptor.capture());
        assertThat(identityCaptor.getValue().getProviderUserId()).isEqualTo("google-123");
    }

    @Test
    void oAuthLoginWithExistingIdentityReusesUserAndRefreshesProfile() {
        User existingUser = new User("existing@example.com", "hash", "Old Name", null);
        OAuthIdentity identity = new OAuthIdentity(existingUser, OAuthProvider.GOOGLE, "google-999", "existing@example.com");
        when(oAuthIdentityRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-999"))
                .thenReturn(Optional.of(identity));
        stubTokenIssuance();

        OAuthUserInfo info = new OAuthUserInfo(OAuthProvider.GOOGLE, "google-999", "existing@example.com", true,
                "New Display Name", "http://new-avatar");
        authService.loginOrRegisterOAuthUser(info);

        assertThat(existingUser.getDisplayName()).isEqualTo("New Display Name");
        assertThat(existingUser.getAvatarUrl()).isEqualTo("http://new-avatar");
        verify(userRepository, never()).save(any());
        verify(oAuthIdentityRepository, never()).save(any());
    }

    @Test
    void oAuthLoginWithVerifiedEmailLinksOntoExistingPasswordAccount() {
        User existingUser = new User("shared@example.com", "hash", "Password User", null);
        when(oAuthIdentityRepository.findByProviderAndProviderUserId(OAuthProvider.GITHUB, "gh-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("shared@example.com")).thenReturn(Optional.of(existingUser));
        stubTokenIssuance();

        OAuthUserInfo info = new OAuthUserInfo(OAuthProvider.GITHUB, "gh-1", "shared@example.com", true, "GH Name", null);
        authService.loginOrRegisterOAuthUser(info);

        verify(userRepository, never()).save(any());
        ArgumentCaptor<OAuthIdentity> identityCaptor = ArgumentCaptor.forClass(OAuthIdentity.class);
        verify(oAuthIdentityRepository).save(identityCaptor.capture());
        assertThat(identityCaptor.getValue().getUser()).isEqualTo(existingUser);
    }

    @Test
    void oAuthLoginWithUnverifiedEmailConflictingWithExistingAccountThrowsWithoutLinking() {
        User existingUser = new User("conflict@example.com", "hash", "Real User", null);
        when(oAuthIdentityRepository.findByProviderAndProviderUserId(OAuthProvider.GITHUB, "gh-2"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("conflict@example.com")).thenReturn(Optional.of(existingUser));

        OAuthUserInfo info = new OAuthUserInfo(OAuthProvider.GITHUB, "gh-2", "conflict@example.com", false, "Fake Name", null);

        assertThatThrownBy(() -> authService.loginOrRegisterOAuthUser(info))
                .isInstanceOf(OAuthEmailConflictException.class);
        verify(userRepository, never()).save(any());
        verify(oAuthIdentityRepository, never()).save(any());
    }

    @Test
    void oAuthLoginWithUnverifiedEmailAndNoExistingAccountStillCreatesNewUser() {
        when(oAuthIdentityRepository.findByProviderAndProviderUserId(eq(OAuthProvider.GITHUB), anyString()))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("brandnew@example.com")).thenReturn(Optional.empty());
        stubTokenIssuance();

        OAuthUserInfo info = new OAuthUserInfo(OAuthProvider.GITHUB, "gh-3", "brandnew@example.com", false, "New Person", null);
        authService.loginOrRegisterOAuthUser(info);

        verify(userRepository).save(any());
    }
}
