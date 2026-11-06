package com.learnplatform.service;

import com.learnplatform.domain.*;
import com.learnplatform.repo.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** The certification workflow: lessons, knowledge test, optional simulation, then the final exam. */
@Service
public class CertificationService {
    public record Step(String key, String label, boolean done, String hint) {}
    public record Status(List<Step> steps, boolean eligibleForExam, boolean certified, String certificateCode,
                         int passMark, int examCount, int examMinutes) {}

    private final LessonRepository lessons;
    private final LessonCompletionRepository completions;
    private final AttemptRepository attempts;
    private final SimulationRepository simulations;
    private final CertificateService certificates;

    public CertificationService(LessonRepository lessons, LessonCompletionRepository completions,
                                AttemptRepository attempts, SimulationRepository simulations,
                                CertificateService certificates) {
        this.lessons = lessons;
        this.completions = completions;
        this.attempts = attempts;
        this.simulations = simulations;
        this.certificates = certificates;
    }

    public Status status(Course c, UUID userId) {
        List<Lesson> ls = lessons.findByCourseIdOrderBySortOrderAsc(c.getId());
        Set<UUID> done = ls.isEmpty() ? Set.of()
                : completions.findByUserIdAndLessonIdIn(userId, ls.stream().map(Lesson::getId).toList())
                    .stream().map(LessonCompletion::getLessonId).collect(Collectors.toSet());
        boolean lessonsDone = done.size() >= ls.size();

        List<Attempt> mine = attempts.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(a -> a.getCourseId().equals(c.getId()) && "SUBMITTED".equals(a.getStatus()) && a.getScore() != null)
                .toList();
        boolean knowledge = mine.stream().anyMatch(a -> !"EXAM".equals(a.getMode()) && a.getScore() >= a.getPassMark());
        boolean exam = mine.stream().anyMatch(a -> "EXAM".equals(a.getMode()) && a.getScore() >= a.getPassMark());
        boolean sim = !c.isRequiresSimulation() || simulations.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .anyMatch(s -> "FINISHED".equals(s.getStatus()) && s.getOverallScore() != null && s.getOverallScore() >= 60);

        List<Step> steps = new ArrayList<>();
        steps.add(new Step("LESSONS", "Complete all lessons", lessonsDone, done.size() + " of " + ls.size() + " done"));
        steps.add(new Step("KNOWLEDGE", "Pass a knowledge test (" + c.getPassMark() + "% or more)", knowledge,
                "Take a mock test or adaptive practice below"));
        if (c.isRequiresSimulation()) {
            steps.add(new Step("SIMULATION", "Finish an AI simulation with 60% or more", sim, "Open Simulations"));
        }
        steps.add(new Step("EXAM", "Pass the final exam", exam, c.getExamCount() + " questions, " + c.getExamMinutes() + " minutes"));

        boolean eligible = lessonsDone && knowledge && sim;
        String code = certificates.findActive(userId, CertificateService.titleFor(c)).map(Certificate::getCode).orElse(null);
        return new Status(steps, eligible, code != null, code, c.getPassMark(), c.getExamCount(), c.getExamMinutes());
    }
}
