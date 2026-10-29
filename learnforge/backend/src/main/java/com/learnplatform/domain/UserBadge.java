package com.learnplatform.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "user_badge")
@Getter
@Setter
public class UserBadge {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "org_id") private UUID orgId;
    @Column(name = "user_id") private UUID userId;
    private String code;
    @Column(name = "created_at") private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
