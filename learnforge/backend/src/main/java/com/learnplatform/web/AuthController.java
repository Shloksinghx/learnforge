package com.learnplatform.web;

import com.learnplatform.config.JwtService;
import com.learnplatform.domain.*;
import com.learnplatform.repo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public record RegisterOrg(@NotBlank String orgName, @NotBlank String fullName,
                              @Email @NotBlank String email, @Size(min = 8) String password) {}
    public record Login(@Email @NotBlank String email, @NotBlank String password) {}
    public record UserView(UUID id, UUID orgId, String fullName, String email, Role role) {}
    public record AuthResponse(String token, UserView user) {}

    private final OrganisationRepository orgs;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(OrganisationRepository orgs, AppUserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.orgs = orgs;
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/register-org")
    @Transactional
    public AuthResponse registerOrg(@Valid @RequestBody RegisterOrg req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        Organisation org = new Organisation();
        org.setName(req.orgName());
        orgs.save(org);
        AppUser u = new AppUser();
        u.setOrgId(org.getId());
        u.setEmail(req.email().trim());
        u.setFullName(req.fullName());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole(Role.ORG_ADMIN);
        users.save(u);
        return respond(u);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody Login req) {
        AppUser u = users.findByEmailIgnoreCase(req.email().trim())
                .filter(AppUser::isActive)
                .filter(x -> encoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return respond(u);
    }

    private AuthResponse respond(AppUser u) {
        return new AuthResponse(jwt.issue(u), new UserView(u.getId(), u.getOrgId(), u.getFullName(), u.getEmail(), u.getRole()));
    }
}
