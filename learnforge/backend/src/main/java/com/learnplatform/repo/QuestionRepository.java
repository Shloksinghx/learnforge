package com.learnplatform.repo;

import com.learnplatform.domain.Question;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, UUID> {
    List<Question> findByCourseIdOrderByCreatedAtDesc(UUID courseId);
    List<Question> findByCourseIdAndStatus(UUID courseId, String status);
    int countByCourseIdAndStatus(UUID courseId, String status);
}
