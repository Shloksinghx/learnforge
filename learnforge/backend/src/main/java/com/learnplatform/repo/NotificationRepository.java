package com.learnplatform.repo;

import com.learnplatform.domain.Notification;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findTop30ByUserIdOrderByCreatedAtDesc(UUID userId);
    long countByUserIdAndReadFalse(UUID userId);
    List<Notification> findByUserIdAndReadFalse(UUID userId);
    boolean existsByUserIdAndRef(UUID userId, String ref);
}
