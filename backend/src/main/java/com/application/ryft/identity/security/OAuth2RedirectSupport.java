package com.application.ryft.identity.security;

import com.application.ryft.identity.security.config.OAuth2RedirectProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/** Shared redirect-target logic for {@link OAuth2LoginSuccessHandler} and {@link OAuth2LoginFailureHandler}. */
@Component
public class OAuth2RedirectSupport {

    private final OAuth2RedirectProperties properties;

    public OAuth2RedirectSupport(OAuth2RedirectProperties properties) {
        this.properties = properties;
    }

    public void redirectToSuccess(HttpServletResponse response) throws IOException {
        response.sendRedirect(properties.successRedirectUri());
    }

    /** No token values ever go in this URL — only an error code the frontend can display. */
    public void redirectToFailure(HttpServletResponse response, String errorCode) throws IOException {
        String separator = properties.failureRedirectUri().contains("?") ? "&" : "?";
        String url = properties.failureRedirectUri() + separator + "error="
                + URLEncoder.encode(errorCode, StandardCharsets.UTF_8);
        response.sendRedirect(url);
    }
}
