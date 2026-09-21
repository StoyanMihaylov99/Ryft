package com.application.ryft.activity.repository;

import com.application.ryft.activity.entity.ActivityEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ActivityEventRepository extends MongoRepository<ActivityEvent, String> {

    List<ActivityEvent> findAllByProjectIdAndIssueIdOrderByTimestampAsc(UUID projectId, UUID issueId);
}
