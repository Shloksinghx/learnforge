package com.learnplatform.web;

import com.learnplatform.config.AuthUser;
import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.*;
import com.learnplatform.repo.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Marketplace: organisations share courses and personas; others import an independent copy as a draft. */
@RestController
@RequestMapping("/api/marketplace")
public class MarketplaceController {
    public record CourseItem(UUID id, String title, String description, String category, int lessons, int questions, String publisher) {}
    public record PersonaItem(UUID id, String name, String profession, String scenario, String publisher) {}
    public record Listing(List<CourseItem> courses, List<PersonaItem> personas) {}

    private final CourseRepository courses;
    private final PersonaRepository personas;
    private final LessonRepository lessons;
    private final QuestionRepository questions;
    private final OrganisationRepository orgs;

    public MarketplaceController(CourseRepository courses, PersonaRepository personas, LessonRepository lessons,
                                 QuestionRepository questions, OrganisationRepository orgs) {
        this.courses = courses;
        this.personas = personas;
        this.lessons = lessons;
        this.questions = questions;
        this.orgs = orgs;
    }

    @GetMapping
    public Listing list() {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        Map<UUID, String> names = orgs.findAll().stream().collect(Collectors.toMap(Organisation::getId, Organisation::getName));
        List<CourseItem> cs = courses.findBySharedTrueAndOrgIdNot(me.orgId()).stream()
                .filter(c -> "PUBLISHED".equals(c.getStatus()))
                .map(c -> new CourseItem(c.getId(), c.getTitle(), c.getDescription(), c.getCategory(),
                        lessons.countByCourseId(c.getId()), questions.countByCourseIdAndStatus(c.getId(), "APPROVED"),
                        names.getOrDefault(c.getOrgId(), "Another organisation"))).toList();
        List<PersonaItem> ps = personas.findBySharedTrueAndOrgIdNot(me.orgId()).stream()
                .map(p -> new PersonaItem(p.getId(), p.getName(), p.getProfession(), p.getScenario(),
                        names.getOrDefault(p.getOrgId(), "Another organisation"))).toList();
        return new Listing(cs, ps);
    }

    @PostMapping("/courses/{id}/import")
    public Map<String, Object> importCourse(@PathVariable UUID id) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        Course src = courses.findById(id).filter(c -> c.isShared() && "PUBLISHED".equals(c.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        Course copy = new Course();
        copy.setOrgId(me.orgId());
        copy.setTitle(src.getTitle());
        copy.setDescription(src.getDescription());
        copy.setCategory(src.getCategory());
        courses.save(copy);
        for (Lesson l : lessons.findByCourseIdOrderBySortOrderAsc(src.getId())) {
            Lesson n = new Lesson();
            n.setCourseId(copy.getId());
            n.setTitle(l.getTitle());
            n.setContent(l.getContent());
            n.setModuleTitle(l.getModuleTitle());
            n.setVideoUrl(l.getVideoUrl());
            n.setSortOrder(l.getSortOrder());
            lessons.save(n);
        }
        int qs = 0;
        for (Question q : questions.findByCourseIdAndStatus(src.getId(), "APPROVED")) {
            Question n = new Question();
            n.setOrgId(me.orgId());
            n.setCourseId(copy.getId());
            n.setText(q.getText());
            n.setOptionsJson(q.getOptionsJson());
            n.setCorrectIndex(q.getCorrectIndex());
            n.setCorrectJson(q.getCorrectJson());
            n.setExplanation(q.getExplanation());
            n.setDifficulty(q.getDifficulty());
            n.setKind(q.getKind());
            n.setStatus("DRAFT"); // imported questions are reviewed before use
            questions.save(n);
            qs++;
        }
        return Map.of("courseId", copy.getId(), "questions", qs);
    }

    @PostMapping("/personas/{id}/import")
    public Persona importPersona(@PathVariable UUID id) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        Persona src = personas.findById(id).filter(Persona::isShared)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Persona not found"));
        Persona p = new Persona();
        p.setOrgId(me.orgId());
        p.setName(src.getName());
        p.setProfession(src.getProfession());
        p.setAge(src.getAge());
        p.setPersonality(src.getPersonality());
        p.setCommunication(src.getCommunication());
        p.setKnowledgeLevel(src.getKnowledgeLevel());
        p.setBehaviour(src.getBehaviour());
        p.setObjections(src.getObjections());
        p.setScenario(src.getScenario());
        p.setMandatoryInfo(src.getMandatoryInfo());
        p.setProhibitedStatements(src.getProhibitedStatements());
        p.setObjective(src.getObjective());
        p.setSuccessCriteria(src.getSuccessCriteria());
        p.setExpectedBehaviour(src.getExpectedBehaviour());
        p.setTimeLimitMinutes(src.getTimeLimitMinutes());
        return personas.save(p);
    }

    @PostMapping("/personas/{id}/share")
    public Persona sharePersona(@PathVariable UUID id, @RequestBody CourseController.ShareReq req) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        Persona p = personas.findById(id).filter(x -> x.getOrgId().equals(me.orgId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Persona not found"));
        p.setShared(req.shared());
        return personas.save(p);
    }
}
