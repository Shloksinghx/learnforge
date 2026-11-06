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
    private String mode = "STANDARD";
    private String section;
    @Column(name = "time_limit_minutes") private Integer timeLimitMinutes;
    @Column(name = "negative_marking") private boolean negativeMarking;
    private Integer score;
    @Column(name = "correct_count") private Integer correctCount;
    @Column(name = "wrong_count") private Integer wrongCount;
    private int total;
    @Column(name = "started_at") private Instant createdAt;
    @Column(name = "submitted_at") private Instant submittedAt;

    @Column(name = "pass_mark") private int passMark = 60;
    @Column(name = "mock_test_id") private UUID mockTestId;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
