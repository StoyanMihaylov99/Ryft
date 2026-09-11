package com.application.ryft.identity.security.handler;

import com.application.ryft.identity.security.OAuth2RedirectSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);

    private final OAuth2RedirectSupport redirectSupport;

    public OAuth2LoginFailureHandler(OAuth2RedirectSupport redirectSupport) {
        this.redirectSupport = redirectSupport;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        log.warn("OAuth2 login failed: {}", exception.getMessage());
        redirectSupport.redirectToFailure(response, "oauth_login_failed");
    }
}
