package com.learnplatform.web;

import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.Persona;
import com.learnplatform.repo.PersonaRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/personas")
public class PersonaController {
    public record CreatePersona(@NotBlank String name, @NotBlank String profession, Integer age,
                                String personality, String communication, String knowledgeLevel,
                                String behaviour, String objections, @NotBlank String scenario,
                                String mandatoryInfo, String prohibitedStatements,
                                String objective, String successCriteria, String expectedBehaviour,
                                @Min(1) @Max(60) Integer timeLimitMinutes) {}

    private final PersonaRepository personas;

    public PersonaController(PersonaRepository personas) {
        this.personas = personas;
    }

    @GetMapping
    public List<Persona> list() {
        return personas.findByOrgIdOrderByCreatedAtDesc(CurrentUser.get().orgId());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN')")
    public Persona create(@Valid @RequestBody CreatePersona r) {
        Persona p = new Persona();
        p.setOrgId(CurrentUser.get().orgId());
        p.setName(r.name());
        p.setProfession(r.profession());
        p.setAge(r.age());
        p.setPersonality(r.personality());
        p.setCommunication(r.communication());
        p.setKnowledgeLevel(r.knowledgeLevel());
        p.setBehaviour(r.behaviour());
        p.setObjections(r.objections());
        p.setScenario(r.scenario());
        p.setMandatoryInfo(r.mandatoryInfo());
        p.setProhibitedStatements(r.prohibitedStatements());
        p.setObjective(r.objective());
        p.setSuccessCriteria(r.successCriteria());
        p.setExpectedBehaviour(r.expectedBehaviour());
        p.setTimeLimitMinutes(r.timeLimitMinutes());
        return personas.save(p);
    }
}
