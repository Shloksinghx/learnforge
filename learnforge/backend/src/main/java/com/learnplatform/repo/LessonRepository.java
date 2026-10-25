package com.learnplatform.repo;

import com.learnplatform.domain.Lesson;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {
    List<Lesson> findByCourseIdOrderBySortOrderAsc(UUID courseId);
    int countByCourseId(UUID courseId);
}
