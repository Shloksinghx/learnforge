package com.learnplatform.web;

import com.learnplatform.config.AuthUser;
import com.learnplatform.domain.Course;
import com.learnplatform.domain.Role;
import com.learnplatform.repo.CourseRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Shared access rules for course content. */
final class Access {
    private static final Set<Role> AUTHORS =
            Set.of(Role.SUPER_ADMIN, Role.ORG_ADMIN);

    private Access() {}

    static boolean isAuthor(AuthUser u) {
        return AUTHORS.contains(u.role());
    }

    static void requireAuthor(AuthUser u) {
        if (!isAuthor(u)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
    }

    /** Course in the caller's organisation. Learners cannot see drafts. */
    static Course course(CourseRepository repo, AuthUser u, UUID id) {
        return repo.findById(id)
                .filter(c -> c.getOrgId().equals(u.orgId()))
                .filter(c -> isAuthor(u) || "PUBLISHED".equals(c.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
    }
}
