package com.learnplatform.service;

import com.learnplatform.domain.AppUser;
import com.learnplatform.domain.Certificate;
import com.learnplatform.domain.Notification;
import com.learnplatform.domain.Role;
import com.learnplatform.repo.AppUserRepository;
import com.learnplatform.repo.CertificateRepository;
import com.learnplatform.repo.NotificationRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** In-app notifications. A (user, ref) pair is only ever notified once. */
@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final AppUserRepository users;
    private final CertificateRepository certificates;

    public NotificationService(NotificationRepository notifications, AppUserRepository users,
                               CertificateRepository certificates) {
        this.notifications = notifications;
        this.users = users;
        this.certificates = certificates;
    }

    public void notify(UUID orgId, UUID userId, String title, String body, String link, String ref) {
        if (ref != null && notifications.existsByUserIdAndRef(userId, ref)) return;
        Notification n = new Notification();
        n.setOrgId(orgId);
        n.setUserId(userId);
        n.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
        n.setBody(body);
        n.setLink(link);
        n.setRef(ref);
        try {
            notifications.save(n);
        } catch (DataIntegrityViolationException ignored) {
            // already notified
        }
    }

    /** Tell every learner in the organisation. refPrefix keeps each learner from being notified twice. */
    public void notifyLearners(UUID orgId, String title, String body, String link, String refPrefix) {
        for (AppUser u : users.findByOrgIdOrderByCreatedAtDesc(orgId)) {
            if (u.getRole() == Role.LEARNER && u.isActive()) {
                notify(orgId, u.getId(), title, body, link, refPrefix == null ? null : refPrefix + ":" + u.getId());
            }
        }
    }

    /** Warns a learner about certificates that expire within two weeks. */
    public void noticeExpiring(UUID orgId, UUID userId) {
        Instant now = Instant.now();
        for (Certificate c : certificates.findByLearnerIdOrderByCreatedAtDesc(userId)) {
            if (c.isRevoked() || !c.getValidUntil().isAfter(now) || !c.getValidUntil().isBefore(now.plus(14, ChronoUnit.DAYS))) continue;
            notify(orgId, userId, "Your certificate is about to expire",
                    c.getTitle() + " expires soon. Retake the final exam to renew it.", "/", "expiring:" + c.getCode());
        }
    }

    /** Daily sweep. On free hosting the server may be asleep, so learners also trigger this when they open the bell. */
    @Scheduled(cron = "0 0 3 * * *")
    public void sweepExpiring() {
        for (AppUser u : users.findAll()) {
            if (u.getRole() == Role.LEARNER) noticeExpiring(u.getOrgId(), u.getId());
        }
    }
}
