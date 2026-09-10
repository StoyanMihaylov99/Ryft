package com.application.ryft.identity.repository;

import com.application.ryft.identity.repository.entity.OAuthIdentity;
import com.application.ryft.identity.repository.entity.OAuthProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity, UUID> {

    Optional<OAuthIdentity> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);
}
