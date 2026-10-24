package com.learnplatform.repo;

import com.learnplatform.domain.Simulation;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationRepository extends JpaRepository<Simulation, UUID> {
    List<Simulation> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
