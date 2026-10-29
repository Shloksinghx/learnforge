package com.learnplatform.repo;

import com.learnplatform.domain.Attempt;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptRepository extends JpaRepository<Attempt, UUID> {
    List<Attempt> findByUserIdOrderByCreatedAtDesc(UUID userId);
    boolean existsByCourseIdAndUserIdAndStatus(UUID courseId, UUID userId, String status);
    List<Attempt> findByOrgIdAndStatus(UUID orgId, String status);
}
