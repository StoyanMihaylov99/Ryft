package com.application.ryft.common.websocket;

import com.application.ryft.common.config.CorsProperties;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Single-instance STOMP broker (see ARCHITECTURE.md's "v1 scope note" — no Redis relay yet): the
 * {@code SimpleBroker} holds every WebSocket session directly in this JVM and broadcasts to them
 * itself, which is all a single-instance deployment needs.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final CorsProperties corsProperties;
    private final JsonMapper jsonMapper;

    public WebSocketConfig(StompAuthChannelInterceptor stompAuthChannelInterceptor, CorsProperties corsProperties,
            JsonMapper jsonMapper) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
        this.corsProperties = corsProperties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(new String[0]));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    /**
     * Reuses the app's Boot-managed {@link JsonMapper} bean (the same one backing every REST response)
     * rather than letting {@code JacksonJsonMessageConverter}'s default constructor build its own fresh
     * one — keeps WebSocket payload serialization (date/time formatting, naming, etc.) identical to the
     * REST API's, so {@code BoardUpdateMessage}/{@code NotificationResponse} serialize the same way over
     * both transports. Returning {@code false} tells Spring not to also register its own defaults, so
     * {@link StringMessageConverter}/{@link ByteArrayMessageConverter} are added back explicitly to keep
     * the rest of the default behavior (e.g. plain-text STOMP frames) unchanged.
     */
    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        messageConverters.add(new StringMessageConverter());
        messageConverters.add(new ByteArrayMessageConverter());
        messageConverters.add(new JacksonJsonMessageConverter(jsonMapper));
        return false;
    }
}
