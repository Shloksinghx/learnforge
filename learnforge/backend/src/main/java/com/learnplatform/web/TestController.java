package com.learnplatform.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.config.AuthUser;
import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.*;
import com.learnplatform.repo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Learner tests: random questions from the approved bank, automatic scoring. */
@RestController
@RequestMapping("/api")
public class TestController {
    public static final int PASS_MARK = 60;

    public record StartReq(@Min(1) @Max(50) int count) {}
    public record Answer(@NotNull UUID questionId, Integer selectedIndex) {}
    public record SubmitReq(@NotNull List<Answer> answers) {}
    public record QuestionItem(UUID id, String text, List<String> options, Integer selectedIndex,
                               Integer correctIndex, String explanation, Boolean correct) {}
    public record AttemptView(UUID id, UUID courseId, String courseTitle, String status, Integer score,
                              int correctCount, int total, Boolean passed, List<QuestionItem> questions) {}
    public record AttemptSummary(UUID id, UUID courseId, String courseTitle, String status, Integer score,
                                 int correctCount, int total, Boolean passed, Instant at) {}

    private final CourseRepository courses;
    private final EnrolmentRepository enrolments;
    private final QuestionRepository questions;
    private final AttemptRepository attempts;
    private final AttemptAnswerRepository answers;
    private final ObjectMapper mapper;

    public TestController(CourseRepository courses, EnrolmentRepository enrolments, QuestionRepository questions,
                          AttemptRepository attempts, AttemptAnswerRepository answers, ObjectMapper mapper) {
        this.courses = courses;
        this.enrolments = enrolments;
        this.questions = questions;
        this.attempts = attempts;
        this.answers = answers;
        this.mapper = mapper;
    }

    @PostMapping("/courses/{id}/tests/start")
    public AttemptView start(@PathVariable UUID id, @Valid @RequestBody StartReq req) {
        AuthUser me = CurrentUser.get();
        Course c = Access.course(courses, me, id);
        if (!Access.isAuthor(me) && enrolments.findByCourseIdAndUserId(c.getId(), me.userId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Enrol in the course first");
        }
        List<Question> pool = new ArrayList<>(questions.findByCourseIdAndStatus(c.getId(), "APPROVED"));
        if (pool.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No approved questions for this course yet");
        }
        Collections.shuffle(pool);
        List<Question> picked = pool.subList(0, Math.min(req.count(), pool.size()));

        Attempt a = new Attempt();
        a.setOrgId(me.orgId());
        a.setCourseId(c.getId());
        a.setUserId(me.userId());
        a.setTotal(picked.size());
        attempts.save(a);
        int order = 0;
        for (Question q : picked) {
            AttemptAnswer aa = new AttemptAnswer();
            aa.setAttemptId(a.getId());
            aa.setQuestionId(q.getId());
            aa.setSortOrder(order++);
            answers.save(aa);
        }
        return view(a, c);
    }

    @GetMapping("/attempts/{id}")
    public AttemptView get(@PathVariable UUID id) {
        Attempt a = owned(id);
        return view(a, courses.findById(a.getCourseId()).orElseThrow());
    }

    @PostMapping("/attempts/{id}/submit")
    public AttemptView submit(@PathVariable UUID id, @Valid @RequestBody SubmitReq req) {
        Attempt a = owned(id);
        if (!"IN_PROGRESS".equals(a.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This test was already submitted");
        }
        Map<UUID, Integer> chosen = new HashMap<>();
        for (Answer x : req.answers()) chosen.put(x.questionId(), x.selectedIndex());

        List<AttemptAnswer> rows = answers.findByAttemptIdOrderBySortOrderAsc(a.getId());
        Map<UUID, Question> qs = questions.findAllById(rows.stream().map(AttemptAnswer::getQuestionId).toList())
                .stream().collect(Collectors.toMap(Question::getId, Function.identity()));
        int correct = 0;
        for (AttemptAnswer r : rows) {
            Integer sel = chosen.get(r.getQuestionId());
            boolean ok = sel != null && sel == qs.get(r.getQuestionId()).getCorrectIndex();
            r.setSelectedIndex(sel);
            r.setCorrect(ok);
            if (ok) correct++;
        }
        answers.saveAll(rows);
        a.setCorrectCount(correct);
        a.setScore(a.getTotal() == 0 ? 0 : Math.round(100f * correct / a.getTotal()));
        a.setStatus("SUBMITTED");
        a.setSubmittedAt(Instant.now());
        attempts.save(a);
        return view(a, courses.findById(a.getCourseId()).orElseThrow());
    }

    @GetMapping("/attempts/mine")
    public List<AttemptSummary> mine() {
        return summaries(CurrentUser.get().userId());
    }

    List<AttemptSummary> summaries(UUID userId) {
        return attempts.findByUserIdOrderByCreatedAtDesc(userId).stream().map(a -> {
            String title = courses.findById(a.getCourseId()).map(Course::getTitle).orElse("Course");
            boolean done = "SUBMITTED".equals(a.getStatus());
            return new AttemptSummary(a.getId(), a.getCourseId(), title, a.getStatus(), a.getScore(),
                    a.getCorrectCount() == null ? 0 : a.getCorrectCount(), a.getTotal(),
                    done ? a.getScore() >= PASS_MARK : null,
                    done ? a.getSubmittedAt() : a.getCreatedAt());
        }).toList();
    }

    // ---- helpers ----
    private Attempt owned(UUID id) {
        AuthUser me = CurrentUser.get();
        return attempts.findById(id).filter(a -> a.getUserId().equals(me.userId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Test not found"));
    }

    /** Answers and explanations are only revealed after submission. */
    private AttemptView view(Attempt a, Course c) {
        boolean done = "SUBMITTED".equals(a.getStatus());
        List<AttemptAnswer> rows = answers.findByAttemptIdOrderBySortOrderAsc(a.getId());
        Map<UUID, Question> qs = questions.findAllById(rows.stream().map(AttemptAnswer::getQuestionId).toList())
                .stream().collect(Collectors.toMap(Question::getId, Function.identity()));
        List<QuestionItem> items = new ArrayList<>();
        for (AttemptAnswer r : rows) {
            Question q = qs.get(r.getQuestionId());
            List<String> opts;
            try {
                opts = mapper.readValue(q.getOptionsJson(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                opts = List.of();
            }
            items.add(new QuestionItem(q.getId(), q.getText(), opts, r.getSelectedIndex(),
                    done ? q.getCorrectIndex() : null, done ? q.getExplanation() : null,
                    done ? r.getCorrect() : null));
        }
        return new AttemptView(a.getId(), c.getId(), c.getTitle(), a.getStatus(), a.getScore(),
                a.getCorrectCount() == null ? 0 : a.getCorrectCount(), a.getTotal(),
                done ? a.getScore() >= PASS_MARK : null, items);
    }
}
