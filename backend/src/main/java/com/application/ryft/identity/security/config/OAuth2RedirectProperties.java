package com.application.ryft.identity.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.oauth2")
public record OAuth2RedirectProperties(String successRedirectUri, String failureRedirectUri) {
}
