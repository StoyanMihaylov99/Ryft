package com.application.ryft.identity.security.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.refresh-token")
public record RefreshTokenProperties(
        Duration ttl,
        String cookieName,
        String cookiePath,
        boolean cookieSecure,
        Duration reuseGracePeriod
) {
}
