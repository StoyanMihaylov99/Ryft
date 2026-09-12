package com.application.ryft.projects.repository;

import com.application.ryft.projects.entity.ProjectMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    List<ProjectMember> findAllByProjectIdOrderByAddedAtAsc(UUID projectId);

    List<ProjectMember> findAllByUserIdOrderByAddedAtAsc(UUID userId);
}
