package com.learnplatform.repo;

import com.learnplatform.domain.Course;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    List<Course> findByOrgIdOrderByCreatedAtDesc(UUID orgId);
}
