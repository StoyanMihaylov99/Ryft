package com.application.ryft.identity.service;

import com.application.ryft.identity.dto.UserDTO;
import com.application.ryft.identity.repository.UserRepository;
import com.application.ryft.identity.repository.entity.User;
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

    static UserDTO toDTO(User user) {
        return new UserDTO(user.getId(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl());
    }
}
