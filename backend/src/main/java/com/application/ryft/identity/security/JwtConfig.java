package com.application.ryft.identity.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Access tokens are signed with an RSA keypair generated fresh at every application startup — fine
 * for a single-instance app. A restart invalidates outstanding access tokens only (the client's
 * interceptor transparently refreshes); refresh tokens stay valid since they're persisted in
 * Postgres, not derived from this key. Would need externalized/shared key material if ever
 * horizontally scaled.
 */
@Configuration
public class JwtConfig {

    @Bean
    public KeyPair jwtSigningKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key generation algorithm unavailable", e);
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(KeyPair jwtSigningKeyPair) {
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) jwtSigningKeyPair.getPublic())
                .privateKey((RSAPrivateKey) jwtSigningKeyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(jwkSet));
    }

    @Bean
    public JwtDecoder jwtDecoder(KeyPair jwtSigningKeyPair) {
        return NimbusJwtDecoder.withPublicKey((RSAPublicKey) jwtSigningKeyPair.getPublic()).build();
    }
}
