package com.learnplatform.repo;

import com.learnplatform.domain.LearningPath;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningPathRepository extends JpaRepository<LearningPath, UUID> {
    List<LearningPath> findByOrgIdOrderByCreatedAtDesc(UUID orgId);
}
