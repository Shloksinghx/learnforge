package com.learnplatform.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.config.AuthUser;
import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.*;
import com.learnplatform.repo.*;
import com.learnplatform.service.CertificateService;
import com.learnplatform.service.GamificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Tests: standard timed mock tests and adaptive tests, with automatic scoring. */
@RestController
@RequestMapping("/api")
public class TestController {
    public static final int PASS_MARK = 60;
    private static final List<String> LEVELS = List.of("EASY", "MEDIUM", "HARD");

    public record StartReq(@Min(1) @Max(50) int count,
                           @Pattern(regexp = "(?i)STANDARD|ADAPTIVE") String mode,
                           @Min(1) @Max(180) Integer timeLimitMinutes,
                           Boolean negativeMarking,
                           @Pattern(regexp = "(?i)ALL|EASY|MEDIUM|HARD") String section) {}
    public record Answer(@NotNull UUID questionId, Integer selectedIndex) {}
    public record SubmitReq(@NotNull List<Answer> answers) {}
    public record AnswerReq(@NotNull UUID questionId, @NotNull Integer selectedIndex) {}
    public record QuestionItem(UUID id, String text, String kind, List<String> options, Integer selectedIndex,
                               Integer correctIndex, String explanation, Boolean correct, String difficulty) {}
    public record Breakdown(String difficulty, int correct, int total) {}
    public record AttemptView(UUID id, UUID courseId, String courseTitle, String status, String mode, Integer score,
                              int correctCount, int wrongCount, int total, Boolean passed, String certificateCode,
                              String certificateNote, Integer timeLimitMinutes, boolean negativeMarking,
                              Long secondsLeft, Long timeTakenSeconds, List<Breakdown> analysis,
                              List<QuestionItem> questions) {}
    public record Remediation(String lessonTitle, String excerpt) {}
    public record AnswerResult(boolean correct, Integer correctIndex, String explanation,
                               Remediation remediation, AttemptView attempt) {}
    public record AttemptSummary(UUID id, UUID courseId, String courseTitle, String status, Integer score,
                                 int correctCount, int total, Boolean passed, Instant at) {}

    private final CourseRepository courses;
    private final EnrolmentRepository enrolments;
    private final QuestionRepository questions;
    private final AttemptRepository attempts;
    private final AttemptAnswerRepository answers;
    private final ObjectMapper mapper;
    private final AppUserRepository users;
    private final CertificateService certificates;
    private final LessonRepository lessons;
    private final LessonCompletionRepository completions;
    private final GamificationService gamification;

    public TestController(CourseRepository courses, EnrolmentRepository enrolments, QuestionRepository questions,
                          AttemptRepository attempts, AttemptAnswerRepository answers, ObjectMapper mapper,
                          AppUserRepository users, CertificateService certificates, LessonRepository lessons,
                          LessonCompletionRepository completions, GamificationService gamification) {
        this.courses = courses;
        this.enrolments = enrolments;
        this.questions = questions;
        this.attempts = attempts;
        this.answers = answers;
        this.mapper = mapper;
        this.users = users;
        this.certificates = certificates;
        this.lessons = lessons;
        this.completions = completions;
        this.gamification = gamification;
    }

