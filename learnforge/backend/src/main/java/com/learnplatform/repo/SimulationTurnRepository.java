package com.learnplatform.repo;

import com.learnplatform.domain.SimulationTurn;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationTurnRepository extends JpaRepository<SimulationTurn, UUID> {
    List<SimulationTurn> findBySimulationIdOrderByCreatedAtAsc(UUID simulationId);
}
