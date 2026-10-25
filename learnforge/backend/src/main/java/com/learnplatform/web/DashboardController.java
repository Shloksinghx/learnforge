package com.learnplatform.web;

import com.learnplatform.config.AuthUser;
import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.Attempt;
import com.learnplatform.domain.Enrolment;
import com.learnplatform.repo.AttemptRepository;
import com.learnplatform.repo.EnrolmentRepository;
import com.learnplatform.repo.QuestionRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class DashboardController {
    public record Dashboard(int enrolled, int pendingTests, int testsTaken, int averageScore,
                            List<TestController.AttemptSummary> recent) {}

    private final EnrolmentRepository enrolments;
    private final AttemptRepository attempts;
    private final QuestionRepository questions;
    private final TestController tests;

    public DashboardController(EnrolmentRepository enrolments, AttemptRepository attempts,
                               QuestionRepository questions, TestController tests) {
        this.enrolments = enrolments;
        this.attempts = attempts;
        this.questions = questions;
        this.tests = tests;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        AuthUser me = CurrentUser.get();
        List<Enrolment> mine = enrolments.findByUserId(me.userId());
        int pending = 0;
        for (Enrolment e : mine) {
            boolean hasTest = questions.countByCourseIdAndStatus(e.getCourseId(), "APPROVED") > 0;
            boolean taken = attempts.existsByCourseIdAndUserIdAndStatus(e.getCourseId(), me.userId(), "SUBMITTED");
            if (hasTest && !taken) pending++;
        }
        List<Attempt> done = attempts.findByUserIdOrderByCreatedAtDesc(me.userId()).stream()
                .filter(a -> "SUBMITTED".equals(a.getStatus())).toList();
        int avg = done.isEmpty() ? 0
                : (int) Math.round(done.stream().mapToInt(Attempt::getScore).average().orElse(0));
        List<TestController.AttemptSummary> recent = tests.summaries(me.userId()).stream().limit(5).toList();
        return new Dashboard(mine.size(), pending, done.size(), avg, recent);
    }
}
