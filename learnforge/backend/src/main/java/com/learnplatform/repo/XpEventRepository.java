package com.learnplatform.repo;

import com.learnplatform.domain.XpEvent;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface XpEventRepository extends JpaRepository<XpEvent, UUID> {
    List<XpEvent> findByUserIdOrderByCreatedAtDesc(UUID userId);
    boolean existsByUserIdAndTypeAndRefId(UUID userId, String type, String refId);
    List<XpEvent> findByOrgId(UUID orgId);
    List<XpEvent> findByOrgIdAndCreatedAtAfter(UUID orgId, Instant after);
}
