package com.application.ryft.identity.user.service;

import com.application.ryft.identity.user.dto.UserResponse;
import com.application.ryft.identity.user.repository.UserRepository;
import com.application.ryft.identity.user.entity.User;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserResponse getById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        return toResponse(user);
    }

    @Override
    public Optional<UserResponse> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email).map(UserServiceImpl::toResponse);
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl());
    }
}
