package com.learnplatform.repo;

import com.learnplatform.domain.VoiceAssessment;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceAssessmentRepository extends JpaRepository<VoiceAssessment, UUID> {
    List<VoiceAssessment> findByCourseIdAndUserIdOrderByCreatedAtDesc(UUID courseId, UUID userId);
}
