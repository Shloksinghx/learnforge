package com.learnplatform.config;

import com.learnplatform.domain.Role;
import java.util.UUID;

public record AuthUser(UUID userId, UUID orgId, Role role) {}
