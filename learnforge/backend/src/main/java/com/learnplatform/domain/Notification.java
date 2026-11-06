package com.learnplatform.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "notification")
@Getter
@Setter
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id") private UUID orgId;
    @Column(name = "user_id") private UUID userId;
    private String title;
    @Column(columnDefinition = "text") private String body;
    private String link;
    private String ref;
    @Column(name = "is_read") private boolean read;
    @Column(name = "created_at") private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
