package com.learnplatform.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "simulation")
@Getter
@Setter
public class Simulation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id") private UUID orgId;
    @Column(name = "user_id") private UUID userId;
    @Column(name = "persona_id") private UUID personaId;
    private int difficulty;
    private String status = "ACTIVE";
    @Column(name = "evaluation_json", columnDefinition = "text") private String evaluationJson;
    @Column(name = "overall_score") private Integer overallScore;
    @Column(name = "started_at") private Instant createdAt;
    @Column(name = "ended_at") private Instant endedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
