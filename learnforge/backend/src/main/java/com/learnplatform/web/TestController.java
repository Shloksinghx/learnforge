package com.learnplatform.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.config.AuthUser;
import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.*;
import com.learnplatform.repo.*;
import com.learnplatform.service.CertificateService;
import com.learnplatform.service.CertificationService;
import com.learnplatform.service.GamificationService;
import com.learnplatform.service.QuestionScoring;
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

/** Tests: knowledge tests, named mock tests, adaptive practice and the final exam, scored automatically. */
@RestController
@RequestMapping("/api")
public class TestController {
    private static final List<String> LEVELS = List.of("EASY", "MEDIUM", "HARD");

    public record StartReq(@Min(1) @Max(200) int count,
                           @Pattern(regexp = "(?i)STANDARD|ADAPTIVE") String mode,
                           @Min(1) @Max(240) Integer timeLimitMinutes,
                           Boolean negativeMarking,
                           @Pattern(regexp = "(?i)ALL|EASY|MEDIUM|HARD") String section) {}
    public record Answer(@NotNull UUID questionId, Integer selectedIndex, List<Integer> selectedIndexes) {}
    public record SubmitReq(@NotNull List<Answer> answers) {}
    public record QuestionItem(UUID id, String text, String kind, List<String> options, Integer selectedIndex,
                               List<Integer> selectedIndexes, Integer correctIndex, List<Integer> correctIndexes,
                               String explanation, Boolean correct, String difficulty) {}
    public record Breakdown(String difficulty, int correct, int total) {}
    public record AttemptView(UUID id, UUID courseId, String courseTitle, String title, String status, String mode,
                              Integer score, int correctCount, int wrongCount, int total, Boolean passed, int passMark,
                              String certificateCode, Integer timeLimitMinutes, boolean negativeMarking,
                              Long secondsLeft, Long timeTakenSeconds, List<Breakdown> analysis,
                              List<QuestionItem> questions) {}
    public record Remediation(String lessonTitle, String excerpt) {}
    public record AnswerResult(boolean correct, Integer correctIndex, List<Integer> correctIndexes, String explanation,
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
    private final CertificationService certification;
    private final LessonRepository lessons;
    private final MockTestRepository mockTests;
    private final GamificationService gamification;

    public TestController(CourseRepository courses, EnrolmentRepository enrolments, QuestionRepository questions,
                          AttemptRepository attempts, AttemptAnswerRepository answers, ObjectMapper mapper,
                          AppUserRepository users, CertificateService certificates,
                          CertificationService certification, LessonRepository lessons,
                          MockTestRepository mockTests, GamificationService gamification) {
        this.courses = courses;
        this.enrolments = enrolments;
        this.questions = questions;
        this.attempts = attempts;
        this.answers = answers;
        this.mapper = mapper;
        this.users = users;
        this.certificates = certificates;
        this.certification = certification;
        this.lessons = lessons;
        this.mockTests = mockTests;
        this.gamification = gamification;
    }

    // ---------- starting tests ----------
    @PostMapping("/courses/{id}/tests/start")
    public AttemptView start(@PathVariable UUID id, @Valid @RequestBody StartReq req) {
        AuthUser me = CurrentUser.get();
        Course c = enrolledCourse(me, id);
        boolean adaptive = "ADAPTIVE".equalsIgnoreCase(req.mode());
        String section = req.section() == null ? "ALL" : req.section().toUpperCase();

        List<Question> pool = new ArrayList<>(questions.findByCourseIdAndStatus(c.getId(), "APPROVED"));
        if (!adaptive && !"ALL".equals(section)) pool.removeIf(q -> !section.equals(q.getDifficulty()));
        if (pool.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No approved questions available for this selection");
        }
        Collections.shuffle(pool);
        int n = Math.min(req.count(), pool.size());

        Attempt a = newAttempt(me, c, adaptive ? "ADAPTIVE" : "STANDARD", n);
        a.setSection(adaptive ? "ALL" : section);
        a.setTimeLimitMinutes(adaptive ? null : req.timeLimitMinutes());
        a.setNegativeMarking(Boolean.TRUE.equals(req.negativeMarking()));
        attempts.save(a);

        if (adaptive) {
            Question first = pool.stream().filter(q -> "MEDIUM".equals(q.getDifficulty())).findFirst().orElse(pool.get(0));
            addRow(a.getId(), first.getId(), 0);
        } else {
            addRows(a.getId(), pool.subList(0, n));
        }
        return view(a, c);
    }

    /** The formal certification exam. Needs the earlier workflow steps (lessons, knowledge test, simulation). */
    @PostMapping("/courses/{id}/exam/start")
    public AttemptView startExam(@PathVariable UUID id) {
        AuthUser me = CurrentUser.get();
        Course c = enrolledCourse(me, id);
        if (!Access.isAuthor(me) && !certification.status(c, me.userId()).eligibleForExam()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Finish the earlier certification steps before the final exam");
        }
        List<Question> pool = new ArrayList<>(questions.findByCourseIdAndStatus(c.getId(), "APPROVED"));
        if (pool.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No approved questions for this course yet");
        Collections.shuffle(pool);
        int n = Math.min(c.getExamCount(), pool.size());
        Attempt a = newAttempt(me, c, "EXAM", n);
        a.setSection("ALL");
        a.setTimeLimitMinutes(c.getExamMinutes());
        attempts.save(a);
        addRows(a.getId(), pool.subList(0, n));
        return view(a, c);
    }

    /** A named mock test with a difficulty mix, for example 100 questions in 90 minutes. */
    @PostMapping("/mocks/{mockId}/start")
    public AttemptView startMock(@PathVariable UUID mockId) {
        AuthUser me = CurrentUser.get();
        MockTest m = mockTests.findById(mockId).filter(x -> x.getOrgId().equals(me.orgId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mock test not found"));
        Course c = enrolledCourse(me, m.getCourseId());
        List<Question> pool = new ArrayList<>(questions.findByCourseIdAndStatus(c.getId(), "APPROVED"));
        if (pool.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No approved questions for this course yet");

        List<Question> picked = pickBalanced(pool, m.getQuestionCount(), m.getEasyPct(), m.getMediumPct());
        Attempt a = newAttempt(me, c, "STANDARD", picked.size());
        a.setSection("ALL");
        a.setMockTestId(m.getId());
        a.setTimeLimitMinutes(m.getMinutes());
        a.setNegativeMarking(m.isNegativeMarking());
        attempts.save(a);
        addRows(a.getId(), picked);
        return view(a, c);
    }

    // ---------- taking tests ----------
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
        Map<UUID, List<Integer>> chosen = new HashMap<>();
        for (Answer x : req.answers()) chosen.put(x.questionId(), selection(x.selectedIndex(), x.selectedIndexes()));

        List<AttemptAnswer> rows = answers.findByAttemptIdOrderBySortOrderAsc(a.getId());
        Map<UUID, Question> qs = loadQuestions(rows);
        for (AttemptAnswer r : rows) {
            List<Integer> sel = chosen.getOrDefault(r.getQuestionId(), List.of());
            r.setSelectedIndex(sel.isEmpty() ? null : sel.get(0));
            r.setSelectedJson(sel.isEmpty() ? null : QuestionScoring.toJson(sel));
            r.setCorrect(QuestionScoring.isCorrect(qs.get(r.getQuestionId()), sel));
        }
        answers.saveAll(rows);
        return finish(a);
    }

    /** Adaptive mode: answer one question, get instant feedback and the next question. */
    @PostMapping("/attempts/{id}/answer")
    public AnswerResult answer(@PathVariable UUID id, @Valid @RequestBody Answer req) {
        Attempt a = owned(id);
        if (!"IN_PROGRESS".equals(a.getStatus()) || !"ADAPTIVE".equals(a.getMode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This is not an active adaptive test");
        }
        List<Integer> sel = selection(req.selectedIndex(), req.selectedIndexes());
        if (sel.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an answer");
        List<AttemptAnswer> rows = answers.findByAttemptIdOrderBySortOrderAsc(a.getId());
        AttemptAnswer row = rows.stream()
                .filter(r -> r.getQuestionId().equals(req.questionId()) && r.getCorrect() == null)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Question already answered"));
        Question q = questions.findById(row.getQuestionId()).orElseThrow();
        boolean ok = QuestionScoring.isCorrect(q, sel);
        row.setSelectedIndex(sel.get(0));
        row.setSelectedJson(QuestionScoring.toJson(sel));
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
        List<Integer> correctSet = QuestionScoring.correctSet(q);
        return new AnswerResult(ok, q.getCorrectIndex(), correctSet, q.getExplanation(), rem, v);
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
                    done ? a.getScore() >= a.getPassMark() : null,
                    done ? a.getSubmittedAt() : a.getCreatedAt());
        }).toList();
    }

    // ---------- scoring ----------
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
            boolean passed = score >= a.getPassMark();
            int best = attempts.findByUserIdOrderByCreatedAtDesc(me.userId()).stream()
                    .filter(x -> x.getCourseId().equals(a.getCourseId()) && "SUBMITTED".equals(x.getStatus())
                            && x.getScore() != null && !x.getId().equals(a.getId()))
                    .mapToInt(Attempt::getScore).max().orElse(-1);
            int pts = 20 + (passed ? 30 : 0) + (best >= 0 && score > best ? 20 : 0)
                    + ("EXAM".equals(a.getMode()) && passed ? 50 : 0);
            gamification.award(me.orgId(), me.userId(), "TEST", a.getId().toString(), pts);
            gamification.refresh(me.orgId(), me.userId());
            ensureCertificate(a, c);
        }
        return view(a, c);
    }

    /** Only a passed final exam earns a certificate. */
    private Optional<Certificate> ensureCertificate(Attempt a, Course c) {
        if (!"EXAM".equals(a.getMode()) || a.getScore() == null || a.getScore() < a.getPassMark()) return Optional.empty();
        if (Access.isAuthor(CurrentUser.get())) return Optional.empty();
        String title = CertificateService.titleFor(c);
        Optional<Certificate> existing = certificates.findActive(a.getUserId(), title);
        if (existing.isPresent()) return existing;
        return Optional.of(certificates.issue(users.findById(a.getUserId()).orElseThrow(), title, a.getScore(), 24));
    }

    // ---------- selection helpers ----------
    private static List<Question> pickBalanced(List<Question> pool, int count, int easyPct, int mediumPct) {
        int n = Math.min(count, pool.size());
        Map<String, List<Question>> by = new HashMap<>();
        for (String l : LEVELS) by.put(l, new ArrayList<>());
        for (Question q : pool) by.computeIfAbsent(LEVELS.contains(q.getDifficulty()) ? q.getDifficulty() : "MEDIUM", k -> new ArrayList<>()).add(q);
        by.values().forEach(Collections::shuffle);

        int easy = Math.round(n * easyPct / 100f);
        int medium = Math.round(n * mediumPct / 100f);
        int hard = Math.max(0, n - easy - medium);
        List<Question> out = new ArrayList<>();
        out.addAll(take(by.get("EASY"), easy));
        out.addAll(take(by.get("MEDIUM"), medium));
        out.addAll(take(by.get("HARD"), hard));
        if (out.size() < n) {
            List<Question> rest = new ArrayList<>(pool);
            rest.removeAll(out);
            Collections.shuffle(rest);
            out.addAll(rest.subList(0, Math.min(n - out.size(), rest.size())));
        }
        Collections.shuffle(out);
        return out;
    }

    private static List<Question> take(List<Question> from, int n) {
        return new ArrayList<>(from.subList(0, Math.min(n, from.size())));
    }

    private static List<Integer> selection(Integer single, List<Integer> many) {
        if (many != null && !many.isEmpty()) return many.stream().filter(Objects::nonNull).distinct().sorted().toList();
        return single == null ? List.of() : List.of(single);
    }

    private Question pickNext(Course c, List<AttemptAnswer> rows, Question last, boolean wasCorrect) {
        Set<UUID> used = rows.stream().map(AttemptAnswer::getQuestionId).collect(Collectors.toSet());
        List<Question> left = questions.findByCourseIdAndStatus(c.getId(), "APPROVED").stream()
                .filter(x -> !used.contains(x.getId())).collect(Collectors.toCollection(ArrayList::new));
        if (left.isEmpty()) return null;
        Collections.shuffle(left);
        int current = Math.max(0, LEVELS.indexOf(last.getDifficulty()));
        int target = Math.max(0, Math.min(2, current + (wasCorrect ? 1 : -1)));
        left.sort(Comparator.<Question>comparingInt(x -> Math.abs(Math.max(0, LEVELS.indexOf(x.getDifficulty())) - target)));
        return left.get(0);
    }

    /** Micro-learning intervention: the lesson that best matches a missed question. */
    private Remediation remediation(Course c, Question q) {
        List<Lesson> ls = lessons.findByCourseIdOrderBySortOrderAsc(c.getId());
        if (ls.isEmpty()) return null;
        Set<String> words = Arrays.stream(q.getText().toLowerCase().split("[^\\p{L}\\p{N}]+"))
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

    // ---------- helpers ----------
    private Course enrolledCourse(AuthUser me, UUID courseId) {
        Course c = Access.course(courses, me, courseId);
        if (!Access.isAuthor(me) && enrolments.findByCourseIdAndUserId(c.getId(), me.userId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Enrol in the course first");
        }
        return c;
    }

    private Attempt newAttempt(AuthUser me, Course c, String mode, int total) {
        Attempt a = new Attempt();
        a.setOrgId(me.orgId());
        a.setCourseId(c.getId());
        a.setUserId(me.userId());
        a.setMode(mode);
        a.setTotal(total);
        a.setPassMark(c.getPassMark());
        return attempts.save(a);
    }

    private void addRows(UUID attemptId, List<Question> picked) {
        int order = 0;
        for (Question q : picked) addRow(attemptId, q.getId(), order++);
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
            List<Integer> selected = QuestionScoring.parse(r.getSelectedJson());
            if (selected.isEmpty() && r.getSelectedIndex() != null) selected = List.of(r.getSelectedIndex());
            items.add(new QuestionItem(q.getId(), q.getText(), q.getKind(), opts, r.getSelectedIndex(), selected,
                    reveal ? q.getCorrectIndex() : null, reveal ? QuestionScoring.correctSet(q) : null,
                    reveal ? q.getExplanation() : null, reveal ? r.getCorrect() : null,
                    reveal ? q.getDifficulty() : null));
            if (reveal && done) {
                int[] b = by.get(q.getDifficulty());
                if (b != null) {
                    b[1]++;
                    if (Boolean.TRUE.equals(r.getCorrect())) b[0]++;
                }
            }
        }

        boolean passed = done && a.getScore() >= a.getPassMark();
        String certCode = passed && "EXAM".equals(a.getMode()) && !Access.isAuthor(CurrentUser.get())
                ? certificates.findActive(a.getUserId(), CertificateService.titleFor(c)).map(Certificate::getCode).orElse(null)
                : null;
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
        String title = a.getMockTestId() == null ? null
                : mockTests.findById(a.getMockTestId()).map(MockTest::getTitle).orElse(null);

        return new AttemptView(a.getId(), c.getId(), c.getTitle(), title, a.getStatus(), a.getMode(), a.getScore(),
                a.getCorrectCount() == null ? 0 : a.getCorrectCount(),
                a.getWrongCount() == null ? 0 : a.getWrongCount(), a.getTotal(),
                done ? passed : null, a.getPassMark(), certCode, a.getTimeLimitMinutes(), a.isNegativeMarking(),
                secondsLeft, taken, analysis, items);
    }
}
