package com.application.ryft.issues.service;

import com.application.ryft.issues.dto.BoardResponse;
import java.util.UUID;

public interface BoardService {

    BoardResponse getBoard(UUID callerId, String projectKey);
}
