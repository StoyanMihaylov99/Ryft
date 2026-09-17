package com.application.ryft.identity.user.service;

import com.application.ryft.identity.user.dto.UserResponse;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface UserService {

    UserResponse getById(UUID userId);

    Optional<UserResponse> findByEmail(String email);

    /** Batches the lookup into a single query, keyed by id, so callers can resolve many users at once. */
    Map<UUID, UserResponse> findAllByIds(Collection<UUID> userIds);
}
