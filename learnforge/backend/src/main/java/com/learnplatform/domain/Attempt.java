package com.learnplatform.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "attempt")
@Getter
@Setter
public class Attempt {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id") private UUID orgId;
    @Column(name = "course_id") private UUID courseId;
    @Column(name = "user_id") private UUID userId;
    private String status = "IN_PROGRESS";
    private Integer score;
    @Column(name = "correct_count") private Integer correctCount;
    private int total;
    @Column(name = "started_at") private Instant createdAt;
    @Column(name = "submitted_at") private Instant submittedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
