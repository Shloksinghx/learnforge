package com.learnplatform.web;

import com.learnplatform.config.AuthUser;
import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.*;
import com.learnplatform.repo.AppUserRepository;
import com.learnplatform.service.UsageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
public class UserController {
    public record CreateUser(@NotBlank String fullName, @Email @NotBlank String email,
                             @Size(min = 8) String password, @NotNull Role role, @Size(max = 100) String team) {}
    public record TeamReq(@Size(max = 100) String team) {}
    public record PersonView(UUID id, String fullName, String email, Role role, String team) {}

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final UsageService usage;

    public UserController(AppUserRepository users, PasswordEncoder encoder, UsageService usage) {
        this.users = users;
        this.encoder = encoder;
        this.usage = usage;
    }

    @GetMapping
    public List<PersonView> list() {
        AuthUser me = CurrentUser.get();
        Access.requireViewer(me);
        return users.findByOrgIdOrderByCreatedAtDesc(me.orgId()).stream().map(this::view).toList();
    }

    @PostMapping
    public PersonView create(@Valid @RequestBody CreateUser req) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        if (req.role() == Role.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot assign this role");
        }
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        if (req.role() == Role.LEARNER) {
            long learners = users.findByOrgIdOrderByCreatedAtDesc(me.orgId()).stream().filter(u -> u.getRole() == Role.LEARNER).count();
            if (learners >= usage.planOf(me.orgId()).learners) {
                throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Your plan's learner limit is reached");
            }
        }
        AppUser u = new AppUser();
        u.setOrgId(me.orgId());
        u.setEmail(req.email().trim());
        u.setFullName(req.fullName());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole(req.role());
        u.setTeam(req.team() == null || req.team().isBlank() ? null : req.team().trim());
        return view(users.save(u));
    }

    @PutMapping("/{id}/team")
    public PersonView setTeam(@PathVariable UUID id, @Valid @RequestBody TeamReq req) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        AppUser u = users.findById(id).filter(x -> x.getOrgId().equals(me.orgId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found"));
        u.setTeam(req.team() == null || req.team().isBlank() ? null : req.team().trim());
        return view(users.save(u));
    }

    private PersonView view(AppUser u) {
        return new PersonView(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.getTeam());
    }
}
