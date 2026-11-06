package com.learnplatform.repo;

import com.learnplatform.domain.LearningPathStep;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningPathStepRepository extends JpaRepository<LearningPathStep, UUID> {
    List<LearningPathStep> findByPathIdOrderBySortOrderAsc(UUID pathId);
}
