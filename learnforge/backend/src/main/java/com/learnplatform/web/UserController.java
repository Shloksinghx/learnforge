package com.learnplatform.web;

import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.*;
import com.learnplatform.repo.AppUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
public class UserController {
    public record CreateUser(@NotBlank String fullName, @Email @NotBlank String email,
                             @Size(min = 8) String password, @NotNull Role role) {}

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public UserController(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ORG_ADMIN','LND_MANAGER','TRAINER')")
    public List<AuthController.UserView> list() {
        return users.findByOrgIdOrderByCreatedAtDesc(CurrentUser.get().orgId()).stream()
                .map(u -> new AuthController.UserView(u.getId(), u.getOrgId(), u.getFullName(), u.getEmail(), u.getRole()))
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ORG_ADMIN','LND_MANAGER')")
    public AuthController.UserView create(@Valid @RequestBody CreateUser req) {
        if (req.role() == Role.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot assign this role");
        }
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        AppUser u = new AppUser();
        u.setOrgId(CurrentUser.get().orgId());
        u.setEmail(req.email().trim());
        u.setFullName(req.fullName());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole(req.role());
        users.save(u);
        return new AuthController.UserView(u.getId(), u.getOrgId(), u.getFullName(), u.getEmail(), u.getRole());
    }
}
