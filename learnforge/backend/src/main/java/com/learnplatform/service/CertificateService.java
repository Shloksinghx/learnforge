package com.learnplatform.service;

import com.learnplatform.domain.AppUser;
import com.learnplatform.domain.Certificate;
import com.learnplatform.domain.Course;
import com.learnplatform.repo.CertificateRepository;
import com.learnplatform.repo.OrganisationRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CertificateService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();
    private final CertificateRepository certs;
    private final OrganisationRepository orgs;
    private final NotificationService notifications;
    private final UsageService usage;

    public CertificateService(CertificateRepository certs, OrganisationRepository orgs,
                              NotificationService notifications, UsageService usage) {
        this.certs = certs;
        this.orgs = orgs;
        this.notifications = notifications;
        this.usage = usage;
    }

    public Certificate issue(AppUser learner, String title, int score, int validityMonths) {
        Certificate c = new Certificate();
        c.setOrgId(learner.getOrgId());
        c.setCode(newCode());
        c.setLearnerId(learner.getId());
        c.setLearnerName(learner.getFullName());
        c.setTitle(title.length() > 255 ? title.substring(0, 255) : title);
        c.setScore(score);
        c.setIssuer(orgs.findById(learner.getOrgId()).orElseThrow().getName());
        Instant now = Instant.now();
        c.setCreatedAt(now);
        c.setValidUntil(now.atZone(ZoneOffset.UTC).plusMonths(validityMonths).toInstant());
        Certificate saved = certs.save(c);
        usage.count(learner.getOrgId(), UsageService.CERTIFICATE);
        notifications.notify(learner.getOrgId(), learner.getId(), "Certificate earned",
                saved.getTitle() + " is ready. Open it to download the PDF.", "/verify/" + saved.getCode(),
                "cert:" + saved.getCode());
        return saved;
    }

    /** The certificate name: the course's custom title, or a default built from the course name. */
    public static String titleFor(Course c) {
        String custom = c.getCertificateTitle();
        String t = custom != null && !custom.isBlank() ? custom.trim() : "Certificate of Completion: " + c.getTitle();
        return t.length() > 255 ? t.substring(0, 255) : t;
    }

    public Optional<Certificate> findActive(UUID learnerId, String title) {
        String t = title.length() > 255 ? title.substring(0, 255) : title;
        return certs.findByLearnerIdOrderByCreatedAtDesc(learnerId).stream()
                .filter(c -> c.getTitle().equals(t) && !c.isRevoked())
                .findFirst();
    }

    private String newCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder sb = new StringBuilder("LF-");
            for (int i = 0; i < 10; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            String code = sb.toString();
            if (certs.findByCode(code).isEmpty()) return code;
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not allocate certificate code");
    }
}
