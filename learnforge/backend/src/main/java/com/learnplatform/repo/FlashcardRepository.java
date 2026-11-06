package com.learnplatform.repo;

import com.learnplatform.domain.Flashcard;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlashcardRepository extends JpaRepository<Flashcard, UUID> {
    List<Flashcard> findByCourseIdOrderBySortOrderAsc(UUID courseId);
}
