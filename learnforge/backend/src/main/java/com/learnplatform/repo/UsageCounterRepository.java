package com.learnplatform.repo;

import com.learnplatform.domain.UsageCounter;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageCounterRepository extends JpaRepository<UsageCounter, UUID> {
    Optional<UsageCounter> findByOrgIdAndMonthAndMetric(UUID orgId, String month, String metric);
    List<UsageCounter> findByOrgIdAndMonth(UUID orgId, String month);
}
