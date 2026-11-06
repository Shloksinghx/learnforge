package com.learnplatform.service;

import com.learnplatform.config.OrgContext;
import com.learnplatform.domain.Organisation;
import com.learnplatform.domain.UsageCounter;
import com.learnplatform.repo.OrganisationRepository;
import com.learnplatform.repo.UsageCounterRepository;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Plans and monthly usage metering (AI calls, certificates). Payments are not connected yet. */
@Service
public class UsageService {
    public enum Plan {
        FREE(300, 30), PRO(5000, 500), ENTERPRISE(Integer.MAX_VALUE, Integer.MAX_VALUE);

        public final int aiCalls;
        public final int learners;

        Plan(int aiCalls, int learners) {
            this.aiCalls = aiCalls;
            this.learners = learners;
        }
    }

    public static final String AI_CALL = "AI_CALL";
    public static final String CERTIFICATE = "CERTIFICATE";

    private final UsageCounterRepository counters;
    private final OrganisationRepository orgs;

    public UsageService(UsageCounterRepository counters, OrganisationRepository orgs) {
        this.counters = counters;
        this.orgs = orgs;
    }

    public Plan planOf(UUID orgId) {
        return orgs.findById(orgId).map(Organisation::getPlan).map(p -> {
            try { return Plan.valueOf(p); } catch (Exception e) { return Plan.PRO; }
        }).orElse(Plan.PRO);
    }

    public int used(UUID orgId, String metric) {
        return counters.findByOrgIdAndMonthAndMetric(orgId, month(), metric).map(UsageCounter::getUsedCount).orElse(0);
    }

    /** Called before every AI request. Throws 402 when the plan's monthly allowance is used up. */
    public void consumeAiCall() {
        UUID org = OrgContext.get();
        if (org == null) return;
        if (used(org, AI_CALL) >= planOf(org).aiCalls) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "Your plan's monthly AI allowance is used up. Ask your administrator to upgrade the plan.");
        }
        count(org, AI_CALL);
    }

    public void count(UUID orgId, String metric) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                UsageCounter c = counters.findByOrgIdAndMonthAndMetric(orgId, month(), metric).orElseGet(() -> {
                    UsageCounter n = new UsageCounter();
                    n.setOrgId(orgId);
                    n.setMonth(month());
                    n.setMetric(metric);
                    return n;
                });
                c.setUsedCount(c.getUsedCount() + 1);
                counters.save(c);
                return;
            } catch (DataIntegrityViolationException e) {
                // created concurrently: retry once against the new row
            }
        }
    }

    private static String month() {
        return YearMonth.now(ZoneOffset.UTC).toString();
    }
}
