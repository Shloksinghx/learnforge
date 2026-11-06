package com.learnplatform.web;

import com.learnplatform.config.AuthUser;
import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.*;
import com.learnplatform.repo.*;
import com.learnplatform.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Learning paths: an ordered journey of courses, tests, simulations and the final exam. */
@RestController
@RequestMapping("/api/paths")
public class PathController {
    public record StepReq(@Pattern(regexp = "COURSE|TEST|EXAM|SIMULATION|SCENARIO") String type, @NotNull UUID refId) {}
    public record CreatePath(@NotBlank @Size(max = 200) String title, @Size(max = 1000) String description,
                             @Pattern(regexp = "BEGINNER|EXPERIENCED") String level,
                             @NotNull @Size(min = 1, max = 20) List<@Valid StepReq> steps) {}
    public record StepView(UUID id, String type, UUID refId, String label, boolean done, String link) {}
    public record PathView(UUID id, String title, String description, String level, boolean recommended,
                           int progress, List<StepView> steps) {}

    private final LearningPathRepository paths;
    private final LearningPathStepRepository steps;
    private final CourseRepository courses;
    private final PersonaRepository personas;
    private final ScenarioRepository scenarios;
    private final LessonRepository lessons;
    private final LessonCompletionRepository completions;
    private final AttemptRepository attempts;
    private final SimulationRepository simulations;
    private final NotificationService notifications;

    public PathController(LearningPathRepository paths, LearningPathStepRepository steps, CourseRepository courses,
                          PersonaRepository personas, ScenarioRepository scenarios, LessonRepository lessons,
                          LessonCompletionRepository completions, AttemptRepository attempts,
                          SimulationRepository simulations, NotificationService notifications) {
        this.paths = paths;
        this.steps = steps;
        this.courses = courses;
        this.personas = personas;
        this.scenarios = scenarios;
        this.lessons = lessons;
        this.completions = completions;
        this.attempts = attempts;
        this.simulations = simulations;
        this.notifications = notifications;
    }

    @GetMapping
    public List<PathView> list() {
        AuthUser me = CurrentUser.get();
        List<Attempt> mine = attempts.findByUserIdOrderByCreatedAtDesc(me.userId()).stream()
                .filter(a -> "SUBMITTED".equals(a.getStatus()) && a.getScore() != null).toList();
        double avg = mine.stream().mapToInt(Attempt::getScore).average().orElse(0);
        String myLevel = mine.isEmpty() || avg < 60 ? "BEGINNER" : "EXPERIENCED";
        return paths.findByOrgIdOrderByCreatedAtDesc(me.orgId()).stream().map(p -> view(p, me, myLevel)).toList();
    }

    @PostMapping
    public PathView create(@Valid @RequestBody CreatePath req) {
        AuthUser me = CurrentUser.get();
        Access.requireManager(me);
        List<LearningPathStep> built = new ArrayList<>();
        int order = 0;
        for (StepReq s : req.steps()) {
            LearningPathStep st = new LearningPathStep();
            st.setType(s.type());
            st.setRefId(s.refId());
            st.setSortOrder(order++);
            st.setLabel(label(me, s.type(), s.refId()));
            built.add(st);
        }
        LearningPath p = new LearningPath();
        p.setOrgId(me.orgId());
        p.setTitle(req.title().trim());
        p.setDescription(req.description());
        p.setLevel(req.level() == null ? "BEGINNER" : req.level());
        paths.save(p);
        for (LearningPathStep st : built) {
            st.setPathId(p.getId());
            steps.save(st);
        }
        notifications.notifyLearners(me.orgId(), "New learning path: " + p.getTitle(),
                "A guided path has been added for you.", "/paths", "path:" + p.getId());
        return view(p, me, "BEGINNER");
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        AuthUser me = CurrentUser.get();
        Access.requireManager(me);
        LearningPath p = paths.findById(id).filter(x -> x.getOrgId().equals(me.orgId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Path not found"));
        paths.delete(p);
    }

    // ---- helpers ----
    private String label(AuthUser me, String type, UUID refId) {
        switch (type) {
            case "COURSE", "TEST", "EXAM" -> {
                Course c = courses.findById(refId).filter(x -> x.getOrgId().equals(me.orgId()))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "A course in this path was not found"));
                return ("COURSE".equals(type) ? "Learn: " : "TEST".equals(type) ? "Pass a test: " : "Final exam: ") + c.getTitle();
            }
            case "SIMULATION" -> {
                Persona p = personas.findById(refId).filter(x -> x.getOrgId().equals(me.orgId()))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "A persona in this path was not found"));
                return "Role-play with " + p.getName();
            }
            default -> {
                Scenario s = scenarios.findById(refId).filter(x -> x.getOrgId().equals(me.orgId()))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "A scenario in this path was not found"));
                return "Simulation: " + s.getTitle();
            }
        }
    }

    private PathView view(LearningPath p, AuthUser me, String myLevel) {
        List<StepView> sv = new ArrayList<>();
        int done = 0;
        for (LearningPathStep s : steps.findByPathIdOrderBySortOrderAsc(p.getId())) {
            boolean d = stepDone(me.userId(), s);
            if (d) done++;
            String link = "SIMULATION".equals(s.getType()) || "SCENARIO".equals(s.getType()) ? "/simulations" : "/courses/" + s.getRefId();
            sv.add(new StepView(s.getId(), s.getType(), s.getRefId(), s.getLabel(), d, link));
        }
        int progress = sv.isEmpty() ? 0 : Math.round(100f * done / sv.size());
        return new PathView(p.getId(), p.getTitle(), p.getDescription(), p.getLevel(), p.getLevel().equals(myLevel), progress, sv);
    }

    private boolean stepDone(UUID userId, LearningPathStep s) {
        switch (s.getType()) {
            case "COURSE" -> {
                List<Lesson> ls = lessons.findByCourseIdOrderBySortOrderAsc(s.getRefId());
                if (ls.isEmpty()) return false;
                Set<UUID> done = completions.findByUserIdAndLessonIdIn(userId, ls.stream().map(Lesson::getId).toList())
                        .stream().map(LessonCompletion::getLessonId).collect(Collectors.toSet());
                return done.size() >= ls.size();
            }
            case "TEST", "EXAM" -> {
                boolean exam = "EXAM".equals(s.getType());
                return attempts.findByUserIdOrderByCreatedAtDesc(userId).stream().anyMatch(a ->
                        a.getCourseId().equals(s.getRefId()) && "SUBMITTED".equals(a.getStatus()) && a.getScore() != null
                                && exam == "EXAM".equals(a.getMode()) && a.getScore() >= a.getPassMark());
            }
            case "SIMULATION" -> {
                return simulations.findByUserIdOrderByCreatedAtDesc(userId).stream()
                        .anyMatch(x -> "FINISHED".equals(x.getStatus()) && s.getRefId().equals(x.getPersonaId()));
            }
            default -> {
                return simulations.findByUserIdOrderByCreatedAtDesc(userId).stream()
                        .anyMatch(x -> "FINISHED".equals(x.getStatus()) && s.getRefId().equals(x.getScenarioId()));
            }
        }
    }
}
