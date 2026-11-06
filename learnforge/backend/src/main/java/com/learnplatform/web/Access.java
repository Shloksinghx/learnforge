package com.learnplatform.web;

import com.learnplatform.config.AuthUser;
import com.learnplatform.domain.Course;
import com.learnplatform.domain.Role;
import com.learnplatform.repo.CourseRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Shared access rules. Authors (admins) create content; managers also assign training and view results. */
final class Access {
    private static final Set<Role> AUTHORS = Set.of(Role.SUPER_ADMIN, Role.ORG_ADMIN);
    private static final Set<Role> MANAGERS = Set.of(Role.SUPER_ADMIN, Role.ORG_ADMIN, Role.LND_MANAGER);
    private static final Set<Role> VIEWERS = Set.of(Role.SUPER_ADMIN, Role.ORG_ADMIN, Role.LND_MANAGER, Role.TRAINER, Role.EXAMINER);
    private static final Set<Role> CERTIFIERS = Set.of(Role.SUPER_ADMIN, Role.ORG_ADMIN, Role.CERTIFICATION_AUTHORITY);

    private Access() {}

    static boolean isAuthor(AuthUser u) {
        return AUTHORS.contains(u.role());
    }

    static void requireAuthor(AuthUser u) {
        if (!isAuthor(u)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
    }

    /** Can create learning paths and assign training. */
    static void requireManager(AuthUser u) {
        if (!MANAGERS.contains(u.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
    }

    /** Can view organisation results (insights, people list). */
    static void requireViewer(AuthUser u) {
        if (!VIEWERS.contains(u.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
    }

    /** Can issue and revoke certificates. */
    static void requireCertifier(AuthUser u) {
        if (!CERTIFIERS.contains(u.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
    }

    /** Course in the caller's organisation. Learners cannot see drafts. */
    static Course course(CourseRepository repo, AuthUser u, UUID id) {
        return repo.findById(id)
                .filter(c -> c.getOrgId().equals(u.orgId()))
                .filter(c -> isAuthor(u) || "PUBLISHED".equals(c.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
    }
}
