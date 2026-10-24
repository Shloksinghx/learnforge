package com.learnplatform.web;

import com.learnplatform.config.CurrentUser;
import com.learnplatform.service.SimulationService;
import com.learnplatform.service.SimulationService.SimulationView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulations")
public class SimulationController {
    public record StartReq(@NotNull UUID personaId, @Min(1) @Max(5) int difficulty) {}
    public record MessageReq(@NotBlank @Size(max = 4000) String text) {}

    private final SimulationService service;

    public SimulationController(SimulationService service) {
        this.service = service;
    }

    @PostMapping
    public SimulationView start(@Valid @RequestBody StartReq r) {
        return service.start(CurrentUser.get(), r.personaId(), r.difficulty());
    }

    @GetMapping("/{id}")
    public SimulationView get(@PathVariable UUID id) {
        return service.get(CurrentUser.get(), id);
    }

    @PostMapping("/{id}/messages")
    public SimulationView message(@PathVariable UUID id, @Valid @RequestBody MessageReq r) {
        return service.reply(CurrentUser.get(), id, r.text());
    }

    @PostMapping("/{id}/finish")
    public SimulationView finish(@PathVariable UUID id) {
        return service.finish(CurrentUser.get(), id);
    }
}
