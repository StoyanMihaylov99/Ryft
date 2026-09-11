package com.application.ryft.identity.user.service;

import com.application.ryft.identity.user.dto.UserDTO;
import java.util.UUID;

public interface UserService {

    UserDTO getById(UUID userId);
}
