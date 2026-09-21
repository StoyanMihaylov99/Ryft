package com.application.ryft.common.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock
    private JwtDecoder jwtDecoder;

    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(jwtDecoder);
    }

    @Test
    void connectWithValidTokenSetsTheAccessorUserToTheJwtSubject() {
        String userId = UUID.randomUUID().toString();
        Jwt jwt = validJwt(userId);
        when(jwtDecoder.decode("good-token")).thenReturn(jwt);

        Message<byte[]> message = connectMessage("Bearer good-token");

        interceptor.preSend(message, null);

        Principal user = StompHeaderAccessor.wrap(message).getUser();
        assertThat(user).isNotNull();
        assertThat(user.getName()).isEqualTo(userId);
    }

    @Test
    void connectWithMissingAuthorizationHeaderThrows() {
        Message<byte[]> message = connectMessage(null);

        assertThatThrownBy(() -> interceptor.preSend(message, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void connectWithNonBearerAuthorizationHeaderThrows() {
        Message<byte[]> message = connectMessage("Basic garbage");

        assertThatThrownBy(() -> interceptor.preSend(message, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void connectWithInvalidTokenThrows() {
        when(jwtDecoder.decode("bad-token")).thenThrow(new JwtException("invalid"));

        Message<byte[]> message = connectMessage("Bearer bad-token");

        assertThatThrownBy(() -> interceptor.preSend(message, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nonConnectCommandsPassThroughUnauthenticated() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<byte[]> message = org.springframework.messaging.support.MessageBuilder.withPayload(new byte[0])
                .setHeaders(accessor).build();

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
    }

    /**
     * {@code setLeaveMutable(true)} is essential here, not incidental: it's what makes the resulting
     * message's headers a live {@code MutableMessageHeaders}, the same shape the real inbound WebSocket
     * channel produces — without it, {@code MessageHeaderAccessor.getAccessor(message, ...)} inside the
     * interceptor would return a detached copy whose mutations never reach this test's message.
     */
    private Message<byte[]> connectMessage(String authorizationHeaderValue) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorizationHeaderValue != null) {
            accessor.addNativeHeader("Authorization", authorizationHeaderValue);
        }
        accessor.setLeaveMutable(true);
        // MessageBuilder.createMessage(payload, accessor.getMessageHeaders()), NOT
        // .setHeaders(accessor).build(): the latter calls accessor.toMessageHeaders(), which always
        // returns a *copy*, even with leaveMutable(true) — only getMessageHeaders() returns the live,
        // shared MutableMessageHeaders this test (and the interceptor's getAccessor lookup) needs.
        return org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0],
                accessor.getMessageHeaders());
    }

    private Jwt validJwt(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(60))
                .build();
    }
}
