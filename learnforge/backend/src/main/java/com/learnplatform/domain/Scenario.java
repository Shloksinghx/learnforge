package com.learnplatform.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "scenario")
@Getter
@Setter
public class Scenario {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id") private UUID orgId;
    @Column(name = "persona_id") private UUID personaId;
    @Column(name = "course_id") private UUID courseId;
    private String title;
    @Column(columnDefinition = "text") private String description;
    @Column(columnDefinition = "text") private String objective;
    @Column(name = "expected_behaviour", columnDefinition = "text") private String expectedBehaviour;
    @Column(name = "mandatory_info", columnDefinition = "text") private String mandatoryInfo;
    @Column(name = "prohibited_statements", columnDefinition = "text") private String prohibitedStatements;
    @Column(name = "evaluation_criteria", columnDefinition = "text") private String evaluationCriteria;
    @Column(name = "success_criteria", columnDefinition = "text") private String successCriteria;
    private int difficulty = 3;
    @Column(name = "time_limit_minutes") private Integer timeLimitMinutes;
    @Column(name = "created_at") private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
