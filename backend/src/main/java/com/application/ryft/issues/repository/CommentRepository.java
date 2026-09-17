package com.application.ryft.issues.repository;

import com.application.ryft.issues.entity.Comment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    @Query("select c from Comment c where c.issue.id = :issueId order by c.createdAt asc")
    List<Comment> findAllByIssueIdOrderByCreatedAtAsc(@Param("issueId") UUID issueId);

    void deleteAllByIssueId(UUID issueId);
}
