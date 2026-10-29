package com.learnplatform.repo;

import com.learnplatform.domain.LessonCompletion;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonCompletionRepository extends JpaRepository<LessonCompletion, UUID> {
    boolean existsByLessonIdAndUserId(UUID lessonId, UUID userId);
    List<LessonCompletion> findByUserIdAndLessonIdIn(UUID userId, Collection<UUID> lessonIds);
    List<LessonCompletion> findByLessonIdIn(Collection<UUID> lessonIds);
}
