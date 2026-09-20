package com.application.ryft.sprints.service;

import com.application.ryft.sprints.dto.BurndownResponse;
import java.util.UUID;

public interface BurndownService {

    BurndownResponse getBurndown(UUID callerId, UUID sprintId);
}
