package com.learnplatform.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "mock_test")
@Getter
@Setter
public class MockTest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id") private UUID orgId;
    @Column(name = "course_id") private UUID courseId;
    private String title;
    @Column(name = "question_count") private int questionCount;
    private int minutes;
    @Column(name = "easy_pct") private int easyPct;
    @Column(name = "medium_pct") private int mediumPct;
    @Column(name = "hard_pct") private int hardPct;
    @Column(name = "negative_marking") private boolean negativeMarking;
    @Column(name = "created_at") private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
