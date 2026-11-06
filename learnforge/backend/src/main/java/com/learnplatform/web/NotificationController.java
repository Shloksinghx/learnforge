package com.learnplatform.web;

import com.learnplatform.config.AuthUser;
import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.Notification;
import com.learnplatform.repo.NotificationRepository;
import com.learnplatform.service.NotificationService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    public record Item(UUID id, String title, String body, String link, boolean read, Instant createdAt) {}
    public record Feed(long unread, List<Item> items) {}

    private final NotificationRepository notifications;
    private final NotificationService service;

    public NotificationController(NotificationRepository notifications, NotificationService service) {
        this.notifications = notifications;
        this.service = service;
    }

    @GetMapping
    public Feed feed() {
        AuthUser me = CurrentUser.get();
        service.noticeExpiring(me.orgId(), me.userId());
        List<Item> items = notifications.findTop30ByUserIdOrderByCreatedAtDesc(me.userId()).stream()
                .map(n -> new Item(n.getId(), n.getTitle(), n.getBody(), n.getLink(), n.isRead(), n.getCreatedAt())).toList();
        return new Feed(notifications.countByUserIdAndReadFalse(me.userId()), items);
    }

    @PostMapping("/read-all")
    public void readAll() {
        List<Notification> unread = notifications.findByUserIdAndReadFalse(CurrentUser.get().userId());
        unread.forEach(n -> n.setRead(true));
        notifications.saveAll(unread);
    }

    @PostMapping("/{id}/read")
    public void read(@PathVariable UUID id) {
        AuthUser me = CurrentUser.get();
        notifications.findById(id).filter(n -> n.getUserId().equals(me.userId())).ifPresent(n -> {
            n.setRead(true);
            notifications.save(n);
        });
    }
}
