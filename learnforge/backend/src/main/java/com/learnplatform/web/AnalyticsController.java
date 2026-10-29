package com.learnplatform.web;

import com.learnplatform.config.AuthUser;
import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.*;
import com.learnplatform.repo.*;
import com.learnplatform.service.CompetencyService;
import com.learnplatform.service.GamificationService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Management / L&D dashboard: completion, scores, competency map, expiring certificates. Admin only. */
@RestController
@RequestMapping("/api/admin")
public class AnalyticsController {
    public record ExpiringCert(String code, String learnerName, String title, Instant validUntil) {}
    public record CompetencyRow(String key, String label, Integer score) {}
    public record CourseRow(UUID id, String title, int enrolled, int completed, int attempts, int averageScore, int passRate) {}
    public record Overview(int learners, int activeLearners, int publishedCourses, int completionRate,
                           int averageScore, int passRate, int testsTaken, int certificatesIssued,
                           List<ExpiringCert> expiring, List<CompetencyRow> competencies, List<CourseRow> courses,
                           List<GamificationService.LeaderRow> top) {}

    private final AppUserRepository users;
    private final CourseRepository courses;
    private final EnrolmentRepository enrolments;
    private final LessonRepository lessons;
    private final LessonCompletionRepository completions;
    private final AttemptRepository attempts;
    private final SimulationRepository simulations;
    private final CertificateRepository certificates;
    private final XpEventRepository xp;
    private final CompetencyService competencies;
    private final GamificationService gamification;

    public AnalyticsController(AppUserRepository users, CourseRepository courses, EnrolmentRepository enrolments,
                               LessonRepository lessons, LessonCompletionRepository completions,
                               AttemptRepository attempts, SimulationRepository simulations,
                               CertificateRepository certificates, XpEventRepository xp,
                               CompetencyService competencies, GamificationService gamification) {
        this.users = users;
        this.courses = courses;
        this.enrolments = enrolments;
        this.lessons = lessons;
        this.completions = completions;
        this.attempts = attempts;
        this.simulations = simulations;
        this.certificates = certificates;
        this.xp = xp;
        this.competencies = competencies;
        this.gamification = gamification;
    }

    @GetMapping("/analytics")
    public Overview overview() {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        Set<UUID> learnerIds = learners(me.orgId()).stream().map(AppUser::getId).collect(Collectors.toSet());

        Set<UUID> active = xp.findByOrgIdAndCreatedAtAfter(me.orgId(), Instant.now().minus(7, ChronoUnit.DAYS)).stream()
                .map(XpEvent::getUserId).filter(learnerIds::contains).collect(Collectors.toSet());

        List<Attempt> done = attempts.findByOrgIdAndStatus(me.orgId(), "SUBMITTED").stream()
                .filter(a -> learnerIds.contains(a.getUserId()) && a.getScore() != null).toList();
        Map<UUID, List<Attempt>> byCourse = done.stream().collect(Collectors.groupingBy(Attempt::getCourseId));

        List<CourseRow> rows = new ArrayList<>();
        int enrolledTotal = 0;
        int completedTotal = 0;
        int published = 0;
        for (Course c : courses.findByOrgIdOrderByCreatedAtDesc(me.orgId())) {
            if (!"PUBLISHED".equals(c.getStatus())) continue;
            published++;
            List<Lesson> ls = lessons.findByCourseIdOrderBySortOrderAsc(c.getId());
            Map<UUID, Long> perUser = ls.isEmpty() ? Map.of()
                    : completions.findByLessonIdIn(ls.stream().map(Lesson::getId).toList()).stream()
                        .collect(Collectors.groupingBy(LessonCompletion::getUserId, Collectors.counting()));
            int enrolled = 0;
            int completed = 0;
            for (Enrolment e : enrolments.findByCourseId(c.getId())) {
                if (!learnerIds.contains(e.getUserId())) continue;
                enrolled++;
                if (!ls.isEmpty() && perUser.getOrDefault(e.getUserId(), 0L) >= ls.size()) completed++;
            }
            enrolledTotal += enrolled;
            completedTotal += completed;
            List<Attempt> as = byCourse.getOrDefault(c.getId(), List.of());
            rows.add(new CourseRow(c.getId(), c.getTitle(), enrolled, completed, as.size(), avg(as), passRate(as)));
        }

        Instant now = Instant.now();
        List<Certificate> certs = certificates.findByOrgIdOrderByCreatedAtDesc(me.orgId());
        List<ExpiringCert> expiring = certs.stream()
                .filter(c -> !c.isRevoked() && c.getValidUntil().isAfter(now)
                        && c.getValidUntil().isBefore(now.plus(30, ChronoUnit.DAYS)))
                .limit(10).map(c -> new ExpiringCert(c.getCode(), c.getLearnerName(), c.getTitle(), c.getValidUntil())).toList();

        List<Simulation> sims = simulations.findByOrgIdAndStatus(me.orgId(), "FINISHED").stream()
                .filter(s -> learnerIds.contains(s.getUserId())).toList();
        Map<String, Integer> avgComp = competencies.averages(sims);
        List<CompetencyRow> comp = new ArrayList<>();
        for (Map.Entry<String, String> l : CompetencyService.LABELS.entrySet()) {
            comp.add(new CompetencyRow(l.getKey(), l.getValue(), avgComp.get(l.getKey())));
        }

        int completion = enrolledTotal == 0 ? 0 : Math.round(100f * completedTotal / enrolledTotal);
        return new Overview(learnerIds.size(), active.size(), published, completion, avg(done), passRate(done),
                done.size(), certs.size(), expiring, comp, rows, gamification.leaderboard(me.orgId(), me.userId(), 5));
    }

