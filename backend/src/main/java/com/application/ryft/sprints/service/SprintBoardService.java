package com.application.ryft.sprints.service;

import com.application.ryft.sprints.dto.SprintBoardResponse;
import java.util.UUID;

public interface SprintBoardService {

    SprintBoardResponse getBoard(UUID callerId, String projectKey);
}
