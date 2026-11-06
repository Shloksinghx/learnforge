package com.learnplatform.web;

import com.learnplatform.config.AuthUser;
import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.Persona;
import com.learnplatform.domain.Scenario;
import com.learnplatform.repo.PersonaRepository;
import com.learnplatform.repo.ScenarioRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Scenarios: a reusable simulation set-up that pairs a persona with objectives and success criteria. */
@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {
    public record CreateScenario(@NotNull UUID personaId, UUID courseId, @NotBlank String title, @NotBlank String description,
                                 String objective, String expectedBehaviour, String mandatoryInfo,
                                 String prohibitedStatements, String evaluationCriteria, String successCriteria,
                                 @Min(1) @Max(5) Integer difficulty, @Min(1) @Max(60) Integer timeLimitMinutes) {}
    public record ScenarioView(UUID id, String title, String description, UUID personaId, String personaName,
                               String personaProfession, UUID courseId, int difficulty, Integer timeLimitMinutes,
                               String objective) {}

    private final ScenarioRepository scenarios;
    private final PersonaRepository personas;

    public ScenarioController(ScenarioRepository scenarios, PersonaRepository personas) {
        this.scenarios = scenarios;
        this.personas = personas;
    }

    @GetMapping
    public List<ScenarioView> list() {
        AuthUser me = CurrentUser.get();
        return scenarios.findByOrgIdOrderByCreatedAtDesc(me.orgId()).stream().map(this::view).toList();
    }

    @PostMapping
    public ScenarioView create(@Valid @RequestBody CreateScenario r) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        personas.findById(r.personaId()).filter(p -> p.getOrgId().equals(me.orgId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a persona from your organisation"));
        Scenario s = new Scenario();
        s.setOrgId(me.orgId());
        s.setPersonaId(r.personaId());
        s.setCourseId(r.courseId());
        s.setTitle(r.title().trim());
        s.setDescription(r.description());
        s.setObjective(r.objective());
        s.setExpectedBehaviour(r.expectedBehaviour());
        s.setMandatoryInfo(r.mandatoryInfo());
        s.setProhibitedStatements(r.prohibitedStatements());
        s.setEvaluationCriteria(r.evaluationCriteria());
        s.setSuccessCriteria(r.successCriteria());
        s.setDifficulty(r.difficulty() == null ? 3 : r.difficulty());
        s.setTimeLimitMinutes(r.timeLimitMinutes());
        return view(scenarios.save(s));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        scenarios.delete(scenarios.findById(id).filter(s -> s.getOrgId().equals(me.orgId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scenario not found")));
    }

    private ScenarioView view(Scenario s) {
        Persona p = personas.findById(s.getPersonaId()).orElse(null);
        return new ScenarioView(s.getId(), s.getTitle(), s.getDescription(), s.getPersonaId(),
                p == null ? null : p.getName(), p == null ? null : p.getProfession(), s.getCourseId(),
                s.getDifficulty(), s.getTimeLimitMinutes(), s.getObjective());
    }
}
