package com.application.ryft.identity.security;

import com.application.ryft.identity.config.JwtProperties;
import com.application.ryft.identity.config.OAuth2RedirectProperties;
import com.application.ryft.identity.config.RefreshTokenProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * permitAll: the four password-auth endpoints plus the OAuth2 redirect/callback paths Spring
 * Security auto-registers. Everything else requires a valid bearer JWT. Session creation is
 * IF_REQUIRED rather than STATELESS: Spring's default OAuth2 authorization-request repository needs
 * an HttpSession to bridge the redirect -> provider -> callback gap; plain JWT bearer calls never
 * create one.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, RefreshTokenProperties.class, OAuth2RedirectProperties.class})
public class SecurityConfig {

    private final GitHubOAuth2UserService gitHubOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(GitHubOAuth2UserService gitHubOAuth2UserService,
            OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
            OAuth2LoginFailureHandler oAuth2LoginFailureHandler,
            CorsConfigurationSource corsConfigurationSource) {
        this.gitHubOAuth2UserService = gitHubOAuth2UserService;
        this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
        this.oAuth2LoginFailureHandler = oAuth2LoginFailureHandler;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/code/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(gitHubOAuth2UserService))
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(oAuth2LoginFailureHandler));
        return http.build();
    }
}
