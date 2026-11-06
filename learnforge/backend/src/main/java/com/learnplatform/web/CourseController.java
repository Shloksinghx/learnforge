package com.learnplatform.web;

import com.learnplatform.config.AuthUser;
import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.*;
import com.learnplatform.repo.*;
import com.learnplatform.service.GamificationService;
import com.learnplatform.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
    public record CreateLesson(@NotBlank String title, @NotBlank @Size(max = 50000) String content,
                               @Size(max = 200) String moduleTitle,
                               @Pattern(regexp = "^(https?://\\S+)?$") @Size(max = 500) String videoUrl) {}
    public record Settings(@Size(max = 200) String certificateTitle, @Min(40) @Max(100) int passMark,
                           @Min(1) @Max(200) int examCount, @Min(5) @Max(240) int examMinutes,
                           boolean requiresSimulation) {}
    public record ShareReq(boolean shared) {}
    public record LessonView(UUID id, String title, String content, int order, boolean completed,
                             String moduleTitle, String videoUrl) {}
    public record CourseView(UUID id, String title, String description, String category, String status,
                             int lessonCount, int questionCount, boolean enrolled, int progress,
                             boolean shared, String certificateTitle, int passMark, int examCount,
                             int examMinutes, boolean requiresSimulation) {}
    public record CourseDetail(CourseView course, List<LessonView> lessons) {}

    private final CourseRepository courses;
    private final LessonRepository lessons;
    private final EnrolmentRepository enrolments;
    private final LessonCompletionRepository completions;
    private final QuestionRepository questions;
    private final GamificationService gamification;
    private final NotificationService notifications;

    public CourseController(CourseRepository courses, LessonRepository lessons, EnrolmentRepository enrolments,
                            LessonCompletionRepository completions, QuestionRepository questions,
                            GamificationService gamification, NotificationService notifications) {
        this.courses = courses;
        this.lessons = lessons;
        this.enrolments = enrolments;
        this.completions = completions;
        this.questions = questions;
        this.gamification = gamification;
        this.notifications = notifications;
    }

    @GetMapping
    public List<CourseView> list(@RequestParam(defaultValue = "false") boolean mine) {
        AuthUser me = CurrentUser.get();
        List<Course> visible = courses.findByOrgIdOrderByCreatedAtDesc(me.orgId()).stream()
                .filter(c -> Access.isAuthor(me) || "PUBLISHED".equals(c.getStatus()))
                .toList();
        autoEnrol(me, visible);
        return visible.stream()
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
        Course c = Access.course(courses, me, id);
        autoEnrol(me, List.of(c));
        return detail(c, me);
    }

    @PostMapping("/{id}/publish")
    public CourseView publish(@PathVariable UUID id) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        Course c = Access.course(courses, me, id);
        if (lessons.countByCourseId(c.getId()) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add at least one lesson before publishing");
        }
        boolean first = !"PUBLISHED".equals(c.getStatus());
        c.setStatus("PUBLISHED");
        courses.save(c);
        if (first) {
            notifications.notifyLearners(me.orgId(), "New course: " + c.getTitle(),
                    "A new course is available. Open it to start learning.", "/courses/" + c.getId(), "course:" + c.getId());
        }
        return view(c, me);
    }

    @PutMapping("/{id}/settings")
    public CourseView settings(@PathVariable UUID id, @Valid @RequestBody Settings req) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        Course c = Access.course(courses, me, id);
        c.setCertificateTitle(req.certificateTitle() == null || req.certificateTitle().isBlank() ? null : req.certificateTitle().trim());
        c.setPassMark(req.passMark());
        c.setExamCount(req.examCount());
        c.setExamMinutes(req.examMinutes());
        c.setRequiresSimulation(req.requiresSimulation());
        return view(courses.save(c), me);
    }

    @PostMapping("/{id}/share")
    public CourseView share(@PathVariable UUID id, @RequestBody ShareReq req) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        Course c = Access.course(courses, me, id);
        c.setShared(req.shared());
        return view(courses.save(c), me);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        courses.delete(Access.course(courses, me, id));
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
        l.setModuleTitle(req.moduleTitle() == null || req.moduleTitle().isBlank() ? null : req.moduleTitle().trim());
        l.setVideoUrl(req.videoUrl() == null || req.videoUrl().isBlank() ? null : req.videoUrl().trim());
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
            if (!Access.isAuthor(me)) {
                gamification.award(me.orgId(), me.userId(), "LESSON", l.getId().toString(), 10);
                gamification.refresh(me.orgId(), me.userId());
            }
        }
        return detail(c, me);
    }

    /** Every published course is automatically available to every learner in the organisation. */
    private void autoEnrol(AuthUser me, List<Course> visible) {
        if (Access.isAuthor(me)) return;
        for (Course c : visible) {
            if ("PUBLISHED".equals(c.getStatus())
                    && enrolments.findByCourseIdAndUserId(c.getId(), me.userId()).isEmpty()) {
                Enrolment e = new Enrolment();
                e.setCourseId(c.getId());
                e.setUserId(me.userId());
                enrolments.save(e);
            }
        }
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
                all.size(), questions.countByCourseIdAndStatus(c.getId(), "APPROVED"), enrolled, progress,
                c.isShared(), c.getCertificateTitle(), c.getPassMark(), c.getExamCount(), c.getExamMinutes(),
                c.isRequiresSimulation());
    }

    private CourseDetail detail(Course c, AuthUser me) {
        List<Lesson> all = lessons.findByCourseIdOrderBySortOrderAsc(c.getId());
        boolean canRead = Access.isAuthor(me)
                || enrolments.findByCourseIdAndUserId(c.getId(), me.userId()).isPresent();
        Set<UUID> done = completedIds(all, me.userId());
        List<LessonView> lv = all.stream()
                .map(l -> new LessonView(l.getId(), l.getTitle(), canRead ? l.getContent() : null,
                        l.getSortOrder(), done.contains(l.getId()), l.getModuleTitle(), canRead ? l.getVideoUrl() : null))
                .toList();
        return new CourseDetail(view(c, me), lv);
    }
}
