package com.learnplatform.repo;

import com.learnplatform.domain.MockTest;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MockTestRepository extends JpaRepository<MockTest, UUID> {
    List<MockTest> findByCourseIdOrderByCreatedAtDesc(UUID courseId);
}
