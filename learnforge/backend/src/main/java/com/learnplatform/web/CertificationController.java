package com.learnplatform.web;

import com.learnplatform.config.AuthUser;
import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.Course;
import com.learnplatform.repo.CourseRepository;
import com.learnplatform.service.CertificationService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CertificationController {
    private final CourseRepository courses;
    private final CertificationService certification;

    public CertificationController(CourseRepository courses, CertificationService certification) {
        this.courses = courses;
        this.certification = certification;
    }

    @GetMapping("/api/courses/{id}/certification")
    public CertificationService.Status status(@PathVariable UUID id) {
        AuthUser me = CurrentUser.get();
        Course c = Access.course(courses, me, id);
        return certification.status(c, me.userId());
    }
}
