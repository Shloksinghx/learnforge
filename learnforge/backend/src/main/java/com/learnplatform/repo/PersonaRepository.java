package com.learnplatform.repo;

import com.learnplatform.domain.Persona;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonaRepository extends JpaRepository<Persona, UUID> {
    List<Persona> findByOrgIdOrderByCreatedAtDesc(UUID orgId);
}
