package com.learnplatform.web;

import com.learnplatform.config.AuthUser;
import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.Course;
import com.learnplatform.domain.MockTest;
import com.learnplatform.repo.CourseRepository;
import com.learnplatform.repo.EnrolmentRepository;
import com.learnplatform.repo.MockTestRepository;
import com.learnplatform.repo.QuestionRepository;
import com.learnplatform.service.QuestionGenerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Named mock tests, for example "Advanced Sales Certification: 100 questions, 90 minutes, mostly hard". */
@RestController
@RequestMapping("/api")
public class MockTestController {
    public record CreateMock(@NotBlank @Size(max = 200) String title, @Min(1) @Max(200) int questionCount,
                             @Min(5) @Max(240) int minutes, @Min(0) @Max(100) int easyPct,
                             @Min(0) @Max(100) int mediumPct, @Min(0) @Max(100) int hardPct,
                             boolean negativeMarking) {}
    public record MockView(UUID id, String title, int questionCount, int minutes, int easyPct, int mediumPct,
                           int hardPct, boolean negativeMarking, int available, int shortage) {}

    private final CourseRepository courses;
    private final EnrolmentRepository enrolments;
    private final MockTestRepository mocks;
    private final QuestionRepository questions;
    private final QuestionGenerationService generator;

    public MockTestController(CourseRepository courses, EnrolmentRepository enrolments, MockTestRepository mocks,
                              QuestionRepository questions, QuestionGenerationService generator) {
        this.courses = courses;
        this.enrolments = enrolments;
        this.mocks = mocks;
        this.questions = questions;
        this.generator = generator;
    }

    @GetMapping("/courses/{id}/mocks")
    public List<MockView> list(@PathVariable UUID id) {
        AuthUser me = CurrentUser.get();
        Course c = Access.course(courses, me, id);
        if (!Access.isAuthor(me) && enrolments.findByCourseIdAndUserId(c.getId(), me.userId()).isEmpty()) return List.of();
        int available = questions.countByCourseIdAndStatus(c.getId(), "APPROVED");
        return mocks.findByCourseIdOrderByCreatedAtDesc(c.getId()).stream().map(m -> view(m, available)).toList();
    }

    @PostMapping("/courses/{id}/mocks")
    public MockView create(@PathVariable UUID id, @Valid @RequestBody CreateMock r) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        Course c = Access.course(courses, me, id);
        if (r.easyPct() + r.mediumPct() + r.hardPct() != 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The difficulty mix must add up to 100%");
        }
        MockTest m = new MockTest();
        m.setOrgId(me.orgId());
        m.setCourseId(c.getId());
        m.setTitle(r.title().trim());
        m.setQuestionCount(r.questionCount());
        m.setMinutes(r.minutes());
        m.setEasyPct(r.easyPct());
        m.setMediumPct(r.mediumPct());
        m.setHardPct(r.hardPct());
        m.setNegativeMarking(r.negativeMarking());
        mocks.save(m);
        return view(m, questions.countByCourseIdAndStatus(c.getId(), "APPROVED"));
    }

    @DeleteMapping("/mocks/{mockId}")
    public void delete(@PathVariable UUID mockId) {
        mocks.delete(own(mockId));
    }

    /** AI Mock Test Generator: drafts the questions a mock test is short of, in batches. Drafts still need approval. */
    @PostMapping("/mocks/{mockId}/generate-missing")
    public Map<String, Object> generateMissing(@PathVariable UUID mockId) {
        MockTest m = own(mockId);
        Course c = courses.findById(m.getCourseId()).orElseThrow();
        int approved = questions.countByCourseIdAndStatus(c.getId(), "APPROVED");
        int drafts = questions.countByCourseIdAndStatus(c.getId(), "DRAFT");
        int shortage = Math.max(0, m.getQuestionCount() - approved - drafts);
        if (shortage == 0) {
            return Map.of("created", 0, "message", "Enough questions already exist. Approve the drafts in the AI Question Studio.");
        }
        int toMake = Math.min(shortage, 60);
        int created = 0;
        while (created < toMake) {
            int batch = Math.min(20, toMake - created);
            created += generator.generate(c, batch, "MIXED", "MCQ", null).size();
        }
        return Map.of("created", created, "message", created + " draft questions added. Approve them in the AI Question Studio.");
    }

    private MockTest own(UUID mockId) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        return mocks.findById(mockId).filter(x -> x.getOrgId().equals(me.orgId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mock test not found"));
    }

    private MockView view(MockTest m, int available) {
        return new MockView(m.getId(), m.getTitle(), m.getQuestionCount(), m.getMinutes(), m.getEasyPct(),
                m.getMediumPct(), m.getHardPct(), m.isNegativeMarking(), available,
                Math.max(0, m.getQuestionCount() - available));
    }
}
