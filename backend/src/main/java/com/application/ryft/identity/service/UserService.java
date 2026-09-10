package com.application.ryft.identity.service;

import com.application.ryft.identity.dto.UserDTO;
import java.util.UUID;

public interface UserService {

    UserDTO getById(UUID userId);
}
