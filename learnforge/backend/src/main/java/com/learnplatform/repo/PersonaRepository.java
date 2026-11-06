package com.learnplatform.repo;

import com.learnplatform.domain.Persona;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonaRepository extends JpaRepository<Persona, UUID> {
    List<Persona> findByOrgIdOrderByCreatedAtDesc(UUID orgId);
    List<Persona> findBySharedTrueAndOrgIdNot(UUID orgId);
}
