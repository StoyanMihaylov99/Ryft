package com.application.ryft.identity.security.config;

import com.application.ryft.identity.security.handler.OAuth2LoginFailureHandler;
import com.application.ryft.identity.security.handler.OAuth2LoginSuccessHandler;
import com.application.ryft.identity.security.oauth.GitHubOAuth2UserService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
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
 *
 * <p><strong>Actuator/observability endpoints</strong> ({@code /actuator/health}, including its
 * {@code liveness}/{@code readiness} probe sub-paths, {@code /actuator/prometheus},
 * {@code /actuator/info}) are {@code permitAll} here too, via {@link EndpointRequest#toAnyEndpoint()}
 * — safe precisely because {@code management.endpoints.web.exposure.include} (see
 * application.yaml) only exposes those three; nothing else is reachable regardless of this rule.
 * This one {@code SecurityFilterChain} bean is what secures <em>both</em> the public API port
 * ({@code server.port}, 8080) and the separate management port ({@code management.server.port},
 * 8081) — Spring Boot's management child {@code ApplicationContext} resolves its own
 * {@code SecurityFilterChain} bean by looking up through its parent, so it finds and reuses this
 * exact bean rather than falling back to Boot's own default (HTTP Basic with a generated password).
 * Actuator's request mappings themselves are registered <em>only</em> in that child context's own
 * {@code PathMappedEndpoints}/{@code DispatcherServlet}, bound only to port 8081 — {@link
 * EndpointRequest#toAnyEndpoint()} consults that same context-scoped bean to decide whether a
 * request matches, so on port 8080 (root context, zero mapped endpoints) it correctly evaluates to
 * "no match" and the request falls through to {@code anyRequest().authenticated()} (a plain 401,
 * verified locally — not even a 404, so an unauthenticated caller on the public port can't
 * distinguish "wrong path" from "no auth"), while on port 8081 (child context, health/prometheus/info
 * mapped) it matches and permits. One bean, two ports, two different real outcomes — exactly what
 * keeps metrics off the public port in a deployment that publishes 8080 but not 8081. See
 * ARCHITECTURE.md's Observability section for the full port-vs-filter-chain rationale, including why
 * a same-port-only fix was rejected.
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
                        // The WS handshake HTTP request carries no JWT by design (browsers can't set an
                        // Authorization header on it) — real auth happens on the STOMP CONNECT frame
                        // itself, via StompAuthChannelInterceptor, not this filter chain.
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(gitHubOAuth2UserService))
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(oAuth2LoginFailureHandler));
        return http.build();
    }
}
