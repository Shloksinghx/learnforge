package com.learnplatform.web;

import com.learnplatform.config.AuthUser;
import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.Organisation;
import com.learnplatform.domain.Role;
import com.learnplatform.repo.AppUserRepository;
import com.learnplatform.repo.OrganisationRepository;
import com.learnplatform.service.UsageService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Plan and monthly usage. Payment collection is not connected yet; plan changes are made by the platform owner. */
@RestController
@RequestMapping("/api/admin/plan")
public class PlanController {
    public record PlanView(String plan, int aiCallsUsed, int aiCallsLimit, int certificatesThisMonth,
                           int learners, int learnersLimit, boolean paymentsConnected) {}

    private final UsageService usage;
    private final OrganisationRepository orgs;
    private final AppUserRepository users;

    public PlanController(UsageService usage, OrganisationRepository orgs, AppUserRepository users) {
        this.usage = usage;
        this.orgs = orgs;
        this.users = users;
    }

    @GetMapping
    public PlanView get() {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        UsageService.Plan plan = usage.planOf(me.orgId());
        int learners = (int) users.findByOrgIdOrderByCreatedAtDesc(me.orgId()).stream().filter(u -> u.getRole() == Role.LEARNER).count();
        return new PlanView(plan.name(), usage.used(me.orgId(), UsageService.AI_CALL),
                plan.aiCalls == Integer.MAX_VALUE ? -1 : plan.aiCalls,
                usage.used(me.orgId(), UsageService.CERTIFICATE), learners,
                plan.learners == Integer.MAX_VALUE ? -1 : plan.learners, false);
    }

    @PutMapping
    public PlanView change(@RequestBody Map<String, String> body) {
        AuthUser me = CurrentUser.get();
        if (me.role() != Role.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Plan changes are made by the platform owner");
        }
        Organisation o = orgs.findById(me.orgId()).orElseThrow();
        try {
            o.setPlan(UsageService.Plan.valueOf(String.valueOf(body.get("plan"))).name());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown plan");
        }
        orgs.save(o);
        return get();
    }
}
