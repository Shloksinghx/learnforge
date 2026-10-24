package com.learnplatform.web;

import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.Course;
import com.learnplatform.repo.CourseRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses")
public class CourseController {
    public record CreateCourse(@NotBlank String title, String description) {}

    private final CourseRepository courses;

    public CourseController(CourseRepository courses) {
        this.courses = courses;
    }

    @GetMapping
    public List<Course> list() {
        return courses.findByOrgIdOrderByCreatedAtDesc(CurrentUser.get().orgId());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ORG_ADMIN','LND_MANAGER','CONTENT_MANAGER')")
    public Course create(@Valid @RequestBody CreateCourse req) {
        Course c = new Course();
        c.setOrgId(CurrentUser.get().orgId());
        c.setTitle(req.title());
        c.setDescription(req.description());
        return courses.save(c);
    }
}