    @PostMapping("/courses/{id}/tests/start")
    public AttemptView start(@PathVariable UUID id, @Valid @RequestBody StartReq req) {
        AuthUser me = CurrentUser.get();
        Course c = Access.course(courses, me, id);
        if (!Access.isAuthor(me) && enrolments.findByCourseIdAndUserId(c.getId(), me.userId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Enrol in the course first");
        }
        boolean adaptive = "ADAPTIVE".equalsIgnoreCase(req.mode());
        String section = req.section() == null ? "ALL" : req.section().toUpperCase();

        List<Question> pool = new ArrayList<>(questions.findByCourseIdAndStatus(c.getId(), "APPROVED"));
        if (!adaptive && !"ALL".equals(section)) pool.removeIf(q -> !section.equals(q.getDifficulty()));
        if (pool.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No approved questions available for this selection");
        }
        Collections.shuffle(pool);
        int n = Math.min(req.count(), pool.size());

        Attempt a = new Attempt();
        a.setOrgId(me.orgId());
        a.setCourseId(c.getId());
        a.setUserId(me.userId());
        a.setMode(adaptive ? "ADAPTIVE" : "STANDARD");
        a.setSection(adaptive ? "ALL" : section);
        a.setTimeLimitMinutes(adaptive ? null : req.timeLimitMinutes());
        a.setNegativeMarking(Boolean.TRUE.equals(req.negativeMarking()));
        a.setTotal(n);
        attempts.save(a);

        if (adaptive) {
            Question first = pool.stream().filter(q -> "MEDIUM".equals(q.getDifficulty())).findFirst().orElse(pool.get(0));
            addRow(a.getId(), first.getId(), 0);
        } else {
            int order = 0;
            for (Question q : pool.subList(0, n)) addRow(a.getId(), q.getId(), order++);
        }
        return view(a, c);
    }

    @GetMapping("/attempts/{id}")
    public AttemptView get(@PathVariable UUID id) {
        Attempt a = owned(id);
        Course c = courses.findById(a.getCourseId()).orElseThrow();
        if ("SUBMITTED".equals(a.getStatus())) ensureCertificate(a, c);
        return view(a, c);
    }

    @PostMapping("/attempts/{id}/submit")
    public AttemptView submit(@PathVariable UUID id, @Valid @RequestBody SubmitReq req) {
        Attempt a = owned(id);
        if (!"IN_PROGRESS".equals(a.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This test was already submitted");
        }
        if ("ADAPTIVE".equals(a.getMode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Answer adaptive questions one at a time");
        }
        Map<UUID, Integer> chosen = new HashMap<>();
        for (Answer x : req.answers()) chosen.put(x.questionId(), x.selectedIndex());

        List<AttemptAnswer> rows = answers.findByAttemptIdOrderBySortOrderAsc(a.getId());
        Map<UUID, Question> qs = loadQuestions(rows);
        for (AttemptAnswer r : rows) {
            Integer sel = chosen.get(r.getQuestionId());
            r.setSelectedIndex(sel);
            r.setCorrect(sel != null && sel == qs.get(r.getQuestionId()).getCorrectIndex());
        }
        answers.saveAll(rows);
        return finish(a);
    }

    /** Adaptive mode: answer one question, get instant feedback and the next question. */
    @PostMapping("/attempts/{id}/answer")
    public AnswerResult answer(@PathVariable UUID id, @Valid @RequestBody AnswerReq req) {
        Attempt a = owned(id);
        if (!"IN_PROGRESS".equals(a.getStatus()) || !"ADAPTIVE".equals(a.getMode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This is not an active adaptive test");
        }
        List<AttemptAnswer> rows = answers.findByAttemptIdOrderBySortOrderAsc(a.getId());
        AttemptAnswer row = rows.stream()
                .filter(r -> r.getQuestionId().equals(req.questionId()) && r.getCorrect() == null)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Question already answered"));
        Question q = questions.findById(row.getQuestionId()).orElseThrow();
        boolean ok = req.selectedIndex() == q.getCorrectIndex();
        row.setSelectedIndex(req.selectedIndex());
        row.setCorrect(ok);
        answers.save(row);

        Course c = courses.findById(a.getCourseId()).orElseThrow();
        Remediation rem = ok ? null : remediation(c, q);
        long answered = rows.stream().filter(r -> r.getCorrect() != null).count();
        Question next = answered >= a.getTotal() ? null : pickNext(c, rows, q, ok);
        AttemptView v;
        if (next == null) {
            v = finish(a);
        } else {
            addRow(a.getId(), next.getId(), rows.size());
            v = view(a, c);
        }
        return new AnswerResult(ok, q.getCorrectIndex(), q.getExplanation(), rem, v);
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

    // ---- scoring ----
    private AttemptView finish(Attempt a) {
        List<AttemptAnswer> rows = answers.findByAttemptIdOrderBySortOrderAsc(a.getId());
        int correct = 0;
        int wrong = 0;
        int counted = 0;
        for (AttemptAnswer r : rows) {
            if (r.getCorrect() == null) continue;
            counted++;
            if (Boolean.TRUE.equals(r.getCorrect())) correct++;
            else if (r.getSelectedIndex() != null) wrong++;
        }
        int total = "ADAPTIVE".equals(a.getMode()) ? counted : rows.size();
        double raw = correct - (a.isNegativeMarking() ? 0.25 * wrong : 0);
        int score = total == 0 ? 0 : (int) Math.round(Math.max(0, raw) * 100.0 / total);

        a.setTotal(total);
        a.setCorrectCount(correct);
        a.setWrongCount(wrong);
        a.setScore(score);
        a.setStatus("SUBMITTED");
        a.setSubmittedAt(Instant.now());
        attempts.save(a);

        Course c = courses.findById(a.getCourseId()).orElseThrow();
        AuthUser me = CurrentUser.get();
        if (!Access.isAuthor(me)) {
            int best = attempts.findByUserIdOrderByCreatedAtDesc(me.userId()).stream()
                    .filter(x -> x.getCourseId().equals(a.getCourseId()) && "SUBMITTED".equals(x.getStatus())
                            && x.getScore() != null && !x.getId().equals(a.getId()))
                    .mapToInt(Attempt::getScore).max().orElse(-1);
            int pts = 20 + (score >= PASS_MARK ? 30 : 0) + (best >= 0 && score > best ? 20 : 0);
            gamification.award(me.orgId(), me.userId(), "TEST", a.getId().toString(), pts);
            gamification.refresh(me.orgId(), me.userId());
            ensureCertificate(a, c);
        }
        return view(a, c);
    }

    /** A certificate needs a pass AND all lessons completed. */
    private Optional<Certificate> ensureCertificate(Attempt a, Course c) {
        if (a.getScore() == null || a.getScore() < PASS_MARK) return Optional.empty();
        if (Access.isAuthor(CurrentUser.get())) return Optional.empty();
        String title = certTitle(c);
        Optional<Certificate> existing = certificates.findActive(a.getUserId(), title);
        if (existing.isPresent()) return existing;
        if (!lessonsDone(a.getUserId(), c.getId())) return Optional.empty();
        return Optional.of(certificates.issue(users.findById(a.getUserId()).orElseThrow(), title, a.getScore(), 24));
    }

    private boolean lessonsDone(UUID userId, UUID courseId) {
        List<Lesson> ls = lessons.findByCourseIdOrderBySortOrderAsc(courseId);
        if (ls.isEmpty()) return true;
        Set<UUID> done = completions.findByUserIdAndLessonIdIn(userId, ls.stream().map(Lesson::getId).toList())
                .stream().map(LessonCompletion::getLessonId).collect(Collectors.toSet());
        return done.size() >= ls.size();
    }

    // ---- adaptive helpers ----
    private Question pickNext(Course c, List<AttemptAnswer> rows, Question last, boolean wasCorrect) {
        Set<UUID> used = rows.stream().map(AttemptAnswer::getQuestionId).collect(Collectors.toSet());
        List<Question> left = questions.findByCourseIdAndStatus(c.getId(), "APPROVED").stream()
                .filter(x -> !used.contains(x.getId())).collect(Collectors.toCollection(ArrayList::new));
        if (left.isEmpty()) return null;
        Collections.shuffle(left);
        int current = Math.max(0, LEVELS.indexOf(last.getDifficulty()));
        int target = Math.max(0, Math.min(2, current + (wasCorrect ? 1 : -1)));
        left.sort(Comparator.comparingInt(x -> Math.abs(Math.max(0, LEVELS.indexOf(x.getDifficulty())) - target)));
        return left.get(0);
    }

    /** Micro-learning intervention: the lesson that best matches a missed question. */
    private Remediation remediation(Course c, Question q) {
        List<Lesson> ls = lessons.findByCourseIdOrderBySortOrderAsc(c.getId());
        if (ls.isEmpty()) return null;
        Set<String> words = Arrays.stream(q.getText().toLowerCase().split("[^a-z0-9]+"))
                .filter(w -> w.length() > 3).collect(Collectors.toSet());
        Lesson best = ls.get(0);
        int bestScore = -1;
        for (Lesson l : ls) {
            String hay = (l.getTitle() + " " + l.getContent()).toLowerCase();
            int s = 0;
            for (String w : words) if (hay.contains(w)) s++;
            if (s > bestScore) {
                bestScore = s;
                best = l;
            }
        }
        String ex = best.getContent();
        if (ex.length() > 420) ex = ex.substring(0, 420) + "...";
        return new Remediation(best.getTitle(), ex);
    }

    // ---- helpers ----
    private static String certTitle(Course c) {
        return "Certificate of Completion: " + c.getTitle();
    }

    private void addRow(UUID attemptId, UUID questionId, int order) {
        AttemptAnswer aa = new AttemptAnswer();
        aa.setAttemptId(attemptId);
        aa.setQuestionId(questionId);
        aa.setSortOrder(order);
        answers.save(aa);
    }

    private Map<UUID, Question> loadQuestions(List<AttemptAnswer> rows) {
        return questions.findAllById(rows.stream().map(AttemptAnswer::getQuestionId).toList())
                .stream().collect(Collectors.toMap(Question::getId, Function.identity()));
    }

    private Attempt owned(UUID id) {
        AuthUser me = CurrentUser.get();
        return attempts.findById(id).filter(a -> a.getUserId().equals(me.userId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Test not found"));
    }

    /** Answers and explanations are only revealed once a question has been answered/scored. */
    private AttemptView view(Attempt a, Course c) {
        AuthUser me = CurrentUser.get();
        boolean done = "SUBMITTED".equals(a.getStatus());
        List<AttemptAnswer> rows = answers.findByAttemptIdOrderBySortOrderAsc(a.getId());
        Map<UUID, Question> qs = loadQuestions(rows);

        Map<String, int[]> by = new LinkedHashMap<>();
        for (String l : LEVELS) by.put(l, new int[2]);
        List<QuestionItem> items = new ArrayList<>();
        for (AttemptAnswer r : rows) {
            Question q = qs.get(r.getQuestionId());
            List<String> opts;
            try {
                opts = mapper.readValue(q.getOptionsJson(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                opts = List.of();
            }
            boolean reveal = r.getCorrect() != null;
            items.add(new QuestionItem(q.getId(), q.getText(), q.getKind(), opts, r.getSelectedIndex(),
                    reveal ? q.getCorrectIndex() : null, reveal ? q.getExplanation() : null,
                    reveal ? r.getCorrect() : null, reveal ? q.getDifficulty() : null));
            if (reveal && done) {
                int[] b = by.get(q.getDifficulty());
                if (b != null) {
                    b[1]++;
                    if (Boolean.TRUE.equals(r.getCorrect())) b[0]++;
                }
            }
        }

        boolean passed = done && a.getScore() >= PASS_MARK;
        String certCode = null;
        String note = null;
        if (passed && !Access.isAuthor(me)) {
            certCode = certificates.findActive(a.getUserId(), certTitle(c)).map(Certificate::getCode).orElse(null);
            if (certCode == null && !lessonsDone(a.getUserId(), c.getId())) {
                note = "Complete all lessons in this course to receive your certificate, then open this result again.";
            }
        }
        Long secondsLeft = null;
        if (!done && a.getTimeLimitMinutes() != null) {
            long elapsed = Duration.between(a.getCreatedAt(), Instant.now()).getSeconds();
            secondsLeft = Math.max(0L, a.getTimeLimitMinutes() * 60L - elapsed);
        }
        Long taken = done && a.getSubmittedAt() != null
                ? Duration.between(a.getCreatedAt(), a.getSubmittedAt()).getSeconds() : null;
        List<Breakdown> analysis = done
                ? by.entrySet().stream().filter(e -> e.getValue()[1] > 0)
                    .map(e -> new Breakdown(e.getKey(), e.getValue()[0], e.getValue()[1])).toList()
                : List.of();

        return new AttemptView(a.getId(), c.getId(), c.getTitle(), a.getStatus(), a.getMode(), a.getScore(),
                a.getCorrectCount() == null ? 0 : a.getCorrectCount(),
                a.getWrongCount() == null ? 0 : a.getWrongCount(), a.getTotal(),
                done ? passed : null, certCode, note, a.getTimeLimitMinutes(), a.isNegativeMarking(),
                secondsLeft, taken, analysis, items);
    }
}
