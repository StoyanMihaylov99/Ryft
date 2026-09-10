package com.application.ryft.identity.security;

import com.application.ryft.identity.repository.entity.OAuthProvider;

public record OAuthUserInfo(
        OAuthProvider provider,
        String providerUserId,
        String email,
        boolean emailVerified,
        String displayName,
        String avatarUrl
) {
}
