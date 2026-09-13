package com.application.ryft.identity.user.service;

import com.application.ryft.identity.user.dto.UserResponse;
import java.util.Optional;
import java.util.UUID;

public interface UserService {

    UserResponse getById(UUID userId);

    Optional<UserResponse> findByEmail(String email);
}
