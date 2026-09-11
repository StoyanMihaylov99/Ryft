package com.application.ryft.identity.security.oauth;

import com.application.ryft.identity.oauth.entity.OAuthProvider;

public record OAuthUserInfo(
        OAuthProvider provider,
        String providerUserId,
        String email,
        boolean emailVerified,
        String displayName,
        String avatarUrl
) {
}
