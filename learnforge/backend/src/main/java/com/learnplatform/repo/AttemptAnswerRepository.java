package com.learnplatform.repo;

import com.learnplatform.domain.AttemptAnswer;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, UUID> {
    List<AttemptAnswer> findByAttemptIdOrderBySortOrderAsc(UUID attemptId);
}
