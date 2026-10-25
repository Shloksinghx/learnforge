package com.learnplatform.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "attempt_answer")
@Getter
@Setter
public class AttemptAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "attempt_id") private UUID attemptId;
    @Column(name = "question_id") private UUID questionId;
    @Column(name = "sort_order") private int sortOrder;
    @Column(name = "selected_index") private Integer selectedIndex;
    private Boolean correct;
    @Transient private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
