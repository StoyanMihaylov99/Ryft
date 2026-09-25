package com.application.ryft.seed.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code enabled} gates {@code DemoDataSeeder} entirely (see its javadoc for why a property was chosen
 * over a Spring profile). {@code demoPassword} is the password issued to the {@code demo@ryft.dev}
 * account — overridable per environment (e.g. a real deployed demo instance) via
 * {@code SEED_DEMO_PASSWORD}, same env-var-driven-default convention as every other {@code app.*}
 * property in {@code application.yaml}.
 */
@ConfigurationProperties(prefix = "app.seed")
public record DemoSeedProperties(
        boolean enabled,
        String demoPassword
) {
}