    @GetMapping("/export/results.csv")
    public ResponseEntity<String> exportResults() {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        Map<UUID, AppUser> byId = users.findByOrgIdOrderByCreatedAtDesc(me.orgId()).stream()
                .collect(Collectors.toMap(AppUser::getId, u -> u));
        Map<UUID, String> titles = courses.findByOrgIdOrderByCreatedAtDesc(me.orgId()).stream()
                .collect(Collectors.toMap(Course::getId, Course::getTitle));

        StringBuilder csv = new StringBuilder("Learner,Email,Course,Score (%),Correct,Total,Result,Mode,Submitted at\n");
        attempts.findByOrgIdAndStatus(me.orgId(), "SUBMITTED").stream()
                .sorted((a, b) -> b.getSubmittedAt().compareTo(a.getSubmittedAt()))
                .forEach(a -> {
                    AppUser u = byId.get(a.getUserId());
                    if (u == null) return;
                    csv.append(cell(u.getFullName())).append(',').append(cell(u.getEmail())).append(',')
                       .append(cell(titles.getOrDefault(a.getCourseId(), "Course"))).append(',')
                       .append(a.getScore()).append(',').append(a.getCorrectCount()).append(',').append(a.getTotal()).append(',')
                       .append(a.getScore() != null && a.getScore() >= TestController.PASS_MARK ? "Passed" : "Not passed").append(',')
                       .append(a.getMode()).append(',').append(a.getSubmittedAt()).append('\n');
                });
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8).toString())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"results.csv\"")
                .body(csv.toString());
    }

    private List<AppUser> learners(UUID orgId) {
        return users.findByOrgIdOrderByCreatedAtDesc(orgId).stream().filter(u -> u.getRole() == Role.LEARNER).toList();
    }

    private static int avg(List<Attempt> as) {
        return as.isEmpty() ? 0 : (int) Math.round(as.stream().mapToInt(Attempt::getScore).average().orElse(0));
    }

    private static int passRate(List<Attempt> as) {
        return as.isEmpty() ? 0 : Math.round(100f * as.stream().filter(a -> a.getScore() >= TestController.PASS_MARK).count() / as.size());
    }

    /** CSV cell with quotes escaped, and a leading apostrophe to stop spreadsheet formula injection. */
    private static String cell(String v) {
        String s = v == null ? "" : v;
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
