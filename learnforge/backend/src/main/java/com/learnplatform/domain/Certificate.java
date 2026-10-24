package com.learnplatform.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "certificate")
@Getter
@Setter
public class Certificate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id") private UUID orgId;
    private String code;
    @Column(name = "learner_id") private UUID learnerId;
    @Column(name = "learner_name") private String learnerName;
    private String title;
    private int score;
    private String issuer;
    @Column(name = "issued_at") private Instant createdAt;
    @Column(name = "valid_until") private Instant validUntil;
    private boolean revoked;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
