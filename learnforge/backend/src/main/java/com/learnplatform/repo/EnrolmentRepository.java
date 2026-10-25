package com.learnplatform.repo;

import com.learnplatform.domain.Enrolment;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrolmentRepository extends JpaRepository<Enrolment, UUID> {
    Optional<Enrolment> findByCourseIdAndUserId(UUID courseId, UUID userId);
    List<Enrolment> findByUserId(UUID userId);
}
