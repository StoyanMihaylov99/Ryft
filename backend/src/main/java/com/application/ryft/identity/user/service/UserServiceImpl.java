package com.application.ryft.identity.user.service;

import com.application.ryft.identity.user.dto.UserDTO;
import com.application.ryft.identity.user.repository.UserRepository;
import com.application.ryft.identity.user.entity.User;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDTO getById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        return toDTO(user);
    }

    public static UserDTO toDTO(User user) {
        return new UserDTO(user.getId(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl());
    }
}
