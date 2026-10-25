package com.learnplatform.repo;

import com.learnplatform.domain.AppUser;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<AppUser> findByOrgIdOrderByCreatedAtDesc(UUID orgId);
}
