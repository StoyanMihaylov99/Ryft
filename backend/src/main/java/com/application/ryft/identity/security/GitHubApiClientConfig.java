package com.application.ryft.identity.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class GitHubApiClientConfig {

    @Bean
    public RestClient githubRestClient() {
        return RestClient.builder().baseUrl("https://api.github.com").build();
    }
}
