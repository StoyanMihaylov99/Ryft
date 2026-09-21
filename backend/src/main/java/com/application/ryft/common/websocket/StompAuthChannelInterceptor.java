package com.application.ryft.common.websocket;

import java.util.List;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Authenticates the STOMP {@code CONNECT} frame itself, since the WebSocket handshake's HTTP request
 * carries no JWT by design (browsers can't set an {@code Authorization} header on a WS handshake) — see
 * {@code SecurityConfig}'s {@code /ws/**} {@code permitAll()} and its javadoc for why the HTTP filter
 * chain is deliberately not the trust boundary here.
 *
 * <p>The principal name is set to {@code jwt.getSubject()} (the user's UUID as a string), not the JWT
 * itself or some other display value — {@code SimpMessagingTemplate.convertAndSendToUser(userId, ...)}
 * matches purely on this principal name, so getting it wrong here would silently break every
 * personal-queue notification push.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // MessageHeaderAccessor.getAccessor (not StompHeaderAccessor.wrap) is required here: the inbound
        // WebSocket message channel builds this message with a mutable header map specifically so a
        // channel interceptor can mutate it in place — wrap() would instead copy the headers into a new
        // accessor whose mutations never make it back onto the message actually sent downstream.
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String authorizationHeader = accessor.getFirstNativeHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("Missing bearer token on STOMP CONNECT");
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        try {
            Jwt jwt = jwtDecoder.decode(token);
            accessor.setUser(new JwtAuthenticationToken(jwt, List.of(), jwt.getSubject()));
        } catch (JwtException e) {
            throw new AccessDeniedException("Invalid bearer token on STOMP CONNECT", e);
        }
        return message;
    }
}
