package com.learnplatform.repo;

import com.learnplatform.domain.Certificate;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateRepository extends JpaRepository<Certificate, UUID> {
    Optional<Certificate> findByCode(String code);
    List<Certificate> findByLearnerIdOrderByCreatedAtDesc(UUID learnerId);
    List<Certificate> findByOrgIdOrderByCreatedAtDesc(UUID orgId);
}
