package com.application.ryft.sprints.service;

import com.application.ryft.sprints.dto.VelocityResponse;
import java.util.UUID;

public interface VelocityService {

    VelocityResponse getVelocity(UUID callerId, String projectKey, int limit);
}
