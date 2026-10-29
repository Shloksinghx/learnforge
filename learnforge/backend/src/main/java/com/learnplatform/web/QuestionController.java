package com.learnplatform.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.config.AuthUser;
import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.Course;
import com.learnplatform.domain.Question;
import com.learnplatform.repo.CourseRepository;
import com.learnplatform.repo.QuestionRepository;
import com.learnplatform.service.QuestionGenerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** AI Question Studio: generate, review, approve. Authors only. */
@RestController
@RequestMapping("/api")
public class QuestionController {
    public record GenerateReq(@Min(1) @Max(20) int count,
                              @Pattern(regexp = "(?i)EASY|MEDIUM|HARD|MIXED") String difficulty,
                              @Pattern(regexp = "(?i)MCQ|TRUE_FALSE|CASE_BASED") String type,
                              @Size(max = 200) String topic) {}
    public record QuestionView(UUID id, String text, String kind, List<String> options, int correctIndex,
                               String explanation, String difficulty, String status) {}

    private final CourseRepository courses;
    private final QuestionRepository questions;
    private final QuestionGenerationService generator;
    private final ObjectMapper mapper;

    public QuestionController(CourseRepository courses, QuestionRepository questions,
                              QuestionGenerationService generator, ObjectMapper mapper) {
        this.courses = courses;
        this.questions = questions;
        this.generator = generator;
        this.mapper = mapper;
    }

    @GetMapping("/courses/{id}/questions")
    public List<QuestionView> list(@PathVariable UUID id) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        Course c = Access.course(courses, me, id);
        return questions.findByCourseIdOrderByCreatedAtDesc(c.getId()).stream().map(this::view).toList();
    }

    @PostMapping("/courses/{id}/questions/generate")
    public List<QuestionView> generate(@PathVariable UUID id, @Valid @RequestBody GenerateReq req) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        Course c = Access.course(courses, me, id);
        return generator.generate(c, req.count(), req.difficulty() == null ? "MIXED" : req.difficulty(), req.type(), req.topic()).stream().map(this::view).toList();
    }

    @PostMapping("/courses/{id}/questions/approve-all")
    public List<QuestionView> approveAll(@PathVariable UUID id) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        Course c = Access.course(courses, me, id);
        List<Question> drafts = questions.findByCourseIdAndStatus(c.getId(), "DRAFT");
        drafts.forEach(q -> q.setStatus("APPROVED"));
        questions.saveAll(drafts);
        return questions.findByCourseIdOrderByCreatedAtDesc(c.getId()).stream().map(this::view).toList();
    }

    @PostMapping("/questions/{qid}/approve")
    public QuestionView approve(@PathVariable UUID qid) {
        Question q = own(qid);
        q.setStatus("APPROVED");
        return view(questions.save(q));
    }

    @DeleteMapping("/questions/{qid}")
    public void delete(@PathVariable UUID qid) {
        Question q = own(qid);
        if (!"DRAFT".equals(q.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft questions can be deleted");
        }
        questions.delete(q);
    }

    private Question own(UUID qid) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        return questions.findById(qid).filter(q -> q.getOrgId().equals(me.orgId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));
    }

    private QuestionView view(Question q) {
        List<String> opts;
        try {
            opts = mapper.readValue(q.getOptionsJson(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            opts = List.of();
        }
        return new QuestionView(q.getId(), q.getText(), q.getKind(), opts, q.getCorrectIndex(), q.getExplanation(),
                q.getDifficulty(), q.getStatus());
    }
}
