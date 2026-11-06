package com.learnplatform.repo;

import com.learnplatform.domain.Scenario;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioRepository extends JpaRepository<Scenario, UUID> {
    List<Scenario> findByOrgIdOrderByCreatedAtDesc(UUID orgId);
}
