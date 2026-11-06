package com.learnplatform.repo;

import com.learnplatform.domain.CourseDocument;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseDocumentRepository extends JpaRepository<CourseDocument, UUID> {
    List<CourseDocument> findByCourseIdOrderByCreatedAtDesc(UUID courseId);
}
