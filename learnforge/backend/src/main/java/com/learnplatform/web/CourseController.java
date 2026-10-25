package com.learnplatform.web;

import com.learnplatform.config.AuthUser;
import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.*;
import com.learnplatform.repo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/courses")
public class CourseController {
    public record CreateCourse(@NotBlank String title, String description, String category) {}
    public record CreateLesson(@NotBlank String title, @NotBlank @Size(max = 50000) String content) {}
    public record LessonView(UUID id, String title, String content, int order, boolean completed) {}
    public record CourseView(UUID id, String title, String description, String category, String status,
                             int lessonCount, int questionCount, boolean enrolled, int progress) {}
    public record CourseDetail(CourseView course, List<LessonView> lessons) {}

    private final CourseRepository courses;
    private final LessonRepository lessons;
    private final EnrolmentRepository enrolments;
    private final LessonCompletionRepository completions;
    private final QuestionRepository questions;

    public CourseController(CourseRepository courses, LessonRepository lessons, EnrolmentRepository enrolments,
                            LessonCompletionRepository completions, QuestionRepository questions) {
        this.courses = courses;
        this.lessons = lessons;
        this.enrolments = enrolments;
        this.completions = completions;
        this.questions = questions;
    }

    @GetMapping
    public List<CourseView> list(@RequestParam(defaultValue = "false") boolean mine) {
        AuthUser me = CurrentUser.get();
        return courses.findByOrgIdOrderByCreatedAtDesc(me.orgId()).stream()
                .filter(c -> Access.isAuthor(me) || "PUBLISHED".equals(c.getStatus()))
                .map(c -> view(c, me))
                .filter(v -> !mine || v.enrolled())
                .toList();
    }

    @PostMapping
    public CourseView create(@Valid @RequestBody CreateCourse req) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        Course c = new Course();
        c.setOrgId(me.orgId());
        c.setTitle(req.title().trim());
        c.setDescription(req.description());
        c.setCategory(req.category());
        courses.save(c);
        return view(c, me);
    }

    @GetMapping("/{id}")
    public CourseDetail detail(@PathVariable UUID id) {
        AuthUser me = CurrentUser.get();
        return detail(Access.course(courses, me, id), me);
    }

    @PostMapping("/{id}/publish")
    public CourseView publish(@PathVariable UUID id) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        Course c = Access.course(courses, me, id);
        if (lessons.countByCourseId(c.getId()) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add at least one lesson before publishing");
        }
        c.setStatus("PUBLISHED");
        return view(courses.save(c), me);
    }

    @PostMapping("/{id}/lessons")
    public CourseDetail addLesson(@PathVariable UUID id, @Valid @RequestBody CreateLesson req) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        Course c = Access.course(courses, me, id);
        Lesson l = new Lesson();
        l.setCourseId(c.getId());
        l.setTitle(req.title().trim());
        l.setContent(req.content());
        l.setSortOrder(lessons.countByCourseId(c.getId()) + 1);
        lessons.save(l);
        return detail(c, me);
    }

    @PostMapping("/{id}/enrol")
    public CourseView enrol(@PathVariable UUID id) {
        AuthUser me = CurrentUser.get();
        Course c = Access.course(courses, me, id);
        if (!"PUBLISHED".equals(c.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course is not published yet");
        }
        if (enrolments.findByCourseIdAndUserId(c.getId(), me.userId()).isEmpty()) {
            Enrolment e = new Enrolment();
            e.setCourseId(c.getId());
            e.setUserId(me.userId());
            enrolments.save(e);
        }
        return view(c, me);
    }

    @PostMapping("/{id}/lessons/{lessonId}/complete")
    public CourseDetail complete(@PathVariable UUID id, @PathVariable UUID lessonId) {
        AuthUser me = CurrentUser.get();
        Course c = Access.course(courses, me, id);
        if (enrolments.findByCourseIdAndUserId(c.getId(), me.userId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Enrol in the course first");
        }
        Lesson l = lessons.findById(lessonId).filter(x -> x.getCourseId().equals(c.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found"));
        if (!completions.existsByLessonIdAndUserId(l.getId(), me.userId())) {
            LessonCompletion lc = new LessonCompletion();
            lc.setLessonId(l.getId());
            lc.setUserId(me.userId());
            completions.save(lc);
        }
        return detail(c, me);
    }

    // ---- helpers ----
    private Set<UUID> completedIds(List<Lesson> all, UUID userId) {
        if (all.isEmpty()) return Set.of();
        return completions.findByUserIdAndLessonIdIn(userId, all.stream().map(Lesson::getId).toList())
                .stream().map(LessonCompletion::getLessonId).collect(Collectors.toSet());
    }

    private CourseView view(Course c, AuthUser me) {
        List<Lesson> all = lessons.findByCourseIdOrderBySortOrderAsc(c.getId());
        boolean enrolled = enrolments.findByCourseIdAndUserId(c.getId(), me.userId()).isPresent();
        int progress = 0;
        if (enrolled && !all.isEmpty()) {
            progress = Math.round(100f * completedIds(all, me.userId()).size() / all.size());
        }
        return new CourseView(c.getId(), c.getTitle(), c.getDescription(), c.getCategory(), c.getStatus(),
                all.size(), questions.countByCourseIdAndStatus(c.getId(), "APPROVED"), enrolled, progress);
    }

    private CourseDetail detail(Course c, AuthUser me) {
        List<Lesson> all = lessons.findByCourseIdOrderBySortOrderAsc(c.getId());
        boolean canRead = Access.isAuthor(me)
                || enrolments.findByCourseIdAndUserId(c.getId(), me.userId()).isPresent();
        Set<UUID> done = completedIds(all, me.userId());
        List<LessonView> lv = all.stream()
                .map(l -> new LessonView(l.getId(), l.getTitle(), canRead ? l.getContent() : null,
                        l.getSortOrder(), done.contains(l.getId())))
                .toList();
        return new CourseDetail(view(c, me), lv);
    }
}
